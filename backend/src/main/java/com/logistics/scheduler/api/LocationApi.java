package com.logistics.scheduler.api;

import com.logistics.scheduler.config.AppConfig;
import com.logistics.scheduler.model.Location;
import com.logistics.scheduler.dao.LocationDao;
import com.logistics.scheduler.distance.TencentGeocoderClient;
import io.javalin.http.Context;

import java.util.List;
import java.util.Map;

public class LocationApi {
    private final LocationDao locationDao;
    private final TencentGeocoderClient geocoder;

    public LocationApi(AppConfig cfg) {
        this.locationDao = cfg.locationDao;
        this.geocoder = cfg.geocoderClient;
    }

    public void all(Context ctx) throws Exception { ctx.json(locationDao.findAll()); }

    public void create(Context ctx) throws Exception {
        Location loc = ctx.bodyAsClass(Location.class);
        ctx.json(locationDao.insert(loc));
    }

    public void createBatch(Context ctx) throws Exception {
        Location[] list = ctx.bodyAsClass(Location[].class);
        List<Location> saved = new java.util.ArrayList<>();
        for (Location l : list) saved.add(locationDao.insert(l));
        ctx.json(saved);
    }

    public void get(Context ctx) throws Exception {
        long id = Long.parseLong(ctx.pathParam("id"));
        Location l = locationDao.findById(id);
        if (l == null) { ctx.status(404).result("not found"); return; }
        ctx.json(l);
    }

    public void delete(Context ctx) throws Exception {
        long id = Long.parseLong(ctx.pathParam("id"));
        if (id == 0) {
            ctx.status(400).result("默认地点(ID=0)是系统保留地点，不能删除");
            return;
        }
        try {
            locationDao.delete(id);
            ctx.status(204);
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("foreign key constraint")) {
                ctx.status(409).result("该地点仍被车辆或订单引用，无法删除。请先删除或修改引用此地点的车辆/订单。");
            } else {
                throw e;
            }
        }
    }

    /**
     * 地址解析: POST /api/locations/geocode  body={"address":"北京市海淀区..."}
     * 返回 {"lng":116.xx, "lat":39.xx} 或 4xx/5xx + 错误信息.
     */
    public void geocode(Context ctx) throws Exception {
        Map<String, Object> body = ctx.bodyAsClass(Map.class);
        String address = (String) body.get("address");
        if (address == null || address.isBlank()) {
            ctx.status(400).result("地址不能为空");
            return;
        }
        if (address.trim().length() < 3) {
            ctx.status(400).result("地址太短，请输入更完整的地址（如：北京市海淀区）");
            return;
        }
        try {
            double[] coords = geocoder.geocode(address);
            if (coords == null) {
                ctx.status(502).result("地址解析失败，请检查地址是否完整，或确认地图 Key 已配置");
                return;
            }
            ctx.json(Map.of("lng", coords[0], "lat", coords[1], "address", address));
        } catch (Exception e) {
            ctx.status(502).result("地址解析异常: " + e.getMessage());
        }
    }
}

