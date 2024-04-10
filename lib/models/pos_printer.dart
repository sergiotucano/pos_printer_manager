import 'package:pos_printer_manager/enums/connection_type.dart';

class POSPrinter {
  String? id;
  String? name;
  String? address;
  int? deviceId;
  int? vendorId;
  int? productId;
  bool connected;
  int type;
  ConnectionType? connectionType;

  factory POSPrinter.instance() => POSPrinter();

  POSPrinter({
    this.id,
    this.name,
    this.address,
    this.deviceId,
    this.vendorId,
    this.productId,
    this.connected = false,
    this.type = 0,
    this.connectionType,
  });
}

