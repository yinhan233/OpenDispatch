package com.logistics.scheduler.service;

import com.logistics.scheduler.algorithm.*;
import com.logistics.scheduler.dao.*;
import com.logistics.scheduler.distance.DistanceService;
import com.logistics.scheduler.model.*;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/*调度*/
public class ScheduleService {

    private final OrderDao orderDao;
    private final VehicleDao vehicleDao;
    private final DistanceService distanceService;
    private final RouteDao routeDao;
    private final RouteItemDao routeItemDao;
    private final UnassignedOrderDao unassignedDao;

    private final long penaltyM;
    private final long lambda;
    private final double revenueUnit;

    public ScheduleService(OrderDao orderDao, VehicleDao vehicleDao,
                           DistanceService distanceService,
                           RouteDao routeDao, RouteItemDao routeItemDao,
                           UnassignedOrderDao unassignedDao,
                           long penaltyM, long lambda, double revenueUnit) {
        this.orderDao = orderDao;
        this.vehicleDao = vehicleDao;
        this.distanceService = distanceService;
        this.routeDao = routeDao;
        this.routeItemDao = routeItemDao;
        this.unassignedDao = unassignedDao;
        this.penaltyM = penaltyM;
        this.lambda = lambda;
        this.revenueUnit = revenueUnit;
    }

    /**
     * 仅构建网络(调试用),返回边列表.
     */
    public List<Map<String, Object>> buildOnly() throws Exception {
        List<OrderView> orderViews = loadUnassignedOrderViews();
        List<VehicleView> vehicleViews = loadIdleVehicleViews();
        ArcBuilder builder = new ArcBuilder(distanceService, penaltyM, lambda, revenueUnit, 0);
        ArcBuilder.BuildResult net = builder.build(vehicleViews, orderViews);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Arc a : net.arcs()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("from", a.fromId());
            m.put("to", a.toId());
            m.put("cap", a.capacity());
            m.put("cost", a.cost());
            m.put("kind", a.kind().name());
            if (a.vehicleId() != 0) m.put("vehicleId", a.vehicleId());
            if (a.fromOrderId() != 0) m.put("fromOrder", a.fromOrderId());
            if (a.toOrderId() != 0) m.put("toOrder", a.toOrderId());
            out.add(m);
        }
        return out;
    }

    /**静态调度:从全量 UNASSIGNED 订单与 IDLE 车辆开始求解.适用于首次调度或清空重解.*/
    public ScheduleResult schedule() throws Exception {
        List<OrderView> orderViews = loadUnassignedOrderViews();
        List<VehicleView> vehicleViews = loadIdleVehicleViews();
        return solveAndBuild(orderViews, vehicleViews);
    }

    /**动态调度:考虑冻结集,仅在未冻结订单与可用车辆上求解.*/
    public ScheduleResult scheduleDynamic() throws Exception {
        // 1. 找冻结集:PLANNED/EXECUTING 路线上的所有订单
        List<Route> activeRoutes = routeDao.findByStatusIn(List.of("PLANNED", "EXECUTING"));
        List<Long> activeRouteIds = activeRoutes.stream().map(Route::getId).collect(Collectors.toList());
        List<RouteItem> activeItems = routeItemDao.findByRouteIds(activeRouteIds);
        // 收集所有冻结订单 ID
        Set<Long> frozenOrderIds = new HashSet<>();
        for (RouteItem it : activeItems) frozenOrderIds.add(it.getOrderId());

        // 2. 对每辆车,找其冻结路线中 planned_end 最大的那个订单作为"尾部"
        //    (车辆初始位置 = 该尾部订单的 delivery_loc;可用时刻 = 该尾部 planned_end)
        Map<Long, FrozenTail> frozenTailByVehicle = new HashMap<>();
        Map<Long, Long> orderEndSec = new HashMap<>();  // orderId -> planned_end_sec
        for (RouteItem it : activeItems) {
            long endSec = it.getPlannedEnd().toEpochSecond(ZoneOffset.UTC);
            orderEndSec.put(it.getOrderId(), endSec);
            long vid = -1;
            for (Route r : activeRoutes) {
                if (r.getId() == it.getRouteId()) { vid = r.getVehicleId(); break; }
            }
            if (vid < 0) continue;
            FrozenTail cur = frozenTailByVehicle.get(vid);
            if (cur == null || endSec > cur.endSec) {
                frozenTailByVehicle.put(vid, new FrozenTail(it.getOrderId(), endSec));
            }
        }

        // 3. 冻结订单的 delivery_loc_id
        Map<Long, Order> frozenOrderMap = new HashMap<>();
        if (!frozenOrderIds.isEmpty()) {
            for (Order o : orderDao.findByIds(new ArrayList<>(frozenOrderIds))) frozenOrderMap.put(o.getId(), o);
        }

        // 4. 加载未分配订单(UNASSIGNED),排除冻结订单
        List<OrderView> orderViews = loadUnassignedOrderViews();

        // 5. 加载可用车辆(IDLE + ON_DUTY),并用冻结尾部修正初始位置/时间
        List<VehicleView> vehicleViews = loadAvailableVehicleViews(frozenTailByVehicle, frozenOrderMap);

        return solveAndBuild(orderViews, vehicleViews);
    }

    /**读取当前已持久化的调度结果(PLANNED/EXECUTING 路线 + 未分配订单).*/
    public ScheduleResult getCurrentSchedule() throws Exception {
        List<Route> activeRoutes = routeDao.findByStatusIn(List.of("PLANNED", "EXECUTING"));
        List<Long> routeIds = activeRoutes.stream().map(Route::getId).collect(Collectors.toList());
        List<RouteItem> items = routeItemDao.findByRouteIds(routeIds);

        // 按 routeId 分组
        Map<Long, List<RouteItem>> itemsByRoute = new HashMap<>();
        for (RouteItem it : items) {
            itemsByRoute.computeIfAbsent(it.getRouteId(), k -> new ArrayList<>()).add(it);
        }

        // 收集所有涉及的订单(用于 loadWeight/loadVolume 与收益)
        Set<Long> allOrderIds = new HashSet<>();
        for (RouteItem it : items) allOrderIds.add(it.getOrderId());
        Map<Long, Order> orderMap = new HashMap<>();
        if (!allOrderIds.isEmpty()) {
            for (Order o : orderDao.findByIds(new ArrayList<>(allOrderIds))) orderMap.put(o.getId(), o);
        }

        Map<Long, List<ScheduleResult.RouteLeg>> vehicleRoutes = new LinkedHashMap<>();
        long totalIdle = 0;
        double totalRevenue = 0;

        for (Route r : activeRoutes) {
            List<RouteItem> routeItems = itemsByRoute.get(r.getId());
            if (routeItems == null || routeItems.isEmpty()) continue;
            routeItems.sort(Comparator.comparingInt(RouteItem::getSeq));
            List<ScheduleResult.RouteLeg> legs = new ArrayList<>();
            for (RouteItem it : routeItems) {
                long startSec = it.getPlannedStart().toEpochSecond(ZoneOffset.UTC);
                long endSec = it.getPlannedEnd().toEpochSecond(ZoneOffset.UTC);
                legs.add(new ScheduleResult.RouteLeg(
                        it.getOrderId(), startSec, endSec,
                        it.getIdleBefore(),
                        it.getLoadWeight().doubleValue(),
                        it.getLoadVolume().doubleValue()));
                totalIdle += it.getIdleBefore();
                Order o = orderMap.get(it.getOrderId());
                if (o != null) totalRevenue += o.getRevenue().doubleValue();
            }
            vehicleRoutes.put(r.getVehicleId(), legs);
        }

        // 未分配订单: status=UNASSIGNED 的订单
        List<Long> unassigned = new ArrayList<>();
        for (Order o : orderDao.findByStatus("UNASSIGNED")) unassigned.add(o.getId());

        boolean feasible = unassigned.isEmpty();
        return new ScheduleResult(totalIdle, totalRevenue, vehicleRoutes, unassigned, feasible);
    }
    public void completeOrder(long vehicleId, long orderId) throws Exception {
        Order o = orderDao.findById(orderId);
        if (o == null) throw new IllegalArgumentException("order not found: " + orderId);
        if (!"ASSIGNED".equals(o.getStatus()) && !"EXECUTING".equals(o.getStatus())) {
            throw new IllegalStateException("order not in ASSIGNABLE state: " + o.getStatus());
        }

        orderDao.updateStatus(orderId, "DONE");

        // 找到包含此订单的 PLANNED/EXECUTING 路线
        long plannedEndSec = 0;
        long deliveryLoc = o.getDeliveryLocId();
        long routeId = -1;
        List<Route> routes = routeDao.findByStatusIn(List.of("PLANNED", "EXECUTING"));
        for (Route r : routes) {
            if (r.getVehicleId() != vehicleId) continue;
            for (RouteItem it : routeItemDao.findByRouteId(r.getId())) {
                if (it.getOrderId() == orderId) {
                    plannedEndSec = it.getPlannedEnd().toEpochSecond(ZoneOffset.UTC);
                    routeId = r.getId();
                    break;
                }
            }
            if (routeId > 0) break;
        }
        if (plannedEndSec == 0) plannedEndSec = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);

        // 检查该路线是否所有订单都已完成
        boolean allDone = true;
        if (routeId > 0) {
            for (RouteItem it : routeItemDao.findByRouteId(routeId)) {
                Order ro = orderDao.findById(it.getOrderId());
                if (ro == null || !"DONE".equals(ro.getStatus())) {
                    allDone = false;
                    break;
                }
            }
        }

        Vehicle v = vehicleDao.findById(vehicleId);
        if (v != null) {
            if (allDone && routeId > 0) {
                routeDao.updateStatus(routeId, "COMPLETED");
                vehicleDao.updateStatusAndLocation(vehicleId, "IDLE", deliveryLoc,
                        LocalDateTime.ofEpochSecond(plannedEndSec, 0, ZoneOffset.UTC), v.getEarnedRevenue());
            } else {
                vehicleDao.updateStatusAndLocation(vehicleId, v.getStatus(), deliveryLoc,
                        LocalDateTime.ofEpochSecond(plannedEndSec, 0, ZoneOffset.UTC), v.getEarnedRevenue());
            }
        }
    }


    private ScheduleResult solveAndBuild(List<OrderView> orderViews, List<VehicleView> vehicleViews) throws Exception {
        if (orderViews.isEmpty()) {
            return new ScheduleResult(0, 0, new HashMap<>(), new ArrayList<>(), true);
        }
        ScheduleResult result = solvePhase(orderViews, vehicleViews, 0);
        if (result.getUnassigned().size() == orderViews.size() && !orderViews.isEmpty()) {
            result = solvePhase(orderViews, vehicleViews, penaltyM);
        }
        return result;
    }

    private ScheduleResult solvePhase(List<OrderView> orderViews, List<VehicleView> vehicleViews,
                                      long sinkPenaltyLj) {
        ArcBuilder builder = new ArcBuilder(distanceService, penaltyM, lambda, revenueUnit, sinkPenaltyLj);
        ArcBuilder.BuildResult net = builder.build(vehicleViews, orderViews);

        MinCostFlow mcf = new MinCostFlow(net.nodeCount(), net.S(), net.T());
        for (Arc arc : net.arcs()) {
            if (arc.lowerBound() > 0) {
                mcf.addBoundedEdge(arc.fromId(), arc.toId(), arc.lowerBound(), arc.capacity(), arc.cost());
            } else {
                mcf.addEdge(arc.fromId(), arc.toId(), arc.capacity(), arc.cost());
            }
        }
        MinCostFlow.FlowResult flow = mcf.solve(net.demand());
        MinCostFlow.Solution sol = mcf.extractSolution(net);

        Map<Long, OrderView> orderMap = new HashMap<>();
        for (OrderView ov : orderViews) orderMap.put(ov.getOrderId(), ov);
        Map<Long, VehicleView> vehMap = new HashMap<>();
        for (VehicleView vv : vehicleViews) vehMap.put(vv.getVehicleId(), vv);

        Map<Long, List<ScheduleResult.RouteLeg>> resultRoutes = new HashMap<>();
        long totalIdle = 0;
        double totalRevenue = 0;

        for (Map.Entry<Long, List<Long>> e : sol.vehicleRoutes().entrySet()) {
            long vehicleId = e.getKey();
            List<Long> orderIds = e.getValue();
            if (orderIds.isEmpty()) continue;
            VehicleView vv = vehMap.get(vehicleId);
            double speedMps = vv.getSpeedKmh() * 1000.0 / 3600.0;
            List<ScheduleResult.RouteLeg> legs = new ArrayList<>();
            long prevEndSec = vv.getCurAvailSec();
            long prevLocId = vv.getCurLocId();
            double loadW = 0, loadV = 0;
            for (Long oid : orderIds) {
                OrderView o = orderMap.get(oid);
                var travel1 = distanceService.getTravel(prevLocId, o.getPickupLocId());
                long travelSec = Math.max(1, Math.round(travel1.distanceMeters() / speedMps));
                // 车辆到达取货点时刻
                long arriveSec = prevEndSec + travelSec;
                // 实际取货时刻 = max(订单最早取货, 车辆到达时刻); 允许车辆晚到
                long plannedStart = Math.max(o.getASec(), arriveSec);
                // idle = 车辆到达后等待订单开始的闲置(到达晚于订单开始才有闲置;
                //        早到则延迟出发,idle=0)
                long idleBefore = Math.max(0, arriveSec - o.getASec());
                totalIdle += idleBefore;
                loadW += o.getWeight();
                loadV += o.getVolume();
                // 用实际取货时刻 + 车辆速度重算 eSec
                var travelPD = distanceService.getTravel(o.getPickupLocId(), o.getDeliveryLocId());
                long travelPDSec = Math.max(1, Math.round(travelPD.distanceMeters() / speedMps));
                long endSec = plannedStart + travelPDSec + o.getServiceSec();
                legs.add(new ScheduleResult.RouteLeg(oid, plannedStart, endSec,
                        idleBefore, loadW, loadV));
                totalRevenue += o.getRevenue();
                prevEndSec = endSec;
                prevLocId = o.getDeliveryLocId();
            }
            resultRoutes.put(vehicleId, legs);
        }

        boolean feasible = flow.feasible() && sol.unassigned().isEmpty();
        return new ScheduleResult(totalIdle, totalRevenue, resultRoutes, sol.unassigned(), feasible);
    }

    /*保存结果*/
    public void persistSchedule(ScheduleResult result) throws Exception {
        persistSchedule(result, 1);
    }

    public void persistSchedule(ScheduleResult result, int version) throws Exception {
        Map<Long, Order> orderMap = new HashMap<>();
        for (Order o : orderDao.findByStatus("UNASSIGNED")) orderMap.put(o.getId(), o);
        Map<Long, Vehicle> vehMap = new HashMap<>();
        for (Vehicle v : vehicleDao.findAll()) vehMap.put(v.getId(), v);

        for (Map.Entry<Long, List<ScheduleResult.RouteLeg>> e : result.getVehicleRoutes().entrySet()) {
            long vehicleId = e.getKey();
            List<ScheduleResult.RouteLeg> legs = e.getValue();
            if (legs.isEmpty()) continue;

            double routeRevenue = 0;
            long routeIdle = 0;
            long lastEndSec = 0;
            long lastLocId = 0;
            for (ScheduleResult.RouteLeg leg : legs) {
                Order o = orderMap.get(leg.orderId());
                if (o == null) continue;
                routeRevenue += o.getRevenue().doubleValue();
                routeIdle += leg.idleBeforeSec();
                lastEndSec = leg.plannedEndSec();
                lastLocId = o.getDeliveryLocId();
            }

            Route r = new Route();
            r.setVehicleId(vehicleId);
            r.setStatus("PLANNED");
            r.setTotalIdle((int) routeIdle);
            r.setTotalRevenue(java.math.BigDecimal.valueOf(routeRevenue));
            r.setVersion(version);
            r = routeDao.insert(r);

            int seq = 1;
            for (ScheduleResult.RouteLeg leg : legs) {
                RouteItem item = new RouteItem();
                item.setRouteId(r.getId());
                item.setSeq(seq++);
                item.setOrderId(leg.orderId());
                item.setPlannedStart(LocalDateTime.ofEpochSecond(leg.plannedStartSec(), 0, ZoneOffset.UTC));
                item.setPlannedEnd(LocalDateTime.ofEpochSecond(leg.plannedEndSec(), 0, ZoneOffset.UTC));
                item.setIdleBefore((int) leg.idleBeforeSec());
                item.setLoadWeight(java.math.BigDecimal.valueOf(leg.loadWeight()));
                item.setLoadVolume(java.math.BigDecimal.valueOf(leg.loadVolume()));
                routeItemDao.insert(item);

                orderDao.updateStatus(leg.orderId(), "ASSIGNED");
            }

            Vehicle v = vehMap.get(vehicleId);
            if (v != null) {
                java.math.BigDecimal newRev = v.getEarnedRevenue().add(java.math.BigDecimal.valueOf(routeRevenue));
                vehicleDao.updateStatusAndLocation(vehicleId, "ON_DUTY", lastLocId,
                        LocalDateTime.ofEpochSecond(lastEndSec, 0, ZoneOffset.UTC), newRev);
            }
        }

        for (Long oid : result.getUnassigned()) {
            unassignedDao.save(new UnassignedOrder(oid, "no feasible vehicle"));
        }
    }

    // ---------- 数据加载 ----------

    private List<OrderView> loadUnassignedOrderViews() throws Exception {
        List<OrderView> out = new ArrayList<>();
        for (Order o : orderDao.findByStatus("UNASSIGNED")) {
            out.add(new OrderView(o.getId(), o.getPickupLocId(), o.getDeliveryLocId(),
                    o.getTimeStart(), o.getTimeEnd(), o.getServiceTime(),
                    o.getWeight().doubleValue(), o.getVolume().doubleValue(),
                    o.getRevenue().doubleValue()));
        }
        return out;
    }

    /** 仅 IDLE 车辆(静态调度用). */
    private List<VehicleView> loadIdleVehicleViews() throws Exception {
        List<VehicleView> out = new ArrayList<>();
        for (Vehicle v : vehicleDao.findByStatus("IDLE")) {
            out.add(toVehicleView(v, 0));
        }
        return out;
    }

    private List<VehicleView> loadAvailableVehicleViews(
            Map<Long, FrozenTail> frozenTailByVehicle,
            Map<Long, Order> frozenOrderMap) throws Exception {
        List<VehicleView> out = new ArrayList<>();
        List<Vehicle> all = new ArrayList<>();
        for (Vehicle v : vehicleDao.findByStatus("IDLE")) all.add(v);
        for (Vehicle v : vehicleDao.findByStatus("ON_DUTY")) all.add(v);

        for (Vehicle v : all) {
            FrozenTail tail = frozenTailByVehicle.get(v.getId());
            if (tail != null) {
                Order lastOrder = frozenOrderMap.get(tail.orderId);
                long locId = lastOrder != null ? lastOrder.getDeliveryLocId() : v.getCurLocId();
                LocalDateTime availTime = LocalDateTime.ofEpochSecond(tail.endSec, 0, ZoneOffset.UTC);
                out.add(new VehicleView(v.getId(), locId, availTime,
                        v.getMaxWeight().doubleValue(), v.getMaxVolume().doubleValue(),
                        v.getSpeed().doubleValue(),
                        v.getShiftStart(), v.getShiftEnd(),
                        v.getEarnedRevenue().doubleValue(), 0));
            } else {
                out.add(toVehicleView(v, 0));
            }
        }
        return out;
    }

    private VehicleView toVehicleView(Vehicle v, int baseSegment) {
        return new VehicleView(v.getId(), v.getCurLocId(), v.getCurAvailableTime(),
                v.getMaxWeight().doubleValue(), v.getMaxVolume().doubleValue(),
                v.getSpeed().doubleValue(),
                v.getShiftStart(), v.getShiftEnd(),
                v.getEarnedRevenue().doubleValue(), baseSegment);
    }

    /** 冻结路线末单信息. */
    private record FrozenTail(long orderId, long endSec) {}
}
