
import 'package:esc_pos_utils_plus/esc_pos_utils_plus.dart';
import 'package:pos_printer_manager/enums/connection_response.dart';
import 'package:pos_printer_manager/models/pos_printer.dart';

abstract class PrinterManager {
  late PaperSize paperSize;
  late CapabilityProfile profile;
  late Generator generator;
  bool isConnected = false;
  String address = '';
  int vendorId = 0;
  int productId = 0;
  int deviceId = 0;
  int port = 9100;
  int spaceBetweenRows = 5;
  late POSPrinter printer;

  Future<ConnectionResponse> connect({Duration timeout});

  Future<ConnectionResponse> writeBytes(List<int> data, {bool isDisconnect = true});

  Future<ConnectionResponse> disconnect({Duration timeout});

}
