package com.logistics.scheduler.model;

import java.time.LocalDateTime;

public class UnassignedOrder {
    private Long orderId;
    private String reason;
    private LocalDateTime snapshotAt = LocalDateTime.now();

    public UnassignedOrder() {}

    public UnassignedOrder(Long orderId, String reason) {
        this.orderId = orderId;
        this.reason = reason;
    }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public LocalDateTime getSnapshotAt() { return snapshotAt; }
    public void setSnapshotAt(LocalDateTime snapshotAt) { this.snapshotAt = snapshotAt; }
}
