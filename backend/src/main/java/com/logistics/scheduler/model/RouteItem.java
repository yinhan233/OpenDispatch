package com.logistics.scheduler.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class RouteItem {
    private Long routeId;
    private Integer seq;
    private Long orderId;
    private LocalDateTime plannedStart;
    private LocalDateTime plannedEnd;
    private Integer idleBefore = 0;
    private BigDecimal loadWeight = BigDecimal.ZERO;
    private BigDecimal loadVolume = BigDecimal.ZERO;

    public RouteItem() {}

    public Long getRouteId() { return routeId; }
    public void setRouteId(Long routeId) { this.routeId = routeId; }
    public Integer getSeq() { return seq; }
    public void setSeq(Integer seq) { this.seq = seq; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public LocalDateTime getPlannedStart() { return plannedStart; }
    public void setPlannedStart(LocalDateTime plannedStart) { this.plannedStart = plannedStart; }
    public LocalDateTime getPlannedEnd() { return plannedEnd; }
    public void setPlannedEnd(LocalDateTime plannedEnd) { this.plannedEnd = plannedEnd; }
    public Integer getIdleBefore() { return idleBefore; }
    public void setIdleBefore(Integer idleBefore) { this.idleBefore = idleBefore; }
    public BigDecimal getLoadWeight() { return loadWeight; }
    public void setLoadWeight(BigDecimal loadWeight) { this.loadWeight = loadWeight; }
    public BigDecimal getLoadVolume() { return loadVolume; }
    public void setLoadVolume(BigDecimal loadVolume) { this.loadVolume = loadVolume; }
}
