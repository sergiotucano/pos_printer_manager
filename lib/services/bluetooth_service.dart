import 'dart:io';
import 'package:blue_thermal_printer/blue_thermal_printer.dart' as thermal;
import 'package:pos_printer_manager/models/bluetooth_printer.dart';
import '../pos_printer_manager.dart';

class BluetoothService {
  static Future<List<BluetoothPrinter>> findBluetoothDevice() async {
    List<BluetoothPrinter> devices = [];
    if (Platform.isAndroid || Platform.isIOS) {
      thermal.BlueThermalPrinter bluetooth =
          thermal.BlueThermalPrinter.instance;

      var results = await bluetooth.getBondedDevices();
      devices = results
          .map(
            (d) => BluetoothPrinter(
          id: d.address,
          address: d.address,
          name: d.name,
          type: d.type,
        ),
      )
          .toList();
    }

    return devices;
  }
}