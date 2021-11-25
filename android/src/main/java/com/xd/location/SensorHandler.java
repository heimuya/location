package com.xd.location;

import java.util.HashMap;
import java.util.Map;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;

public class SensorHandler {
  private static final String TAG = "Sensor";
  private static final boolean DEBUG = false;

  private Activity mActivity;

  // 摇晃速度临界值
  private static final int SPEED_SHRESHOLD = 600;

  // 两次检测的时间间隔
  private static final int UPTATE_INTERVAL_TIME = 200;

  // 上次检测时间
  private long lastUpdateTime;
  private SensorManager sensorMag;
  private Sensor aSensor;
  private Sensor mSensor;

  //保存上一次位置记录
  float lastX = 0;
  float lastY = 0;
  float lastZ = 0;

  float tMax = 1.0f;

  private int origen = 0; // 屏幕方向
  private double speed = 0.0; // 速度
  private double speedH = 0.0; // 水平速度
  private double speedV = 0.0; // 垂直速度
  private float angle = 0.0f; // 角度

  float[] accelerometerValues = new float[3];
  float[] magneticFieldValues = new float[3];

  private SensorEventListener sensorEventListener = new SensorEventListener() {
    @Override
    public void onSensorChanged(SensorEvent event) {
      if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
        accelerometerValues = event.values;

        long currentUpdateTime = System.currentTimeMillis();
        long timeInterval = currentUpdateTime - lastUpdateTime;
        if (timeInterval < UPTATE_INTERVAL_TIME) {
          return;
        }

        lastUpdateTime = currentUpdateTime;
        float x = event.values[SensorManager.DATA_X];
        float y = event.values[SensorManager.DATA_Y];
        float z = event.values[SensorManager.DATA_Z];

        calcOrigen(x, y, z);

        calcSpeed(x, y, z, timeInterval);
        return;
      } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
        magneticFieldValues = event.values;
      }

      calcOrientation();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
  };

  public void calcOrigen(float x, float y, float z) {
    float absx = Math.abs(x);
    float absy = Math.abs(y);
    float absz = Math.abs(z);

    if (absx > absy && absx > absz) {
      if (x > tMax) {
        origen = 1; // turn left
      } else {
        origen = 2; // turn right
      }
    } else if (absy > absx && absy > absz) {
      if (y > tMax) {
        origen = 3; // turn up
      } else {
        origen = 4; // turn down
      }
    } else if (absz > absx && absz > absy) {
      if (z > 0) {
        origen = 5; // screen up
      } else {
        origen = 6; // screen down
      }
    } else {
      origen = 0; // unknown
    }
  }

  public void calcSpeed(float x, float y, float z, long timeInterval) {
    // 计算速度
    float deltaX = x - lastX;
    float deltaY = y - lastY;
    float deltaZ = z - lastZ;

    lastX = x;
    lastY = y;
    lastZ = z;

    double speedTemp = Math.sqrt(deltaX*deltaX + deltaY*deltaY + deltaZ*deltaZ)/timeInterval * 10000;
    speedH = Math.sqrt(deltaX*deltaX + deltaY*deltaY)/timeInterval * 10000; // 待优化，根据手机横屏竖屏判断
    speedV = Math.sqrt(deltaX*deltaX + deltaY*deltaY)/timeInterval * 10000; // 待优化，根据手机横屏竖屏判断
  }

  private  void calcOrientation() {
    float[] values = new float[3];
    float[] R = new float[9];
    SensorManager.getRotationMatrix(R, null, accelerometerValues, magneticFieldValues);         
    SensorManager.getOrientation(R, values);

    // 要经过一次数据格式的转换，转换为度
    angle = (float) Math.toDegrees(values[0]);
  }

  public Map<String, Object> getSensorData() {
    Map<String, Object> data = new HashMap<String, Object>();
    data.put("origen", origen);
    data.put("speed", speed);
    data.put("speedH", speedH);
    data.put("speedV", speedV);
    data.put("angle", angle);

    return data;
  }

  public SensorHandler(Activity activity) {
    this.mActivity = activity;
    this.sensorMag = (SensorManager) mActivity.getSystemService(Context.SENSOR_SERVICE);
    this.aSensor = sensorMag.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    this.mSensor = sensorMag.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
  }

  public void start() {
    sensorMag.registerListener(sensorEventListener, aSensor, SensorManager.SENSOR_DELAY_NORMAL);
    sensorMag.registerListener(sensorEventListener, mSensor, SensorManager.SENSOR_DELAY_NORMAL);
  }

  public void stop() {
    sensorMag.unregisterListener(sensorEventListener);
  }
}