package com.logistics.scheduler.dao;

import com.logistics.scheduler.model.UnassignedOrder;

import javax.sql.DataSource;
import java.sql.*;

public class UnassignedOrderDao extends BaseDao {

    public UnassignedOrderDao(DataSource ds) { super(ds); }

    public void save(UnassignedOrder u) throws SQLException {
        exec("INSERT INTO unassigned_order(order_id, reason, snapshot_at) VALUES (?,?,?) " +
                     "ON DUPLICATE KEY UPDATE reason=VALUES(reason), snapshot_at=VALUES(snapshot_at)",
                u.getOrderId(), u.getReason(), Timestamp.valueOf(u.getSnapshotAt()));
    }
}
