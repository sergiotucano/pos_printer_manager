/*
 * Copyright (C) 2017, David PHAM-VAN <dev.nfet.net@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package printing.flutter;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;
import android.print.PrintJob;
import android.print.PrintJobInfo;
import android.print.PrintManager;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * PrintJob
 */
@TargetApi(Build.VERSION_CODES.KITKAT)
public class PrintingJob extends PrintDocumentAdapter {
    private static PrintManager printManager;
    private final Context context;
    private final PrintingHandler printing;
    private PrintJob printJob;
    private byte[] documentData;
    private String jobName;
    private LayoutResultCallback callback;
    int index;

    PrintingJob(Context context, PrintingHandler printing, int index) {
        this.context = context;
        this.printing = printing;
        this.index = index;
        printManager = (PrintManager) context.getSystemService(Context.PRINT_SERVICE);
    }

    static HashMap<String, Object> printingInfo() {
        final boolean canPrint = android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;
        final boolean canRaster = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;

        HashMap<String, Object> result = new HashMap<>();
        result.put("directPrint", false);
        result.put("dynamicLayout", canPrint);
        result.put("canPrint", canPrint);
        result.put("canShare", true);
        result.put("canRaster", canRaster);
        return result;
    }

    @Override
    public void onWrite(PageRange[] pageRanges, ParcelFileDescriptor parcelFileDescriptor,
            CancellationSignal cancellationSignal, WriteResultCallback writeResultCallback) {
        OutputStream output = null;
        try {
            output = new FileOutputStream(parcelFileDescriptor.getFileDescriptor());
            output.write(documentData, 0, documentData.length);
            writeResultCallback.onWriteFinished(new PageRange[] {PageRange.ALL_PAGES});
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (output != null) {
                    output.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onLayout(PrintAttributes oldAttributes, PrintAttributes newAttributes,
            CancellationSignal cancellationSignal, LayoutResultCallback callback, Bundle extras) {
        // Respond to cancellation request
        if (cancellationSignal.isCanceled()) {
            callback.onLayoutCancelled();
            return;
        }

        this.callback = callback;

        PrintAttributes.MediaSize size = newAttributes.getMediaSize();
        PrintAttributes.Margins margins = newAttributes.getMinMargins();
        assert size != null;
        assert margins != null;

        printing.onLayout(this, size.getWidthMils() * 72.0 / 1000.0,
                size.getHeightMils() * 72.0 / 1000.0, margins.getLeftMils() * 72.0 / 1000.0,
                margins.getTopMils() * 72.0 / 1000.0, margins.getRightMils() * 72.0 / 1000.0,
                margins.getBottomMils() * 72.0 / 1000.0);
    }

    @Override
    public void onFinish() {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final boolean[] wait = {true};
                    int count = 5 * 60 * 10; // That's 10 minutes.
                    while (wait[0]) {
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                int state = printJob == null ? PrintJobInfo.STATE_FAILED
                                                             : printJob.getInfo().getState();

                                if (state == PrintJobInfo.STATE_COMPLETED) {
                                    printing.onCompleted(PrintingJob.this, true, null);
                                    wait[0] = false;
                                } else if (state == PrintJobInfo.STATE_CANCELED) {
                                    printing.onCompleted(PrintingJob.this, false, null);
                                    wait[0] = false;
                                } else if (state == PrintJobInfo.STATE_FAILED) {
                                    printing.onCompleted(
                                            PrintingJob.this, false, "Unable to print");
                                    wait[0] = false;
                                }
                            }
                        });

                        if (--count <= 0) {
                            throw new Exception("Timeout waiting for the job to finish");
                        }

                        if (wait[0]) {
                            Thread.sleep(200);
                        }
                    }
                } catch (final Exception e) {
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            printing.onCompleted(PrintingJob.this,
                                    printJob != null && printJob.isCompleted(), e.getMessage());
                        }
                    });
                }

                printJob = null;
            }
        });

        thread.start();
    }

    void printPdf(String name, Double width, Double height) {
        jobName = name;

        PrintAttributes.Builder attrBuilder = new PrintAttributes.Builder();

        int widthMils = Double.valueOf(width * 1000.0 / 72.0).intValue();
        int heightMils = Double.valueOf(height * 1000.0 / 72.0).intValue();

        PrintAttributes.MediaSize mediaSize = null;
        boolean isPortrait = heightMils >= widthMils;

        // get the media size from predefined media sizes
        for (PrintAttributes.MediaSize size : getAllPredefinedSizes()) {
            // https://github.com/DavBfr/dart_pdf/issues/635
            int err = 20;
            PrintAttributes.MediaSize m = isPortrait ? size.asPortrait() : size.asLandscape();
            if ((widthMils + err) >= m.getWidthMils() && (widthMils - err) <= m.getWidthMils()
                    && (heightMils + err) >= m.getHeightMils()
                    && (heightMils - err) <= m.getHeightMils()) {
                mediaSize = m;
                break;
            }
        }

        if (mediaSize == null) {
            mediaSize = isPortrait ? PrintAttributes.MediaSize.UNKNOWN_PORTRAIT
                                   : PrintAttributes.MediaSize.UNKNOWN_LANDSCAPE;
        }

        attrBuilder.setMediaSize(mediaSize);
        PrintAttributes attrib = attrBuilder.build();
        printJob = printManager.print(name, this, attrib);
    }

    List<PrintAttributes.MediaSize> getAllPredefinedSizes() {
        List<PrintAttributes.MediaSize> sizes = new ArrayList<>();

        // ISO sizes
        sizes.add(PrintAttributes.MediaSize.ISO_A0);
        sizes.add(PrintAttributes.MediaSize.ISO_A1);
        sizes.add(PrintAttributes.MediaSize.ISO_A2);
        sizes.add(PrintAttributes.MediaSize.ISO_A3);
        sizes.add(PrintAttributes.MediaSize.ISO_A4);
        sizes.add(PrintAttributes.MediaSize.ISO_A5);
        sizes.add(PrintAttributes.MediaSize.ISO_A6);
        sizes.add(PrintAttributes.MediaSize.ISO_A7);
        sizes.add(PrintAttributes.MediaSize.ISO_A8);
        sizes.add(PrintAttributes.MediaSize.ISO_A9);
        sizes.add(PrintAttributes.MediaSize.ISO_A10);
        sizes.add(PrintAttributes.MediaSize.ISO_B0);
        sizes.add(PrintAttributes.MediaSize.ISO_B1);
        sizes.add(PrintAttributes.MediaSize.ISO_B2);
        sizes.add(PrintAttributes.MediaSize.ISO_B3);
        sizes.add(PrintAttributes.MediaSize.ISO_B4);
        sizes.add(PrintAttributes.MediaSize.ISO_B5);
        sizes.add(PrintAttributes.MediaSize.ISO_B6);
        sizes.add(PrintAttributes.MediaSize.ISO_B7);
        sizes.add(PrintAttributes.MediaSize.ISO_B8);
        sizes.add(PrintAttributes.MediaSize.ISO_B9);
        sizes.add(PrintAttributes.MediaSize.ISO_B10);
        sizes.add(PrintAttributes.MediaSize.ISO_C0);
        sizes.add(PrintAttributes.MediaSize.ISO_C1);
        sizes.add(PrintAttributes.MediaSize.ISO_C2);
        sizes.add(PrintAttributes.MediaSize.ISO_C3);
        sizes.add(PrintAttributes.MediaSize.ISO_C4);
        sizes.add(PrintAttributes.MediaSize.ISO_C5);
        sizes.add(PrintAttributes.MediaSize.ISO_C6);
        sizes.add(PrintAttributes.MediaSize.ISO_C7);
        sizes.add(PrintAttributes.MediaSize.ISO_C8);
        sizes.add(PrintAttributes.MediaSize.ISO_C9);
        sizes.add(PrintAttributes.MediaSize.ISO_C10);

        // North America
        sizes.add(PrintAttributes.MediaSize.NA_LETTER);
        sizes.add(PrintAttributes.MediaSize.NA_GOVT_LETTER);
        sizes.add(PrintAttributes.MediaSize.NA_LEGAL);
        sizes.add(PrintAttributes.MediaSize.NA_JUNIOR_LEGAL);
        sizes.add(PrintAttributes.MediaSize.NA_LEDGER);
        sizes.add(PrintAttributes.MediaSize.NA_TABLOID);
        sizes.add(PrintAttributes.MediaSize.NA_INDEX_3X5);
        sizes.add(PrintAttributes.MediaSize.NA_INDEX_4X6);
        sizes.add(PrintAttributes.MediaSize.NA_INDEX_5X8);
        sizes.add(PrintAttributes.MediaSize.NA_MONARCH);
        sizes.add(PrintAttributes.MediaSize.NA_QUARTO);
        sizes.add(PrintAttributes.MediaSize.NA_FOOLSCAP);

        // Chinese
        sizes.add(PrintAttributes.MediaSize.ROC_8K);
        sizes.add(PrintAttributes.MediaSize.ROC_16K);
        sizes.add(PrintAttributes.MediaSize.PRC_1);
        sizes.add(PrintAttributes.MediaSize.PRC_2);
        sizes.add(PrintAttributes.MediaSize.PRC_3);
        sizes.add(PrintAttributes.MediaSize.PRC_4);
        sizes.add(PrintAttributes.MediaSize.PRC_5);
        sizes.add(PrintAttributes.MediaSize.PRC_6);
        sizes.add(PrintAttributes.MediaSize.PRC_7);
        sizes.add(PrintAttributes.MediaSize.PRC_8);
        sizes.add(PrintAttributes.MediaSize.PRC_9);
        sizes.add(PrintAttributes.MediaSize.PRC_10);
        sizes.add(PrintAttributes.MediaSize.PRC_16K);
        sizes.add(PrintAttributes.MediaSize.OM_PA_KAI);
        sizes.add(PrintAttributes.MediaSize.OM_DAI_PA_KAI);
        sizes.add(PrintAttributes.MediaSize.OM_JUURO_KU_KAI);

        // Japanese
        sizes.add(PrintAttributes.MediaSize.JIS_B10);
        sizes.add(PrintAttributes.MediaSize.JIS_B9);
        sizes.add(PrintAttributes.MediaSize.JIS_B8);
        sizes.add(PrintAttributes.MediaSize.JIS_B7);
        sizes.add(PrintAttributes.MediaSize.JIS_B6);
        sizes.add(PrintAttributes.MediaSize.JIS_B5);
        sizes.add(PrintAttributes.MediaSize.JIS_B4);
        sizes.add(PrintAttributes.MediaSize.JIS_B3);
        sizes.add(PrintAttributes.MediaSize.JIS_B2);
        sizes.add(PrintAttributes.MediaSize.JIS_B1);
        sizes.add(PrintAttributes.MediaSize.JIS_B0);
        sizes.add(PrintAttributes.MediaSize.JIS_EXEC);
        sizes.add(PrintAttributes.MediaSize.JPN_CHOU4);
        sizes.add(PrintAttributes.MediaSize.JPN_CHOU3);
        sizes.add(PrintAttributes.MediaSize.JPN_CHOU2);
        sizes.add(PrintAttributes.MediaSize.JPN_HAGAKI);
        sizes.add(PrintAttributes.MediaSize.JPN_OUFUKU);
        sizes.add(PrintAttributes.MediaSize.JPN_KAHU);
        sizes.add(PrintAttributes.MediaSize.JPN_KAKU2);
        sizes.add(PrintAttributes.MediaSize.JPN_YOU4);

        return sizes;
    }

    void cancelJob(String message) {
        if (callback != null) callback.onLayoutCancelled();
        if (printJob != null) printJob.cancel();
        printing.onCompleted(PrintingJob.this, false, message);
    }

    void setDocument(byte[] data) {
        documentData = data;

        PrintDocumentInfo info = new PrintDocumentInfo.Builder(jobName)
                                         .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                                         .build();

        // Content layout reflow is complete
        callback.onLayoutFinished(info, true);
    }
}
