package com.logistics.scheduler.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Route {
    private Long id;
    private Long vehicleId;
    private String status = "PLANNED";
    private Integer totalIdle = 0;
    private BigDecimal totalRevenue = BigDecimal.ZERO;
    private Integer version = 1;
    private LocalDateTime createdAt = LocalDateTime.now();

    public Route() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getVehicleId() { return vehicleId; }
    public void setVehicleId(Long vehicleId) { this.vehicleId = vehicleId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getTotalIdle() { return totalIdle; }
    public void setTotalIdle(Integer totalIdle) { this.totalIdle = totalIdle; }
    public BigDecimal getTotalRevenue() { return totalRevenue; }
    public void setTotalRevenue(BigDecimal totalRevenue) { this.totalRevenue = totalRevenue; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
