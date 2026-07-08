package com.logistics.scheduler.algorithm;

import java.time.LocalDateTime;

/**
 * 调度输入:单个订单的可计算视图(从DB实体投影).
 * 时间用秒数(epoch秒)便于算法运算.
 */
public class OrderView {
    private final long orderId;
    private final long pickupLocId;
    private final long deliveryLocId;
    private final long aSec;        // 取货最早时刻(秒)
    private final long bSec;        // 送货最晚时刻(秒)
    private final int serviceSec;   // 服务时长(秒)
    private final double weight;
    private final double volume;
    private final double revenue;
    private long eSec;              // 结束时刻 = a + travel(p,d) + service(由ArcBuilder填充)

    public OrderView(long orderId, long pickupLocId, long deliveryLocId,
                     LocalDateTime timeStart, LocalDateTime timeEnd,
                     int serviceSec, double weight, double volume, double revenue) {
        this.orderId = orderId;
        this.pickupLocId = pickupLocId;
        this.deliveryLocId = deliveryLocId;
        this.aSec = timeStart.toEpochSecond(java.time.ZoneOffset.UTC);
        this.bSec = timeEnd.toEpochSecond(java.time.ZoneOffset.UTC);
        this.serviceSec = serviceSec;
        this.weight = weight;
        this.volume = volume;
        this.revenue = revenue;
    }

    public long getOrderId() { return orderId; }
    public long getPickupLocId() { return pickupLocId; }
    public long getDeliveryLocId() { return deliveryLocId; }
    public long getASec() { return aSec; }
    public long getBSec() { return bSec; }
    public int getServiceSec() { return serviceSec; }
    public double getWeight() { return weight; }
    public double getVolume() { return volume; }
    public double getRevenue() { return revenue; }
    public long getESec() { return eSec; }
    public void setESec(long eSec) { this.eSec = eSec; }

    /** 是否可行: e_i ≤ b_i. */
    public boolean isFeasible() { return eSec <= bSec; }
}
