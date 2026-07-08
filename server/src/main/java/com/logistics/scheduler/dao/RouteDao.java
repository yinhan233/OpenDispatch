package com.logistics.scheduler.dao;

import com.logistics.scheduler.model.Route;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class RouteDao extends BaseDao {

    public RouteDao(DataSource ds) { super(ds); }

    public Route insert(Route r) throws SQLException {
        long id = insertAndReturnKey(
                "INSERT INTO route(vehicle_id, status, total_idle, total_revenue, version) VALUES (?,?,?,?,?)",
                r.getVehicleId(), r.getStatus(), r.getTotalIdle(), r.getTotalRevenue(), r.getVersion());
        r.setId(id);
        return r;
    }

    public List<Route> findByVehicleId(long vehicleId) throws SQLException {
        List<Route> out = new ArrayList<>();
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT route_id, vehicle_id, status, total_idle, total_revenue, version, created_at FROM route WHERE vehicle_id=?")) {
            ps.setLong(1, vehicleId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(mapRow(rs));
            }
        }
        return out;
    }

    public List<Route> findByStatus(String status) throws SQLException {
        List<Route> out = new ArrayList<>();
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT route_id, vehicle_id, status, total_idle, total_revenue, version, created_at FROM route WHERE status=?")) {
            ps.setString(1, status);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(mapRow(rs));
            }
        }
        return out;
    }

    /** 查询多个状态的路线(用于动态调度找冻结集). */
    public List<Route> findByStatusIn(List<String> statuses) throws SQLException {
        if (statuses.isEmpty()) return new ArrayList<>();
        StringBuilder sb = new StringBuilder("SELECT route_id, vehicle_id, status, total_idle, total_revenue, version, created_at FROM route WHERE status IN (");
        for (int i = 0; i < statuses.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("?");
        }
        sb.append(")");
        List<Route> out = new ArrayList<>();
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement(sb.toString())) {
            for (int i = 0; i < statuses.size(); i++) ps.setString(i + 1, statuses.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(mapRow(rs));
            }
        }
        return out;
    }

    /** 查询某车辆最新一条路线(按 route_id 倒序). */
    public Route findLatestByVehicleId(long vehicleId) throws SQLException {
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT route_id, vehicle_id, status, total_idle, total_revenue, version, created_at FROM route WHERE vehicle_id=? ORDER BY route_id DESC LIMIT 1")) {
            ps.setLong(1, vehicleId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    public void updateStatus(long id, String status) throws SQLException {
        exec("UPDATE route SET status=? WHERE route_id=?", status, id);
    }

    public void deleteById(long id) throws SQLException {
        exec("DELETE FROM route WHERE route_id=?", id);
    }

    private Route mapRow(ResultSet rs) throws SQLException {
        Route r = new Route();
        r.setId(rs.getLong("route_id"));
        r.setVehicleId(rs.getLong("vehicle_id"));
        r.setStatus(rs.getString("status"));
        r.setTotalIdle(rs.getInt("total_idle"));
        r.setTotalRevenue(rs.getBigDecimal("total_revenue"));
        r.setVersion(rs.getInt("version"));
        r.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        return r;
    }
}
