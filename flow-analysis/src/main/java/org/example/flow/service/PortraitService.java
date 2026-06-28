package org.example.flow.service;

import org.example.flow.constant.SqlConst;
import org.example.flow.model.PortraitBucket;
import org.example.flow.model.PortraitResult;
import org.example.flow.model.enums.Direction;
import org.example.flow.model.enums.Granularity;
import org.example.flow.model.enums.PortraitDimension;
import org.example.flow.repository.FlowRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 流入/流出人群画像分布。
 *
 * <p>口径与 trend 同源（用户集合来自 OD 自连接），按流动次数计，
 * 各 bucket 之和严格 == 该方向 flow 总次数。维度列由
 * {@link SqlConst#portrait} 以白名单枚举 {@code %s} 注入，service 仅绑定 5 个位置参数。
 */
@Service
public class PortraitService {

    private static final DateTimeFormatter SQL_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final FlowRepository flowRepository;

    public PortraitService(FlowRepository flowRepository) {
        this.flowRepository = flowRepository;
    }

    /**
     * 查询某区域某方向的人群画像分布。
     *
     * @param regionId    目标 region_id（R）
     * @param granularity hour | day
     * @param start       ISO 起点（{@code >=}），yyyy-MM-dd'T'HH:mm:ss
     * @param end         ISO 终点（{@code <}），yyyy-MM-dd'T'HH:mm:ss
     * @param direction   in | out
     * @param dimension   gender | age_group | is_resident
     * @return total + 各 bucket（含 ratio；total=0 时 ratio=0）
     */
    public PortraitResult portrait(String regionId, String granularity, String start,
                                   String end, String direction, String dimension) {
        Granularity g         = parseGranularity(granularity);
        Direction dir         = parseDirection(direction);
        PortraitDimension dim = parseDimension(dimension);
        String startSql       = toSqlTime(start, "start");
        String endSql         = toSqlTime(end, "end");

        // 维度列已由 SqlConst.portrait 用白名单枚举注入（注入安全）；service 不处理列名
        String sql = SqlConst.portrait(dir, g, dim);

        // 位置参数顺序：?1=granularity ?2=start(>=) ?3=end(<) ?4=R ?5=R
        List<PortraitBucket> buckets = flowRepository.query(
                sql,
                (rs, rowNum) -> new PortraitBucket(rs.getString("bucket"), rs.getLong("cnt"), 0d),
                g.getValue(), startSql, endSql, regionId, regionId);

        long total = 0L;
        for (PortraitBucket b : buckets) {
            total += b.getCount();
        }
        for (PortraitBucket b : buckets) {
            // total=0 时 ratio=0，防除零 / NaN
            b.setRatio(total == 0L ? 0d : (double) b.getCount() / total);
        }

        return new PortraitResult(
                dir.name().toLowerCase(),
                dim.getValue(),
                total,
                buckets);
    }

    private Granularity parseGranularity(String s) {
        if (s != null) {
            for (Granularity g : Granularity.values()) {
                if (g.getValue().equalsIgnoreCase(s)) {
                    return g;
                }
            }
        }
        throw new IllegalArgumentException("Invalid granularity: " + s + " (expected: hour|day)");
    }

    private Direction parseDirection(String s) {
        if (s != null) {
            for (Direction dir : Direction.values()) {
                if (dir.name().equalsIgnoreCase(s)) {
                    return dir;
                }
            }
        }
        throw new IllegalArgumentException("Invalid direction: " + s + " (expected: in|out)");
    }

    private PortraitDimension parseDimension(String s) {
        if (s != null) {
            for (PortraitDimension d : PortraitDimension.values()) {
                if (d.getValue().equalsIgnoreCase(s)) {
                    return d;
                }
            }
        }
        throw new IllegalArgumentException(
                "Invalid dimension: " + s + " (expected: gender|age_group|is_resident)");
    }

    /** ISO {@code yyyy-MM-dd'T'HH:mm:ss} → ClickHouse 字面量 {@code yyyy-MM-dd HH:mm:ss}。 */
    private String toSqlTime(String iso, String field) {
        if (iso == null) {
            throw new IllegalArgumentException("Missing " + field + " datetime");
        }
        try {
            return LocalDateTime.parse(iso).format(SQL_FMT);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Invalid " + field + " datetime: " + iso
                            + " (expected ISO yyyy-MM-dd'T'HH:mm:ss)", e);
        }
    }
}
