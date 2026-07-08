package com.logistics.scheduler.api;

import com.logistics.scheduler.config.AppConfig;
import com.logistics.scheduler.model.Order;
import com.logistics.scheduler.model.Task;
import com.logistics.scheduler.model.Vehicle;
import com.logistics.scheduler.dao.OrderDao;
import com.logistics.scheduler.dao.TaskDao;
import com.logistics.scheduler.dao.RouteDao;
import com.logistics.scheduler.dao.RouteItemDao;
import com.logistics.scheduler.dao.VehicleDao;
import io.javalin.http.Context;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class OrderApi {
    private final OrderDao orderDao;
    private final TaskDao taskDao;
    private final RouteItemDao routeItemDao;
    private final RouteDao routeDao;
    private final VehicleDao vehicleDao;

    public OrderApi(AppConfig cfg) {
        this.orderDao = cfg.orderDao;
        this.taskDao = cfg.taskDao;
        this.routeItemDao = cfg.routeItemDao;
        this.routeDao = cfg.routeDao;
        this.vehicleDao = cfg.vehicleDao;
    }

    private static final Set<String> VALID_STATUSES = Set.of(
            "UNASSIGNED", "ASSIGNED", "EXECUTING", "DONE", "CANCELLED");

    public void all(Context ctx) throws Exception {
        List<Order> list = orderDao.findAll();
        ctx.json(list);
    }

    public void byStatus(Context ctx) throws Exception {
        String status = ctx.pathParam("status");
        ctx.json(orderDao.findByStatus(status));
    }

    public void create(Context ctx) throws Exception {
        Order o = ctx.bodyAsClass(Order.class);
        if (o.getTaskId() == null) {
            Task t = taskDao.insert("OPEN");
            o.setTaskId(t.getId());
        }
        o.setStatus("UNASSIGNED");
        ctx.json(orderDao.insert(o));
    }

    public void createBatch(Context ctx) throws Exception {
        Order[] list = ctx.bodyAsClass(Order[].class);
        Task t = taskDao.insert("OPEN");
        java.util.List<Order> saved = new java.util.ArrayList<>();
        for (Order o : list) {
            o.setTaskId(t.getId());
            o.setStatus("UNASSIGNED");
            saved.add(orderDao.insert(o));
        }
        ctx.json(saved);
    }

    public void get(Context ctx) throws Exception {
        long id = Long.parseLong(ctx.pathParam("id"));
        Order o = orderDao.findById(id);
        if (o == null) { ctx.status(404).result("not found"); return; }
        ctx.json(o);
    }

    /** 设置订单状态: POST /api/orders/{id}/status  body: {"status":"ASSIGNED"}
     *  当设为 UNASSIGNED 时,同步删除 route_item 解除分配关系. */
    public void setStatus(Context ctx) throws Exception {
        long id = Long.parseLong(ctx.pathParam("id"));
        Order o = orderDao.findById(id);
        if (o == null) { ctx.status(404).result("order not found"); return; }
        var body = ctx.bodyAsClass(java.util.Map.class);
        Object statusObj = body.get("status");
        if (statusObj == null) { ctx.status(400).result("missing 'status'"); return; }
        String status = statusObj.toString().trim().toUpperCase();
        if (!VALID_STATUSES.contains(status)) {
            ctx.status(400).result("invalid status: " + status +
                    ", valid: " + VALID_STATUSES);
            return;
        }
        // 设为 UNASSIGNED 时解除分配关系(删除 route_item 中该订单的记录)
        if ("UNASSIGNED".equals(status)) {
            long vehicleId = routeItemDao.findVehicleByOrderId(id);
            routeItemDao.deleteByOrderId(id);
            if (vehicleId > 0) {
                List<Long> routeIds = routeDao.findByStatusIn(List.of("PLANNED", "EXECUTING"))
                        .stream().filter(r -> r.getVehicleId() == vehicleId)
                        .map(r -> r.getId()).collect(java.util.stream.Collectors.toList());
                List<com.logistics.scheduler.model.RouteItem> remaining = routeItemDao.findByRouteIds(routeIds);
                if (remaining.isEmpty()) {
                    // 路线变空:删除空路线,车辆重置为 IDLE
                    for (Long rid : routeIds) routeDao.deleteById(rid);
                    Vehicle v = vehicleDao.findById(vehicleId);
                    if (v != null) {
                        vehicleDao.updateStatusAndLocation(vehicleId, "IDLE",
                                v.getCurLocId(), v.getCurAvailableTime(), v.getEarnedRevenue());
                    }
                }
            }
        }
        orderDao.updateStatus(id, status);
        o.setStatus(status);
        ctx.json(o);
    }

    /** 查询订单被分配到哪辆车: GET /api/orders/{id}/vehicle
     *  返回 {"orderId":id,"vehicleId":vid,"assigned":true} 或 {"orderId":id,"assigned":false} */
    public void assignedVehicle(Context ctx) throws Exception {
        long id = Long.parseLong(ctx.pathParam("id"));
        Order o = orderDao.findById(id);
        if (o == null) { ctx.status(404).result("order not found"); return; }
        long vid = routeItemDao.findVehicleByOrderId(id);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("orderId", id);
        if (vid < 0) {
            out.put("assigned", false);
        } else {
            out.put("assigned", true);
            out.put("vehicleId", vid);
        }
        ctx.json(out);
    }

    public void delete(Context ctx) throws Exception {
        long id = Long.parseLong(ctx.pathParam("id"));
        orderDao.delete(id);
        ctx.status(204);
    }
}
