package com.logistics.scheduler.algorithm;

import com.logistics.scheduler.distance.DistanceService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 构建"最小路径覆盖"网络(二部图 + 最小费用最大流),精确求解总闲置最短.
 *
 * 节点编号:
 *   0                = S
 *   1 .. V           = L_v(车辆出点)
 *   V+1 .. V+n       = R_j(订单入点)
 *   V+n+1 .. V+2n    = L_j(订单出点)
 *   V+2n+1           = T
 *
 * 边:
 *   S→L_v    容量1, 费用λ·base_v+收益惩罚  (车 v 启动一条链;收益越高惩罚越大)
 *   S→L_j    容量1, 费用=sinkPenaltyLj    (j 作续接起点;阶段1=0,阶段2=M)
 *   S→R_j    容量1, 费用M                 (兜底:j 无前驱,未分配)
 *   L_v→R_j  容量1, 费用=idle(v,j)        (车 v 接 j 作首单)
 *   L_i→R_j  容量1, 费用=idle(i,j)        (i 后紧接 j)
 *   R_j→T    容量1, 费用0                 (j 被覆盖)
 *
 * demand = n. 每单位流覆盖1个订单.
 *   车链首: S→L_v→R_j→T  (v 接 j)
 *   续接:   S→L_j→R_k→T  (j→k, j 已由车链覆盖)
 *   兜底:   S→R_j→T      (j 未分配,费用M)
 *
 * 两阶段求解(防止"无车环"):
 *   阶段1: sinkPenaltyLj=0. SSP 可能全选 S→L_j 形成无车环(绕过车辆).
 *          extractSolution 只从 S→L_v 拼接链;无车环中的订单→unassigned.
 *          若所有订单 unassigned(无 S→L_v 被使用),进入阶段2.
 *   阶段2: sinkPenaltyLj=M. S→L_j 变昂贵,SSP 被迫使用 S→L_v.
 *          每个订单分配给一辆车(或兜底).无续接,但保证车辆被使用.
 */
public class ArcBuilder {

    private final DistanceService distanceService;
    private final long penaltyM;
    private final long lambda;
    private final double revenueUnit;
    private final long sinkPenaltyLj;

    public ArcBuilder(DistanceService distanceService, long penaltyM, long lambda,
                      double revenueUnit, long sinkPenaltyLj) {
        this.distanceService = distanceService;
        this.penaltyM = penaltyM;
        this.lambda = lambda;
        this.revenueUnit = revenueUnit;
        this.sinkPenaltyLj = sinkPenaltyLj;
    }

    public BuildResult build(List<VehicleView> vehicles, List<OrderView> orders) {
        int V = vehicles.size();
        int n = orders.size();
        int S = 0;
        int firstV = 1;
        int firstR = V + 1;
        int firstL = V + n + 1;
        int T = V + 2 * n + 1;

        Map<Long, Integer> vehNode = new HashMap<>();
        for (int k = 0; k < V; k++) vehNode.put(vehicles.get(k).getVehicleId(), firstV + k);
        Map<Long, Integer> orderIndex = new HashMap<>();
        for (int k = 0; k < n; k++) orderIndex.put(orders.get(k).getOrderId(), k);

        List<Arc> arcs = new ArrayList<>();

        // 1. 计算每单 e_i = a_i + travel(p_i, q_i) + service_i
        for (OrderView o : orders) {
            var ti = distanceService.getTravel(o.getPickupLocId(), o.getDeliveryLocId());
            o.setESec(o.getASec() + ti.travelTimeSeconds() + o.getServiceSec());
        }

        // 2. S→L_v (车辆启动一条链).
        //    费用 = λ·base_v + revenueUnit·max(0, earned_v − avgEarned)  (收益均衡惩罚).
        //    以全体车辆的平均已获收益为基准: 只惩罚"高于平均"的车,惩罚额 = 超出均值的差值,
        //    从而 SSP 倾向把新单分给收益≤平均的车,实现个体收益均衡.
        //    低于/等于平均的车惩罚为0; 全部 earned 相等(含全0)时退化为纯 λ·base_v.
        //
        //    惩罚上限 clamp 到 penaltyM/2: 保证 penalty+idle < 兜底费用M.
        //    这样若某订单只有这一辆(高收益)车可行,覆盖它的最优选择仍是派给该车
        //    (S→L_v→R_j→T 费用 < S→R_j→T 的 M),而非放弃 → "唯一可行车必派单".
        double avgEarned = 0;
        if (!vehicles.isEmpty()) {
            double sum = 0;
            for (VehicleView v : vehicles) sum += v.getEarnedRevenue();
            avgEarned = sum / vehicles.size();
        }
        long penaltyCap = penaltyM / 2;
        for (VehicleView v : vehicles) {
            int nodeLv = vehNode.get(v.getVehicleId());
            long revenuePenalty = Math.round(revenueUnit * Math.max(0, v.getEarnedRevenue() - avgEarned));
            revenuePenalty = Math.min(revenuePenalty, penaltyCap);
            long cost = lambda * v.getBaseSegment() + revenuePenalty;
            arcs.add(new Arc(S, nodeLv, 1, cost, Arc.Kind.SOURCE_TO_VEHICLE,
                    v.getVehicleId(), 0, 0));
        }

        // 3. S→L_j (j 可作续接起点). 费用 = sinkPenaltyLj (阶段1=0, 阶段2=M).
        for (int j = 0; j < n; j++) {
            int nodeLj = firstL + j;
            arcs.add(new Arc(S, nodeLj, 1, sinkPenaltyLj, Arc.Kind.SINK_PENALTY,
                    0, 0, orders.get(j).getOrderId()));
        }

        // 4. S→R_j 兜底(j 无前驱,未分配). 费用 M.
        for (int j = 0; j < n; j++) {
            int nodeRj = firstR + j;
            arcs.add(new Arc(S, nodeRj, 1, penaltyM, Arc.Kind.SINK_PENALTY,
                    0, 0, orders.get(j).getOrderId()));
        }

        // 5. L_v→R_j 首单(车 v 接 j 作链首,仅可行时)
        for (VehicleView v : vehicles) {
            int nodeLv = vehNode.get(v.getVehicleId());
            double speedMps = v.getSpeedKmh() * 1000.0 / 3600.0;
            for (int j = 0; j < n; j++) {
                OrderView o = orders.get(j);
                if (!o.isFeasible()) continue;
                var ti = distanceService.getTravel(v.getCurLocId(), o.getPickupLocId());
                long travelSec = Math.max(1, Math.round(ti.distanceMeters() / speedMps));
                long arriveSec = v.getCurAvailSec() + travelSec;
                long actualStart = Math.max(o.getASec(), arriveSec);
                long idle = Math.max(0, arriveSec - o.getASec());
                var ti2 = distanceService.getTravel(o.getPickupLocId(), o.getDeliveryLocId());
                long travelPDSec = Math.max(1, Math.round(ti2.distanceMeters() / speedMps));
                long actualEnd = actualStart + travelPDSec + o.getServiceSec();
                if (actualEnd > o.getBSec()) continue;
                if (actualStart < v.getShiftStartSec() || actualEnd > v.getShiftEndSec()) continue;
                if (o.getWeight() > v.getMaxWeight() || o.getVolume() > v.getMaxVolume()) continue;
                int rNode = firstR + j;
                arcs.add(new Arc(nodeLv, rNode, 1, idle, Arc.Kind.VEHICLE_TO_ORDER,
                        v.getVehicleId(), 0, o.getOrderId()));
            }
        }

        // 6. L_i→R_j 续单(i 后接 j)
        for (int i = 0; i < n; i++) {
            OrderView oi = orders.get(i);
            if (!oi.isFeasible()) continue;
            int nodeLi = firstL + i;
            for (int j = 0; j < n; j++) {
                if (i == j) continue;
                OrderView oj = orders.get(j);
                if (!oj.isFeasible()) continue;
                var ti = distanceService.getTravel(oi.getDeliveryLocId(), oj.getPickupLocId());
                long arriveSec = oi.getESec() + ti.travelTimeSeconds();
                long actualStart = Math.max(oj.getASec(), arriveSec);
                long idle = Math.max(0, arriveSec - oj.getASec());
                long actualEnd = actualStart + (oj.getESec() - oj.getASec());
                if (actualEnd > oj.getBSec()) continue;
                int rNode = firstR + j;
                arcs.add(new Arc(nodeLi, rNode, 1, idle, Arc.Kind.ORDER_TO_ORDER,
                        0, oi.getOrderId(), oj.getOrderId()));
            }
        }

        // 7. R_j→T (j 被覆盖). 容量 1.
        for (int j = 0; j < n; j++) {
            arcs.add(new Arc(firstR + j, T, 1, 0, Arc.Kind.ORDER_TO_SINK,
                    0, orders.get(j).getOrderId(), 0));
        }

        int nodeCount = T + 1;
        long demand = n;
        return new BuildResult(arcs, nodeCount, S, T, V, n,
                vehNode, orderIndex, demand, firstR, firstL);
    }

    public record BuildResult(List<Arc> arcs, int nodeCount, int S, int T,
                              int vehicleCount, int orderCount,
                              Map<Long, Integer> vehicleNode, Map<Long, Integer> orderIndex,
                              long demand, int firstR, int firstL) {}
}
