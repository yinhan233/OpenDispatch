package com.logistics.scheduler.dao;

import com.logistics.scheduler.model.Distance;

import javax.sql.DataSource;
import java.sql.*;

public class DistanceDao extends BaseDao {

    public DistanceDao(DataSource ds) { super(ds); }

    public Distance findById(long from, long to) throws SQLException {
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT from_loc_id, to_loc_id, travel_time, dist, source FROM distance WHERE from_loc_id=? AND to_loc_id=?")) {
            ps.setLong(1, from);
            ps.setLong(2, to);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Distance d = new Distance();
                d.setFromLocId(rs.getLong("from_loc_id"));
                d.setToLocId(rs.getLong("to_loc_id"));
                d.setTravelTime(rs.getInt("travel_time"));
                d.setDist(rs.getDouble("dist"));
                d.setSource(rs.getString("source"));
                return d;
            }
        }
    }

    public void save(Distance d) throws SQLException {
        exec("INSERT INTO distance(from_loc_id, to_loc_id, travel_time, dist, source) VALUES (?,?,?,?,?) " +
                     "ON DUPLICATE KEY UPDATE travel_time=VALUES(travel_time), dist=VALUES(dist), source=VALUES(source)",
                d.getFromLocId(), d.getToLocId(), d.getTravelTime(), d.getDist(), d.getSource());
    }
}
