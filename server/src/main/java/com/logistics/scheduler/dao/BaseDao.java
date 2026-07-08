package com.logistics.scheduler.dao;

import javax.sql.DataSource;
import java.sql.*;

public abstract class BaseDao {
    protected final DataSource ds;
    protected final boolean standalone;

    protected BaseDao(DataSource ds) { this(ds, false); }
    protected BaseDao(DataSource ds, boolean standalone) { this.ds = ds; this.standalone = standalone; }

    protected Connection open() throws SQLException { return ds.getConnection(); }

    protected long insertAndReturnKey(String sql, Object... params) throws SQLException {
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getLong(1);
            }
            throw new SQLException("无法获取自增主键");
        }
    }

    protected int exec(String sql, Object... params) throws SQLException {
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            return ps.executeUpdate();
        }
    }

    public void resetAutoIncrement(String table, String column) throws SQLException {
        if (standalone) {
            exec("ALTER TABLE " + table + " ALTER COLUMN " + column + " RESTART WITH 1");
        } else {
            exec("ALTER TABLE " + table + " AUTO_INCREMENT = 1");
        }
    }
}
