package com.logistics.scheduler.dao;

import com.logistics.scheduler.model.Vehicle;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class VehicleDao extends BaseDao {

    public VehicleDao(DataSource ds) { super(ds); }
    public VehicleDao(DataSource ds, boolean standalone) { super(ds, standalone); }

    public Vehicle insert(Vehicle v) throws SQLException {
        long id = insertAndReturnKey(
                "INSERT INTO vehicle(person_id, status, cur_loc_id, cur_available_time, tool_type, " +
                        "max_weight, max_volume, speed, shift_start, shift_end, earned_revenue) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                v.getPersonId(), v.getStatus(), v.getCurLocId(),
                Timestamp.valueOf(v.getCurAvailableTime()), v.getToolType(),
                v.getMaxWeight(), v.getMaxVolume(), v.getSpeed(),
                v.getShiftStart() == null ? null : Timestamp.valueOf(v.getShiftStart()),
                v.getShiftEnd() == null ? null : Timestamp.valueOf(v.getShiftEnd()),
                v.getEarnedRevenue());
        v.setId(id);
        return v;
    }

    public Vehicle findById(long id) throws SQLException {
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT vehicle_id, person_id, status, cur_loc_id, cur_available_time, tool_type, " +
                             "max_weight, max_volume, speed, shift_start, shift_end, earned_revenue FROM vehicle WHERE vehicle_id=?")) {
            ps.setLong(1, id);
            return fetchOne(ps);
        }
    }

    public List<Vehicle> findByStatus(String status) throws SQLException {
        List<Vehicle> out = new ArrayList<>();
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT vehicle_id, person_id, status, cur_loc_id, cur_available_time, tool_type, " +
                             "max_weight, max_volume, speed, shift_start, shift_end, earned_revenue FROM vehicle WHERE status=?")) {
            ps.setString(1, status);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(mapRow(rs));
            }
        }
        return out;
    }

    public List<Vehicle> findAll() throws SQLException {
        List<Vehicle> out = new ArrayList<>();
        try (Connection c = open();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT vehicle_id, person_id, status, cur_loc_id, cur_available_time, tool_type, " +
                             "max_weight, max_volume, speed, shift_start, shift_end, earned_revenue FROM vehicle")) {
            while (rs.next()) out.add(mapRow(rs));
        }
        return out;
    }

    public void updateStatusAndLocation(long id, String status, long curLocId,
                                        java.time.LocalDateTime curAvail, java.math.BigDecimal earnedRevenue) throws SQLException {
        exec("UPDATE vehicle SET status=?, cur_loc_id=?, cur_available_time=?, earned_revenue=? WHERE vehicle_id=?",
                status, curLocId, Timestamp.valueOf(curAvail), earnedRevenue, id);
    }

    /** 查询某车辆的所有路线ID(用于级联删除). */
    public java.util.List<Long> findRouteIdsByVehicle(long vehicleId) throws SQLException {
        java.util.List<Long> out = new java.util.ArrayList<>();
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement("SELECT route_id FROM route WHERE vehicle_id=?")) {
            ps.setLong(1, vehicleId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(rs.getLong(1));
            }
        }
        return out;
    }

    /** 暴露 exec 供 API 层级联删除使用. */
    public int exec(String sql, Object... params) throws SQLException {
        return super.exec(sql, params);
    }

    private Vehicle fetchOne(PreparedStatement ps) throws SQLException {
        try (ResultSet rs = ps.executeQuery()) {
            return rs.next() ? mapRow(rs) : null;
        }
    }

    private Vehicle mapRow(ResultSet rs) throws SQLException {
        Vehicle v = new Vehicle();
        v.setId(rs.getLong("vehicle_id"));
        v.setPersonId(rs.getString("person_id"));
        v.setStatus(rs.getString("status"));
        v.setCurLocId(rs.getLong("cur_loc_id"));
        v.setCurAvailableTime(rs.getTimestamp("cur_available_time").toLocalDateTime());
        v.setToolType(rs.getString("tool_type"));
        v.setMaxWeight(rs.getBigDecimal("max_weight"));
        v.setMaxVolume(rs.getBigDecimal("max_volume"));
        v.setSpeed(rs.getBigDecimal("speed"));
        Timestamp ss = rs.getTimestamp("shift_start");
        if (ss != null) v.setShiftStart(ss.toLocalDateTime());
        Timestamp se = rs.getTimestamp("shift_end");
        if (se != null) v.setShiftEnd(se.toLocalDateTime());
        v.setEarnedRevenue(rs.getBigDecimal("earned_revenue"));
        return v;
    }
}
