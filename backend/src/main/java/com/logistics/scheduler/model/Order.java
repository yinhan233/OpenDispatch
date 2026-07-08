package com.logistics.scheduler.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Order {
    private Long id;
    private Long taskId;
    private Long pickupLocId;
    private Long deliveryLocId;
    private LocalDateTime timeStart;
    private LocalDateTime timeEnd;
    private BigDecimal revenue = BigDecimal.ZERO;
    private BigDecimal weight = BigDecimal.ZERO;
    private BigDecimal volume = BigDecimal.ZERO;
    private Integer serviceTime = 0;
    private String status = "UNASSIGNED";
    private LocalDateTime createdAt = LocalDateTime.now();

    public Order() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Long getPickupLocId() { return pickupLocId; }
    public void setPickupLocId(Long pickupLocId) { this.pickupLocId = pickupLocId; }
    public Long getDeliveryLocId() { return deliveryLocId; }
    public void setDeliveryLocId(Long deliveryLocId) { this.deliveryLocId = deliveryLocId; }
    public LocalDateTime getTimeStart() { return timeStart; }
    public void setTimeStart(LocalDateTime timeStart) { this.timeStart = timeStart; }
    public LocalDateTime getTimeEnd() { return timeEnd; }
    public void setTimeEnd(LocalDateTime timeEnd) { this.timeEnd = timeEnd; }
    public BigDecimal getRevenue() { return revenue; }
    public void setRevenue(BigDecimal revenue) { this.revenue = revenue; }
    public BigDecimal getWeight() { return weight; }
    public void setWeight(BigDecimal weight) { this.weight = weight; }
    public BigDecimal getVolume() { return volume; }
    public void setVolume(BigDecimal volume) { this.volume = volume; }
    public Integer getServiceTime() { return serviceTime; }
    public void setServiceTime(Integer serviceTime) { this.serviceTime = serviceTime; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
