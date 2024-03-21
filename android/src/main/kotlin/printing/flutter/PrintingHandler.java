package printing.flutter;

import android.app.Activity;
import android.print.PrintAttributes;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.util.ArrayList;
import java.util.HashMap;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;

public class PrintingHandler implements MethodChannel.MethodCallHandler {
    private final Activity activity;
    private final MethodChannel channel;

    PrintingHandler(@NonNull Activity activity, @NonNull MethodChannel channel) {
        this.activity = activity;
        this.channel = channel;
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull MethodChannel.Result result) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            switch (call.method) {
                case "printPdf": {
                    final String name = call.argument("name");
                    Double width = call.argument("width");
                    Double height = call.argument("height");

                    final PrintingJob printJob =
                            new PrintingJob(activity, this, (int) call.argument("job"));
                    assert name != null;
                    printJob.printPdf(name, width, height);

                    result.success(1);
                    break;
                }
                case "cancelJob": {
                    final PrintingJob printJob =
                            new PrintingJob(activity, this, (int) call.argument("job"));
                    printJob.cancelJob(null);
                    result.success(1);
                    break;
                }
                case "sharePdf": {
                    final byte[] document = call.argument("doc");
                    final String name = call.argument("name");
                    final String subject = call.argument("subject");
                    final String body = call.argument("body");
                    final ArrayList<String> emails = call.argument("emails");
                    PrintingJob.sharePdf(activity, document, name, subject, body, emails);
                    result.success(1);
                    break;
                }
                case "printingInfo": {
                    result.success(PrintingJob.printingInfo());
                    break;
                }
                case "rasterPdf": {
                    final byte[] document = call.argument("doc");
                    final ArrayList<Integer> pages = call.argument("pages");
                    Double scale = call.argument("scale");
                    final PrintingJob printJob =
                            new PrintingJob(activity, this, (int) call.argument("job"));
                    printJob.rasterPdf(document, pages, scale);
                    result.success(1);
                    break;
                }
                default:
                    result.notImplemented();
                    break;
            }
        } else {
            result.notImplemented();
        }
    }

    /// Request the Pdf document from flutter
    void onLayout(final PrintingJob printJob, Double width, double height, double marginLeft,
            double marginTop, double marginRight, double marginBottom) {
        HashMap<String, Object> args = new HashMap<>();
        args.put("width", width);
        args.put("height", height);

        args.put("marginLeft", marginLeft);
        args.put("marginTop", marginTop);
        args.put("marginRight", marginRight);
        args.put("marginBottom", marginBottom);
        args.put("job", printJob.index);

        channel.invokeMethod("onLayout", args, new MethodChannel.Result() {
            @Override
            public void success(Object result) {
                if (result instanceof byte[]) {
                    printJob.setDocument((byte[]) result);
                } else {
                    printJob.cancelJob("Unknown data received");
                }
            }

            @Override
            public void error(String errorCode, String errorMessage, Object errorDetails) {
                printJob.cancelJob(errorMessage);
            }

            @Override
            public void notImplemented() {
                printJob.cancelJob("notImplemented");
            }
        });
    }

    /// send completion status to flutter
    void onCompleted(PrintingJob printJob, boolean completed, String error) {
        HashMap<String, Object> args = new HashMap<>();
        args.put("completed", completed);

        args.put("error", error);
        args.put("job", printJob.index);

        channel.invokeMethod("onCompleted", args);
    }

    /// send pdf to raster data result to flutter
    void onPageRasterized(PrintingJob printJob, byte[] imageData, int width, int height) {
        HashMap<String, Object> args = new HashMap<>();
        args.put("image", imageData);
        args.put("width", width);
        args.put("height", height);
        args.put("job", printJob.index);

        channel.invokeMethod("onPageRasterized", args);
    }

    /// The page has been converted to an image
    void onPageRasterEnd(PrintingJob printJob, String error) {
        HashMap<String, Object> args = new HashMap<>();
        args.put("job", printJob.index);
        if (error != null) {
            args.put("error", error);
        }

        channel.invokeMethod("onPageRasterEnd", args);
    }
}
