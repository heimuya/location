package com.xd.location;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.EventChannel;

import android.location.LocationManager;
import android.location.Location;
import android.location.GnssMeasurementsEvent;
import android.location.GnssClock;
import android.location.GnssMeasurement;
import android.location.GnssStatus;
import android.location.GnssNavigationMessage;
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

  private Map<String, String> navigationData = new HashMap<String, String>();

  private EventChannel eChannel;
  private EventChannel.EventSink eventSink;

  private LocationManager mLocationManager;

  private GnssLocationListener locationListener = new GnssLocationListener();

  private GnssNavigationMessage.Callback gnssNavigationCallback = new GnssNavigationMessage.Callback() {
    @Override
    public void onGnssNavigationMessageReceived(GnssNavigationMessage event) {
      LocationPlugin.this.onGnssNavigationMessageReceived(event);
    }
  };

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

  private GnssStatus gnssStatus; // GnssStatus数据

  private GnssStatus.Callback gnssStatusCallback = new GnssStatus.Callback() {
    @Override
    public void onSatelliteStatusChanged(GnssStatus status) {
      LocationPlugin.this.gnssStatus = status;
    }

    @Override
    public void onStarted() {
      // Log.i("location", "gnss started");
    }

    @Override
    public void onStopped() {
      // Log.i("location", "gnss stopped");
    }
  };

  public void onGnssNavigationMessageReceived(GnssNavigationMessage event) {
    if (event.getStatus() != GnssNavigationMessage.STATUS_UNKNOWN) {
      navigationData.put(getNavigationMessageType(event.getType(), event.getSvid()), new String(event.getData()));
    }
  }


  public String getNavigationMessageType(int stype, int svid) {
    Locale locale = Locale.getDefault();
    switch (stype) {
      case GnssNavigationMessage.TYPE_GPS_L1CA:
      case GnssNavigationMessage.TYPE_GPS_L2CNAV:
      case GnssNavigationMessage.TYPE_GPS_L5CNAV:
      case GnssNavigationMessage.TYPE_GPS_CNAV2:
        return "G#" + String.format(locale, "%02d", svid);
      case GnssNavigationMessage.TYPE_GLO_L1CA:
        return "R#" + String.format(locale, "%02d", svid);
      case GnssNavigationMessage.TYPE_BDS_D1:
      case GnssNavigationMessage.TYPE_BDS_D2:
      case GnssNavigationMessage.TYPE_BDS_CNAV1:
        return "C#" + String.format(locale, "%02d", svid);
      case GnssNavigationMessage.TYPE_GAL_I:
      case GnssNavigationMessage.TYPE_GAL_F:
        return "E#" + String.format(locale, "%02d", svid);
      case GnssNavigationMessage.TYPE_QZS_L1CA:
        return "J#" + String.format(locale, "%02d", svid);
      default:
        return "U#" + String.format(locale, "%02d", svid);
    }
  }

  public void onGnssMeasurementsReceived(GnssMeasurementsEvent event) {
    Map<String, Object> data = new HashMap<String, Object>();
    GnssClock gnssClock = event.getClock();
    data.put("rx_clock", formatClock(gnssClock));

    Location loc = mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
    if (loc != null) {
      data.put("accuracy", loc.getAccuracy());
      data.put("log", loc.getLongitude());
      data.put("lat", loc.getLatitude());
      data.put("speed", loc.getSpeed());
      data.put("speed_accuracy", loc.getSpeedAccuracyMetersPerSecond());
    }

    data.put("satellite_count", 0);
    if (gnssStatus != null) {
      data.put("satellite_count", gnssStatus.getSatelliteCount());

      List<Map<String, Object>> satelliteData = new ArrayList<Map<String, Object>>();
      for (GnssMeasurement measurement : event.getMeasurements()) { // 遍历所有的卫星数据
        GnssData gnssdata = new GnssData(measurement, gnssClock, gnssStatus);
        Map<String, Object> temp = new HashMap<String, Object>();
        temp.put("svid", gnssdata.getPRN());
        temp.put("ttx", gnssdata.getTTx());
        temp.put("azimuth_degrees", gnssdata.getAzimuthDegrees());
        temp.put("elevation_degrees", gnssdata.getElevationDegrees());
        temp.put("cn0_db", gnssdata.getCn0DbHz());
        // temp.put("carrier_frequency", gnssdata.getCarrierFrequencyHz());
        // temp.put("base_cn0_db", gnssdata.getBasebandCn0DbHz());
        // temp.put("navigation", navigationData.get(gnssdata.getPRN()));

        satelliteData.add(temp);
      }

      data.put("satellites", satelliteData);
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
    mLocationManager.registerGnssMeasurementsCallback(gnssMeasurementEventListener, null);
    mLocationManager.registerGnssStatusCallback(gnssStatusCallback, null);
    mLocationManager.registerGnssNavigationMessageCallback(gnssNavigationCallback, null);

    mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 30000, 0, locationListener);
  }

  // 关闭定位数据监测
  public void closeLocationListen() {
    mLocationManager.unregisterGnssMeasurementsCallback(gnssMeasurementEventListener);
    mLocationManager.unregisterGnssStatusCallback(gnssStatusCallback);
    mLocationManager.unregisterGnssNavigationMessageCallback(gnssNavigationCallback);
  }

  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    mChannel.setMethodCallHandler(null);
  }
}
