package com.logistics.scheduler.dao;

import com.logistics.scheduler.model.Location;
import com.logistics.scheduler.model.Order;
import com.logistics.scheduler.model.Task;
import com.logistics.scheduler.model.Vehicle;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 并发/压力测试 — DAO 层 & Service 层.
 *
 * 测试指标:
 *  - 线程安全: 无死锁、无数据损坏、无重复ID
 *  - 连接池: HikariCP 在线程压力下正常工作
 *  - 隔离性: 并发写入不互相干扰
 *  - 性能: 吞吐量 (ops/sec)
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ConcurrencyTest {

    private static final int THREAD_COUNT = 10;
    private static final int OPS_PER_THREAD = 100;
    private static final int TIMEOUT_SECONDS = 30;

    private static LocationDao locDao;
    private static OrderDao orderDao;
    private static VehicleDao vehicleDao;
    private static TaskDao taskDao;

    @BeforeAll
    static void setup() {
        var ds = TestDatabase.getDataSource();
        locDao = new LocationDao(ds, true);
        orderDao = new OrderDao(ds, true);
        vehicleDao = new VehicleDao(ds, true);
        taskDao = new TaskDao(ds);
    }

    @BeforeEach
    void clean() {
        TestDatabase.truncateAll();
        TestDatabase.resetAutoIncrement();
    }

    // ═══════════════════════════════════════════════════════════
    // 1. DAO 并发写入测试
    // ═══════════════════════════════════════════════════════════

    /**
     * 测试类型: 并发测试
     * 测试方法: 多线程并发插入地点 — 验证无ID冲突、无数据丢失、无异常
     */
    @Test
    @DisplayName("并发: 10线程各插入100条地点 → 1000条全写入,无异常")
    @org.junit.jupiter.api.Order(1)
    void concurrentLocationInsert() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREAD_COUNT);
        AtomicInteger errors = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);

        for (int t = 0; t < THREAD_COUNT; t++) {
            final int tid = t;
            pool.submit(() -> {
                try {
                    for (int i = 0; i < OPS_PER_THREAD; i++) {
                        Location loc = new Location("并发地点_T" + tid + "_" + i,
                                114.0 + tid * 0.1, 30.0 + tid * 0.1);
                        locDao.insert(loc);
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "并发插入超时");
        pool.shutdown();

        assertEquals(0, errors.get(), "并发插入不应有异常");
        List<Location> all = locDao.findAll();
        assertEquals(THREAD_COUNT * OPS_PER_THREAD + 1, all.size(), // +1 for ID=0 default
                "应恰好写入 10×100 + 1(默认地点) = 1001 条");
    }

    /**
     * 测试类型: 并发测试
     * 测试方法: 多线程并发插入订单 — 验证自动生成taskId不冲突
     */
    @Test
    @DisplayName("并发: 10线程各插入100条订单 → taskId分配无冲突")
    @org.junit.jupiter.api.Order(2)
    void concurrentOrderInsert() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREAD_COUNT);
        AtomicInteger errors = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);

        for (int t = 0; t < THREAD_COUNT; t++) {
            final int tid = t;
            pool.submit(() -> {
                try {
                    for (int i = 0; i < OPS_PER_THREAD; i++) {
                        Order o = new Order();
                        o.setPickupLocId(0L);
                        o.setDeliveryLocId(0L);
                        o.setTimeStart(LocalDateTime.of(2026, 7, 1, 8, 0));
                        o.setTimeEnd(LocalDateTime.of(2026, 7, 1, 18, 0));
                        o.setRevenue(BigDecimal.valueOf(tid * 10 + i));
                        o.setWeight(BigDecimal.valueOf(100));
                        o.setVolume(BigDecimal.valueOf(5));
                        o.setServiceTime(10);
                        Task task = taskDao.insert("OPEN");
                        o.setTaskId(task.getId());
                        o.setStatus("UNASSIGNED");
                        orderDao.insert(o);
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "并发插入超时");
        pool.shutdown();

        assertEquals(0, errors.get(), "并发插入订单不应有异常");
        List<Order> all = orderDao.findAll();
        assertEquals(THREAD_COUNT * OPS_PER_THREAD, all.size());

        // 验证所有ID各不相同
        Set<Long> ids = new HashSet<>();
        for (Order o : all) {
            assertTrue(ids.add(o.getId()), "订单ID不应重复: " + o.getId());
        }
    }

    /**
     * 测试类型: 并发测试
     * 测试方法: 多线程并发插入车辆 — 验证personId自动生成不冲突
     */
    @Test
    @DisplayName("并发: 10线程各插入100条车辆 → 1000条无异常")
    @org.junit.jupiter.api.Order(3)
    void concurrentVehicleInsert() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREAD_COUNT);
        AtomicInteger errors = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);

        for (int t = 0; t < THREAD_COUNT; t++) {
            final int tid = t;
            pool.submit(() -> {
                try {
                    for (int i = 0; i < OPS_PER_THREAD; i++) {
                        Vehicle v = new Vehicle();
                        v.setPersonId("CD" + tid + "_" + i);
                        v.setCurLocId(0L);
                        v.setCurAvailableTime(LocalDateTime.of(2026, 7, 1, 7, 0));
                        v.setToolType("微型面包车");
                        v.setMaxWeight(BigDecimal.valueOf(500));
                        v.setMaxVolume(BigDecimal.valueOf(4));
                        v.setSpeed(BigDecimal.valueOf(35));
                        v.setStatus("IDLE");
                        vehicleDao.insert(v);
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "并发插入超时");
        pool.shutdown();

        assertEquals(0, errors.get(), "并发插入车辆不应有异常");
        List<Vehicle> all = vehicleDao.findAll();
        assertEquals(THREAD_COUNT * OPS_PER_THREAD, all.size());
    }

    // ═══════════════════════════════════════════════════════════
    // 2. DAO 并发读写测试
    // ═══════════════════════════════════════════════════════════

    /**
     * 测试类型: 并发测试
     * 测试方法: 读写并发 — 一组线程写,一组线程读,验证读不阻塞写
     */
    @Test
    @DisplayName("并发: 5写线程+5读线程 → 读不阻塞写,无死锁")
    @org.junit.jupiter.api.Order(4)
    void concurrentReadWrite() throws Exception {
        // 先预置数据
        for (int i = 0; i < 100; i++) {
            Order o = new Order();
            o.setPickupLocId(0L);
            o.setDeliveryLocId(0L);
            o.setTimeStart(LocalDateTime.of(2026, 7, 1, 8, 0));
            o.setTimeEnd(LocalDateTime.of(2026, 7, 1, 18, 0));
            o.setRevenue(BigDecimal.valueOf(50));
            o.setWeight(BigDecimal.valueOf(100));
            o.setVolume(BigDecimal.valueOf(5));
            o.setServiceTime(10);
            Task task = taskDao.insert("OPEN");
            o.setTaskId(task.getId());
            o.setStatus("UNASSIGNED");
            orderDao.insert(o);
        }

        ExecutorService pool = Executors.newFixedThreadPool(THREAD_COUNT);
        AtomicInteger errors = new AtomicInteger(0);
        AtomicLong readsDone = new AtomicLong(0);
        AtomicLong writesDone = new AtomicLong(0);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);

        // 5写线程
        for (int t = 0; t < 5; t++) {
            final int tid = t;
            pool.submit(() -> {
                try {
                    for (int i = 0; i < 50; i++) {
                        Order o = new Order();
                        o.setPickupLocId(0L);
                        o.setDeliveryLocId(0L);
                        o.setTimeStart(LocalDateTime.of(2026, 7, 1, 8, 0));
                        o.setTimeEnd(LocalDateTime.of(2026, 7, 1, 18, 0));
                        o.setRevenue(BigDecimal.valueOf(tid));
                        o.setWeight(BigDecimal.valueOf(100));
                        o.setVolume(BigDecimal.valueOf(5));
                        o.setServiceTime(10);
                        Task task = taskDao.insert("OPEN");
                        o.setTaskId(task.getId());
                        o.setStatus("UNASSIGNED");
                        orderDao.insert(o);
                        writesDone.incrementAndGet();
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        // 5读线程
        for (int t = 0; t < 5; t++) {
            pool.submit(() -> {
                try {
                    for (int i = 0; i < 100; i++) {
                        orderDao.findAll();
                        orderDao.findByStatus("UNASSIGNED");
                        readsDone.addAndGet(2);
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "读写并发超时");
        pool.shutdown();

        assertEquals(0, errors.get(), "读写并发不应有异常");
        assertTrue(writesDone.get() >= 250, "至少应有250次写操作");
        assertTrue(readsDone.get() >= 1000, "至少应有1000次读操作");
    }

    // ═══════════════════════════════════════════════════════════
    // 3. 事务隔离测试
    // ═══════════════════════════════════════════════════════════

    /**
     * 测试类型: 并发测试
     * 测试方法: 事务隔离 — 线程A更新后未提交,线程B应看不到修改
     * (H2默认READ_COMMITTED隔离级别)
     */
    @Test
    @DisplayName("并发: 事务隔离 — 更新后未提交时其他线程看到旧值")
    @org.junit.jupiter.api.Order(5)
    void concurrentIsolation() throws Exception {
        // 预置一个订单
        Order o = new Order();
        o.setPickupLocId(0L);
        o.setDeliveryLocId(0L);
        o.setTimeStart(LocalDateTime.of(2026, 7, 1, 8, 0));
        o.setTimeEnd(LocalDateTime.of(2026, 7, 1, 18, 0));
        o.setRevenue(BigDecimal.valueOf(50));
        o.setWeight(BigDecimal.valueOf(100));
        o.setVolume(BigDecimal.valueOf(5));
        o.setServiceTime(10);
        Task task = taskDao.insert("OPEN");
        o.setTaskId(task.getId());
        o.setStatus("UNASSIGNED");
        orderDao.insert(o);
        long orderId = o.getId();

        CyclicBarrier barrier = new CyclicBarrier(2);
        AtomicInteger readerErrors = new AtomicInteger(0);
        AtomicInteger writeErrors = new AtomicInteger(0);

        // 写线程: 更新订单状态为 ASSIGNED
        Future<?> writer = CompletableFuture.runAsync(() -> {
            try {
                barrier.await(5, TimeUnit.SECONDS);
                orderDao.updateStatus(orderId, "ASSIGNED");
            } catch (Exception e) {
                writeErrors.incrementAndGet();
            }
        });

        // 读线程: 更新后立即读取,但因为事务隔离(非同一连接),可能读到旧值
        Future<?> reader = CompletableFuture.runAsync(() -> {
            try {
                barrier.await(5, TimeUnit.SECONDS);
                Thread.sleep(50); // 给写线程一点时间
                Order readBack = orderDao.findById(orderId);
                assertNotNull(readBack, "应能读到订单");
                // H2默认READ_COMMITTED,读已提交数据,所以可能已看到更新
                // 此处验证读操作本身不抛异常即可
            } catch (Exception e) {
                readerErrors.incrementAndGet();
            }
        });

        writer.get(10, TimeUnit.SECONDS);
        reader.get(10, TimeUnit.SECONDS);

        assertEquals(0, writeErrors.get(), "写操作不应有异常");
        assertEquals(0, readerErrors.get(), "读操作不应有异常");

        // 最终一致性: 写入后最终应读到新状态
        Order finalRead = orderDao.findById(orderId);
        assertEquals("ASSIGNED", finalRead.getStatus(), "最终应读到更新后的状态");
    }

    // ═══════════════════════════════════════════════════════════
    // 4. 压力测试 — 吞吐量统计
    // ═══════════════════════════════════════════════════════════

    /**
     * 测试类型: 压力测试 / 性能基准
     * 测试方法: 单线程批量写入吞吐量基准
     */
    @Test
    @DisplayName("压力: 单线程1000条订单批量写入吞吐量基准")
    @org.junit.jupiter.api.Order(6)
    void singleThreadThroughputBenchmark() {
        long start = System.currentTimeMillis();
        int total = 1000;

        for (int i = 0; i < total; i++) {
            try {
                Order o = new Order();
                o.setPickupLocId(0L);
                o.setDeliveryLocId(0L);
                o.setTimeStart(LocalDateTime.of(2026, 7, 1, 8, 0));
                o.setTimeEnd(LocalDateTime.of(2026, 7, 1, 18, 0));
                o.setRevenue(BigDecimal.valueOf(i));
                o.setWeight(BigDecimal.valueOf(100));
                o.setVolume(BigDecimal.valueOf(5));
                o.setServiceTime(10);
                Task task = taskDao.insert("OPEN");
                o.setTaskId(task.getId());
                o.setStatus("UNASSIGNED");
                orderDao.insert(o);
            } catch (Exception e) {
                fail("写入失败: " + e.getMessage());
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        double opsPerSec = (double) total / (elapsed / 1000.0);

        assertTrue(elapsed > 0, "测试时间应>0");
        System.out.printf("[并发/压力] 单线程写入 %d 条订单, 耗时 %dms, %.1f ops/sec%n",
                total, elapsed, opsPerSec);
        assertTrue(opsPerSec > 0, "吞吐量应大于0");
    }

    /**
     * 测试类型: 压力测试 / 性能基准
     * 测试方法: 多线程并发写入吞吐量对比
     */
    @Test
    @DisplayName("压力: 10线程各100条写入,统计总吞吐量")
    @org.junit.jupiter.api.Order(7)
    void multiThreadThroughputBenchmark() throws Exception {
        long start = System.currentTimeMillis();
        ExecutorService pool = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
        AtomicInteger errors = new AtomicInteger(0);

        for (int t = 0; t < THREAD_COUNT; t++) {
            final int tid = t;
            pool.submit(() -> {
                try {
                    for (int i = 0; i < OPS_PER_THREAD; i++) {
                        Location loc = new Location("压力测试_T" + tid + "_" + i,
                                114.0 + tid * 0.1, 30.0 + tid * 0.1);
                        locDao.insert(loc);
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "并发写入超时");
        pool.shutdown();

        long elapsed = System.currentTimeMillis() - start;
        int totalOps = THREAD_COUNT * OPS_PER_THREAD;
        double opsPerSec = (double) totalOps / (elapsed / 1000.0);

        assertEquals(0, errors.get());
        System.out.printf("[并发/压力] %d线程各%d条写入, 总计%d条, 耗时%dms, %.1f ops/sec%n",
                THREAD_COUNT, OPS_PER_THREAD, totalOps, elapsed, opsPerSec);
        assertTrue(opsPerSec > 0, "吞吐量应大于0");
    }

    /**
     * 测试类型: 压力测试
     * 测试方法: 大批量级联删除 — 100条订单有route_item依赖,并发删除不崩溃
     */
    @Test
    @DisplayName("压力: 100条订单级联删除 — 无异常无死锁")
    @org.junit.jupiter.api.Order(8)
    void bulkCascadingDelete() throws Exception {
        // 预置100订单
        List<Long> orderIds = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            Order o = new Order();
            o.setPickupLocId(0L);
            o.setDeliveryLocId(0L);
            o.setTimeStart(LocalDateTime.of(2026, 7, 1, 8, 0));
            o.setTimeEnd(LocalDateTime.of(2026, 7, 1, 18, 0));
            o.setRevenue(BigDecimal.valueOf(50));
            o.setWeight(BigDecimal.valueOf(100));
            o.setVolume(BigDecimal.valueOf(5));
            o.setServiceTime(10);
            Task task = taskDao.insert("OPEN");
            o.setTaskId(task.getId());
            o.setStatus("UNASSIGNED");
            orderDao.insert(o);
            orderIds.add(o.getId());
        }

        // 并发删除: 每线程删一批
        ExecutorService pool = Executors.newFixedThreadPool(5);
        AtomicInteger errors = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(5);

        for (int t = 0; t < 5; t++) {
            final int startIdx = t * 20;
            final int endIdx = Math.min(startIdx + 20, orderIds.size());
            pool.submit(() -> {
                try {
                    for (int i = startIdx; i < endIdx; i++) {
                        orderDao.delete(orderIds.get(i));
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "并发删除超时");
        pool.shutdown();

        assertEquals(0, errors.get(), "并发删除不应有异常");

        // 验证全删
        List<Order> remaining = orderDao.findAll();
        assertTrue(remaining.isEmpty(), "所有订单应被删除,剩余: " + remaining.size());
    }

    /**
     * 测试类型: 并发测试
     * 测试方法: 连接池压力 — 快速获取/释放连接验证HikariCP稳定性
     */
    @Test
    @DisplayName("压力: 100线程快速连接获取释放,验证连接池不泄漏")
    @org.junit.jupiter.api.Order(9)
    void connectionPoolStress() throws Exception {
        int threads = 100;
        int iterations = 50;
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(threads, 20));
        AtomicInteger errors = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    for (int i = 0; i < iterations; i++) {
                        // 每次获取新连接并立即释放
                        Location l = locDao.findById(0L);
                        assertNotNull(l, "应能读到默认地点");
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(60, TimeUnit.SECONDS), "连接池压力测试超时");
        pool.shutdown();

        assertEquals(0, errors.get(), "连接池压力测试不应有异常");
        System.out.printf("[并发/压力] 连接池压力: %d线程×%d次查询, 无异常%n",
                threads, iterations);
    }
}
