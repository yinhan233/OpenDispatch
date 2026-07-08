package com.logistics.scheduler.dao;

import com.logistics.scheduler.model.Location;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class LocationDao extends BaseDao {

    public LocationDao(DataSource ds) { super(ds); }
    public LocationDao(DataSource ds, boolean standalone) { super(ds, standalone); }

    public Location insert(Location loc) throws SQLException {
        long id = insertAndReturnKey(
                "INSERT INTO location(name, lng, lat) VALUES (?,?,?)",
                loc.getName(), loc.getLng(), loc.getLat());
        loc.setId(id);
        return loc;
    }

    public Location findById(long id) throws SQLException {
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement("SELECT loc_id, name, lng, lat FROM location WHERE loc_id=?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Location l = new Location(rs.getString("name"), rs.getDouble("lng"), rs.getDouble("lat"));
                l.setId(rs.getLong("loc_id"));
                return l;
            }
        }
    }

    public List<Location> findAll() throws SQLException {
        List<Location> out = new ArrayList<>();
        try (Connection c = open();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT loc_id, name, lng, lat FROM location")) {
            while (rs.next()) {
                Location l = new Location(rs.getString("name"), rs.getDouble("lng"), rs.getDouble("lat"));
                l.setId(rs.getLong("loc_id"));
                out.add(l);
            }
        }
        return out;
    }

    /** 查询最大ID(用于自动生成地点名称). */
    public long maxId() throws SQLException {
        try (Connection c = open();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT COALESCE(MAX(loc_id),0) FROM location")) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    public void delete(long id) throws SQLException {
        // 级联删除引用此地点的 distance 缓存
        exec("DELETE FROM distance WHERE from_loc_id=? OR to_loc_id=?", id, id);
        // 级联删除引用此地点的订单(OrderDao.delete 自身会级联 route_item/unassigned_order)
        exec("DELETE FROM route_item WHERE order_id IN (SELECT order_id FROM order_table WHERE pickup_loc_id=? OR delivery_loc_id=?)", id, id);
        exec("DELETE FROM unassigned_order WHERE order_id IN (SELECT order_id FROM order_table WHERE pickup_loc_id=? OR delivery_loc_id=?)", id, id);
        exec("DELETE FROM order_table WHERE pickup_loc_id=? OR delivery_loc_id=?", id, id);
        // 删除地点本身(若被车辆引用,FK 会阻止删除——调用方应先处理车辆)
        exec("DELETE FROM location WHERE loc_id=?", id);
        resetAutoIncrement("location", "loc_id");
    }
}
