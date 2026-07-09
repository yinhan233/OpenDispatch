package com.logistics.scheduler.dao;

import com.logistics.scheduler.model.Distance;
import com.logistics.scheduler.model.Location;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LocationDaoTest {

    private DataSource ds;
    private LocationDao locationDao;

    @BeforeEach
    void setUp() {
        TestDatabase.truncateAll();
        TestDatabase.resetAutoIncrement();
        ds = TestDatabase.getDataSource();
        locationDao = new LocationDao(ds, true);
    }

    /**
     * 测试类型=白盒, 测试方法=语句覆盖
     * 插入地点后通过 findById 查回，验证所有字段一致。
     */
    @Test
    void insertAndFindById() throws Exception {
        Location loc = new Location("测试地点A", 120.0, 30.0);
        Location inserted = locationDao.insert(loc);
        assertNotNull(inserted.getId());

        Location found = locationDao.findById(inserted.getId());
        assertNotNull(found);
        assertEquals("测试地点A", found.getName());
        assertEquals(120.0, found.getLng());
        assertEquals(30.0, found.getLat());
    }

    /**
     * 测试类型=白盒, 测试方法=语句覆盖
     * findAll 包含默认地点(ID=0)。
     */
    @Test
    void findAllIncludesDefault() throws Exception {
        List<Location> all = locationDao.findAll();
        assertFalse(all.isEmpty());
        assertEquals(0L, all.get(0).getId());
        assertEquals("默认地点(武汉)", all.get(0).getName());
    }

    /**
     * 测试类型=白盒, 测试方法=等价类: 无效ID
     * 查询不存在的地点ID，返回null。
     */
    @Test
    void findByNonExistent() throws Exception {
        Location found = locationDao.findById(9999L);
        assertNull(found);
    }

    /**
     * 测试类型=白盒, 测试方法=语句覆盖
     * maxId 返回当前最大地点ID。
     */
    @Test
    void maxId() throws Exception {
        assertEquals(0L, locationDao.maxId());

        locationDao.insert(new Location("地点A", 120.0, 30.0));
        assertEquals(1L, locationDao.maxId());

        locationDao.insert(new Location("地点B", 121.0, 31.0));
        assertEquals(2L, locationDao.maxId());
    }

    /**
     * 测试类型=白盒, 测试方法=级联路径
     * 删除地点后级联删除关联的 distance 缓存记录。
     */
    @Test
    void deleteCascadesDistance() throws Exception {
        Location loc1 = locationDao.insert(new Location("地点A", 120.0, 30.0));
        Location loc2 = locationDao.insert(new Location("地点B", 121.0, 31.0));

        DistanceDao distanceDao = new DistanceDao(ds);
        Distance d = new Distance();
        d.setFromLocId(loc1.getId());
        d.setToLocId(loc2.getId());
        d.setTravelTime(60);
        d.setDist(100.0);
        d.setSource("test");
        distanceDao.save(d);

        Distance found = distanceDao.findById(loc1.getId(), loc2.getId());
        assertNotNull(found);

        locationDao.delete(loc1.getId());

        assertNull(distanceDao.findById(loc1.getId(), loc2.getId()));
    }

    /**
     * 测试类型=白盒, 测试方法=语句覆盖
     * 插入完整字段的地点（含名称和坐标）后验证所有字段。
     */
    @Test
    void insertWithAllFields() throws Exception {
        Location loc = new Location("完整地点", 116.397428, 39.90923);
        Location inserted = locationDao.insert(loc);

        Location found = locationDao.findById(inserted.getId());
        assertEquals("完整地点", found.getName());
        assertEquals(116.397428, found.getLng());
        assertEquals(39.90923, found.getLat());
    }
}
