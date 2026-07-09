package com.logistics.scheduler.service;

import com.logistics.scheduler.algorithm.ScheduleResult;
import com.logistics.scheduler.dao.*;
import com.logistics.scheduler.distance.DistanceService;
import com.logistics.scheduler.distance.EuclideanMapClient;
import com.logistics.scheduler.distance.MapApiClient;
import com.logistics.scheduler.model.Location;
import com.logistics.scheduler.model.Order;
import com.logistics.scheduler.model.Vehicle;
import org.junit.jupiter.api.*;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ScheduleServiceTest {

    private DataSource ds;
    private LocationDao locationDao;
    private OrderDao orderDao;
    private VehicleDao vehicleDao;
    private TaskDao taskDao;
    private RouteDao routeDao;
    private RouteItemDao routeItemDao;
    private DistanceDao distanceDao;
    private UnassignedOrderDao unassignedDao;
    private ScheduleService schedService;

    @BeforeEach
    void setUp() {
        TestDatabase.truncateAll();
        TestDatabase.resetAutoIncrement();
        ds = TestDatabase.getDataSource();
        locationDao = new LocationDao(ds, true);
        orderDao = new OrderDao(ds, true);
        vehicleDao = new VehicleDao(ds, true);
        taskDao = new TaskDao(ds);
        routeDao = new RouteDao(ds);
        routeItemDao = new RouteItemDao(ds);
        distanceDao = new DistanceDao(ds);
        unassignedDao = new UnassignedOrderDao(ds);
        MapApiClient mapClient = new EuclideanMapClient();
        DistanceService distService = new DistanceService(distanceDao, locationDao, mapClient);
        schedService = new ScheduleService(orderDao, vehicleDao, distService,
                routeDao, routeItemDao, unassignedDao,
                2592000, 10, 100.0);
    }

    private Location insertLocation(String name, double lng, double lat) throws Exception {
        return locationDao.insert(new Location(name, lng, lat));
    }

    private Order insertOrder(long pickupLocId, long deliveryLocId) throws Exception {
        com.logistics.scheduler.model.Task t = taskDao.insert("OPEN");
        Order o = new Order();
        o.setTaskId(t.getId());
        o.setPickupLocId(pickupLocId);
        o.setDeliveryLocId(deliveryLocId);
        o.setTimeStart(LocalDateTime.of(2026, 7, 1, 8, 0));
        o.setTimeEnd(LocalDateTime.of(2026, 7, 1, 18, 0));
        o.setRevenue(BigDecimal.valueOf(50));
        o.setWeight(BigDecimal.valueOf(100));
        o.setVolume(BigDecimal.valueOf(5));
        o.setServiceTime(10);
        o.setStatus("UNASSIGNED");
        return orderDao.insert(o);
    }

    private Vehicle insertVehicle(long curLocId, int maxWeight, int maxVolume, double speed) throws Exception {
        Vehicle v = new Vehicle();
        v.setPersonId("TEST001");
        v.setCurLocId(curLocId);
        v.setCurAvailableTime(LocalDateTime.of(2026, 7, 1, 7, 0));
        v.setToolType("微型面包车");
        v.setMaxWeight(BigDecimal.valueOf(maxWeight));
        v.setMaxVolume(BigDecimal.valueOf(maxVolume));
        v.setSpeed(BigDecimal.valueOf(speed));
        v.setStatus("IDLE");
        return vehicleDao.insert(v);
    }

    @Test
    @DisplayName("边界值: 0订单 0车辆 → empty result, feasible=true")
    void scheduleNoData() throws Exception {
        ScheduleResult result = schedService.schedule();

        assertNotNull(result);
        assertEquals(0, result.getTotalIdle());
        assertEquals(0.0, result.getTotalRevenue(), 0.001);
        assertTrue(result.getVehicleRoutes().isEmpty());
        assertTrue(result.getUnassigned().isEmpty());
        assertTrue(result.isFeasible());
    }

    @Test
    @DisplayName("等价类: 1订单 0车辆 → 全部 unassigned")
    void scheduleOneOrderNoVehicle() throws Exception {
        Location pickup = insertLocation("取货点", 114.30, 30.60);
        Location delivery = insertLocation("送货点", 114.32, 30.61);
        Order o = insertOrder(pickup.getId(), delivery.getId());

        ScheduleResult result = schedService.schedule();

        assertNotNull(result);
        assertEquals(0, result.getVehicleRoutes().size());
        assertEquals(1, result.getUnassigned().size());
        assertEquals(o.getId(), result.getUnassigned().get(0));
        assertFalse(result.isFeasible());
    }

    @Test
    @DisplayName("等价类: 1车辆 0订单 → empty result")
    void scheduleOneVehicleNoOrder() throws Exception {
        Location home = insertLocation("起点", 114.30, 30.59);
        insertVehicle(home.getId(), 1000, 40, 35);

        ScheduleResult result = schedService.schedule();

        assertNotNull(result);
        assertTrue(result.getVehicleRoutes().isEmpty());
        assertTrue(result.getUnassigned().isEmpty());
        assertTrue(result.isFeasible());
    }

    @Test
    @DisplayName("语句覆盖: 1车辆 1订单 可行 → assigned")
    void scheduleOneOrderOneVehicleFeasible() throws Exception {
        Location pickup = insertLocation("取货点", 114.30, 30.60);
        Location delivery = insertLocation("送货点", 114.32, 30.61);
        Location home = insertLocation("车辆起点", 114.30, 30.59);

        Vehicle v = insertVehicle(home.getId(), 1000, 40, 35);
        Order o = insertOrder(pickup.getId(), delivery.getId());

        ScheduleResult result = schedService.schedule();

        assertNotNull(result);
        assertTrue(result.getUnassigned().isEmpty(), "订单应被分配");
        assertFalse(result.getVehicleRoutes().isEmpty(), "应有车辆路线");

        Map.Entry<Long, List<ScheduleResult.RouteLeg>> entry =
                result.getVehicleRoutes().entrySet().iterator().next();
        assertEquals(v.getId(), entry.getKey());
        assertEquals(1, entry.getValue().size());
        assertEquals(o.getId(), entry.getValue().get(0).orderId());
        assertTrue(result.isFeasible());
    }

    @Test
    @DisplayName("分支覆盖: 订单重量超车辆容量 → unassigned")
    void scheduleOrderExceedsCapacity() throws Exception {
        Location pickup = insertLocation("取货点", 114.30, 30.60);
        Location delivery = insertLocation("送货点", 114.32, 30.61);
        Location home = insertLocation("车辆起点", 114.30, 30.59);

        // 车辆最大载重只有50kg
        insertVehicle(home.getId(), 50, 40, 35);
        // 订单重量100kg 超过容量
        Order o = insertOrder(pickup.getId(), delivery.getId());

        ScheduleResult result = schedService.schedule();

        assertNotNull(result);
        assertEquals(1, result.getUnassigned().size(), "订单超载应无法分配");
        assertEquals(o.getId(), result.getUnassigned().get(0));
        assertFalse(result.isFeasible());
    }

    @Test
    @DisplayName("路径覆盖: persist后 getCurrent 返回一致结果")
    void persistThenGetCurrent() throws Exception {
        Location pickup = insertLocation("取货点", 114.30, 30.60);
        Location delivery = insertLocation("送货点", 114.32, 30.61);
        Location home = insertLocation("车辆起点", 114.30, 30.59);

        insertVehicle(home.getId(), 1000, 40, 35);
        insertOrder(pickup.getId(), delivery.getId());

        ScheduleResult result = schedService.schedule();
        schedService.persistSchedule(result);

        ScheduleResult current = schedService.getCurrentSchedule();

        assertNotNull(current);
        assertFalse(current.getVehicleRoutes().isEmpty());
        assertEquals(result.getTotalIdle(), current.getTotalIdle());

        // 已分配订单不再 UNASSIGNED,所以 current unassigned 为空
        assertTrue(current.getUnassigned().isEmpty());

        // 验证订单状态已变为 ASSIGNED
        long assignedOrderId = result.getVehicleRoutes().values().iterator().next().get(0).orderId();
        Order updated = orderDao.findById(assignedOrderId);
        assertEquals("ASSIGNED", updated.getStatus());
    }

    @Test
    @DisplayName("路径覆盖: schedule → complete → order DONE, vehicle updated")
    void completeOrderFlow() throws Exception {
        Location pickup = insertLocation("取货点", 114.30, 30.60);
        Location delivery = insertLocation("送货点", 114.32, 30.61);
        Location home = insertLocation("车辆起点", 114.30, 30.59);

        Vehicle v = insertVehicle(home.getId(), 1000, 40, 35);
        Order o = insertOrder(pickup.getId(), delivery.getId());

        ScheduleResult result = schedService.schedule();
        schedService.persistSchedule(result);

        assertEquals("ASSIGNED", orderDao.findById(o.getId()).getStatus());

        schedService.completeOrder(v.getId(), o.getId());

        Order completed = orderDao.findById(o.getId());
        assertEquals("DONE", completed.getStatus());

        Vehicle updated = vehicleDao.findById(v.getId());
        assertEquals(delivery.getId(), updated.getCurLocId());
    }

    @Test
    @DisplayName("语句覆盖: buildOnly 返回非空弧列表")
    void buildOnlyHasArcs() throws Exception {
        Location pickup = insertLocation("取货点", 114.30, 30.60);
        Location delivery = insertLocation("送货点", 114.32, 30.61);
        Location home = insertLocation("车辆起点", 114.30, 30.59);

        insertVehicle(home.getId(), 1000, 40, 35);
        insertOrder(pickup.getId(), delivery.getId());

        List<Map<String, Object>> arcs = schedService.buildOnly();

        assertNotNull(arcs);
        assertTrue(arcs.size() > 0, "buildOnly应返回非空弧列表");

        boolean hasVehicleToOrder = arcs.stream().anyMatch(
                a -> "VEHICLE_TO_ORDER".equals(String.valueOf(a.get("kind"))));
        assertTrue(hasVehicleToOrder, "应该有车辆→订单的弧");
    }
}
