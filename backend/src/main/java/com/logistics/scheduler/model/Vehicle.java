package com.logistics.scheduler.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Vehicle {
    private Long id;
    private String personId;
    private String status = "IDLE";
    private Long curLocId;
    private LocalDateTime curAvailableTime = LocalDateTime.now();
    private String toolType;
    private BigDecimal maxWeight = BigDecimal.ZERO;
    private BigDecimal maxVolume = BigDecimal.ZERO;
    private BigDecimal speed = new BigDecimal("30.0");
    private LocalDateTime shiftStart;
    private LocalDateTime shiftEnd;
    private BigDecimal earnedRevenue = BigDecimal.ZERO;

    public Vehicle() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPersonId() { return personId; }
    public void setPersonId(String personId) { this.personId = personId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getCurLocId() { return curLocId; }
    public void setCurLocId(Long curLocId) { this.curLocId = curLocId; }
    public LocalDateTime getCurAvailableTime() { return curAvailableTime; }
    public void setCurAvailableTime(LocalDateTime curAvailableTime) { this.curAvailableTime = curAvailableTime; }
    public String getToolType() { return toolType; }
    public void setToolType(String toolType) { this.toolType = toolType; }
    public BigDecimal getMaxWeight() { return maxWeight; }
    public void setMaxWeight(BigDecimal maxWeight) { this.maxWeight = maxWeight; }
    public BigDecimal getMaxVolume() { return maxVolume; }
    public void setMaxVolume(BigDecimal maxVolume) { this.maxVolume = maxVolume; }
    public BigDecimal getSpeed() { return speed; }
    public void setSpeed(BigDecimal speed) { this.speed = speed; }
    public LocalDateTime getShiftStart() { return shiftStart; }
    public void setShiftStart(LocalDateTime shiftStart) { this.shiftStart = shiftStart; }
    public LocalDateTime getShiftEnd() { return shiftEnd; }
    public void setShiftEnd(LocalDateTime shiftEnd) { this.shiftEnd = shiftEnd; }
    public BigDecimal getEarnedRevenue() { return earnedRevenue; }
    public void setEarnedRevenue(BigDecimal earnedRevenue) { this.earnedRevenue = earnedRevenue; }
}
