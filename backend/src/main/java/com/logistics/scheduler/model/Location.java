package com.logistics.scheduler.model;

public class Location {
    private Long id;
    private String name;
    private Double lng;
    private Double lat;

    public Location() {}

    public Location(Double lng, Double lat) {
        this.lng = lng;
        this.lat = lat;
    }

    public Location(String name, Double lng, Double lat) {
        this.name = name;
        this.lng = lng;
        this.lat = lat;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Double getLng() { return lng; }
    public void setLng(Double lng) { this.lng = lng; }
    public Double getLat() { return lat; }
    public void setLat(Double lat) { this.lat = lat; }
}
