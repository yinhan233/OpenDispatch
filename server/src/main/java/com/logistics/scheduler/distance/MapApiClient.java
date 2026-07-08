package com.logistics.scheduler.distance;

/**
 * 地图API客户端接口(预留).实现可接入高德/OSM等.
 * 当前默认实现 EuclideanDistanceService 退化估算.
 */
public interface MapApiClient {
    /**
     * 调用外部地图API获取行驶时间(秒)与距离(米).
     * 失败时返回 null,由调用方退化.
     */
    TravelInfo getTravelTime(double fromLng, double fromLat, double toLng, double toLat);

    record TravelInfo(int travelTimeSeconds, double distanceMeters) {}
}
