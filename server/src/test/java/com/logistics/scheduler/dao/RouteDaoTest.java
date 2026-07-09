package com.logistics.scheduler.dao;

import com.logistics.scheduler.model.Route;
import com.logistics.scheduler.model.Vehicle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RouteDaoTest {

    private DataSource ds;
    private RouteDao routeDao;
    private VehicleDao vehicleDao;

    @BeforeEach
    void setUp() {
        TestDatabase.truncateAll();
        TestDatabase.resetAutoIncrement();
        ds = TestDatabase.getDataSource();
        routeDao = new RouteDao(ds);
        vehicleDao = new VehicleDao(ds, true);
    }

    private Vehicle createVehicle() throws Exception {
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
        return vehicleDao.insert(v);
    }

    private Route buildRoute(Long vehicleId, String status) {
        Route r = new Route();
        r.setVehicleId(vehicleId);
        r.setStatus(status);
        r.setTotalIdle(0);
        r.setTotalRevenue(BigDecimal.ZERO);
        r.setVersion(1);
        return r;
    }

    /**
     * 测试类型=白盒, 测试方法=语句覆盖
     * 插入路线后通过 findByVehicleId 查回，验证关联正确。
     */
    @Test
    void insertAndFindByVehicleId() throws Exception {
        Vehicle v = createVehicle();
        Route r = routeDao.insert(buildRoute(v.getId(), "PLANNED"));
        assertNotNull(r.getId());

        List<Route> routes = routeDao.findByVehicleId(v.getId());
        assertEquals(1, routes.size());
        assertEquals(r.getId(), routes.get(0).getId());
        assertEquals("PLANNED", routes.get(0).getStatus());
    }

    /**
     * 测试类型=白盒, 测试方法=分支覆盖
     * 插入不同状态的路线，按状态过滤。
     */
    @Test
    void findByStatus() throws Exception {
        Vehicle v = createVehicle();
        routeDao.insert(buildRoute(v.getId(), "PLANNED"));
        routeDao.insert(buildRoute(v.getId(), "EXECUTING"));

        List<Route> planned = routeDao.findByStatus("PLANNED");
        assertEquals(1, planned.size());
        assertEquals("PLANNED", planned.get(0).getStatus());

        List<Route> executing = routeDao.findByStatus("EXECUTING");
        assertEquals(1, executing.size());
        assertEquals("EXECUTING", executing.get(0).getStatus());
    }

    /**
     * 测试类型=白盒, 测试方法=分支覆盖
     * 按多个状态查询，返回所有匹配的路线。
     */
    @Test
    void findByStatusIn() throws Exception {
        Vehicle v = createVehicle();
        routeDao.insert(buildRoute(v.getId(), "PLANNED"));
        routeDao.insert(buildRoute(v.getId(), "EXECUTING"));
        routeDao.insert(buildRoute(v.getId(), "FROZEN"));

        List<Route> found = routeDao.findByStatusIn(Arrays.asList("PLANNED", "EXECUTING"));
        assertEquals(2, found.size());
    }

    /**
     * 测试类型=白盒, 测试方法=语句覆盖
     * 查询某车辆的最新路线（按 route_id 倒序第一条）。
     */
    @Test
    void findLatestByVehicleId() throws Exception {
        Vehicle v = createVehicle();
        Route r1 = routeDao.insert(buildRoute(v.getId(), "PLANNED"));
        Route r2 = routeDao.insert(buildRoute(v.getId(), "EXECUTING"));

        Route latest = routeDao.findLatestByVehicleId(v.getId());
        assertNotNull(latest);
        assertEquals(r2.getId(), latest.getId());
        assertEquals("EXECUTING", latest.getStatus());
    }

    /**
     * 测试类型=白盒, 测试方法=语句覆盖
     * 更新路线状态后验证变更生效。
     */
    @Test
    void updateStatus() throws Exception {
        Vehicle v = createVehicle();
        Route r = routeDao.insert(buildRoute(v.getId(), "PLANNED"));

        routeDao.updateStatus(r.getId(), "COMPLETED");

        List<Route> routes = routeDao.findByVehicleId(v.getId());
        assertEquals("COMPLETED", routes.get(0).getStatus());
    }

    /**
     * 测试类型=白盒, 测试方法=语句覆盖
     * 删除路线后 findByVehicleId 不再返回该路线。
     */
    @Test
    void deleteById() throws Exception {
        Vehicle v = createVehicle();
        Route r = routeDao.insert(buildRoute(v.getId(), "PLANNED"));

        routeDao.deleteById(r.getId());

        List<Route> routes = routeDao.findByVehicleId(v.getId());
        assertTrue(routes.isEmpty());
    }
}
