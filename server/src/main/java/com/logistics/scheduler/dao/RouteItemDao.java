package com.logistics.scheduler.dao;

import com.logistics.scheduler.model.RouteItem;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class RouteItemDao extends BaseDao {

    public RouteItemDao(DataSource ds) { super(ds); }

    public void insert(RouteItem item) throws SQLException {
        exec("INSERT INTO route_item(route_id, seq, order_id, planned_start, planned_end, " +
                     "idle_before, load_weight, load_volume) VALUES (?,?,?,?,?,?,?,?)",
                item.getRouteId(), item.getSeq(), item.getOrderId(),
                Timestamp.valueOf(item.getPlannedStart()), Timestamp.valueOf(item.getPlannedEnd()),
                item.getIdleBefore(), item.getLoadWeight(), item.getLoadVolume());
    }

    public List<RouteItem> findByRouteId(long routeId) throws SQLException {
        List<RouteItem> out = new ArrayList<>();
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT route_id, seq, order_id, planned_start, planned_end, idle_before, load_weight, load_volume " +
                     "FROM route_item WHERE route_id=? ORDER BY seq ASC")) {
            ps.setLong(1, routeId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(mapRow(rs));
            }
        }
        return out;
    }

    /** 批量查询多条路线的明细(动态调度找冻结集). */
    public List<RouteItem> findByRouteIds(List<Long> routeIds) throws SQLException {
        if (routeIds.isEmpty()) return new ArrayList<>();
        StringBuilder sb = new StringBuilder("SELECT route_id, seq, order_id, planned_start, planned_end, idle_before, load_weight, load_volume FROM route_item WHERE route_id IN (");
        for (int i = 0; i < routeIds.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("?");
        }
        sb.append(") ORDER BY route_id, seq ASC");
        List<RouteItem> out = new ArrayList<>();
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement(sb.toString())) {
            for (int i = 0; i < routeIds.size(); i++) ps.setLong(i + 1, routeIds.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(mapRow(rs));
            }
        }
        return out;
    }

    private RouteItem mapRow(ResultSet rs) throws SQLException {
        RouteItem it = new RouteItem();
        it.setRouteId(rs.getLong("route_id"));
        it.setSeq(rs.getInt("seq"));
        it.setOrderId(rs.getLong("order_id"));
        it.setPlannedStart(rs.getTimestamp("planned_start").toLocalDateTime());
        it.setPlannedEnd(rs.getTimestamp("planned_end").toLocalDateTime());
        it.setIdleBefore(rs.getInt("idle_before"));
        it.setLoadWeight(rs.getBigDecimal("load_weight"));
        it.setLoadVolume(rs.getBigDecimal("load_volume"));
        return it;
    }

    /**
     * 查询某订单被分配到哪辆车(通过 route_item JOIN route).
     * 仅查 PLANNED/EXECUTING 路线(有效分配). 返回 vehicleId, 若无返回 -1.
     */
    public long findVehicleByOrderId(long orderId) throws SQLException {
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT r.vehicle_id FROM route_item ri " +
                     "JOIN route r ON ri.route_id = r.route_id " +
                     "WHERE ri.order_id = ? AND r.status IN ('PLANNED','EXECUTING') " +
                     "ORDER BY ri.route_id DESC LIMIT 1")) {
            ps.setLong(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : -1;
            }
        }
    }

    /** 删除指定订单的所有 route_item 记录(解除分配关系). */
    public void deleteByOrderId(long orderId) throws SQLException {
        exec("DELETE FROM route_item WHERE order_id=?", orderId);
    }
}
