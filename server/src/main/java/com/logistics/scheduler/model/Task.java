package com.logistics.scheduler.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Task {
    private Long id;
    private LocalDateTime createdAt = LocalDateTime.now();
    private String status = "OPEN";

    public Task() {}

    public Task(String status) { this.status = status; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
