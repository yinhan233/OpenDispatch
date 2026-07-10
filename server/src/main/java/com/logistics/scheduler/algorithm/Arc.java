package com.logistics.scheduler.algorithm;


public record Arc(int fromId, int toId, long capacity, long cost, Kind kind,
                  long vehicleId, long fromOrderId, long toOrderId, long lowerBound) {
    public enum Kind { SOURCE_TO_VEHICLE, VEHICLE_TO_ORDER, ORDER_TO_ORDER, ORDER_COVER, ORDER_TO_SINK, SINK_PENALTY }

    /**(无下界)**/
    public Arc(int fromId, int toId, long capacity, long cost, Kind kind,
               long vehicleId, long fromOrderId, long toOrderId) {
        this(fromId, toId, capacity, cost, kind, vehicleId, fromOrderId, toOrderId, 0);
    }
}
