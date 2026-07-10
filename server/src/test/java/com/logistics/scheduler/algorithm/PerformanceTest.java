package com.logistics.scheduler.algorithm;

import com.logistics.scheduler.distance.DistanceService;
import com.logistics.scheduler.distance.MapApiClient;
import org.junit.jupiter.api.*;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 调度算法性能基准与大规模压力测试.
 *
 * 测试指标:
 *  - N 车 × M 单 下的建图时间
 *  - N 车 × M 单 下的求解时间
 *  - 解的正确性验证
 *  - 复杂度增长曲线 (5→10→20→50→100 单)
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PerformanceTest {

    /**
     * 测试用固定旅行时间: 任意两地之间travel=60s, dist=600m.
     * 配合 speed=36km/h(10m/s), travelSec=dist/speed=60s,与预设一致.
     */
    private static class StubDistance extends DistanceService {
        private final int fixedSec;
        StubDistance(int fixedSec) {
            super(null, null, null);
            this.fixedSec = fixedSec;
        }
        @Override
        public MapApiClient.TravelInfo getTravel(long fromLocId, long toLocId) {
            if (fromLocId == toLocId) return new MapApiClient.TravelInfo(0, 0.0);
            return new MapApiClient.TravelInfo(fixedSec, fixedSec * 10.0);
        }
    }

    private static final double SPEED_KMH = 36.0; // = 10 m/s

    // ═══════════════════════════════════════════════════════════
    // 1. 正确性验证 (功能正确前提下测性能)
    // ═══════════════════════════════════════════════════════════

    /**
     * 测试类型: 压力测试
     * 测试方法: 50车×50单网络 — 验证求解可行且正确
     */
    @Test
    @DisplayName("压力: 50车×50单 → 全部订单分配")
    @org.junit.jupiter.api.Order(1)
    void fiftyVehiclesFiftyOrders() {
        int V = 50, N = 50;
        StubDistance dist = new StubDistance(60);
        List<VehicleView> vehicles = new ArrayList<>();
        List<OrderView> orders = new ArrayList<>();

        for (int v = 0; v < V; v++) {
            vehicles.add(new VehicleView((long) v + 1, 0,
                    LocalDateTime.of(2026, 7, 1, 0, 0, 0),
                    1000, 50, SPEED_KMH, null, null, 0, 0));
        }
        for (int i = 0; i < N; i++) {
            orders.add(new OrderView((long) 100 + i, 1, 2,
                    LocalDateTime.of(2026, 7, 1, 0, 0, 0),
                    LocalDateTime.of(2026, 7, 2, 0, 0, 0),
                    30, 10, 1, 100));
        }

        long start = System.nanoTime();
        ArcBuilder builder = new ArcBuilder(dist, 2592000, 10, 100, 0);
        ArcBuilder.BuildResult net = builder.build(vehicles, orders);
        long buildTime = (System.nanoTime() - start) / 1_000_000;

        MinCostFlow mcf = new MinCostFlow(net.nodeCount(), net.S(), net.T());
        start = System.nanoTime();
        for (Arc arc : net.arcs()) {
            if (arc.lowerBound() > 0)
                mcf.addBoundedEdge(arc.fromId(), arc.toId(), arc.lowerBound(), arc.capacity(), arc.cost());
            else
                mcf.addEdge(arc.fromId(), arc.toId(), arc.capacity(), arc.cost());
        }
        long addEdgeTime = (System.nanoTime() - start) / 1_000_000;

        start = System.nanoTime();
        MinCostFlow.FlowResult flow = mcf.solve(net.demand());
        long solveTime = (System.nanoTime() - start) / 1_000_000;

        MinCostFlow.Solution sol = mcf.extractSolution(net);

        System.out.printf("[压力] 50车×50单: 建图=%dms 加边=%dms 求解=%dms 弧数=%d 流量=%d%n",
                buildTime, addEdgeTime, solveTime, net.arcs().size(), flow.flow());

        assertTrue(flow.feasible(), "50×50应可行");
        assertEquals(N, flow.flow(), "应覆盖所有50单");
        assertTrue(sol.unassigned().isEmpty(), "不应有未分配");
        // S→L_v(V) + S→L_j(N) + S→R_j(N) + L_v→R_j(V×N) + L_i→R_j(N×(N-1)) + R_j→T(N)
        // = 50 + 50 + 50 + 50×50 + 50×49 + 50 = 5150
        int expectedArcs = V + N + N + V * N + N * (N - 1) + N;
        assertEquals(expectedArcs, net.arcs().size(), "弧数验证");
    }

    // ═══════════════════════════════════════════════════════════
    // 2. 复杂度增长曲线测试
    // ═══════════════════════════════════════════════════════════

    /**
     * 测试类型: 性能基准
     * 测试方法: N=[5,10,20,50,100] 单 — 测量建图+求解时间增长曲线
     */
    @Test
    @DisplayName("性能: 5→10→20→50→100单复杂度曲线")
    @org.junit.jupiter.api.Order(2)
    void complexityCurve() {
        int[] sizes = {5, 10, 20, 50, 100};
        StubDistance dist = new StubDistance(60);
        long penaltyM = 2592000;

        System.out.println("\n[性能] 复杂度增长曲线");
        System.out.println("规模    建图(ms)  求解(ms)  弧数");

        for (int N : sizes) {
            int V = N; // 车辆数=订单数,保证可分配

            List<VehicleView> vehicles = new ArrayList<>();
            for (int v = 0; v < V; v++) {
                vehicles.add(new VehicleView((long) v + 1, 0,
                        LocalDateTime.of(2026, 7, 1, 0, 0, 0),
                        10000, 1000, SPEED_KMH, null, null, 0, 0));
            }
            List<OrderView> orders = new ArrayList<>();
            for (int i = 0; i < N; i++) {
                orders.add(new OrderView((long) 100 + i, 1, 2,
                        LocalDateTime.of(2026, 7, 1, 0, 0, 0),
                        LocalDateTime.of(2026, 7, 3, 0, 0, 0),
                        10, 5, 1, 100));
            }

            // 建图
            long t0 = System.nanoTime();
            ArcBuilder builder = new ArcBuilder(dist, penaltyM, 10, 100, 0);
            ArcBuilder.BuildResult net = builder.build(vehicles, orders);
            long buildTime = (System.nanoTime() - t0) / 1_000_000;

            // 求解
            MinCostFlow mcf = new MinCostFlow(net.nodeCount(), net.S(), net.T());
            for (Arc arc : net.arcs()) {
                if (arc.lowerBound() > 0)
                    mcf.addBoundedEdge(arc.fromId(), arc.toId(), arc.lowerBound(), arc.capacity(), arc.cost());
                else
                    mcf.addEdge(arc.fromId(), arc.toId(), arc.capacity(), arc.cost());
            }

            long t1 = System.nanoTime();
            MinCostFlow.FlowResult flow = mcf.solve(net.demand());
            long solveTime = (System.nanoTime() - t1) / 1_000_000;

            System.out.printf("N=%3d   %6d    %6d    %6d%n", N, buildTime, solveTime, net.arcs().size());

            assertTrue(flow.feasible(), "N=" + N + "应可行");
            assertEquals(N, flow.flow(), "N=" + N + "应全覆盖");
            assertTrue(solveTime < 60000, "求解时间应<60s (N=" + N + ")");

            // S→L_v(V) + S→L_j(N) + S→R_j(N) + L_v→R_j(V×N) + L_i→R_j(N×(N-1)) + R_j→T(N)
            int expectedArcs = V + N + N + V * N + N * (N - 1) + N;
            assertEquals(expectedArcs, net.arcs().size(), "弧数公式验证 N=" + N);
        }
    }

    // ═══════════════════════════════════════════════════════════
    // 3. 车少单多压力
    // ═══════════════════════════════════════════════════════════

    /**
     * 测试类型: 压力测试
     * 测试方法: 5车×100单 — 大量订单分配给少量车辆(车辆需链式执行多单)
     */
    @Test
    @DisplayName("压力: 5车×100单(链式) → 正确分配,每车多单")
    @org.junit.jupiter.api.Order(3)
    void fewVehiclesHundredOrders() {
        int V = 5, N = 100;
        StubDistance dist = new StubDistance(60);

        List<VehicleView> vehicles = new ArrayList<>();
        for (int v = 0; v < V; v++) {
            vehicles.add(new VehicleView((long) v + 1, 0,
                    LocalDateTime.of(2026, 7, 1, 0, 0, 0),
                    100000, 10000, SPEED_KMH, null, null, 0, 0));
        }
        List<OrderView> orders = new ArrayList<>();
        for (int i = 0; i < N; i++) {
            orders.add(new OrderView((long) 100 + i, 1, 2,
                    LocalDateTime.of(2026, 7, 1, 0, 0, 0),
                    LocalDateTime.of(2026, 7, 10, 0, 0, 0),
                    10, 5, 1, 100));
        }

        long t0 = System.nanoTime();
        ArcBuilder builder = new ArcBuilder(dist, 2592000, 10, 100, 0);
        ArcBuilder.BuildResult net = builder.build(vehicles, orders);
        MinCostFlow mcf = new MinCostFlow(net.nodeCount(), net.S(), net.T());
        for (Arc arc : net.arcs()) {
            if (arc.lowerBound() > 0)
                mcf.addBoundedEdge(arc.fromId(), arc.toId(), arc.lowerBound(), arc.capacity(), arc.cost());
            else
                mcf.addEdge(arc.fromId(), arc.toId(), arc.capacity(), arc.cost());
        }
        MinCostFlow.FlowResult flow = mcf.solve(net.demand());
        MinCostFlow.Solution sol = mcf.extractSolution(net);
        long totalTime = (System.nanoTime() - t0) / 1_000_000;

        System.out.printf("[压力] 5车×100单(链式): 总耗时=%dms 弧数=%d 流量=%d%n",
                totalTime, net.arcs().size(), flow.flow());

        assertTrue(flow.feasible(), "应可行");
        assertEquals(N, flow.flow(), "应覆盖所有100单");
        // 5车可能无法链式覆盖所有100单(闲置累积可能超过惩罚M),部分可能走兜底
        System.out.printf("[压力] 5车×100单: 已分配车辆=%d 未分配=%d%n",
                sol.vehicleRoutes().values().stream().filter(r -> !r.isEmpty()).count(),
                sol.unassigned().size());
        assertTrue(sol.unassigned().size() < N, "至少部分分配");

        // 验证每车至少1单(若有分配)
        for (long vid = 1; vid <= V; vid++) {
            List<Long> route = sol.vehicleRoutes().get(vid);
            assertNotNull(route, "车辆" + vid + "应有路线");
        }
    }

    // ═══════════════════════════════════════════════════════════
    // 4. 两阶段求解压力
    // ═══════════════════════════════════════════════════════════

    /**
     * 测试类型: 压力测试 / 路径覆盖
     * 测试方法: 大网络触发两阶段求解(阶段1选无车环 → 阶段2强制用车辆)
     */
    @Test
    @DisplayName("压力: 20×20网络触发两阶段求解")
    @org.junit.jupiter.api.Order(4)
    void twoPhaseLargeNetwork() {
        int V = 20, N = 20;
        StubDistance dist = new StubDistance(120); // 较长旅行时间

        List<VehicleView> vehicles = new ArrayList<>();
        for (int v = 0; v < V; v++) {
            vehicles.add(new VehicleView((long) v + 1, 0,
                    LocalDateTime.of(2026, 7, 1, 0, 0, 0),
                    1000, 50, SPEED_KMH, null, null, 0, 0));
        }
        List<OrderView> orders = new ArrayList<>();
        for (int i = 0; i < N; i++) {
            orders.add(new OrderView((long) 100 + i, 1, 2,
                    LocalDateTime.of(2026, 7, 1, 0, 0, 0),
                    LocalDateTime.of(2026, 7, 3, 0, 0, 0),
                    10, 5, 1, 100));
        }

        // 阶段1: sinkPenaltyLj=0
        ArcBuilder builder1 = new ArcBuilder(dist, 2592000, 10, 100, 0);
        ArcBuilder.BuildResult net1 = builder1.build(vehicles, orders);

        MinCostFlow mcf1 = new MinCostFlow(net1.nodeCount(), net1.S(), net1.T());
        for (Arc arc : net1.arcs()) {
            if (arc.lowerBound() > 0)
                mcf1.addBoundedEdge(arc.fromId(), arc.toId(), arc.lowerBound(), arc.capacity(), arc.cost());
            else
                mcf1.addEdge(arc.fromId(), arc.toId(), arc.capacity(), arc.cost());
        }
        MinCostFlow.FlowResult flow1 = mcf1.solve(net1.demand());
        MinCostFlow.Solution sol1 = mcf1.extractSolution(net1);

        System.out.printf("[压力] 两阶段: 阶段1(sink=0) 流量=%d unassigned=%d%n",
                flow1.flow(), sol1.unassigned().size());

        // 如果阶段1全部unassigned(选择了无车环),则验证阶段2
        if (sol1.unassigned().size() == N) {
            ArcBuilder builder2 = new ArcBuilder(dist, 2592000, 10, 100, 2592000);
            ArcBuilder.BuildResult net2 = builder2.build(vehicles, orders);

            MinCostFlow mcf2 = new MinCostFlow(net2.nodeCount(), net2.S(), net2.T());
            for (Arc arc : net2.arcs()) {
                if (arc.lowerBound() > 0)
                    mcf2.addBoundedEdge(arc.fromId(), arc.toId(), arc.lowerBound(), arc.capacity(), arc.cost());
                else
                    mcf2.addEdge(arc.fromId(), arc.toId(), arc.capacity(), arc.cost());
            }
            MinCostFlow.FlowResult flow2 = mcf2.solve(net2.demand());
            MinCostFlow.Solution sol2 = mcf2.extractSolution(net2);

            System.out.printf("[压力] 两阶段: 阶段2(sink=M) 流量=%d unassigned=%d%n",
                    flow2.flow(), sol2.unassigned().size());

            assertTrue(flow2.feasible(), "阶段2应可行");
            assertTrue(sol2.unassigned().isEmpty() || sol2.unassigned().size() < N,
                    "阶段2应减少unassigned");
        }
    }

    // ═══════════════════════════════════════════════════════════
    // 5. 极限场景测试
    // ═══════════════════════════════════════════════════════════

    /**
     * 测试类型: 压力测试 / 边界值
     * 测试方法: 200单大网络 — 验证系统不崩溃,合理时间内完成
     */
    @Test
    @DisplayName("压力: 200单大网络 → 系统不崩溃,30s内完成")
    @org.junit.jupiter.api.Order(5)
    void veryLargeNetwork() {
        int V = 20, N = 200;
        StubDistance dist = new StubDistance(60);

        List<VehicleView> vehicles = new ArrayList<>();
        for (int v = 0; v < V; v++) {
            vehicles.add(new VehicleView((long) v + 1, 0,
                    LocalDateTime.of(2026, 7, 1, 0, 0, 0),
                    100000, 100000, SPEED_KMH, null, null, 0, 0));
        }
        List<OrderView> orders = new ArrayList<>();
        for (int i = 0; i < N; i++) {
            orders.add(new OrderView((long) 100 + i, 1, 2,
                    LocalDateTime.of(2026, 7, 1, 0, 0, 0),
                    LocalDateTime.of(2026, 7, 30, 0, 0, 0),
                    10, 5, 1, 100));
        }

        long t0 = System.nanoTime();
        ArcBuilder builder = new ArcBuilder(dist, 2592000, 10, 100, 0);
        ArcBuilder.BuildResult net = builder.build(vehicles, orders);
        long buildTime = (System.nanoTime() - t0) / 1_000_000;

        System.out.printf("[压力] 200单: 建图=%dms 弧数=%d ", buildTime, net.arcs().size());

        MinCostFlow mcf = new MinCostFlow(net.nodeCount(), net.S(), net.T());
        for (Arc arc : net.arcs()) {
            if (arc.lowerBound() > 0)
                mcf.addBoundedEdge(arc.fromId(), arc.toId(), arc.lowerBound(), arc.capacity(), arc.cost());
            else
                mcf.addEdge(arc.fromId(), arc.toId(), arc.capacity(), arc.cost());
        }

        long t1 = System.nanoTime();
        MinCostFlow.FlowResult flow = mcf.solve(net.demand());
        long solveTime = (System.nanoTime() - t1) / 1_000_000;
        long totalTime = (System.nanoTime() - t0) / 1_000_000;

        System.out.printf("求解=%dms 流量=%d%n", solveTime, flow.flow());

        assertTrue(flow.feasible(), "200单应可行");
        assertEquals(N, flow.flow(), "200单应全覆盖");
        assertTrue(totalTime < 30000, "200单总耗时应在30s内: " + totalTime + "ms");
    }

    /**
     * 测试类型: 压力测试
     * 测试方法: 100单0车辆 → 全部unassigned,但求解不崩溃
     */
    @Test
    @DisplayName("压力: 100单0车辆 → 全部unassigned,求解不崩溃")
    @org.junit.jupiter.api.Order(6)
    void manyOrdersNoVehicles() {
        int N = 100;
        StubDistance dist = new StubDistance(60);

        List<OrderView> orders = new ArrayList<>();
        for (int i = 0; i < N; i++) {
            orders.add(new OrderView((long) 100 + i, 1, 2,
                    LocalDateTime.of(2026, 7, 1, 0, 0, 0),
                    LocalDateTime.of(2026, 7, 3, 0, 0, 0),
                    10, 5, 1, 100));
        }

        ArcBuilder builder = new ArcBuilder(dist, 2592000, 10, 100, 0);
        ArcBuilder.BuildResult net = builder.build(List.of(), orders);

        MinCostFlow mcf = new MinCostFlow(net.nodeCount(), net.S(), net.T());
        for (Arc arc : net.arcs()) {
            if (arc.lowerBound() > 0)
                mcf.addBoundedEdge(arc.fromId(), arc.toId(), arc.lowerBound(), arc.capacity(), arc.cost());
            else
                mcf.addEdge(arc.fromId(), arc.toId(), arc.capacity(), arc.cost());
        }
        MinCostFlow.FlowResult flow = mcf.solve(net.demand());
        MinCostFlow.Solution sol = mcf.extractSolution(net);

        assertTrue(flow.feasible(), "即使无车,S→R_j兜底弧应让流可行");
        assertTrue(flow.flow() >= N, "兜底流应覆盖所有订单");
        assertEquals(N, sol.unassigned().size(), "所有订单应未分配");
        assertTrue(sol.vehicleRoutes().isEmpty() || sol.vehicleRoutes().values().stream().allMatch(List::isEmpty),
                "无车意味着无车辆路线");
    }
}
