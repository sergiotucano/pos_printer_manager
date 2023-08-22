import 'package:esc_pos_utils_plus/esc_pos_utils.dart';
import 'package:pos_printer_manager/enums/connection_response.dart';
import 'package:pos_printer_manager/models/pos_printer.dart';

abstract class PrinterManager {
  PaperSize paperSize;
  CapabilityProfile profile;
  Generator generator;
  bool isConnected = false;
  String address;
  int vendorId;
  int productId;
  int deviceId;
  int port = 9100;
  int spaceBetweenRows = 5;
  POSPrinter printer;

  Future<ConnectionResponse> connect({Duration timeout});

  Future<ConnectionResponse> writeBytes(List<int> data, {bool isDisconnect = true});

  Future<ConnectionResponse> disconnect({Duration timeout});

}
