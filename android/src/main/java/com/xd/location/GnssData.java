package com.xd.location;

import java.util.Locale;
import android.location.GnssMeasurement;
import android.location.GnssClock;
import android.location.GnssStatus;

public class GnssData {
  private static final double L1Frequency = 1575.42 * 1E6;
  private static final double L2Frequency = 1227.60 * 1E6;
  private static final double c_ON_NANO = 299792458E-9;
  private static final double WEEK_SECOND = 604800;
  private static final double WEEK_NANOSECOND = 604800 * 1E9;
  private static final double DAY_NANOSECOND = 86400 * 1E9;

  private final GnssMeasurement measurement;  // final修饰的成员变量必须在定义时或者在构造器中初始化。
  private final GnssClock clock;
  private final int satelliteIndex; // 卫星在gnssstatus中的索引号
  private final int prn;
  private final int constellationType;
  private final double carrierFrequencyHZ;
  private final GnssStatus status;

  private double tTxNanos; // 发送时间
  private double tRxNanos; // 接收时间

  private double pseudorange;

// 构造函数，传入 GnssClock 和 GnssMeasurement 对象
  public GnssData(GnssMeasurement measurement, GnssClock clock, GnssStatus status) {
    this.measurement = measurement;
    this.status = status;
    this.clock = clock;
    this.prn = measurement.getSvid();
    this.constellationType = measurement.getConstellationType();
    this.carrierFrequencyHZ =
            measurement.hasCarrierFrequencyHz() ? measurement.getCarrierFrequencyHz() : L1Frequency;
    this.pseudorange = 0;

    this.satelliteIndex = getSatelliteStatusIndex();

    calcPseudorange(); // 计算伪距
  }

// 获取卫星标识
  public String getPRN() {
    Locale locale = Locale.getDefault();
    switch (constellationType) {
      case GnssStatus.CONSTELLATION_BEIDOU:
        return "C#" + String.format(locale, "%02d", prn);
      case GnssStatus.CONSTELLATION_GLONASS:
        return "R#" + String.format(locale, "%02d", prn);
      case GnssStatus.CONSTELLATION_GPS:
        return "G#" + String.format(locale, "%02d", prn);
      case GnssStatus.CONSTELLATION_GALILEO:
        return "E#" + String.format(locale, "%02d", prn);
      case GnssStatus.CONSTELLATION_QZSS:
        return "J#" + String.format(locale, "%02d", prn);
      default:
        return "U#" + String.format(locale, "%02d", prn);
    }
  }

  public float getAzimuthDegrees() {
    if (satelliteIndex >= 0) {
      return status.getAzimuthDegrees(satelliteIndex);
    }

    return 0.0f;
  }

  public float getElevationDegrees() {
    if (satelliteIndex >= 0) {
      return status.getElevationDegrees(satelliteIndex);
    }

    return 0.0f;
  }

  public float getBasebandCn0DbHz() {
    if (satelliteIndex >= 0 && status.hasBasebandCn0DbHz(satelliteIndex)) {
      return status.getBasebandCn0DbHz(satelliteIndex);
    }

    return 0.0f;
  }

  public float getCarrierFrequencyHz() {
    if (satelliteIndex >= 0 && status.hasCarrierFrequencyHz(satelliteIndex)) {
      return status.	getCarrierFrequencyHz(satelliteIndex);
    }

    return 0.0f;
  }

  // 获取cn0db，以 MHz 为单位
  public float getCn0DbHz() {
    if (satelliteIndex >= 0) {
      return status.getCn0DbHz(satelliteIndex);
    }

    return 0.0f;
  }

  // 获取伪距，以 m 为单位
  public double getPseudorange() {
    return pseudorange;
  }

  // 获取tTx
  public double getTTx() {
    return tTxNanos;
  }

  // 获取tRx
  public double getTRx() {
    return tRxNanos;
  }

  private int getSatelliteStatusIndex() {
    int count = status.getSatelliteCount();
    for (int i = 0; i < count; i++) {
      if (prn == status.getSvid(i)) {
        return i;
      }
    }

    return -1;
  }

  private void calcPseudorange() {
    double TimeNanos = clock.getTimeNanos();
    double TimeOffsetNanos = measurement.getTimeOffsetNanos();
    double FullBiasNanos = clock.hasFullBiasNanos() ? clock.getFullBiasNanos() : 0;
    double BiasNanos = clock.hasBiasNanos() ? clock.getBiasNanos() : 0;
    double ReceivedSvTimeNanos = measurement.getReceivedSvTimeNanos();
    double LeapSecond = clock.hasLeapSecond() ? clock.getLeapSecond() : 0;

    // Arrival Time
    tTxNanos = ReceivedSvTimeNanos;

    // Transmission Time
    int weekNumber = (int) Math.floor(-(double) (FullBiasNanos) * 1E-9 / WEEK_SECOND);
    tRxNanos = (TimeNanos + TimeOffsetNanos) - (FullBiasNanos + BiasNanos) - weekNumber * WEEK_NANOSECOND;

    switch (constellationType) {
        case GnssStatus.CONSTELLATION_GALILEO:
        case GnssStatus.CONSTELLATION_GPS:
            break;
        case GnssStatus.CONSTELLATION_BEIDOU:
            tRxNanos -= 14E9;
            break;
        case GnssStatus.CONSTELLATION_GLONASS:
            tRxNanos = tRxNanos - LeapSecond * 1E9 + 3 * 3600 * 1E9;
            tRxNanos = tRxNanos % DAY_NANOSECOND;
            break;
        default:
            tRxNanos = tTxNanos;
    }

    pseudorange = (tRxNanos - tTxNanos) * c_ON_NANO;
  }
}