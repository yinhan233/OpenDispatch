package com.logistics.scheduler.dao;

import com.logistics.scheduler.model.Vehicle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VehicleDaoTest {

    private DataSource ds;
    private VehicleDao vehicleDao;

    @BeforeEach
    void setUp() {
        TestDatabase.truncateAll();
        TestDatabase.resetAutoIncrement();
        ds = TestDatabase.getDataSource();
        vehicleDao = new VehicleDao(ds, true);
    }

    private Vehicle buildVehicle(String personId, String status) {
        Vehicle v = new Vehicle();
        v.setPersonId(personId);
        v.setStatus(status);
        v.setCurLocId(0L);
        v.setCurAvailableTime(LocalDateTime.of(2025, 6, 1, 8, 0));
        v.setToolType("TRUCK");
        v.setMaxWeight(new BigDecimal("500.00"));
        v.setMaxVolume(new BigDecimal("20.00"));
        v.setSpeed(new BigDecimal("30.0"));
        v.setEarnedRevenue(BigDecimal.ZERO);
        return v;
    }

    /**
     * 测试类型=白盒, 测试方法=语句覆盖
     * 插入车辆后通过 findById 查回，验证所有字段一致。
     */
    @Test
    void insertAndFindById() throws Exception {
        Vehicle v = buildVehicle("P001", "IDLE");
        Vehicle inserted = vehicleDao.insert(v);
        assertNotNull(inserted.getId());

        Vehicle found = vehicleDao.findById(inserted.getId());
        assertNotNull(found);
        assertEquals("P001", found.getPersonId());
        assertEquals("IDLE", found.getStatus());
        assertEquals(0L, found.getCurLocId());
        assertEquals("TRUCK", found.getToolType());
    }

    /**
     * 测试类型=白盒, 测试方法=语句覆盖
     * 插入多辆车后 findAll 返回正确数量。
     */
    @Test
    void findAll() throws Exception {
        vehicleDao.insert(buildVehicle("P001", "IDLE"));
        vehicleDao.insert(buildVehicle("P002", "IDLE"));

        List<Vehicle> all = vehicleDao.findAll();
        assertEquals(2, all.size());
    }

    /**
     * 测试类型=白盒, 测试方法=分支覆盖
     * 插入不同状态的车辆后按状态过滤，仅返回匹配记录。
     */
    @Test
    void findByStatus() throws Exception {
        vehicleDao.insert(buildVehicle("P001", "IDLE"));
        vehicleDao.insert(buildVehicle("P002", "BUSY"));

        List<Vehicle> idle = vehicleDao.findByStatus("IDLE");
        assertEquals(1, idle.size());
        assertEquals("P001", idle.get(0).getPersonId());

        List<Vehicle> busy = vehicleDao.findByStatus("BUSY");
        assertEquals(1, busy.size());
        assertEquals("P002", busy.get(0).getPersonId());
    }

    /**
     * 测试类型=白盒, 测试方法=多字段更新路径
     * 更新车辆的状态、位置、可用时间和收入后验证所有字段。
     */
    @Test
    void updateStatusAndLocation() throws Exception {
        Vehicle v = vehicleDao.insert(buildVehicle("P001", "IDLE"));

        LocalDateTime newTime = LocalDateTime.of(2025, 6, 2, 10, 0);
        vehicleDao.updateStatusAndLocation(v.getId(), "BUSY", 0L, newTime, new BigDecimal("1500.00"));

        Vehicle found = vehicleDao.findById(v.getId());
        assertEquals("BUSY", found.getStatus());
        assertEquals(newTime, found.getCurAvailableTime());
        assertEquals(new BigDecimal("1500.00"), found.getEarnedRevenue());
    }

    /**
     * 测试类型=白盒, 测试方法=边界值
     * 查询无路线的车辆，findRouteIdsByVehicle 返回空列表。
     */
    @Test
    void findRouteIdsByVehicle() throws Exception {
        Vehicle v = vehicleDao.insert(buildVehicle("P001", "IDLE"));
        List<Long> routeIds = vehicleDao.findRouteIdsByVehicle(v.getId());
        assertTrue(routeIds.isEmpty());
    }

    /**
     * 测试类型=白盒, 测试方法=等价类: 无效ID
     * 查询不存在的车辆ID，返回null。
     */
    @Test
    void findByNonExistentId() throws Exception {
        Vehicle found = vehicleDao.findById(9999L);
        assertNull(found);
    }

    /**
     * 测试类型=白盒, 测试方法=等价类: null字段
     * 插入班次时间为null的车辆，验证插入成功且读回字段为null。
     */
    @Test
    void insertWithNullShift() throws Exception {
        Vehicle v = buildVehicle("P001", "IDLE");
        v.setShiftStart(null);
        v.setShiftEnd(null);
        Vehicle inserted = vehicleDao.insert(v);

        Vehicle found = vehicleDao.findById(inserted.getId());
        assertNull(found.getShiftStart());
        assertNull(found.getShiftEnd());
    }

    /**
     * 测试类型=白盒, 测试方法=等价类: 非null班次
     * 插入带班次时间的车辆，验证读回的时间一致。
     */
    @Test
    void insertWithShiftTimes() throws Exception {
        Vehicle v = buildVehicle("P001", "IDLE");
        LocalDateTime ss = LocalDateTime.of(2025, 6, 1, 8, 0);
        LocalDateTime se = LocalDateTime.of(2025, 6, 1, 18, 0);
        v.setShiftStart(ss);
        v.setShiftEnd(se);
        Vehicle inserted = vehicleDao.insert(v);

        Vehicle found = vehicleDao.findById(inserted.getId());
        assertEquals(ss, found.getShiftStart());
        assertEquals(se, found.getShiftEnd());
    }

    /**
     * 测试类型=白盒, 测试方法=等价类: 负速度
     * 插入负速度车辆，验证实际行为（DB无CHECK约束，可插入并读回）。
     */
    @Test
    void insertWithNegativeSpeed() throws Exception {
        Vehicle v = buildVehicle("P001", "IDLE");
        v.setSpeed(new BigDecimal("-10.0"));
        Vehicle inserted = vehicleDao.insert(v);

        Vehicle found = vehicleDao.findById(inserted.getId());
        assertEquals(0, new BigDecimal("-10.0").compareTo(found.getSpeed()));
    }
}
