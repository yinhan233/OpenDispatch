package com.logistics.scheduler.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.logistics.scheduler.dao.*;
import com.logistics.scheduler.distance.DistanceService;
import com.logistics.scheduler.distance.EuclideanMapClient;
import com.logistics.scheduler.distance.MapApiClient;
import com.logistics.scheduler.distance.TencentGeocoderClient;
import com.logistics.scheduler.service.ScheduleService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

public class AppConfig {

    public final DataSource dataSource;
    public final ObjectMapper objectMapper;
    public final TaskDao taskDao;
    public final LocationDao locationDao;
    public final DistanceDao distanceDao;
    public final OrderDao orderDao;
    public final VehicleDao vehicleDao;
    public final RouteDao routeDao;
    public final RouteItemDao routeItemDao;
    public final UnassignedOrderDao unassignedOrderDao;
    public final DistanceService distanceService;
    public final MapKeyConfig mapKeyConfig;
    public final TencentGeocoderClient geocoderClient;
    public final ScheduleService scheduleService;
    public final boolean standalone;

    public AppConfig() {
        this.standalone = !isExternalDbConfigured();
        this.dataSource = buildDataSource();
        if (standalone) initH2Schema();

        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        this.taskDao = new TaskDao(dataSource);
        this.locationDao = new LocationDao(dataSource, standalone);
        this.distanceDao = new DistanceDao(dataSource);
        this.orderDao = new OrderDao(dataSource, standalone);
        this.vehicleDao = new VehicleDao(dataSource, standalone);
        this.routeDao = new RouteDao(dataSource);
        this.routeItemDao = new RouteItemDao(dataSource);
        this.unassignedOrderDao = new UnassignedOrderDao(dataSource);

        MapApiClient mapClient = new EuclideanMapClient();
        this.distanceService = new DistanceService(distanceDao, locationDao, mapClient);
        this.mapKeyConfig = new MapKeyConfig();
        this.geocoderClient = new TencentGeocoderClient(mapKeyConfig);

        long penaltyM = Long.parseLong(System.getProperty("scheduler.penaltyM", "2592000"));
        long lambda = Long.parseLong(System.getProperty("scheduler.lambda", "10"));
        double revenueUnit = Double.parseDouble(System.getProperty("scheduler.revenueUnit", "100.0"));
        this.scheduleService = new ScheduleService(orderDao, vehicleDao, distanceService,
                routeDao, routeItemDao, unassignedOrderDao, penaltyM, lambda, revenueUnit);
    }

    private static boolean isExternalDbConfigured() {
        return System.getProperty("db.url") != null
            || System.getenv("DB_HOST") != null
            || System.getProperty("db.host") != null;
    }

    private static DataSource buildDataSource() {
        String driver, url, user, pass;

        if (System.getProperty("db.url") != null || System.getenv("DB_HOST") != null || System.getProperty("db.host") != null) {
            driver = "com.mysql.cj.jdbc.Driver";
            String host = System.getProperty("db.host",
                System.getenv().getOrDefault("DB_HOST", "localhost"));
            String port = System.getProperty("db.port",
                System.getenv().getOrDefault("DB_PORT", "3306"));
            String name = System.getProperty("db.name",
                System.getenv().getOrDefault("DB_NAME", "logistics"));
            url = System.getProperty("db.url",
                String.format("jdbc:mysql://%s:%s/%s?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&createDatabaseIfNotExist=true",
                    host, port, name));
            user = System.getProperty("db.user",
                System.getenv().getOrDefault("DB_USER", "logistics"));
            pass = System.getProperty("db.password",
                System.getenv().getOrDefault("DB_PASSWORD", "logistics123"));
        } else {
            driver = "org.h2.Driver";
            String dbPath = System.getProperty("db.path", "./data/logistics");
            url = "jdbc:h2:file:" + dbPath + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE";
            user = "sa";
            pass = "";
        }

        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(url);
        hc.setUsername(user);
        hc.setPassword(pass);
        hc.setDriverClassName(driver);
        hc.setMaximumPoolSize(10);
        hc.setMinimumIdle(2);
        return new HikariDataSource(hc);
    }

    private void initH2Schema() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='PUBLIC' AND TABLE_NAME='LOCATION'");
            boolean exists = rs.next() && rs.getInt(1) > 0;
            rs.close();
            if (exists) return;

            InputStream in = getClass().getClassLoader().getResourceAsStream("schema_h2.sql");
            if (in == null) return;
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            for (String part : sql.split(";")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) stmt.execute(trimmed);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to init H2 schema", e);
        }
    }

    public void resetAutoIncrement(Connection conn, String table, String column) {
        try (Statement stmt = conn.createStatement()) {
            if (standalone) {
                stmt.execute("ALTER TABLE " + table + " ALTER COLUMN " + column + " RESTART WITH 1");
            } else {
                stmt.execute("ALTER TABLE " + table + " AUTO_INCREMENT = 1");
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to reset auto-increment for " + table, e);
        }
    }
}
