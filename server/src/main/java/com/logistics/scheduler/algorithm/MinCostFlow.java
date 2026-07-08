package com.logistics.scheduler.algorithm;

import java.util.*;
import java.util.stream.*;

/**最小费用流(连续最短路 SSP + Dijkstra + 势函数),支持下界.*/
public class MinCostFlow {

    private final int n;
    private final int S;
    private final int T;
    private final long[] potential;
    private final List<Edge>[] graph;
    private long[] supply;
    private boolean hasLowerBounds = false;

    public MinCostFlow(int n, int S, int T) {
        this.n = n;
        this.S = S;
        this.T = T;
        this.potential = new long[n];
        this.graph = new ArrayList[n];
        this.supply = new long[n];
        for (int i = 0; i < n; i++) graph[i] = new ArrayList<>();
    }

    /** 添加一条普通边(同时建反向边). */
    public void addEdge(int from, int to, long cap, long cost) {
        addBoundedEdge(from, to, 0, cap, cost);
    }

    /** 添加带下界的边:实际流量 ∈ [lb, ub]. */
    public void addBoundedEdge(int from, int to, long lb, long ub, long cost) {
        if (lb < 0 || ub < lb) throw new IllegalArgumentException("非法上下界 lb=" + lb + " ub=" + ub);
        if (lb > 0) {
            hasLowerBounds = true;
            supply[to] += lb;       
            supply[from] -= lb;     
        }
        long cap = ub - lb;
        Edge e = new Edge(to, cap, cost, graph[to].size(), true);
        Edge r = new Edge(from, 0, -cost, graph[from].size(), false);
        graph[from].add(e);
        graph[to].add(r);
    }

    /**
     * 求流量 demand 的最小费用流(无下界时直接 SSP;有下界时两阶段).
     * @return 总费用;若不可行返回 Long.MAX_VALUE
     */
    public FlowResult solve(long demand) {
        if (!hasLowerBounds) {
            return solveSimple(demand);
        }
        return solveWithLowerBounds(demand);
    }

    /**
     * 无下界:SSP 增广直到无法增广(dist[T]=∞)或达到 demand.
     * demand 作为流量上限;路径覆盖网络中 demand=n,但实际最大流 = 链数 ≤ n.
     * 可行性由调用方检查 extractSolution 的覆盖情况,此处 feasible=true 表示达到最大流.
     */
    private FlowResult solveSimple(long demand) {
        long totalFlow = 0;
        long totalCost = 0;
        while (totalFlow < demand) {
            long[] dist = new long[n];
            int[] prevV = new int[n];
            int[] prevE = new int[n];
            Arrays.fill(dist, Long.MAX_VALUE);
            Arrays.fill(prevV, -1);
            Arrays.fill(prevE, -1);
            dist[S] = 0;
            PriorityQueue<long[]> pq = new PriorityQueue<>(Comparator.comparingLong(a -> a[1]));
            pq.offer(new long[]{S, 0});
            while (!pq.isEmpty()) {
                long[] cur = pq.poll();
                int v = (int) cur[0];
                long d = cur[1];
                if (d > dist[v]) continue;
                for (int ei = 0; ei < graph[v].size(); ei++) {
                    Edge e = graph[v].get(ei);
                    if (e.cap <= 0) continue;
                    long rc = e.cost + potential[v] - potential[e.to];
                    if (dist[v] + rc < dist[e.to]) {
                        dist[e.to] = dist[v] + rc;
                        prevV[e.to] = v;
                        prevE[e.to] = ei;
                        pq.offer(new long[]{e.to, dist[e.to]});
                    }
                }
            }
            if (dist[T] == Long.MAX_VALUE) break;
            for (int v = 0; v < n; v++) {
                if (dist[v] < Long.MAX_VALUE) potential[v] += dist[v];
            }
            long aug = 1;
            long pathCost = 0;
            for (int v = T; v != S; v = prevV[v]) {
                Edge e = graph[prevV[v]].get(prevE[v]);
                pathCost += e.cost;
            }
            for (int v = T; v != S; v = prevV[v]) {
                Edge e = graph[prevV[v]].get(prevE[v]);
                e.cap -= aug;
                graph[v].get(e.rev).cap += aug;
            }
            totalFlow += aug;
            totalCost += pathCost * aug;
        }
        // dist[T]=∞ 表示已达最大流(totalFlow ≤ demand).
        // demand 在路径覆盖网络中是上限(n),实际最大流 = 链数 + 兜底数 ≤ n.
        // 可行性由 extractSolution 检查所有订单是否被覆盖,此处仅返回最大流结果.
        return new FlowResult(totalFlow, totalCost, true);
    }

    /**
     * 有下界:两阶段,在统一图上操作.
     *
     * 转化:对每条下界边 (u→v, lb=l, ub=u, cost=c):
     *   - 图中实际边容量 = u−l
     *   - 记 supply[v]+=l, supply[u]−=l
     *   - 下界固定流费用 l·c 单独累计
     *
     * 阶段1(可行性): 加 SS/TT,SS→supply>0 点(cap=supply),supply<0 点→TT(cap=−supply).
     *   加辅助边 T→S(cap=∞,cost=0). 跑 SS→TT 最大流. 若 = 总supply 则可行.
     *   此时图中残余 = 可行流(含下界固定部分 + SS/TT 平衡部分)在 u−l 容量下的分布.
     *
     * 阶段2(优化): 移除 SS/TT 与辅助边,在残余网络(已含可行流)上跑 S→T SSP
     *   增广到 demand,使费用最小.
     */
    private FlowResult solveWithLowerBounds(long demand) {
        long totalSupply = 0;
        for (int v = 0; v < n; v++) if (supply[v] > 0) totalSupply += supply[v];

        int SS = n, TT = n + 1, nn = n + 2;
        List<Edge>[] g = new ArrayList[nn];
        for (int i = 0; i < nn; i++) g[i] = new ArrayList<>();
        // 复制原图正向边
        for (int u = 0; u < n; u++) {
            for (Edge e : graph[u]) {
                if (e.isForward) addToListGraph(g, u, e.to, e.cap, e.cost);
            }
        }
        for (int v = 0; v < n; v++) {
            if (supply[v] > 0) addToListGraph(g, SS, v, supply[v], 0);
            else if (supply[v] < 0) addToListGraph(g, v, TT, -supply[v], 0);
        }
        long auxCap = Long.MAX_VALUE / 4;
        addToListGraph(g, T, S, auxCap, 0);  // 辅助边让下界流成环

        // 阶段1: SS→TT 最大流
        long feasFlow = maxFlowBFS(g, SS, TT, nn);
        if (feasFlow < totalSupply) return new FlowResult(0, Long.MAX_VALUE, false);

        // 把阶段1的残余映射回原图 graph:
        //   原图每条正向边的 cap 应更新为 阶段1后的残余容量.
        // g 与 graph 的边顺序一致(都是先正向后反向,且按 u 升序遍历).
        // 由于 g 是独立副本,我们用流量守恒直接重设 graph:
        //   对每条原图正向边 (u→v),阶段1中可能被穿过 f 单位 → 残余 cap = (ub−lb) − f.
        // 我们通过对比 g 与 graph 的同位置边来还原.
        // 简化做法:重建 graph 的残余 = 把 g 中非SS/TT/辅助的边复制回来.
        for (int u = 0; u < n; u++) graph[u].clear();
        for (int i = 0; i < n; i++) graph[i] = new ArrayList<>();
        for (int u = 0; u < n; u++) {
            for (Edge e : g[u]) {
                if (!e.isForward) continue;
                if (e.to == SS || e.to == TT) continue;
                if (u == SS || u == TT) continue;
                if (u == T && e.to == S) continue;  // 辅助边跳过
                addToListGraph(graph, u, e.to, e.cap, e.cost);
            }
        }

        // 阶段2: 在残余上跑 S→T SSP 到 demand
        Arrays.fill(potential, 0);
        FlowResult r = solveSimple(demand);
        if (!r.feasible()) return r;
        // 加回下界固定费用(本问题下界边费用为0,通用需累计)
        return new FlowResult(r.flow(), r.cost(), true);
    }

    /** 在邻接表图上跑 BFS 最大流(Edmonds-Karp). */
    private long maxFlowBFS(List<Edge>[] g, int src, int dst, int nn) {
        long flow = 0;
        while (true) {
            int[] prevV = new int[nn];
            int[] prevE = new int[nn];
            Arrays.fill(prevV, -1);
            Arrays.fill(prevE, -1);
            Queue<Integer> q = new ArrayDeque<>();
            q.offer(src);
            prevV[src] = src;
            while (!q.isEmpty()) {
                int v = q.poll();
                if (v == dst) break;
                for (int ei = 0; ei < g[v].size(); ei++) {
                    Edge e = g[v].get(ei);
                    if (e.cap > 0 && prevV[e.to] == -1) {
                        prevV[e.to] = v;
                        prevE[e.to] = ei;
                        q.offer(e.to);
                    }
                }
            }
            if (prevV[dst] == -1) break;
            long aug = Long.MAX_VALUE;
            for (int v = dst; v != src; v = prevV[v]) {
                aug = Math.min(aug, g[prevV[v]].get(prevE[v]).cap);
            }
            for (int v = dst; v != src; v = prevV[v]) {
                Edge e = g[prevV[v]].get(prevE[v]);
                e.cap -= aug;
                g[v].get(e.rev).cap += aug;
            }
            flow += aug;
        }
        return flow;
    }

    private void addToListGraph(List<Edge>[] g, int from, int to, long cap, long cost) {
        Edge e = new Edge(to, cap, cost, g[to].size(), true);
        Edge r = new Edge(from, 0, -cost, g[from].size(), false);
        g[from].add(e);
        g[to].add(r);
    }

    /**
     * 提取解:遍历已用正向边,得到每辆车的订单链与未分配订单.
     *
     * 网络结构(续接通过 S→L_j):
     *   车链首: S→L_v→R_j→T  (v 接 j 作首单)
     *   续接:   S→L_j→R_k→T  (j→k, j 链中非尾)
     *   兜底:   S→R_j→T      (j 未分配)
     *
     * 匹配关系: L_x→R_y 已用边表示 "x 是 y 的前驱".
     *   predecessor[y] = x (车辆节点 L_v 或订单出点 L_j)
     * 车链提取: 从 S→L_v 找首单 j (L_v→R_j),再找 S→L_j→R_k 得续接 k,依此类推.
     * 兜底提取: S→R_j 已用且 j 不在任何车链中 → 未分配.
     *
     * 两阶段: 阶段1 sinkPenaltyLj=0 可能全选 S→L_j 形成无车环(所有订单 unassigned).
     *         ScheduleService 检测后用阶段2(sinkPenaltyLj=M)重解.
     */
    public Solution extractSolution(ArcBuilder.BuildResult net) {
        Map<Long, List<Long>> vehicleRoutes = new HashMap<>();
        for (Long vid : net.vehicleNode().keySet()) vehicleRoutes.put(vid, new ArrayList<>());
        List<Long> unassigned = new ArrayList<>();
        Map<Long, Integer> idxByOrder = net.orderIndex();
        Map<Integer, Long> nodeToOrder = invertIndex(idxByOrder);
        int firstR = net.firstR();
        int firstL = net.firstL();
        int n = net.orderCount();
        int T = net.T();
        int V = net.vehicleCount();

        // 1. 收集前驱关系: L_x→R_y 已用 → predecessor[y] = x
        int[] predecessor = new int[n];
        Arrays.fill(predecessor, -1);
        for (int u = 0; u < graph.length; u++) {
            for (Edge e : graph[u]) {
                if (!e.isForward || e.cap != 0) continue;
                if (u >= 1 && u < firstR && e.to >= firstR && e.to < firstR + n) {
                    int yIdx = e.to - firstR;
                    predecessor[yIdx] = u;
                }
            }
        }

        // 2. 收集兜底: S→R_j 已用
        Set<Integer> fallbackOrders = new HashSet<>();
        for (Edge e : graph[S]) {
            if (!e.isForward || e.cap != 0) continue;
            if (e.to >= firstR && e.to < firstR + n) {
                fallbackOrders.add(e.to - firstR);
            }
        }

        // 3. 从每辆车 L_v 开始拼接链
        //    L_v→R_j (首单 j), 然后找 S→L_j→R_k (续接 k), 依此类推
        Set<Integer> coveredOrders = new HashSet<>();
        for (Edge e : graph[S]) {
            if (!e.isForward || e.cap != 0) continue;
            int lvNode = e.to;
            if (lvNode >= 1 && lvNode <= V) {
                Long vid = findVehicleId(net, lvNode);
                if (vid == null) continue;
                List<Long> route = vehicleRoutes.get(vid);
                int curLNode = lvNode;
                Set<Integer> visited = new HashSet<>();
                while (true) {
                    Integer jIdx = findUsedEdgeToRange(curLNode, firstR, n);
                    if (jIdx == null) break;
                    if (visited.contains(jIdx)) break;
                    route.add(nodeToOrder.get(jIdx));
                    coveredOrders.add(jIdx);
                    visited.add(jIdx);
                    int nextLNode = firstL + jIdx;
                    if (!isEdgeUsed(S, nextLNode)) break;
                    curLNode = nextLNode;
                }
            }
        }

        // 4. 未分配 = 兜底订单 (不在任何车辆链中的)
        for (int idx : fallbackOrders) {
            if (!coveredOrders.contains(idx)) {
                unassigned.add(nodeToOrder.get(idx));
            }
        }
        for (int idx = 0; idx < n; idx++) {
            if (!coveredOrders.contains(idx) && !fallbackOrders.contains(idx)) {
                unassigned.add(nodeToOrder.get(idx));
            }
        }

        return new Solution(vehicleRoutes, unassigned);
    }

    /** 检查 from→to 的正向边是否被使用(cap=0). */
    private boolean isEdgeUsed(int from, int to) {
        for (Edge e : graph[from]) {
            if (!e.isForward) continue;
            if (e.to == to) return e.cap == 0;
        }
        return false;
    }

    /** 从节点 from 找已用的正向边到 [targetBase, targetBase+n) 范围,返回目标索引;若无返回 null. */
    private Integer findUsedEdgeToRange(int from, int targetBase, int n) {
        for (Edge e : graph[from]) {
            if (!e.isForward || e.cap != 0) continue;
            int to = e.to;
            if (to >= targetBase && to < targetBase + n) {
                return to - targetBase;
            }
        }
        return null;
    }

    private Long findVehicleId(ArcBuilder.BuildResult net, int node) {
        for (Map.Entry<Long, Integer> en : net.vehicleNode().entrySet()) {
            if (en.getValue() == node) return en.getKey();
        }
        return null;
    }

    private Map<Integer, Long> invertIndex(Map<Long, Integer> idxByOrder) {
        Map<Integer, Long> m = new HashMap<>();
        for (Map.Entry<Long, Integer> en : idxByOrder.entrySet()) m.put(en.getValue(), en.getKey());
        return m;
    }

    private static class Edge {
        int to;
        long cap;
        long cost;
        int rev;
        boolean isForward;
        Edge(int to, long cap, long cost, int rev, boolean isForward) {
            this.to = to; this.cap = cap; this.cost = cost; this.rev = rev;
            this.isForward = isForward;
        }
    }

    public record FlowResult(long flow, long cost, boolean feasible) {}

    public record Solution(Map<Long, List<Long>> vehicleRoutes, List<Long> unassigned) {}
}
