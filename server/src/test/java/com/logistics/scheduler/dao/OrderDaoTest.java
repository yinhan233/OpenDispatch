package com.logistics.scheduler.dao;

import com.logistics.scheduler.model.Order;
import com.logistics.scheduler.model.Route;
import com.logistics.scheduler.model.RouteItem;
import com.logistics.scheduler.model.Task;
import com.logistics.scheduler.model.UnassignedOrder;
import com.logistics.scheduler.model.Vehicle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OrderDaoTest {

    private DataSource ds;
    private OrderDao orderDao;
    private TaskDao taskDao;

    @BeforeEach
    void setUp() {
        TestDatabase.truncateAll();
        TestDatabase.resetAutoIncrement();
        ds = TestDatabase.getDataSource();
        orderDao = new OrderDao(ds, true);
        taskDao = new TaskDao(ds);
    }

    private Task createTask() throws Exception {
        return taskDao.insert("OPEN");
    }

    private Order buildOrder(Long taskId, String status) {
        Order o = new Order();
        o.setTaskId(taskId);
        o.setPickupLocId(0L);
        o.setDeliveryLocId(0L);
        o.setTimeStart(LocalDateTime.of(2025, 6, 1, 8, 0));
        o.setTimeEnd(LocalDateTime.of(2025, 6, 1, 18, 0));
        o.setRevenue(new BigDecimal("100.00"));
        o.setWeight(new BigDecimal("50.00"));
        o.setVolume(new BigDecimal("2.00"));
        o.setServiceTime(30);
        o.setStatus(status);
        return o;
    }

    /**
     * 测试类型=白盒, 测试方法=语句覆盖
     * 插入一个订单后通过 findById 查回，验证所有字段一致。
     */
    @Test
    void insertAndFindById() throws Exception {
        Task task = createTask();
        Order o = buildOrder(task.getId(), "UNASSIGNED");
        Order inserted = orderDao.insert(o);
        assertNotNull(inserted.getId());

        Order found = orderDao.findById(inserted.getId());
        assertNotNull(found);
        assertEquals(inserted.getId(), found.getId());
        assertEquals(task.getId(), found.getTaskId());
        assertEquals(0L, found.getPickupLocId());
        assertEquals(0L, found.getDeliveryLocId());
        assertEquals("UNASSIGNED", found.getStatus());
        assertEquals(new BigDecimal("100.00"), found.getRevenue());
    }

    /**
     * 测试类型=白盒, 测试方法=分支覆盖
     * 插入2个订单后 findAll 返回2条记录。
     */
    @Test
    void findAllMultiple() throws Exception {
        Task task = createTask();
        orderDao.insert(buildOrder(task.getId(), "UNASSIGNED"));
        orderDao.insert(buildOrder(task.getId(), "UNASSIGNED"));

        List<Order> all = orderDao.findAll();
        assertEquals(2, all.size());
    }

    /**
     * 测试类型=白盒, 测试方法=分支覆盖
     * 插入不同状态的订单后按状态过滤，仅返回匹配的记录。
     */
    @Test
    void findByStatus() throws Exception {
        Task task = createTask();
        orderDao.insert(buildOrder(task.getId(), "UNASSIGNED"));
        orderDao.insert(buildOrder(task.getId(), "ASSIGNED"));

        List<Order> unassigned = orderDao.findByStatus("UNASSIGNED");
        assertEquals(1, unassigned.size());
        assertEquals("UNASSIGNED", unassigned.get(0).getStatus());

        List<Order> assigned = orderDao.findByStatus("ASSIGNED");
        assertEquals(1, assigned.size());
        assertEquals("ASSIGNED", assigned.get(0).getStatus());
    }

    /**
     * 测试类型=白盒, 测试方法=语句覆盖
     * 更新订单状态后再次查询，验证状态已变更。
     */
    @Test
    void updateStatus() throws Exception {
        Task task = createTask();
        Order o = orderDao.insert(buildOrder(task.getId(), "UNASSIGNED"));

        orderDao.updateStatus(o.getId(), "ASSIGNED");
        Order found = orderDao.findById(o.getId());
        assertEquals("ASSIGNED", found.getStatus());
    }

    /**
     * 测试类型=白盒, 测试方法=等价类: 正常批量
     * 批量更新3个订单状态，验证所有订单状态均被更新。
     */
    @Test
    void updateStatusBatch() throws Exception {
        Task task = createTask();
        Order o1 = orderDao.insert(buildOrder(task.getId(), "UNASSIGNED"));
        Order o2 = orderDao.insert(buildOrder(task.getId(), "UNASSIGNED"));
        Order o3 = orderDao.insert(buildOrder(task.getId(), "UNASSIGNED"));

        orderDao.updateStatusBatch(Arrays.asList(o1.getId(), o2.getId(), o3.getId()), "FROZEN");

        assertEquals("FROZEN", orderDao.findById(o1.getId()).getStatus());
        assertEquals("FROZEN", orderDao.findById(o2.getId()).getStatus());
        assertEquals("FROZEN", orderDao.findById(o3.getId()).getStatus());
    }

    /**
     * 测试类型=白盒, 测试方法=边界值
     * 传入空列表调用批量更新，不应抛出异常。
     */
    @Test
    void updateStatusBatchEmpty() {
        assertDoesNotThrow(() -> orderDao.updateStatusBatch(Collections.emptyList(), "FROZEN"));
    }

    /**
     * 测试类型=白盒, 测试方法=等价类: 正常批量
     * 批量查询多个订单ID，返回对应数量的订单。
     */
    @Test
    void findByIds() throws Exception {
        Task task = createTask();
        Order o1 = orderDao.insert(buildOrder(task.getId(), "UNASSIGNED"));
        Order o2 = orderDao.insert(buildOrder(task.getId(), "UNASSIGNED"));
        Order o3 = orderDao.insert(buildOrder(task.getId(), "UNASSIGNED"));

        List<Order> found = orderDao.findByIds(Arrays.asList(o1.getId(), o2.getId(), o3.getId()));
        assertEquals(3, found.size());
    }

    /**
     * 测试类型=白盒, 测试方法=边界值
     * 传入空列表批量查询，返回空列表。
     */
    @Test
    void findByIdsEmpty() throws Exception {
        List<Order> found = orderDao.findByIds(Collections.emptyList());
        assertTrue(found.isEmpty());
    }

    /**
     * 测试类型=白盒, 测试方法=等价类: 无效ID
     * 查询不存在的订单ID，应返回null。
     */
    @Test
    void findByNonExistentId() throws Exception {
        Order found = orderDao.findById(9999L);
        assertNull(found);
    }

    /**
     * 测试类型=白盒, 测试方法=路径覆盖: 级联删除路径
     * 删除订单后验证关联的 route_item 和 unassigned_order 记录被级联删除。
     */
    @Test
    void deleteCascading() throws Exception {
        Task task = createTask();
        Order o = orderDao.insert(buildOrder(task.getId(), "UNASSIGNED"));

        VehicleDao vehicleDao = new VehicleDao(ds, true);
        Vehicle v = new Vehicle();
        v.setPersonId("P001");
        v.setStatus("IDLE");
        v.setCurLocId(0L);
        v.setCurAvailableTime(LocalDateTime.of(2025, 6, 1, 8, 0));
        v.setToolType("TRUCK");
        v.setMaxWeight(new BigDecimal("500.00"));
        v.setMaxVolume(new BigDecimal("20.00"));
        v.setSpeed(new BigDecimal("30.0"));
        v.setEarnedRevenue(BigDecimal.ZERO);
        vehicleDao.insert(v);

        RouteDao routeDao = new RouteDao(ds);
        Route r = new Route();
        r.setVehicleId(v.getId());
        r.setStatus("PLANNED");
        r.setTotalIdle(0);
        r.setTotalRevenue(BigDecimal.ZERO);
        r.setVersion(1);
        routeDao.insert(r);

        RouteItemDao routeItemDao = new RouteItemDao(ds);
        RouteItem ri = new RouteItem();
        ri.setRouteId(r.getId());
        ri.setSeq(1);
        ri.setOrderId(o.getId());
        ri.setPlannedStart(LocalDateTime.of(2025, 6, 1, 8, 0));
        ri.setPlannedEnd(LocalDateTime.of(2025, 6, 1, 9, 0));
        ri.setIdleBefore(0);
        ri.setLoadWeight(new BigDecimal("50.00"));
        ri.setLoadVolume(new BigDecimal("2.00"));
        routeItemDao.insert(ri);

        UnassignedOrderDao uoDao = new UnassignedOrderDao(ds);
        UnassignedOrder uo = new UnassignedOrder(o.getId(), "no_vehicle");
        uo.setSnapshotAt(LocalDateTime.of(2025, 6, 1, 8, 0));
        uoDao.save(uo);

        orderDao.delete(o.getId());

        assertNull(orderDao.findById(o.getId()));

        List<RouteItem> items = routeItemDao.findByRouteId(r.getId());
        assertTrue(items.isEmpty(), "route_item should be cascade-deleted");

        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT order_id FROM unassigned_order WHERE order_id=?")) {
            ps.setLong(1, o.getId());
            try (ResultSet rs = ps.executeQuery()) {
                assertFalse(rs.next(), "unassigned_order should be cascade-deleted");
            }
        }
    }
}
