package com.logistics.scheduler.model;

public class Distance {
    private Long fromLocId;
    private Long toLocId;
    private Integer travelTime;
    private Double dist;
    private String source = "euclid";

    public Distance() {}

    public Long getFromLocId() { return fromLocId; }
    public void setFromLocId(Long fromLocId) { this.fromLocId = fromLocId; }
    public Long getToLocId() { return toLocId; }
    public void setToLocId(Long toLocId) { this.toLocId = toLocId; }
    public Integer getTravelTime() { return travelTime; }
    public void setTravelTime(Integer travelTime) { this.travelTime = travelTime; }
    public Double getDist() { return dist; }
    public void setDist(Double dist) { this.dist = dist; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
}
