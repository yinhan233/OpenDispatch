package com.logistics.scheduler.algorithm;

import com.logistics.scheduler.distance.DistanceService;
import com.logistics.scheduler.distance.MapApiClient;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ArcBuilder + MinCostFlow 端到端正确性测试(无DB/Spring依赖).
 * 用 stub DistanceService 固定行驶时间,验证:
 *   - 单车双单顺序执行(链式)
 *   - 闲置计算正确
 *   - demand=n 保证所有订单被覆盖
 */
class MinCostFlowTest {

    /** 固定行驶时间的 stub:任意两点间 travel = (a,b) 表中预置值,默认60秒.
     *  distanceMeters = sec * 10.0(配合 speed=36km/h 使 time=dist/speed=sec 不变). */
    private static class StubDistance extends DistanceService {
        private final Map<Long, Map<Long, Integer>> times = new HashMap<>();
        private int defaultSec = 60;

        StubDistance() { super(null, null, null); }

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

    /** 36 km/h = 10 m/s; 与 stub 的 dist=sec*10 配合,使 travelSec=dist/speed=sec. */
    private static final double TEST_SPEED_KMH = 36.0;

    @Test
    void singleVehicleTwoOrdersInSequence() {
        StubDistance dist = new StubDistance();
        dist.put(1, 2, 100);
        dist.put(2, 3, 200);
        dist.put(3, 4, 50);
        dist.put(4, 5, 200);

        OrderView o1 = new OrderView(101, 2, 3,
                LocalDateTime.of(2026, 1, 1, 0, 16, 40),
                LocalDateTime.of(2026, 1, 1, 1, 0, 0),
                60, 10, 1, 100);
        OrderView o2 = new OrderView(102, 4, 5,
                LocalDateTime.of(2026, 1, 1, 0, 23, 20),
                LocalDateTime.of(2026, 1, 1, 1, 0, 0),
                60, 10, 1, 100);

        VehicleView v = new VehicleView(1, 1, LocalDateTime.of(2026, 1, 1, 0, 0, 0),
                100, 100, TEST_SPEED_KMH, null, null, 0, 0);

        ArcBuilder builder = new ArcBuilder(dist, 86400, 0, 100, 0);
        ArcBuilder.BuildResult net = builder.build(List.of(v), List.of(o1, o2));

        MinCostFlow mcf = new MinCostFlow(net.nodeCount(), net.S(), net.T());
        for (Arc arc : net.arcs()) {
            if (arc.lowerBound() > 0) mcf.addBoundedEdge(arc.fromId(), arc.toId(), arc.lowerBound(), arc.capacity(), arc.cost());
            else mcf.addEdge(arc.fromId(), arc.toId(), arc.capacity(), arc.cost());
        }
        MinCostFlow.FlowResult flow = mcf.solve(net.demand());

        assertTrue(flow.feasible(), "SSP应返回feasible");
        assertEquals(2, flow.flow(), "demand=n=2, 2单位流覆盖2单");

        MinCostFlow.Solution sol = mcf.extractSolution(net);
        List<Long> route = sol.vehicleRoutes().get(1L);
        assertNotNull(route);
        assertEquals(2, route.size(), "车1应执行2单");
        assertEquals(101L, route.get(0), "首单应为101");
        assertEquals(102L, route.get(1), "次单应为102");
        assertTrue(sol.unassigned().isEmpty(), "不应有未分配订单");
    }

    @Test
    void unassignedWhenNoFeasibleVehicle() {
        StubDistance dist = new StubDistance();
        dist.put(1, 2, 100);
        dist.put(2, 3, 200);

        OrderView o1 = new OrderView(201, 2, 3,
                LocalDateTime.of(2026, 1, 1, 0, 16, 40),
                LocalDateTime.of(2026, 1, 1, 0, 16, 40),
                60, 10, 1, 100);

        VehicleView v = new VehicleView(1, 1, LocalDateTime.of(2026, 1, 1, 0, 0, 0),
                100, 100, TEST_SPEED_KMH, null, null, 0, 0);

        ArcBuilder builder = new ArcBuilder(dist, 86400, 0, 100, 0);
        ArcBuilder.BuildResult net = builder.build(List.of(v), List.of(o1));

        MinCostFlow mcf = new MinCostFlow(net.nodeCount(), net.S(), net.T());
        for (Arc arc : net.arcs()) {
            if (arc.lowerBound() > 0) mcf.addBoundedEdge(arc.fromId(), arc.toId(), arc.lowerBound(), arc.capacity(), arc.cost());
            else mcf.addEdge(arc.fromId(), arc.toId(), arc.capacity(), arc.cost());
        }
        MinCostFlow.FlowResult flow = mcf.solve(net.demand());

        assertTrue(flow.feasible(), "兜底边应让流可满足");
        MinCostFlow.Solution sol = mcf.extractSolution(net);
        assertTrue(sol.vehicleRoutes().get(1L).isEmpty(), "不可行单不应分配给车");
        assertEquals(1, sol.unassigned().size(), "不可行单应进兜底");
        assertEquals(201L, sol.unassigned().get(0));
    }

    @Test
    void twoVehiclesPickCheaperIdle() {
        StubDistance dist = new StubDistance();
        dist.put(1, 2, 100);
        dist.put(2, 3, 200);
        dist.put(1, 4, 900);
        dist.put(4, 5, 200);
        dist.put(6, 4, 50);
        dist.put(6, 2, 900);
        dist.put(3, 4, 50);

        OrderView o1 = new OrderView(301, 2, 3,
                LocalDateTime.of(2026, 1, 1, 0, 16, 40),
                LocalDateTime.of(2026, 1, 1, 2, 0, 0),
                60, 10, 1, 100);
        OrderView o2 = new OrderView(302, 4, 5,
                LocalDateTime.of(2026, 1, 1, 0, 16, 40),
                LocalDateTime.of(2026, 1, 1, 2, 0, 0),
                60, 10, 1, 100);

        VehicleView v1 = new VehicleView(1, 1, LocalDateTime.of(2026, 1, 1, 0, 0, 0),
                100, 100, TEST_SPEED_KMH, null, null, 0, 0);
        VehicleView v2 = new VehicleView(2, 6, LocalDateTime.of(2026, 1, 1, 0, 0, 0),
                100, 100, TEST_SPEED_KMH, null, null, 0, 0);

        ArcBuilder builder = new ArcBuilder(dist, 86400, 0, 100, 0);
        ArcBuilder.BuildResult net = builder.build(List.of(v1, v2), List.of(o1, o2));

        MinCostFlow mcf = new MinCostFlow(net.nodeCount(), net.S(), net.T());
        for (Arc arc : net.arcs()) {
            if (arc.lowerBound() > 0) mcf.addBoundedEdge(arc.fromId(), arc.toId(), arc.lowerBound(), arc.capacity(), arc.cost());
            else mcf.addEdge(arc.fromId(), arc.toId(), arc.capacity(), arc.cost());
        }
        MinCostFlow.FlowResult flow = mcf.solve(net.demand());

        assertTrue(flow.feasible());
        MinCostFlow.Solution sol = mcf.extractSolution(net);
        // 最优:车1→o2(idle=1000-0-900=100), 车2→o1(idle=1000-0-900=100), 总idle=200
        // (而非 车1→o1 + 车2→o2: 900+950=1850)
        assertEquals(302L, sol.vehicleRoutes().get(1L).get(0), "车1应接订单302(idle更小)");
        assertEquals(301L, sol.vehicleRoutes().get(2L).get(0), "车2应接订单301(idle更小)");
        assertTrue(sol.unassigned().isEmpty());
    }
}
