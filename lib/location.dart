import 'dart:async';

import 'package:flutter/services.dart';
import 'package:permission_handler/permission_handler.dart';

class Location {
  static const MethodChannel _channel = MethodChannel('location');
  static const EventChannel _eChannel =
      EventChannel('com.xd.location/location');

  static Future<String?> get platformVersion async {
    final String? version = await _channel.invokeMethod('getPlatformVersion');
    return version;
  }

  static Future<String?> eventMessage() async {
    final String? message = await _channel.invokeMethod('getEventMessage');
    return message;
  }

  // 开启定位
  static Future<bool> open() async {
    if (await Permission.location.request().isGranted) {
      return await _channel.invokeMethod("open");
    }
    return false;
  }

  // 关闭定位
  static Future<bool> close() async {
    return await _channel.invokeMethod("close");
  }

  // 监听native event数据流
  static void onListenData(onEvent, onError) {
    _eChannel.receiveBroadcastStream().listen(onEvent, onError: onError);
  }
}
