package com.logistics.scheduler.algorithm;

import java.util.List;
import java.util.Map;

/**
 * 一次完整调度结果(供持久化与JSON输出).
 */
public class ScheduleResult {
    private final long totalIdle;          // 秒
    private final double totalRevenue;
    private final Map<Long, List<RouteLeg>> vehicleRoutes; // vehicleId -> 路线
    private final List<Long> unassigned;
    private final boolean feasible;

    public ScheduleResult(long totalIdle, double totalRevenue,
                          Map<Long, List<RouteLeg>> vehicleRoutes,
                          List<Long> unassigned, boolean feasible) {
        this.totalIdle = totalIdle;
        this.totalRevenue = totalRevenue;
        this.vehicleRoutes = vehicleRoutes;
        this.unassigned = unassigned;
        this.feasible = feasible;
    }

    public long getTotalIdle() { return totalIdle; }
    public double getTotalRevenue() { return totalRevenue; }
    public Map<Long, List<RouteLeg>> getVehicleRoutes() { return vehicleRoutes; }
    public List<Long> getUnassigned() { return unassigned; }
    public boolean isFeasible() { return feasible; }

    public record RouteLeg(long orderId, long plannedStartSec, long plannedEndSec,
                           long idleBeforeSec, double loadWeight, double loadVolume) {}
}
