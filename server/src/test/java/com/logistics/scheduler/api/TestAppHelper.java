package com.logistics.scheduler.api;

import com.logistics.scheduler.dao.*;
import com.logistics.scheduler.distance.*;
import com.logistics.scheduler.service.ScheduleService;
import com.logistics.scheduler.model.*;
import com.logistics.scheduler.config.MapKeyConfig;
import io.javalin.Javalin;
import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;

public class TestAppHelper {
    public static Javalin create(DataSource ds) {
        LocationDao locDao = new LocationDao(ds, true);
        OrderDao orderDao = new OrderDao(ds, true);
        VehicleDao vehicleDao = new VehicleDao(ds, true);
        TaskDao taskDao = new TaskDao(ds);
        RouteDao routeDao = new RouteDao(ds);
        RouteItemDao routeItemDao = new RouteItemDao(ds);
        DistanceDao distanceDao = new DistanceDao(ds);
        UnassignedOrderDao unassignedDao = new UnassignedOrderDao(ds);
        MapApiClient mapClient = new EuclideanMapClient();
        DistanceService distService = new DistanceService(distanceDao, locDao, mapClient);
        ScheduleService schedService = new ScheduleService(orderDao, vehicleDao, distService,
                routeDao, routeItemDao, unassignedDao, 2592000, 10, 100.0);
        MapKeyConfig mapKey = new MapKeyConfig();
        TencentGeocoderClient geocoder = new TencentGeocoderClient(mapKey);

        Set<String> VALID_ORDER_STATUS = Set.of("UNASSIGNED", "ASSIGNED", "EXECUTING", "DONE", "CANCELLED");
        Set<String> VALID_VEH_STATUS = Set.of("IDLE", "ON_DUTY", "OFFLINE");

        Javalin app = Javalin.create();

        // --- Orders ---
        app.get("/api/orders", ctx -> ctx.json(orderDao.findAll()));
        app.get("/api/orders/status/{status}", ctx -> ctx.json(orderDao.findByStatus(ctx.pathParam("status"))));
        app.post("/api/orders", ctx -> {
            Order o = ctx.bodyAsClass(Order.class);
            if (o.getTaskId() == null) { Task t = taskDao.insert("OPEN"); o.setTaskId(t.getId()); }
            o.setStatus("UNASSIGNED");
            ctx.json(orderDao.insert(o));
        });
        app.post("/api/orders/batch", ctx -> {
            Order[] list = ctx.bodyAsClass(Order[].class);
            Task t = taskDao.insert("OPEN");
            List<Order> saved = new ArrayList<>();
            for (Order o : list) { o.setTaskId(t.getId()); o.setStatus("UNASSIGNED"); saved.add(orderDao.insert(o)); }
            ctx.json(saved);
        });
        app.get("/api/orders/{id}", ctx -> {
            long id = Long.parseLong(ctx.pathParam("id"));
            Order o = orderDao.findById(id);
            if (o == null) { ctx.status(404).result("not found"); return; }
            ctx.json(o);
        });
        app.post("/api/orders/{id}/status", ctx -> {
            long id = Long.parseLong(ctx.pathParam("id"));
            Order o = orderDao.findById(id);
            if (o == null) { ctx.status(404).result("order not found"); return; }
            Map body = ctx.bodyAsClass(Map.class);
            Object statusObj = body.get("status");
            if (statusObj == null) { ctx.status(400).result("missing 'status'"); return; }
            String status = statusObj.toString().trim().toUpperCase();
            if (!VALID_ORDER_STATUS.contains(status)) { ctx.status(400).result("invalid status"); return; }
            if ("UNASSIGNED".equals(status)) {
                long vid = routeItemDao.findVehicleByOrderId(id);
                routeItemDao.deleteByOrderId(id);
                if (vid > 0) {
                    List<Route> routes = routeDao.findByStatusIn(List.of("PLANNED", "EXECUTING"));
                    List<Long> rids = routes.stream().filter(r -> r.getVehicleId() == vid).map(Route::getId).toList();
                    List<RouteItem> remaining = routeItemDao.findByRouteIds(rids);
                    if (remaining.isEmpty()) {
                        for (Long rid : rids) routeDao.deleteById(rid);
                        Vehicle v = vehicleDao.findById(vid);
                        if (v != null) vehicleDao.updateStatusAndLocation(vid, "IDLE", v.getCurLocId(), v.getCurAvailableTime(), v.getEarnedRevenue());
                    }
                }
            }
            orderDao.updateStatus(id, status);
            o.setStatus(status);
            ctx.json(o);
        });
        app.get("/api/orders/{id}/vehicle", ctx -> {
            long id = Long.parseLong(ctx.pathParam("id"));
            Order o = orderDao.findById(id);
            if (o == null) { ctx.status(404).result("order not found"); return; }
            long vid = routeItemDao.findVehicleByOrderId(id);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("orderId", id);
            if (vid < 0) { out.put("assigned", false); }
            else { out.put("assigned", true); out.put("vehicleId", vid); }
            ctx.json(out);
        });
        app.delete("/api/orders/{id}", ctx -> {
            long id = Long.parseLong(ctx.pathParam("id"));
            orderDao.delete(id);
            ctx.status(204);
        });

        // --- Vehicles ---
        app.get("/api/vehicles", ctx -> ctx.json(vehicleDao.findAll()));
        app.get("/api/vehicles/types", ctx -> ctx.json(VehicleType.presets()));
        app.get("/api/vehicles/status/{status}", ctx -> ctx.json(vehicleDao.findByStatus(ctx.pathParam("status"))));
        app.post("/api/vehicles", ctx -> {
            Vehicle v = ctx.bodyAsClass(Vehicle.class);
            if (v.getPersonId() == null || v.getPersonId().isBlank()) v.setPersonId("D" + String.format("%04d", System.currentTimeMillis() % 10000));
            if (v.getCurLocId() == null) v.setCurLocId(0L);
            ctx.json(vehicleDao.insert(v));
        });
        app.post("/api/vehicles/batch", ctx -> {
            Vehicle[] list = ctx.bodyAsClass(Vehicle[].class);
            List<Vehicle> saved = new ArrayList<>();
            for (Vehicle v : list) saved.add(vehicleDao.insert(v));
            ctx.json(saved);
        });
        app.get("/api/vehicles/{id}", ctx -> {
            long id = Long.parseLong(ctx.pathParam("id"));
            Vehicle v = vehicleDao.findById(id);
            if (v == null) { ctx.status(404).result("not found"); return; }
            ctx.json(v);
        });
        app.post("/api/vehicles/{id}/offline", ctx -> {
            long id = Long.parseLong(ctx.pathParam("id"));
            Vehicle v = vehicleDao.findById(id);
            if (v == null) { ctx.status(404).result("not found"); return; }
            v.setStatus("OFFLINE");
            vehicleDao.updateStatusAndLocation(id, "OFFLINE", v.getCurLocId(), v.getCurAvailableTime(), v.getEarnedRevenue());
            ctx.json(v);
        });
        app.post("/api/vehicles/{id}/status", ctx -> {
            long id = Long.parseLong(ctx.pathParam("id"));
            Map body = ctx.bodyAsClass(Map.class);
            String newStatus = (String) body.get("status");
            if (newStatus == null || !VALID_VEH_STATUS.contains(newStatus)) { ctx.status(400).result("invalid status"); return; }
            Vehicle v = vehicleDao.findById(id);
            if (v == null) { ctx.status(404).result("not found"); return; }
            vehicleDao.updateStatusAndLocation(id, newStatus, v.getCurLocId(), v.getCurAvailableTime(), v.getEarnedRevenue());
            v.setStatus(newStatus);
            ctx.json(v);
        });
        app.delete("/api/vehicles/{id}", ctx -> {
            long id = Long.parseLong(ctx.pathParam("id"));
            var routes = vehicleDao.findRouteIdsByVehicle(id);
            for (Long rid : routes) { vehicleDao.exec("DELETE FROM route_item WHERE route_id=?", rid); }
            vehicleDao.exec("DELETE FROM route WHERE vehicle_id=?", id);
            vehicleDao.exec("DELETE FROM vehicle WHERE vehicle_id=?", id);
            vehicleDao.resetAutoIncrement("vehicle", "vehicle_id");
            ctx.status(204);
        });
        app.post("/api/vehicles/{id}/complete", ctx -> {
            long id = Long.parseLong(ctx.pathParam("id"));
            long orderId = Long.parseLong(ctx.queryParam("orderId"));
            schedService.completeOrder(id, orderId);
            ctx.status(200).result("order " + orderId + " completed by vehicle " + id);
        });

        // --- Locations ---
        app.get("/api/locations", ctx -> ctx.json(locDao.findAll()));
        app.post("/api/locations", ctx -> { Location loc = ctx.bodyAsClass(Location.class); ctx.json(locDao.insert(loc)); });
        app.post("/api/locations/batch", ctx -> {
            Location[] list = ctx.bodyAsClass(Location[].class);
            List<Location> saved = new ArrayList<>();
            for (Location l : list) saved.add(locDao.insert(l));
            ctx.json(saved);
        });
        app.post("/api/locations/geocode", ctx -> {
            Map body = ctx.bodyAsClass(Map.class);
            String address = (String) body.get("address");
            if (address == null || address.isBlank()) { ctx.status(400).result("地址不能为空"); return; }
            if (address.trim().length() < 3) { ctx.status(400).result("地址太短"); return; }
            try {
                double[] coords = geocoder.geocode(address);
                if (coords == null) { ctx.status(502).result("地址解析失败"); return; }
                ctx.json(Map.of("lng", coords[0], "lat", coords[1], "address", address));
            } catch (Exception e) { ctx.status(502).result("地址解析异常: " + e.getMessage()); }
        });
        app.get("/api/locations/{id}", ctx -> {
            long id = Long.parseLong(ctx.pathParam("id"));
            Location l = locDao.findById(id);
            if (l == null) { ctx.status(404).result("not found"); return; }
            ctx.json(l);
        });
        app.delete("/api/locations/{id}", ctx -> {
            long id = Long.parseLong(ctx.pathParam("id"));
            if (id == 0) { ctx.status(400).result("默认地点不能删除"); return; }
            try { locDao.delete(id); ctx.status(204); }
            catch (Exception e) { ctx.status(409).result("引用冲突"); }
        });

        // --- Schedule ---
        app.post("/api/schedule", ctx -> {
            var result = schedService.schedule();
            schedService.persistSchedule(result);
            ctx.json(toScheduleJson(result));
        });
        app.get("/api/schedule/dry-run", ctx -> ctx.json(toScheduleJson(schedService.schedule())));
        app.get("/api/schedule/debug/arcs", ctx -> ctx.json(schedService.buildOnly()));
        app.post("/api/schedule/reschedule", ctx -> {
            var result = schedService.scheduleDynamic();
            schedService.persistSchedule(result, 2);
            ctx.json(toScheduleJson(result));
        });
        app.get("/api/schedule/dry-run-dynamic", ctx -> ctx.json(toScheduleJson(schedService.scheduleDynamic())));
        app.get("/api/schedule/current", ctx -> ctx.json(toScheduleJson(schedService.getCurrentSchedule())));

        // --- Config ---
        MapKeyConfig mkcfg = new MapKeyConfig();
        app.get("/api/config/mapkey", ctx -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", mkcfg.getKey() != null && !mkcfg.getKey().isBlank() ? "****" : "");
            m.put("sk", mkcfg.getSk() != null && !mkcfg.getSk().isBlank() ? "******" : "");
            m.put("configured", mkcfg.isConfigured());
            m.put("path", mkcfg.getConfigPath().toString());
            ctx.json(m);
        });
        app.post("/api/config/mapkey", ctx -> {
            Map body = ctx.bodyAsClass(Map.class);
            String key = (String) body.get("key");
            if (key == null || key.isBlank()) { ctx.status(400).result("key is required"); return; }
            String sk = (String) body.get("sk");
            mkcfg.save(key.trim(), sk != null ? sk.trim() : "");
            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("ok", true);
            resp.put("configured", mkcfg.isConfigured());
            ctx.json(resp);
        });

        app.exception(Exception.class, (e, ctx) -> { ctx.status(500).result("Internal error: " + e.getMessage()); });
        return app;
    }

    private static Map<String, Object> toScheduleJson(com.logistics.scheduler.algorithm.ScheduleResult r) {
        List<Map<String, Object>> vehicles = new ArrayList<>();
        for (var e : r.getVehicleRoutes().entrySet()) {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("vehicleId", e.getKey());
            List<Map<String, Object>> route = new ArrayList<>();
            for (var leg : e.getValue()) {
                Map<String, Object> l = new LinkedHashMap<>();
                l.put("seq", route.size() + 1);
                l.put("orderId", leg.orderId());
                l.put("plannedStart", java.time.LocalDateTime.ofEpochSecond(leg.plannedStartSec(), 0, java.time.ZoneOffset.UTC).toString());
                l.put("plannedEnd", java.time.LocalDateTime.ofEpochSecond(leg.plannedEndSec(), 0, java.time.ZoneOffset.UTC).toString());
                l.put("idleBefore", leg.idleBeforeSec());
                l.put("loadWeight", leg.loadWeight());
                l.put("loadVolume", leg.loadVolume());
                route.add(l);
            }
            v.put("route", route);
            vehicles.add(v);
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalIdle", r.getTotalIdle());
        stats.put("totalRevenue", r.getTotalRevenue());
        stats.put("feasible", r.isFeasible());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("vehicles", vehicles);
        out.put("unassigned", r.getUnassigned());
        out.put("stats", stats);
        return out;
    }
}
