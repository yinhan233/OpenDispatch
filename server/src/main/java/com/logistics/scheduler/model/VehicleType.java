package com.logistics.scheduler.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/*车辆类*/
public class VehicleType {
    private final String name;
    private final BigDecimal maxWeight;
    private final BigDecimal maxVolume;
    private final BigDecimal speed;

    public VehicleType(String name, double maxWeight, double maxVolume, double speed) {
        this.name = name;
        this.maxWeight = BigDecimal.valueOf(maxWeight);
        this.maxVolume = BigDecimal.valueOf(maxVolume);
        this.speed = BigDecimal.valueOf(speed);
    }

    public String getName() { return name; }
    public BigDecimal getMaxWeight() { return maxWeight; }
    public BigDecimal getMaxVolume() { return maxVolume; }
    public BigDecimal getSpeed() { return speed; }

    /** 预设车型列表. */
    public static List<VehicleType> presets() {
        List<VehicleType> list = new ArrayList<>();
        list.add(new VehicleType("电瓶车",     150,   1.5,  20));
        list.add(new VehicleType("三轮车",     300,   2.0,  18));
        list.add(new VehicleType("微型面包车", 500,   4.0,  35));
        list.add(new VehicleType("厢式货车",  1500,  12.0,  40));
        list.add(new VehicleType("吉普车",     400,   3.0,  60));
        list.add(new VehicleType("平板卡车",  3000,  20.0,  45));
        list.add(new VehicleType("冷藏车",    2000,  10.0,  40));
        list.add(new VehicleType("大型重卡", 10000,  40.0,  50));
        return list;
    }
}
