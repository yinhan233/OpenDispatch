package com.logistics.scheduler.algorithm;

import java.time.LocalDateTime;

/**
 * 调度输入:单个车辆的可计算视图.
 */
public class VehicleView {
    private final long vehicleId;
    private final long curLocId;
    private final long curAvailSec;     // 当前可用时刻(秒)
    private final double maxWeight;
    private final double maxVolume;
    private final double speedKmh;     // 平均行驶速度 km/h
    private final long shiftStartSec;   // 班次开始(秒),0表示无限制
    private final long shiftEndSec;
    private final double earnedRevenue; // 历史已获收益(用于均衡)
    private final int baseSegment;      // 已接单数(动态场景下作为凸费用起点)

    public VehicleView(long vehicleId, long curLocId, LocalDateTime curAvailableTime,
                       double maxWeight, double maxVolume, double speedKmh,
                       LocalDateTime shiftStart, LocalDateTime shiftEnd,
                       double earnedRevenue, int baseSegment) {
        this.vehicleId = vehicleId;
        this.curLocId = curLocId;
        this.curAvailSec = curAvailableTime.toEpochSecond(java.time.ZoneOffset.UTC);
        this.maxWeight = maxWeight;
        this.maxVolume = maxVolume;
        this.speedKmh = speedKmh;
        this.shiftStartSec = shiftStart == null ? 0 : shiftStart.toEpochSecond(java.time.ZoneOffset.UTC);
        this.shiftEndSec = shiftEnd == null ? Long.MAX_VALUE : shiftEnd.toEpochSecond(java.time.ZoneOffset.UTC);
        this.earnedRevenue = earnedRevenue;
        this.baseSegment = baseSegment;
    }

    public long getVehicleId() { return vehicleId; }
    public long getCurLocId() { return curLocId; }
    public long getCurAvailSec() { return curAvailSec; }
    public double getMaxWeight() { return maxWeight; }
    public double getMaxVolume() { return maxVolume; }
    public double getSpeedKmh() { return speedKmh; }
    public long getShiftStartSec() { return shiftStartSec; }
    public long getShiftEndSec() { return shiftEndSec; }
    public double getEarnedRevenue() { return earnedRevenue; }
    public int getBaseSegment() { return baseSegment; }
}
