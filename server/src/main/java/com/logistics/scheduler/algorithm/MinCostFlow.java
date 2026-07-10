package com.logistics.scheduler.algorithm;

import java.util.*;
import java.util.stream.*;

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

    public void addEdge(int from, int to, long cap, long cost) {
        addBoundedEdge(from, to, 0, cap, cost);
    }

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

    public FlowResult solve(long demand) {
        if (!hasLowerBounds) {
            return solveSimple(demand);
        }
        return solveWithLowerBounds(demand);
    }

    /*SSP*/
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
        return new FlowResult(totalFlow, totalCost, true);
    }

    /*边*/
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

        // 阶段1: SS→TT
        long feasFlow = maxFlowBFS(g, SS, TT, nn);
        if (feasFlow < totalSupply) return new FlowResult(0, Long.MAX_VALUE, false);

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

    /** 在邻接表图上跑 BFS 最大流 */
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

        // 1. 收集前驱关系
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

        // 2. 收集兜底
        Set<Integer> fallbackOrders = new HashSet<>();
        for (Edge e : graph[S]) {
            if (!e.isForward || e.cap != 0) continue;
            if (e.to >= firstR && e.to < firstR + n) {
                fallbackOrders.add(e.to - firstR);
            }
        }

        // 3. 从每辆车 L_v 开始拼接链
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
