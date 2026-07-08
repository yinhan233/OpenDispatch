package com.logistics.scheduler.api;

import com.logistics.scheduler.config.AppConfig;
import com.logistics.scheduler.model.Vehicle;
import com.logistics.scheduler.model.VehicleType;
import com.logistics.scheduler.dao.VehicleDao;
import com.logistics.scheduler.service.ScheduleService;
import io.javalin.http.Context;

import java.util.List;
import java.util.Map;

public class VehicleApi {
    private final VehicleDao vehicleDao;
    private final ScheduleService scheduleService;

    private static final java.util.Set<String> VALID_STATUS =
            java.util.Set.of("IDLE", "ON_DUTY", "OFFLINE");

    public VehicleApi(AppConfig cfg) {
        this.vehicleDao = cfg.vehicleDao;
        this.scheduleService = cfg.scheduleService;
    }

    public void all(Context ctx) throws Exception { ctx.json(vehicleDao.findAll()); }

    public void byStatus(Context ctx) throws Exception {
        ctx.json(vehicleDao.findByStatus(ctx.pathParam("status")));
    }

    /** 返回预设车型列表(供前端下拉框使用). */
    public void types(Context ctx) throws Exception {
        ctx.json(VehicleType.presets());
    }

    public void create(Context ctx) throws Exception {
        Vehicle v = ctx.bodyAsClass(Vehicle.class);
        // 司机工号为空时自动生成
        if (v.getPersonId() == null || v.getPersonId().isBlank()) {
            v.setPersonId("D" + String.format("%04d", System.currentTimeMillis() % 10000));
        }
        // 地点ID为空时默认为0(默认地点-武汉)
        if (v.getCurLocId() == null) {
            v.setCurLocId(0L);
        }
        ctx.json(vehicleDao.insert(v));
    }

    public void createBatch(Context ctx) throws Exception {
        Vehicle[] list = ctx.bodyAsClass(Vehicle[].class);
        List<Vehicle> saved = new java.util.ArrayList<>();
        for (Vehicle v : list) saved.add(vehicleDao.insert(v));
        ctx.json(saved);
    }

    public void get(Context ctx) throws Exception {
        long id = Long.parseLong(ctx.pathParam("id"));
        Vehicle v = vehicleDao.findById(id);
        if (v == null) { ctx.status(404).result("not found"); return; }
        ctx.json(v);
    }

    public void offline(Context ctx) throws Exception {
        long id = Long.parseLong(ctx.pathParam("id"));
        Vehicle v = vehicleDao.findById(id);
        if (v == null) { ctx.status(404).result("not found"); return; }
        v.setStatus("OFFLINE");
        vehicleDao.updateStatusAndLocation(id, "OFFLINE", v.getCurLocId(),
                v.getCurAvailableTime(), v.getEarnedRevenue());
        ctx.json(v);
    }

    /** 通用状态修改: POST /api/vehicles/{id}/status  body={"status":"IDLE|ON_DUTY|OFFLINE"}. */
    public void setStatus(Context ctx) throws Exception {
        long id = Long.parseLong(ctx.pathParam("id"));
        Map<String, Object> body = ctx.bodyAsClass(Map.class);
        String newStatus = (String) body.get("status");
        if (newStatus == null || !VALID_STATUS.contains(newStatus)) {
            ctx.status(400).result("invalid status, must be one of: " + VALID_STATUS);
            return;
        }
        Vehicle v = vehicleDao.findById(id);
        if (v == null) { ctx.status(404).result("not found"); return; }
        vehicleDao.updateStatusAndLocation(id, newStatus, v.getCurLocId(),
                v.getCurAvailableTime(), v.getEarnedRevenue());
        v.setStatus(newStatus);
        ctx.json(v);
    }

    /** 删除车辆(需先解除 route 引用). */
    public void delete(Context ctx) throws Exception {
        long id = Long.parseLong(ctx.pathParam("id"));
        // 级联删除引用此车辆的 route_item → route
        var routes = vehicleDao.findRouteIdsByVehicle(id);
        for (Long rid : routes) {
            exec("DELETE FROM route_item WHERE route_id=?", rid);
        }
        exec("DELETE FROM route WHERE vehicle_id=?", id);
        exec("DELETE FROM vehicle WHERE vehicle_id=?", id);
        vehicleDao.resetAutoIncrement("vehicle", "vehicle_id");
        ctx.status(204);
    }

    private int exec(String sql, Object... params) throws java.sql.SQLException {
        return vehicleDao.exec(sql, params);
    }

    /** 完成单个订单(动态触发器). */
    public void complete(Context ctx) throws Exception {
        long id = Long.parseLong(ctx.pathParam("id"));
        long orderId = Long.parseLong(ctx.queryParam("orderId"));
        scheduleService.completeOrder(id, orderId);
        ctx.status(200).result("order " + orderId + " completed by vehicle " + id);
    }
}
