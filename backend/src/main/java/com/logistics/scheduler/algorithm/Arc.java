package com.logistics.scheduler.algorithm;

/**
 * 一条可行边(车辆首单 或 订单续接).
 * fromId/toId 为节点编号(由ArcBuilder统一分配).
 * cost 为单位流量费用(秒).
 * lowerBound 为流量下界(默认0;R_j→L_j 覆盖边为1,强制每单被覆盖).
 */
public record Arc(int fromId, int toId, long capacity, long cost, Kind kind,
                  long vehicleId, long fromOrderId, long toOrderId, long lowerBound) {
    public enum Kind { SOURCE_TO_VEHICLE, VEHICLE_TO_ORDER, ORDER_TO_ORDER, ORDER_COVER, ORDER_TO_SINK, SINK_PENALTY }

    /** 兼容旧调用(无下界). */
    public Arc(int fromId, int toId, long capacity, long cost, Kind kind,
               long vehicleId, long fromOrderId, long toOrderId) {
        this(fromId, toId, capacity, cost, kind, vehicleId, fromOrderId, toOrderId, 0);
    }
}
