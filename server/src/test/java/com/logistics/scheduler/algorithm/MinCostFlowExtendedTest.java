package com.logistics.scheduler.algorithm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MinCostFlow 扩展白盒测试, 聚焦 solveWithLowerBounds 路径与边界情况。
 */
class MinCostFlowExtendedTest {

    private record MFResult(MinCostFlow.FlowResult flow) {}

    private static MFResult runMF(int nodeCount, int S, int T, long demand,
                                   java.util.function.Consumer<MinCostFlow> graphBuilder) {
        MinCostFlow mcf = new MinCostFlow(nodeCount, S, T);
        graphBuilder.accept(mcf);
        return new MFResult(mcf.solve(demand));
    }

    /**
     * 测试类型: 白盒测试 — 路径覆盖
     * 测试方法: 覆盖 solveWithLowerBounds 的完整路径 (两阶段: 可行性 + 优化)
     * 覆盖的代码路径: addBoundedEdge → supply调整 → SS/TT构建 → maxFlowBFS → 残余重建 → solveSimple SSP
     */
    @Test
    void solveWithLowerBoundsSingleEdge() {
        MFResult r = runMF(3, 0, 2, 1, mcf -> {
            mcf.addBoundedEdge(0, 1, 1, 1, 0);
            mcf.addEdge(1, 2, 1, 0);
        });

        assertTrue(r.flow().feasible(), "单条下界边应可行");
        assertEquals(0, r.flow().cost(), "费用为0");
    }

    /**
     * 测试类型: 白盒测试 — 分支覆盖
     * 测试方法: 下界超出网络总供给 → feasFlow < totalSupply → infeasible
     * 覆盖的代码路径: solveWithLowerBounds 中 maxFlowBFS 返回 < totalSupply 的分支
     */
    @Test
    void solveWithLowerBoundsInfeasible() {
        MFResult r = runMF(3, 0, 2, 10, mcf -> {
            mcf.addBoundedEdge(0, 1, 10, 10, 0);
        });

        assertFalse(r.flow().feasible(), "下界10超出孤立网络总供给,应不可行");
        assertEquals(Long.MAX_VALUE, r.flow().cost());
    }

    /**
     * 测试类型: 白盒测试 — 边界值分析
     * 测试方法: 空图求解 demand=0
     * 覆盖的代码路径: solveSimple 中 while(totalFlow < demand) 首次即跳出
     */
    @Test
    void emptyGraphSolve() {
        MinCostFlow mcf = new MinCostFlow(3, 0, 2);
        MinCostFlow.FlowResult flow = mcf.solve(0);

        assertTrue(flow.feasible());
        assertEquals(0, flow.flow());
        assertEquals(0, flow.cost());
    }

    /**
     * 测试类型: 白盒测试 — 语句覆盖
     * 测试方法: 最基本单边流
     * 覆盖的代码路径: solveSimple 中 addEdge → Dijkstra → 增广 → 费用累加
     */
    @Test
    void singleEdgeSimple() {
        MFResult r = runMF(3, 0, 2, 5, mcf -> {
            mcf.addEdge(0, 1, 5, 3);
            mcf.addEdge(1, 2, 5, 0);
        });

        assertTrue(r.flow().feasible());
        assertEquals(5, r.flow().flow());
        assertEquals(15, r.flow().cost(), "5单位 × 费用3 = 15");
    }

    /**
     * 测试类型: 白盒测试 — 边界值分析
     * 测试方法: demand=0 但图有边, 验证不增广任何流
     * 覆盖的代码路径: solveSimple 中 while(totalFlow < 0) 不进入循环
     */
    @Test
    void negativeDemand() {
        MFResult r = runMF(3, 0, 2, 0, mcf -> {
            mcf.addEdge(0, 1, 10, 5);
            mcf.addEdge(1, 2, 10, 0);
        });

        assertTrue(r.flow().feasible());
        assertEquals(0, r.flow().flow(), "demand=0不应推送任何流");
        assertEquals(0, r.flow().cost());
    }

    /**
     * 测试类型: 白盒测试 — 等价类划分 (路径选择正确性)
     * 测试方法: 两条S→T路径, 验证选择费用低的路径
     * 覆盖的代码路径: SSP每次迭代选择最短路 → Dijkstra势函数更新
     */
    @Test
    void multiplePathsChooseCheaper() {
        MFResult r = runMF(4, 0, 3, 1, mcf -> {
            mcf.addEdge(0, 1, 1, 10);
            mcf.addEdge(0, 2, 1, 1);
            mcf.addEdge(1, 3, 1, 0);
            mcf.addEdge(2, 3, 1, 0);
        });

        assertTrue(r.flow().feasible());
        assertEquals(1, r.flow().flow());
        assertEquals(1, r.flow().cost(), "应选择费用1的路径");
    }

    /**
     * 测试类型: 白盒测试 — 边界值分析
     * 测试方法: 超大容量边, 验证容量不溢出
     * 覆盖的代码路径: edmonds-karp 中 aug=min(caps) 在大容量下的正确性
     */
    @Test
    void largeCapacityEdge() {
        long bigCap = Long.MAX_VALUE / 4;
        MFResult r = runMF(3, 0, 2, 100, mcf -> {
            mcf.addEdge(0, 1, bigCap, 1);
            mcf.addEdge(1, 2, bigCap, 0);
        });

        assertTrue(r.flow().feasible());
        assertEquals(100, r.flow().flow());
        assertEquals(100, r.flow().cost(), "100单位 × 费用1 = 100");
    }

    /**
     * 测试类型: 白盒测试 — 特殊等价类
     * 测试方法: 零费用环不影响最终结果 (残余图中的零费用环被势函数处理)
     * 覆盖的代码路径: SSP + 势函数处理零费用/负费用逆边的正确性
     */
    @Test
    void zeroCostCycle() {
        MFResult r = runMF(4, 0, 3, 1, mcf -> {
            mcf.addEdge(0, 1, 1, 5);
            mcf.addEdge(0, 2, 1, 5);
            mcf.addEdge(1, 3, 1, 0);
            mcf.addEdge(2, 3, 1, 0);
        });

        assertTrue(r.flow().feasible());
        assertEquals(1, r.flow().flow());
        assertEquals(5, r.flow().cost(), "两条等费路径,总费用为5");
    }
}
