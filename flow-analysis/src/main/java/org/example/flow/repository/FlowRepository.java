package org.example.flow.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class FlowRepository {

    private final JdbcTemplate jdbcTemplate;

    public FlowRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public <T> List<T> query(String sql, RowMapper<T> mapper, Object... args) {
        return jdbcTemplate.query(sql, mapper, args);
    }

    public int ping() {
        Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        return result != null ? result : 0;
    }
}
