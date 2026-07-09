package com.logistics.scheduler.dao;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;

public class TestDatabase {
    private static final HikariDataSource ds;

    static {
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl("jdbc:h2:mem:testdb;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        hc.setUsername("sa");
        hc.setPassword("");
        hc.setDriverClassName("org.h2.Driver");
        hc.setMaximumPoolSize(5);
        ds = new HikariDataSource(hc);
        initSchema();
    }

    private static void initSchema() {
        try (Connection conn = ds.getConnection();
             InputStream in = TestDatabase.class.getClassLoader().getResourceAsStream("schema_h2.sql")) {
            if (in == null) throw new RuntimeException("schema_h2.sql not found");
            String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            try (Statement stmt = conn.createStatement()) {
                for (String part : sql.split(";")) {
                    String trimmed = part.trim();
                    if (!trimmed.isEmpty() && !trimmed.toUpperCase().contains("INSERT INTO LOCATION")) {
                        stmt.execute(trimmed);
                    }
                }
                // Insert default location manually to avoid conflicts
                stmt.execute("INSERT INTO location (loc_id, name, lng, lat) VALUES (0, '默认地点(武汉)', 114.305469, 30.592849)");
                stmt.execute("ALTER TABLE location ALTER COLUMN loc_id RESTART WITH 1");
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to init test DB", e);
        }
    }

    public static DataSource getDataSource() { return ds; }

    public static void truncateAll() {
        try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM unassigned_order");
            stmt.execute("DELETE FROM route_item");
            stmt.execute("DELETE FROM route");
            stmt.execute("DELETE FROM order_table");
            stmt.execute("DELETE FROM vehicle");
            stmt.execute("DELETE FROM distance");
            stmt.execute("DELETE FROM location WHERE loc_id != 0");
            stmt.execute("DELETE FROM task");
        } catch (Exception e) {
            throw new RuntimeException("Failed to truncate", e);
        }
    }

    public static void resetAutoIncrement() {
        try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE location ALTER COLUMN loc_id RESTART WITH 1");
            stmt.execute("ALTER TABLE order_table ALTER COLUMN order_id RESTART WITH 1");
            stmt.execute("ALTER TABLE vehicle ALTER COLUMN vehicle_id RESTART WITH 1");
            stmt.execute("ALTER TABLE route ALTER COLUMN route_id RESTART WITH 1");
            stmt.execute("ALTER TABLE task ALTER COLUMN task_id RESTART WITH 1");
        } catch (Exception e) {
            throw new RuntimeException("Failed to reset auto increment", e);
        }
    }
}
