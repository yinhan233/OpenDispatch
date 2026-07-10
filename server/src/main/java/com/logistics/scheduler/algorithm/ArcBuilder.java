package com.logistics.scheduler.algorithm;

import com.logistics.scheduler.distance.DistanceService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/** 两阶段求解 **/
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

        // 2. S→L_v
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

        // 3. S→L_j = sinkPenaltyLj
        for (int j = 0; j < n; j++) {
            int nodeLj = firstL + j;
            arcs.add(new Arc(S, nodeLj, 1, sinkPenaltyLj, Arc.Kind.SINK_PENALTY,
                    0, 0, orders.get(j).getOrderId()));
        }

        // 4. S→R_j. 兜底费用 M.
        for (int j = 0; j < n; j++) {
            int nodeRj = firstR + j;
            arcs.add(new Arc(S, nodeRj, 1, penaltyM, Arc.Kind.SINK_PENALTY,
                    0, 0, orders.get(j).getOrderId()));
        }

        // 5. L_v→R_j 首单
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

        // 6. L_i→R_j 续单
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
