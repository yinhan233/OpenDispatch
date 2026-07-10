package com.logistics.scheduler.distance;

public interface MapApiClient {
    TravelInfo getTravelTime(double fromLng, double fromLat, double toLng, double toLat);

    record TravelInfo(int travelTimeSeconds, double distanceMeters) {}
}
