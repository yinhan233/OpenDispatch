package com.logistics.scheduler.algorithm;

import com.logistics.scheduler.distance.DistanceService;
import com.logistics.scheduler.distance.MapApiClient;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ArcBuilder 白盒测试：语句覆盖、分支覆盖、路径覆盖、边界值分析、等价类划分。
 * 验证 ArcBuilder#build 的所有代码路径与 Arc.Kind 覆盖。
 */
class ArcBuilderWhiteBoxTest {

    private static class StubDistance extends DistanceService {
        private final Map<Long, Map<Long, Integer>> times = new HashMap<>();
        private final int defaultSec;

        StubDistance() {
            this(60);
        }

        StubDistance(int defaultSec) {
            super(null, null, null);
            this.defaultSec = defaultSec;
        }

        void put(long from, long to, int sec) {
            times.computeIfAbsent(from, k -> new HashMap<>()).put(to, sec);
        }

        @Override
        public MapApiClient.TravelInfo getTravel(long fromLocId, long toLocId) {
            if (fromLocId == toLocId) return new MapApiClient.TravelInfo(0, 0.0);
            int sec = times.getOrDefault(fromLocId, Map.of()).getOrDefault(toLocId, defaultSec);
            return new MapApiClient.TravelInfo(sec, sec * 10.0);
        }
    }

    private static final double TEST_SPEED_KMH = 36.0;

    private record OBTestResult(ArcBuilder.BuildResult net, MinCostFlow.FlowResult flow, MinCostFlow.Solution sol) {}

    private static OBTestResult runTest(StubDistance dist, List<VehicleView> vehicles,
                                         List<OrderView> orders, long sinkPenaltyLj) {
        ArcBuilder builder = new ArcBuilder(dist, 86400, 0, 100, sinkPenaltyLj);
        ArcBuilder.BuildResult net = builder.build(vehicles, orders);
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
        return new OBTestResult(net, flow, sol);
    }

    private static OBTestResult runTestWithLambda(StubDistance dist, List<VehicleView> vehicles,
                                                   List<OrderView> orders, long sinkPenaltyLj, long lambda) {
        ArcBuilder builder = new ArcBuilder(dist, 86400, lambda, 100, sinkPenaltyLj);
        ArcBuilder.BuildResult net = builder.build(vehicles, orders);
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
        return new OBTestResult(net, flow, sol);
    }

    /**
     * 测试类型: 白盒测试 — 语句覆盖
     * 测试方法: 语句覆盖 ArcBuilder#build 的正常执行路径
     * 覆盖的代码路径: 1车1单正常分配，覆盖 S→L_v, L_v→R_j, R_j→T 边构建，以及 extractSolution 的车辆路线提取
     */
    @Test
    void singleOrderSingleVehicle() {
        StubDistance dist = new StubDistance();
        dist.put(1, 2, 100);
        dist.put(2, 3, 200);

        OrderView o1 = new OrderView(101, 2, 3,
                LocalDateTime.of(2026, 1, 1, 0, 0, 0),
                LocalDateTime.of(2026, 1, 1, 3, 0, 0),
                60, 10, 1, 100);

        VehicleView v = new VehicleView(1, 1, LocalDateTime.of(2026, 1, 1, 0, 0, 0),
                100, 100, TEST_SPEED_KMH, null, null, 0, 0);

        OBTestResult r = runTest(dist, List.of(v), List.of(o1), 0);

        assertTrue(r.flow().feasible(), "1车1单应可行");
        assertEquals(1, r.flow().flow(), "1单需求对应1单位流");
        assertNotNull(r.sol().vehicleRoutes().get(1L));
        assertEquals(1, r.sol().vehicleRoutes().get(1L).size());
        assertEquals(101L, r.sol().vehicleRoutes().get(1L).get(0));
        assertTrue(r.sol().unassigned().isEmpty());
    }

    /**
     * 测试类型: 白盒测试 — 分支覆盖
     * 测试方法: 分支覆盖 weight 判断分支 (o.getWeight() > v.getMaxWeight())
     * 覆盖的代码路径: ArcBuilder L_v→R_j 构建中 weight 超标导致 continue, 订单进兜底
     */
    @Test
    void orderExceedsVehicleWeight() {
        StubDistance dist = new StubDistance();
        dist.put(1, 2, 100);
        dist.put(2, 3, 200);

        OrderView o1 = new OrderView(201, 2, 3,
                LocalDateTime.of(2026, 1, 1, 0, 0, 0),
                LocalDateTime.of(2026, 1, 1, 3, 0, 0),
                60, 20, 1, 100);

        VehicleView v = new VehicleView(1, 1, LocalDateTime.of(2026, 1, 1, 0, 0, 0),
                5, 100, TEST_SPEED_KMH, null, null, 0, 0);

        OBTestResult r = runTest(dist, List.of(v), List.of(o1), 0);

        assertTrue(r.flow().feasible(), "兜底使流可达");
        assertTrue(r.sol().vehicleRoutes().get(1L).isEmpty(), "车辆不应被分配此单");
        assertEquals(1, r.sol().unassigned().size());
        assertEquals(201L, r.sol().unassigned().get(0));
    }

    /**
     * 测试类型: 白盒测试 — 分支覆盖
     * 测试方法: 分支覆盖 volume 判断分支 (o.getVolume() > v.getMaxVolume())
     * 覆盖的代码路径: ArcBuilder L_v→R_j 构建中 volume 超标导致 continue
     */
    @Test
    void orderExceedsVehicleVolume() {
        StubDistance dist = new StubDistance();
        dist.put(1, 2, 100);
        dist.put(2, 3, 200);

        OrderView o1 = new OrderView(301, 2, 3,
                LocalDateTime.of(2026, 1, 1, 0, 0, 0),
                LocalDateTime.of(2026, 1, 1, 3, 0, 0),
                60, 10, 10, 100);

        VehicleView v = new VehicleView(1, 1, LocalDateTime.of(2026, 1, 1, 0, 0, 0),
                100, 3, TEST_SPEED_KMH, null, null, 0, 0);

        OBTestResult r = runTest(dist, List.of(v), List.of(o1), 0);

        assertTrue(r.flow().feasible());
        assertTrue(r.sol().vehicleRoutes().get(1L).isEmpty());
        assertEquals(1, r.sol().unassigned().size());
        assertEquals(301L, r.sol().unassigned().get(0));
    }

    /**
     * 测试类型: 白盒测试 — 分支覆盖
     * 测试方法: 分支覆盖 actualEnd > bSec 判断分支
     * 覆盖的代码路径: 车辆到达太晚，actualEnd 超出订单时间窗截止 bSec → 跳过该 L_v→R_j 边
     */
    @Test
    void orderMissesTimeWindow() {
        StubDistance dist = new StubDistance();
        dist.put(1, 2, 200);
        dist.put(2, 3, 50);

        OrderView o1 = new OrderView(401, 2, 3,
                LocalDateTime.of(2026, 1, 1, 0, 0, 0),
                LocalDateTime.of(2026, 1, 1, 0, 1, 40),
                0, 10, 1, 100);

        VehicleView v = new VehicleView(1, 1, LocalDateTime.of(2026, 1, 1, 0, 0, 0),
                100, 100, TEST_SPEED_KMH, null, null, 0, 0);

        OBTestResult r = runTest(dist, List.of(v), List.of(o1), 0);

        assertTrue(r.flow().feasible());
        assertTrue(r.sol().vehicleRoutes().get(1L).isEmpty(),
                "到达时间超出时间窗,车辆无法接单");
        assertEquals(1, r.sol().unassigned().size());
        assertEquals(401L, r.sol().unassigned().get(0));
    }

    /**
     * 测试类型: 白盒测试 — 路径覆盖
     * 测试方法: 路径覆盖 续接边 L_i→R_j (ORDER_TO_ORDER), 验证链式调度中订单顺序
     * 覆盖的代码路径: S→L_v→R_j→T (首单) + S→L_j→R_k→T (续单), extractSolution 链条拼接
     */
    @Test
    void multipleOrdersChain() {
        StubDistance dist = new StubDistance();
        dist.put(1, 2, 100);
        dist.put(2, 3, 200);
        dist.put(3, 4, 100);

        OrderView o1 = new OrderView(501, 2, 3,
                LocalDateTime.of(2026, 1, 1, 0, 16, 40),
                LocalDateTime.of(2026, 1, 1, 2, 0, 0),
                60, 10, 1, 100);

        OrderView o2 = new OrderView(502, 3, 4,
                LocalDateTime.of(2026, 1, 1, 0, 30, 0),
                LocalDateTime.of(2026, 1, 1, 2, 0, 0),
                60, 10, 1, 100);

        VehicleView v1 = new VehicleView(1, 1, LocalDateTime.of(2026, 1, 1, 0, 0, 0),
                100, 100, TEST_SPEED_KMH, null, null, 0, 0);
        VehicleView v2 = new VehicleView(2, 10, LocalDateTime.of(2026, 1, 1, 0, 0, 0),
                0, 0, TEST_SPEED_KMH, null, null, 0, 0);
        VehicleView v3 = new VehicleView(3, 11, LocalDateTime.of(2026, 1, 1, 0, 0, 0),
                0, 0, TEST_SPEED_KMH, null, null, 0, 0);

        OBTestResult r = runTest(dist, List.of(v1, v2, v3), List.of(o1, o2), 0);

        assertTrue(r.flow().feasible(), "链式调度应可行");
        assertEquals(2, r.flow().flow());

        List<Long> route1 = r.sol().vehicleRoutes().get(1L);
        assertNotNull(route1, "车1应被分配路线");
        assertEquals(2, route1.size(), "车1应执行2单(链式)");
        assertEquals(501L, route1.get(0), "链首应为订单501");
        assertEquals(502L, route1.get(1), "链尾应为订单502");
        assertTrue(r.sol().vehicleRoutes().get(2L).isEmpty(),
                "车2(容量为0)不应接单");
        assertTrue(r.sol().vehicleRoutes().get(3L).isEmpty(),
                "车3(容量为0)不应接单");
        assertTrue(r.sol().unassigned().isEmpty());
    }

    /**
     * 测试类型: 白盒测试 — 边界值分析
     * 测试方法: 边界值 车辆数为0
     * 覆盖的代码路径: V=0 → 无 S→L_v 边, 无 L_v→R_j 边, 所有订单走兜底或 S→L_j 链
     */
    @Test
    void noVehiclesAvailable() {
        StubDistance dist = new StubDistance();
        dist.put(2, 3, 200);

        OrderView o1 = new OrderView(601, 2, 3,
                LocalDateTime.of(2026, 1, 1, 0, 0, 0),
                LocalDateTime.of(2026, 1, 1, 3, 0, 0),
                60, 10, 1, 100);

        OrderView o2 = new OrderView(602, 3, 4,
                LocalDateTime.of(2026, 1, 1, 0, 0, 0),
                LocalDateTime.of(2026, 1, 1, 3, 0, 0),
                60, 10, 1, 100);

        OBTestResult r = runTest(dist, List.of(), List.of(o1, o2), 0);

        assertTrue(r.flow().feasible(), "0车辆时流仍应可通过兜底满足");
        assertEquals(2, r.sol().unassigned().size(), "无车辆时所有订单均应未分配");
    }

    /**
     * 测试类型: 白盒测试 — 边界值分析
     * 测试方法: 边界值 订单数为0
     * 覆盖的代码路径: n=0 → demand=0, nodeCount=V+1, 无 L_j/R_j 节点, 无 R_j→T 边
     */
    @Test
    void noOrdersToSchedule() {
        StubDistance dist = new StubDistance();

        VehicleView v1 = new VehicleView(1, 1, LocalDateTime.of(2026, 1, 1, 0, 0, 0),
                100, 100, TEST_SPEED_KMH, null, null, 0, 0);

        VehicleView v2 = new VehicleView(2, 2, LocalDateTime.of(2026, 1, 1, 0, 0, 0),
                100, 100, TEST_SPEED_KMH, null, null, 0, 0);

        OBTestResult r = runTest(dist, List.of(v1, v2), List.of(), 0);

        assertTrue(r.flow().feasible(), "无订单应可行");
        assertEquals(0, r.flow().flow(), "无订单需求,流为0");
        assertEquals(0, r.net().demand());
        assertTrue(r.sol().vehicleRoutes().get(1L).isEmpty());
        assertTrue(r.sol().vehicleRoutes().get(2L).isEmpty());
        assertTrue(r.sol().unassigned().isEmpty());
    }

    /**
     * 测试类型: 白盒测试 — 压力测试
     * 测试方法: 10车10单大网络, 验证算法在较大规模下的可行性
     * 覆盖的代码路径: 全路径一次覆盖, 包括多车辆多订单下的边构建与流求解
     */
    @Test
    void veryLargeNetwork() {
        StubDistance dist = new StubDistance();
        for (int i = 1; i <= 10; i++) {
            dist.put(i, i + 10, 100);
            dist.put(i + 10, i + 20, 200);
        }

        List<VehicleView> vehicles = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            vehicles.add(new VehicleView(i + 1, i + 1,
                    LocalDateTime.of(2026, 1, 1, 0, 0, 0),
                    1000, 1000, TEST_SPEED_KMH, null, null, 0, 0));
        }

        List<OrderView> orders = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            orders.add(new OrderView(1001 + i, i + 11, i + 21,
                    LocalDateTime.of(2026, 1, 1, 0, 0, 0),
                    LocalDateTime.of(2026, 1, 1, 5, 0, 0),
                    60, 10, 1, 100));
        }

        OBTestResult r = runTest(dist, vehicles, orders, 0);

        assertTrue(r.flow().feasible(), "10车10单应可行");
        assertEquals(10, r.flow().flow());
        assertEquals(10, r.net().orderCount());
        assertEquals(10, r.net().vehicleCount());
    }

    /**
     * 测试类型: 白盒测试 — 边界值分析
     * 测试方法: 车辆与订单取货地相同 → idle = max(0, arriveSec - aSec), 当 aSec 远大于 arriveSec 时 idle=0
     * 覆盖的代码路径: L_v→R_j 中 travelSec 计算与 idle 计算
     */
    @Test
    void sameLocationOrderAndVehicle() {
        StubDistance dist = new StubDistance();
        dist.put(1, 1, 0);
        dist.put(1, 2, 100);

        OrderView o1 = new OrderView(701, 1, 2,
                LocalDateTime.of(2026, 1, 1, 0, 10, 0),
                LocalDateTime.of(2026, 1, 1, 3, 0, 0),
                60, 10, 1, 100);

        VehicleView v = new VehicleView(1, 1, LocalDateTime.of(2026, 1, 1, 0, 0, 0),
                100, 100, TEST_SPEED_KMH, null, null, 0, 0);

        OBTestResult r = runTest(dist, List.of(v), List.of(o1), 0);

        assertTrue(r.flow().feasible());
        assertEquals(1, r.sol().vehicleRoutes().get(1L).size());
        assertEquals(701L, r.sol().vehicleRoutes().get(1L).get(0));
        assertTrue(r.sol().unassigned().isEmpty());
    }

    /**
     * 测试类型: 白盒测试 — 边界值分析
     * 测试方法: 紧时间窗，actualEnd 恰好等于 bSec → 可行
     * 覆盖的代码路径: actualEnd <= bSec 的边界条件，actualEnd == bSec 时应通过检查
     */
    @Test
    void tightTimeWindow() {
        StubDistance dist = new StubDistance();
        dist.put(1, 2, 100);
        dist.put(2, 3, 60);

        OrderView o1 = new OrderView(801, 2, 3,
                LocalDateTime.of(2026, 1, 1, 0, 1, 40),
                LocalDateTime.of(2026, 1, 1, 0, 4, 41),
                0, 10, 1, 100);

        VehicleView v = new VehicleView(1, 1, LocalDateTime.of(2026, 1, 1, 0, 0, 0),
                100, 100, TEST_SPEED_KMH, null, null, 0, 0);

        OBTestResult r = runTest(dist, List.of(v), List.of(o1), 0);

        assertTrue(r.flow().feasible());
        assertEquals(1, r.sol().vehicleRoutes().get(1L).size());
        assertEquals(801L, r.sol().vehicleRoutes().get(1L).get(0));
        assertTrue(r.sol().unassigned().isEmpty());
    }

    /**
     * 测试类型: 白盒测试 — 边界值分析
     * 测试方法: 订单重量等于车辆容量 → 应可行 (o.getWeight() > v.getMaxWeight() 为 false, 即 weight <= maxWeight)
     * 覆盖的代码路径: weight 比较的边界条件
     */
    @Test
    void orderAtVehicleCapacityLimit() {
        StubDistance dist = new StubDistance();
        dist.put(1, 2, 100);
        dist.put(2, 3, 200);

        OrderView o1 = new OrderView(901, 2, 3,
                LocalDateTime.of(2026, 1, 1, 0, 0, 0),
                LocalDateTime.of(2026, 1, 1, 3, 0, 0),
                60, 100, 10, 100);

        VehicleView v = new VehicleView(1, 1, LocalDateTime.of(2026, 1, 1, 0, 0, 0),
                100, 100, TEST_SPEED_KMH, null, null, 0, 0);

        OBTestResult r = runTest(dist, List.of(v), List.of(o1), 0);

        assertTrue(r.flow().feasible());
        assertEquals(1, r.sol().vehicleRoutes().get(1L).size());
        assertEquals(901L, r.sol().vehicleRoutes().get(1L).get(0));
        assertTrue(r.sol().unassigned().isEmpty());
    }

    /**
     * 测试类型: 白盒测试 — 等价类划分 (异常值)
     * 测试方法: vehicle baseSegment 为负时 S→L_v 边的费用为 lambda * baseSegment(负值)
     * 覆盖的代码路径: ArcBuilder 中 cost = lambda * baseSegment 在 baseSegment 为负时的行为
     */
    @Test
    void negativeBaseSegment() {
        StubDistance dist = new StubDistance();
        dist.put(1, 2, 100);
        dist.put(2, 3, 200);

        OrderView o1 = new OrderView(1001, 2, 3,
                LocalDateTime.of(2026, 1, 1, 0, 0, 0),
                LocalDateTime.of(2026, 1, 1, 3, 0, 0),
                60, 10, 1, 100);

        VehicleView v = new VehicleView(1, 1, LocalDateTime.of(2026, 1, 1, 0, 0, 0),
                100, 100, TEST_SPEED_KMH, null, null, 0, -3);

        OBTestResult r = runTestWithLambda(dist, List.of(v), List.of(o1), 0, 100);

        assertNotNull(r.net(), "负baseSegment不应导致构建失败");
        boolean hasSourceToVehicle = false;
        for (Arc arc : r.net().arcs()) {
            if (arc.kind() == Arc.Kind.SOURCE_TO_VEHICLE && arc.vehicleId() == 1L) {
                hasSourceToVehicle = true;
                assertEquals(-300, arc.cost(), "cost = 100 * (-3) = -300");
                break;
            }
        }
        assertTrue(hasSourceToVehicle, "带负baseSegment的车辆应有S→L_v边");
    }

    /**
     * 测试类型: 白盒测试 — 分支覆盖
     * 测试方法: 车辆shift时间限制, 订单时间在shift之外 → 不可分配
     * 覆盖的代码路径: L_v→R_j 中 actualStart < shiftStartSec 分支
     */
    @Test
    void vehicleShiftTimeRestriction() {
        StubDistance dist = new StubDistance();
        dist.put(1, 2, 100);
        dist.put(2, 3, 200);

        OrderView o1 = new OrderView(1101, 2, 3,
                LocalDateTime.of(2026, 1, 1, 0, 0, 0),
                LocalDateTime.of(2026, 1, 1, 5, 0, 0),
                60, 10, 1, 100);

        VehicleView v = new VehicleView(1, 1, LocalDateTime.of(2026, 1, 1, 0, 0, 0),
                100, 100, TEST_SPEED_KMH,
                LocalDateTime.of(2026, 1, 1, 1, 0, 0),
                LocalDateTime.of(2026, 1, 1, 2, 0, 0),
                0, 0);

        OBTestResult r = runTest(dist, List.of(v), List.of(o1), 0);

        assertTrue(r.flow().feasible(), "兜底使流可达");
        assertTrue(r.sol().vehicleRoutes().get(1L).isEmpty(),
                "订单开始时间在shift之前,车辆不可用");
        assertEquals(1, r.sol().unassigned().size());
        assertEquals(1101L, r.sol().unassigned().get(0));
    }

    /**
     * 测试类型: 白盒测试 — 语句覆盖
     * 测试方法: 验证 BuildResult.nodeCount() = V + 2*n + 2
     * 覆盖的代码路径: ArcBuilder#build 中节点编号逻辑
     */
    @Test
    void buildResultNodeCount() {
        StubDistance dist = new StubDistance();

        VehicleView v1 = new VehicleView(1, 1, LocalDateTime.of(2026, 1, 1, 0, 0, 0),
                100, 100, TEST_SPEED_KMH, null, null, 0, 0);
        VehicleView v2 = new VehicleView(2, 2, LocalDateTime.of(2026, 1, 1, 0, 0, 0),
                100, 100, TEST_SPEED_KMH, null, null, 0, 0);
        VehicleView v3 = new VehicleView(3, 3, LocalDateTime.of(2026, 1, 1, 0, 0, 0),
                100, 100, TEST_SPEED_KMH, null, null, 0, 0);

        OrderView o1 = new OrderView(1201, 4, 5,
                LocalDateTime.of(2026, 1, 1, 0, 0, 0),
                LocalDateTime.of(2026, 1, 1, 3, 0, 0),
                60, 10, 1, 100);
        OrderView o2 = new OrderView(1202, 5, 6,
                LocalDateTime.of(2026, 1, 1, 0, 0, 0),
                LocalDateTime.of(2026, 1, 1, 3, 0, 0),
                60, 10, 1, 100);

        OBTestResult r = runTest(dist, List.of(v1, v2, v3), List.of(o1, o2), 0);

        int V = 3;
        int n = 2;
        int expectedNodeCount = V + 2 * n + 2;
        assertEquals(expectedNodeCount, r.net().nodeCount(),
                "nodeCount = V + 2*n + 2 = " + expectedNodeCount);
        assertEquals(0, r.net().S(), "S节点编号为0");
        assertEquals(expectedNodeCount - 1, r.net().T(), "T节点为最后一个");

        assertEquals(V, r.net().vehicleCount());
        assertEquals(n, r.net().orderCount());
        assertEquals(n, r.net().demand());
        assertEquals(V + 1, r.net().firstR(), "firstR = V+1");
        assertEquals(V + n + 1, r.net().firstL(), "firstL = V+n+1");
    }

    /**
     * 测试类型: 白盒测试 — 语句覆盖
     * 测试方法: 验证网络中包含所有有意义的 Arc.Kind 值
     * 覆盖的代码路径: ArcBuilder#build 中所有边构建代码段, 确保 SOURCE_TO_VEHICLE, VEHICLE_TO_ORDER,
     *                 ORDER_TO_ORDER, ORDER_TO_SINK, SINK_PENALTY 均被生成
     */
    @Test
    void arcKindsCovered() {
        StubDistance dist = new StubDistance();
        dist.put(1, 2, 100);
        dist.put(2, 3, 200);
        dist.put(3, 4, 50);

        OrderView o1 = new OrderView(1301, 2, 3,
                LocalDateTime.of(2026, 1, 1, 0, 0, 0),
                LocalDateTime.of(2026, 1, 1, 3, 0, 0),
                60, 10, 1, 100);

        OrderView o2 = new OrderView(1302, 3, 4,
                LocalDateTime.of(2026, 1, 1, 0, 16, 40),
                LocalDateTime.of(2026, 1, 1, 2, 0, 0),
                60, 10, 1, 100);

        VehicleView v = new VehicleView(1, 1, LocalDateTime.of(2026, 1, 1, 0, 0, 0),
                100, 100, TEST_SPEED_KMH, null, null, 0, 0);

        ArcBuilder builder = new ArcBuilder(dist, 86400, 0, 100, 0);
        ArcBuilder.BuildResult net = builder.build(List.of(v), List.of(o1, o2));

        Set<Arc.Kind> kinds = EnumSet.noneOf(Arc.Kind.class);
        for (Arc arc : net.arcs()) {
            kinds.add(arc.kind());
        }

        assertTrue(kinds.contains(Arc.Kind.SOURCE_TO_VEHICLE),
                "应包含 SOURCE_TO_VEHICLE 类型的边");
        assertTrue(kinds.contains(Arc.Kind.VEHICLE_TO_ORDER),
                "应包含 VEHICLE_TO_ORDER 类型的边");
        assertTrue(kinds.contains(Arc.Kind.ORDER_TO_ORDER),
                "应包含 ORDER_TO_ORDER 类型的边");
        assertTrue(kinds.contains(Arc.Kind.ORDER_TO_SINK),
                "应包含 ORDER_TO_SINK 类型的边");
        assertTrue(kinds.contains(Arc.Kind.SINK_PENALTY),
                "应包含 SINK_PENALTY 类型的边");
    }
}
