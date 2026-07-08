package com.logistics.scheduler.distance;

import com.logistics.scheduler.dao.DistanceDao;
import com.logistics.scheduler.dao.LocationDao;
import com.logistics.scheduler.model.Distance;
import com.logistics.scheduler.model.Location;

import java.util.Optional;

import com.logistics.scheduler.distance.MapApiClient.TravelInfo;

/**
 * 距离服务:优先查缓存表,未命中则调用MapApiClient并落库.
 * 支持API失败时退化到欧氏估算.
 */
public class DistanceService {

    private final DistanceDao distanceDao;
    private final LocationDao locationDao;
    private final MapApiClient mapClient;

    public DistanceService(DistanceDao distanceDao, LocationDao locationDao, MapApiClient mapClient) {
        this.distanceDao = distanceDao;
        this.locationDao = locationDao;
        this.mapClient = mapClient;
    }

    public TravelInfo getTravel(long fromLocId, long toLocId) {
        if (fromLocId == toLocId) {
            return new TravelInfo(0, 0.0);
        }
        try {
            Distance cached = distanceDao.findById(fromLocId, toLocId);
            if (cached != null) {
                return new TravelInfo(cached.getTravelTime(), cached.getDist());
            }
            Location from = locationDao.findById(fromLocId);
            Location to = locationDao.findById(toLocId);
            if (from == null || to == null) {
                throw new IllegalStateException("位置不存在: " + fromLocId + " 或 " + toLocId);
            }
            TravelInfo info = computeFromApi(from, to);
            persist(fromLocId, toLocId, info, "api");
            return info;
        } catch (Exception e) {
            throw new RuntimeException("获取行驶时间失败: " + fromLocId + "->" + toLocId, e);
        }
    }

    private TravelInfo computeFromApi(Location from, Location to) {
        try {
            TravelInfo info = mapClient.getTravelTime(
                    from.getLng(), from.getLat(), to.getLng(), to.getLat());
            if (info != null) return info;
        } catch (Exception ignored) {
        }
        double dist = EuclideanMapClient.haversine(
                from.getLat(), from.getLng(), to.getLat(), to.getLng());
        int sec = Math.max(1, (int) Math.round(dist / (30_000.0 / 3600.0)));
        return new TravelInfo(sec, dist);
    }

    private void persist(long from, long to, TravelInfo info, String source) throws Exception {
        Distance d = new Distance();
        d.setFromLocId(from);
        d.setToLocId(to);
        d.setTravelTime(info.travelTimeSeconds());
        d.setDist(info.distanceMeters());
        d.setSource(source);
        distanceDao.save(d);
    }
}
