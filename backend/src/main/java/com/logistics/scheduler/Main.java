package com.logistics.scheduler;

import com.logistics.scheduler.api.*;
import com.logistics.scheduler.config.AppConfig;
import io.javalin.Javalin;

public class Main {
    public static void main(String[] args) {
        AppConfig cfg = new AppConfig();
        OrderApi orderApi = new OrderApi(cfg);
        VehicleApi vehicleApi = new VehicleApi(cfg);
        LocationApi locationApi = new LocationApi(cfg);
        ScheduleApi scheduleApi = new ScheduleApi(cfg);
        ConfigApi configApi = new ConfigApi(cfg);

        Javalin app = Javalin.create().start(8080);

        app.get("/api/orders", orderApi::all);
        app.get("/api/orders/status/{status}", orderApi::byStatus);
        app.post("/api/orders", orderApi::create);
        app.post("/api/orders/batch", orderApi::createBatch);
        app.get("/api/orders/{id}", orderApi::get);
        app.post("/api/orders/{id}/status", orderApi::setStatus);
        app.get("/api/orders/{id}/vehicle", orderApi::assignedVehicle);
        app.delete("/api/orders/{id}", orderApi::delete);

        app.get("/api/vehicles", vehicleApi::all);
        app.get("/api/vehicles/types", vehicleApi::types);
        app.get("/api/vehicles/status/{status}", vehicleApi::byStatus);
        app.post("/api/vehicles", vehicleApi::create);
        app.post("/api/vehicles/batch", vehicleApi::createBatch);
        app.get("/api/vehicles/{id}", vehicleApi::get);
        app.post("/api/vehicles/{id}/offline", vehicleApi::offline);
        app.post("/api/vehicles/{id}/status", vehicleApi::setStatus);
        app.delete("/api/vehicles/{id}", vehicleApi::delete);
        app.post("/api/vehicles/{id}/complete", vehicleApi::complete);

        app.get("/api/locations", locationApi::all);
        app.post("/api/locations", locationApi::create);
        app.post("/api/locations/batch", locationApi::createBatch);
        app.post("/api/locations/geocode", locationApi::geocode);
        app.get("/api/locations/{id}", locationApi::get);
        app.delete("/api/locations/{id}", locationApi::delete);

        app.post("/api/schedule", scheduleApi::schedule);
        app.get("/api/schedule/dry-run", scheduleApi::dryRun);
        app.get("/api/schedule/debug/arcs", scheduleApi::debugArcs);
        app.post("/api/schedule/reschedule", scheduleApi::reschedule);
        app.get("/api/schedule/dry-run-dynamic", scheduleApi::dryRunDynamic);
        app.get("/api/schedule/current", scheduleApi::current);

        app.get("/api/config/mapkey", configApi::getMapKey);
        app.post("/api/config/mapkey", configApi::saveMapKey);

        app.exception(Exception.class, (e, ctx) -> {
            ctx.status(500).result("Internal error: " + e.getMessage());
            e.printStackTrace();
        });

        System.out.println("Scheduler backend started on http://localhost:8080");
    }
}
