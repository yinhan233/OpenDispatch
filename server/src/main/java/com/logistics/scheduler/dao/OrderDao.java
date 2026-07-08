package com.logistics.scheduler.dao;

import com.logistics.scheduler.model.Order;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class OrderDao extends BaseDao {

    public OrderDao(DataSource ds) { super(ds); }
    public OrderDao(DataSource ds, boolean standalone) { super(ds, standalone); }

    public Order insert(Order o) throws SQLException {
        long id = insertAndReturnKey(
                "INSERT INTO order_table(task_id, pickup_loc_id, delivery_loc_id, time_start, time_end, " +
                        "revenue, weight, volume, service_time, status) VALUES (?,?,?,?,?,?,?,?,?,?)",
                o.getTaskId(), o.getPickupLocId(), o.getDeliveryLocId(),
                Timestamp.valueOf(o.getTimeStart()), Timestamp.valueOf(o.getTimeEnd()),
                o.getRevenue(), o.getWeight(), o.getVolume(), o.getServiceTime(), o.getStatus());
        o.setId(id);
        return o;
    }

    public Order findById(long id) throws SQLException {
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT order_id, task_id, pickup_loc_id, delivery_loc_id, time_start, time_end, " +
                             "revenue, weight, volume, service_time, status, created_at FROM order_table WHERE order_id=?")) {
            ps.setLong(1, id);
            return fetchOne(ps);
        }
    }

    public List<Order> findByStatus(String status) throws SQLException {
        List<Order> out = new ArrayList<>();
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT order_id, task_id, pickup_loc_id, delivery_loc_id, time_start, time_end, " +
                             "revenue, weight, volume, service_time, status, created_at FROM order_table WHERE status=? ORDER BY time_start")) {
            ps.setString(1, status);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(mapRow(rs));
            }
        }
        return out;
    }

    public List<Order> findAll() throws SQLException {
        List<Order> out = new ArrayList<>();
        try (Connection c = open();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(
                     "SELECT order_id, task_id, pickup_loc_id, delivery_loc_id, time_start, time_end, " +
                             "revenue, weight, volume, service_time, status, created_at FROM order_table")) {
            while (rs.next()) out.add(mapRow(rs));
        }
        return out;
    }

    public void updateStatus(long id, String status) throws SQLException {
        exec("UPDATE order_table SET status=? WHERE order_id=?", status, id);
    }

    /** 批量更新状态. */
    public void updateStatusBatch(List<Long> ids, String status) throws SQLException {
        if (ids.isEmpty()) return;
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement("UPDATE order_table SET status=? WHERE order_id=?")) {
            ps.setString(1, status);
            for (long id : ids) {
                ps.setLong(2, id);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /** 批量查订单(用于冻结集查询). */
    public List<Order> findByIds(List<Long> ids) throws SQLException {
        if (ids.isEmpty()) return new ArrayList<>();
        StringBuilder sb = new StringBuilder("SELECT order_id, task_id, pickup_loc_id, delivery_loc_id, time_start, time_end, revenue, weight, volume, service_time, status, created_at FROM order_table WHERE order_id IN (");
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("?");
        }
        sb.append(") ORDER BY time_start");
        List<Order> out = new ArrayList<>();
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement(sb.toString())) {
            for (int i = 0; i < ids.size(); i++) ps.setLong(i + 1, ids.get(i));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(mapRow(rs));
            }
        }
        return out;
    }

    public void delete(long id) throws SQLException {
        // 级联删除引用此订单的 route_item（外键无 ON DELETE CASCADE，需手动处理）
        exec("DELETE FROM route_item WHERE order_id=?", id);
        // 级联删除未分配兜底记录
        exec("DELETE FROM unassigned_order WHERE order_id=?", id);
        exec("DELETE FROM order_table WHERE order_id=?", id);
        resetAutoIncrement("order_table", "order_id");
    }

    private Order fetchOne(PreparedStatement ps) throws SQLException {
        try (ResultSet rs = ps.executeQuery()) {
            return rs.next() ? mapRow(rs) : null;
        }
    }

    private Order mapRow(ResultSet rs) throws SQLException {
        Order o = new Order();
        o.setId(rs.getLong("order_id"));
        o.setTaskId(rs.getLong("task_id"));
        o.setPickupLocId(rs.getLong("pickup_loc_id"));
        o.setDeliveryLocId(rs.getLong("delivery_loc_id"));
        o.setTimeStart(rs.getTimestamp("time_start").toLocalDateTime());
        o.setTimeEnd(rs.getTimestamp("time_end").toLocalDateTime());
        o.setRevenue(rs.getBigDecimal("revenue"));
        o.setWeight(rs.getBigDecimal("weight"));
        o.setVolume(rs.getBigDecimal("volume"));
        o.setServiceTime(rs.getInt("service_time"));
        o.setStatus(rs.getString("status"));
        o.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        return o;
    }
}
