package com.logistics.scheduler.dao;

import com.logistics.scheduler.model.Task;

import javax.sql.DataSource;
import java.sql.*;

public class TaskDao extends BaseDao {

    public TaskDao(DataSource ds) { super(ds); }

    public Task insert(String status) throws SQLException {
        long id = insertAndReturnKey(
                "INSERT INTO task(status) VALUES (?)", status);
        Task t = new Task(status);
        t.setId(id);
        return t;
    }

    public Task findById(long id) throws SQLException {
        try (Connection c = open();
             PreparedStatement ps = c.prepareStatement("SELECT task_id, status, created_at FROM task WHERE task_id=?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Task t = new Task(rs.getString("status"));
                t.setId(rs.getLong("task_id"));
                t.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                return t;
            }
        }
    }
}
