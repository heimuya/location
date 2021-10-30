package com.xd.location;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.EventChannel;

import android.location.LocationManager;
import 	android.location.Location;
import android.location.GnssMeasurementsEvent;
import android.location.GnssClock;
import android.location.GnssMeasurement;
import android.content.Context;
import android.app.Activity;
import android.util.Log;
import android.os.Looper;
import android.os.Handler;


/** LocationPlugin */
public class LocationPlugin implements FlutterPlugin, ActivityAware, MethodCallHandler {
  private String METHOD_CHANNEL = "location";

  private String EVENT_CHANNEL = "com.xd.location/location";

  private Handler uiThreadHandler = new Handler(Looper.getMainLooper());

  private Activity mActivity;
  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  private MethodChannel mChannel;

  private EventChannel eChannel;
  private EventChannel.EventSink eventSink;

  private LocationManager mLocationManager;

  private GnssMeasurementsEvent.Callback gnssMeasurementEventListener = new GnssMeasurementsEvent.Callback() {
    @Override
    public void onGnssMeasurementsReceived(GnssMeasurementsEvent eventArgs) {
      LocationPlugin.this.onGnssMeasurementsReceived(eventArgs);
    }

    @Override
    public void onStatusChanged(int status) {
        LocationPlugin.this.onStatusChanged(status);
    }
  };

  public void onGnssMeasurementsReceived(GnssMeasurementsEvent event) {
    Map<String, Object> data = new HashMap<String, Object>();
    GnssClock gnssClock = event.getClock();
    data.put("rx_clock", formatClock(gnssClock));

    Location loc = mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
    if (loc == null) {
      loc = mLocationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
    }

    data.put("accuracy", loc.getAccuracy());
    data.put("log", loc.getLongitude());
    data.put("lat", loc.getLatitude());
    data.put("speed", loc.getSpeed());
    data.put("speed_accuracy", loc.getSpeedAccuracyMetersPerSecond());

    List<Map<String, Object>> satelliteData = new ArrayList<Map<String, Object>>();
    for (GnssMeasurement measurement : event.getMeasurements()) { // 遍历所有的卫星数据
      GnssData gnssdata = new GnssData(measurement, gnssClock);
    }

    uiThreadHandler.post(() -> eventSink.success(data));
  }

  public Map<String, Object> formatClock(GnssClock gnssClock) {
    Map<String, Object> clock = new HashMap<String, Object>();
    clock.put("leap_second", gnssClock.getLeapSecond());
    clock.put("time_nanos", gnssClock.getTimeNanos());
    if (gnssClock.hasTimeUncertaintyNanos()) {
      clock.put("time_uncertaint_nanos", gnssClock.getTimeUncertaintyNanos());
    }
    if (gnssClock.hasFullBiasNanos()) {
      clock.put("full_bias_nanos", gnssClock.getFullBiasNanos());
    }
    if (gnssClock.hasBiasNanos()) {
      clock.put("bias_nanos", gnssClock.getBiasNanos());
    }
    if (gnssClock.hasBiasUncertaintyNanos()) {
      clock.put("bias_uncertaint_nanos", gnssClock.getBiasUncertaintyNanos());
    }

    if (gnssClock.hasDriftNanosPerSecond()) {
      clock.put("drift_nanos_second", gnssClock.getDriftNanosPerSecond());
    }
    if (gnssClock.hasDriftUncertaintyNanosPerSecond()) {
      clock.put("bias_uncertaint_nanos", gnssClock.getDriftUncertaintyNanosPerSecond());
    }
    
    clock.put("hardware_clock_discontinuity_count", gnssClock.getHardwareClockDiscontinuityCount());

    return clock;
  }

  // 卫星监测状态变化回调
  public void onStatusChanged(int status) {
    // TODO 处理状态数据
  }

  @Override
  public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
    mChannel = new MethodChannel(flutterPluginBinding.getBinaryMessenger(), METHOD_CHANNEL);
    mChannel.setMethodCallHandler(this);

    eChannel = new EventChannel(flutterPluginBinding.getBinaryMessenger(), EVENT_CHANNEL);
    eChannel.setStreamHandler(new EventChannel.StreamHandler() {
      @Override
      public void onListen(Object obj, EventChannel.EventSink eSink) {
        LocationPlugin.this.eventSink = eSink;
      }

      @Override
      public void onCancel(Object obj) {
        LocationPlugin.this.eventSink = null;
      }
    });
  }
  
  @Override
  public void onAttachedToActivity(@NonNull final ActivityPluginBinding binding) {
    mActivity = binding.getActivity();
    mLocationManager = (LocationManager) mActivity.getSystemService(Context.LOCATION_SERVICE);
  }

  @Override
  public void onDetachedFromActivityForConfigChanges() {

  }

  @Override
  public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {

  }

  @Override
  public void onDetachedFromActivity() {

  }

  @Override
  public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
    if (call.method.equals("getPlatformVersion")) {
      result.success("Android " + android.os.Build.VERSION.RELEASE);
    } else if (call.method.equals("getEventMessage")) {
      Map<String, Object> event = new HashMap<String, Object>();
      event.put("username", "admin");
      event.put("age", 11);
      eventSink.success(event);

      result.success("Message: send ok!");
    } else if (call.method.equals("open")) {
      openLocationListen();
      result.success(true);
    } else if (call.method.equals("close")) {
      closeLocationListen();
      result.success(true);
    } else {
      result.notImplemented();
    }
  }

  // 开启定位数据监测
  public void openLocationListen() {
    mLocationManager.registerGnssMeasurementsCallback(gnssMeasurementEventListener);
  }

  // 关闭定位数据监测
  public void closeLocationListen() {
    mLocationManager.unregisterGnssMeasurementsCallback(gnssMeasurementEventListener);
  }

  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    mChannel.setMethodCallHandler(null);
  }
}
