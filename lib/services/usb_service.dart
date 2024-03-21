import 'dart:io';
import 'package:flutter/services.dart';
import 'package:flutter_usb_printer/flutter_usb_printer.dart';
import 'package:pos_printer_manager/models/printer.dart';
import 'package:pos_printer_manager/models/usb_printer.dart';
import 'package:pos_printer_manager/pos_printer_manager.dart';

class USBService {

  static Future<List<Printer>> listPrinters() async {
    const MethodChannel _channel = MethodChannel('printing.flutter');

    final params = <String, dynamic>{};
    final list =
    await _channel.invokeMethod<List<dynamic>>('listPrinters', params);

    final printers = <Printer>[];

    for (final printer in list!) {
      printers.add(Printer.fromMap(printer));
    }

    return printers;
  }

  static Future<List<USBPrinter>> findUSBPrinter() async {
    List<USBPrinter> devices = [];

    if (Platform.isWindows || Platform.isMacOS) {
      var results = await listPrinters();
      devices = [
        ...results
            .where((entry) => entry.isAvailable)
            .toList()
            .map((e) => USBPrinter(
                  name: e.name,
                  address: e.url,
                ))
            .toList()
      ];
    } else if (Platform.isAndroid) {
      var results = await FlutterUsbPrinter.getUSBDeviceList();

      devices = [
        ...results
            .map((e) => USBPrinter(
                  name: e["productName"],
                  address: e["manufacturer"],
                  vendorId: int.tryParse(e["vendorId"]) ?? 0,
                  productId: int.tryParse(e["productId"])?? 0,
                  deviceId: int.tryParse(e["deviceId"])?? 0,
                ))
            .toList()
      ];
    } else {
      /// no support
    }

    return devices;
  }


}
