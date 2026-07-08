package com.logistics.scheduler.api;

import com.logistics.scheduler.algorithm.ScheduleResult;
import com.logistics.scheduler.config.AppConfig;
import com.logistics.scheduler.service.ScheduleService;
import io.javalin.http.Context;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

public class ScheduleApi {
    private final ScheduleService scheduleService;

    public ScheduleApi(AppConfig cfg) { this.scheduleService = cfg.scheduleService; }

    public void schedule(Context ctx) throws Exception {
        ScheduleResult result = scheduleService.schedule();
        scheduleService.persistSchedule(result);
        ctx.json(toJson(result));
    }

    public void dryRun(Context ctx) throws Exception {
        ScheduleResult result = scheduleService.schedule();
        ctx.json(toJson(result));
    }

    public void debugArcs(Context ctx) throws Exception {
        ctx.json(scheduleService.buildOnly());
    }

    /** 动态重解(rolling horizon):考虑冻结集. */
    public void reschedule(Context ctx) throws Exception {
        ScheduleResult result = scheduleService.scheduleDynamic();
        scheduleService.persistSchedule(result, nextVersion());
        ctx.json(toJson(result));
    }

    /** 动态 dry-run:不落库,仅预览. */
    public void dryRunDynamic(Context ctx) throws Exception {
        ScheduleResult result = scheduleService.scheduleDynamic();
        ctx.json(toJson(result));
    }

    /** 查看当前已持久化的调度结果(不重新求解). */
    public void current(Context ctx) throws Exception {
        ScheduleResult result = scheduleService.getCurrentSchedule();
        ctx.json(toJson(result));
    }

    private int nextVersion() {
        return java.time.Instant.now().getEpochSecond() > 0 ? 2 : 1;
    }

    private Map<String, Object> toJson(ScheduleResult r) {
        List<Map<String, Object>> vehicles = new ArrayList<>();
        for (var e : r.getVehicleRoutes().entrySet()) {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("vehicleId", e.getKey());
            List<Map<String, Object>> route = new ArrayList<>();
            for (var leg : e.getValue()) {
                Map<String, Object> l = new LinkedHashMap<>();
                l.put("seq", route.size() + 1);
                l.put("orderId", leg.orderId());
                l.put("plannedStart", LocalDateTime.ofEpochSecond(leg.plannedStartSec(), 0, ZoneOffset.UTC).toString());
                l.put("plannedEnd", LocalDateTime.ofEpochSecond(leg.plannedEndSec(), 0, ZoneOffset.UTC).toString());
                l.put("idleBefore", leg.idleBeforeSec());
                l.put("loadWeight", leg.loadWeight());
                l.put("loadVolume", leg.loadVolume());
                route.add(l);
            }
            v.put("route", route);
            vehicles.add(v);
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalIdle", r.getTotalIdle());
        stats.put("totalRevenue", r.getTotalRevenue());
        stats.put("feasible", r.isFeasible());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("vehicles", vehicles);
        out.put("unassigned", r.getUnassigned());
        out.put("stats", stats);
        return out;
    }
}
