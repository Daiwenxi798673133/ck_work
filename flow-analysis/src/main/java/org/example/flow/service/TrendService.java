package org.example.flow.service;

import org.example.flow.constant.SqlConst;
import org.example.flow.model.FlowTrendPoint;
import org.example.flow.model.enums.Granularity;
import org.example.flow.repository.FlowRepository;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class TrendService {

    private static final DateTimeFormatter SQL_DATETIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final RowMapper<FlowTrendPoint> TREND_MAPPER = (rs, rowNum) -> {
        // ClickHouse DateTime via JDBC getString -> "yyyy-MM-dd HH:mm:ss"; tolerate ISO 'T'.
        String rawWindow = rs.getString("window_start").replace('T', ' ');
        LocalDateTime windowStart = LocalDateTime.parse(rawWindow, SQL_DATETIME_FMT);
        return new FlowTrendPoint(
                windowStart,
                rs.getLong("population"),
                rs.getLong("inflow"),
                rs.getLong("outflow"),
                rs.getLong("retained"));
    };

    private final FlowRepository flowRepository;

    public TrendService(FlowRepository flowRepository) {
        this.flowRepository = flowRepository;
    }

    public List<FlowTrendPoint> trend(String regionId, String granularity,
                                      LocalDateTime start, LocalDateTime end) {
        Granularity g = parseGranularity(granularity);
        String startStr = start.format(SQL_DATETIME_FMT);
        String endStr = end.format(SQL_DATETIME_FMT);

        // Positional binding MUST match trend.sql / SqlConst.TREND_TEMPLATE placeholder order:
        Object[] args = {
                regionId, regionId, regionId, regionId, regionId, // ?1..?5  = regionId
                regionId, regionId, regionId, regionId, regionId, // ?6..?10 = regionId
                g.getValue(),  // ?11 granularity "hour"/"day"
                startStr,      // ?12 start (>=)
                endStr,        // ?13 end (<)
                regionId,      // ?14 cur.region_id
                regionId       // ?15 prev.region_id
        };
        return flowRepository.query(SqlConst.trend(g), TREND_MAPPER, args);
    }

    private Granularity parseGranularity(String raw) {
        if (raw != null) {
            for (Granularity g : Granularity.values()) {
                if (g.getValue().equalsIgnoreCase(raw)) {
                    return g;
                }
            }
        }
        throw new IllegalArgumentException(
                "Invalid granularity: " + raw + " (expected: hour|day)");
    }
}
