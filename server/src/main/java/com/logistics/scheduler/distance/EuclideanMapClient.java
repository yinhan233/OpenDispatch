package com.logistics.scheduler.distance;

/*退化处理*/
public class EuclideanMapClient implements MapApiClient {

    private static final double AVG_SPEED_MPS = 30_000.0 / 3600.0; // 30 km/h -> m/s
    private static final double EARTH_R = 6_371_000.0; // 米

    @Override
    public TravelInfo getTravelTime(double fromLng, double fromLat, double toLng, double toLat) {
        double dist = haversine(fromLat, fromLng, toLat, toLng);
        int seconds = Math.max(1, (int) Math.round(dist / AVG_SPEED_MPS));
        return new TravelInfo(seconds, dist);
    }

    /** Haversine公式,返回米. */
    public static double haversine(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_R * c;
    }
}
