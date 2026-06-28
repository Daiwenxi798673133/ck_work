package org.example.flow.service;

import org.example.flow.constant.RegionDef;
import org.example.flow.constant.SqlConst;
import org.example.flow.model.ODFlow;
import org.example.flow.model.enums.Granularity;
import org.example.flow.repository.FlowRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 区域 OD 矩阵查询（口径见 sql/analysis/od_matrix.sql）。
 *
 * <p>od_matrix.sql 按 (from, to, window) 分组，同一 (from,to) 在时段内会有多行。聚合方式二选一：
 * 此处取 <b>Java 端 reduce</b>（按 from/to 累加 flow），不再包一层 SQL——region 数极小（N=3，
 * 矩阵 ≤ 9 项），Java 聚合开销可忽略且复用既有 OD 常量。含对角线（from==to 即 retained），
 * region 中文名由 {@link RegionDef#regionName(String)} 补。
 */
@Service
public class OdService {

    private static final DateTimeFormatter SQL_TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final FlowRepository flowRepository;

    public OdService(FlowRepository flowRepository) {
        this.flowRepository = flowRepository;
    }

    /**
     * 查询某粒度、时段 {@code [startIso, endIso)} 内的区域 OD 总流量矩阵（含对角线）。
     * 时间入参 ISO {@code yyyy-MM-dd'T'HH:mm:ss}；start 含（>=）、end 不含（<）。
     */
    public List<ODFlow> odMatrix(Granularity granularity, String startIso, String endIso) {
        String sql      = SqlConst.od(granularity);
        String startSql = toSqlTimestamp(startIso);
        String endSql   = toSqlTimestamp(endIso);

        // od_matrix.sql 3 个位置参数顺序严格：?1=granularity ?2=start(>=) ?3=end(<)。
        List<ODFlow> perWindowRows = flowRepository.query(
                sql,
                (rs, rowNum) -> {
                    String fromId = rs.getString("from_region");
                    String toId   = rs.getString("to_region");
                    return new ODFlow(
                            fromId, RegionDef.regionName(fromId),
                            toId,   RegionDef.regionName(toId),
                            rs.getLong("flow"));
                },
                granularity.getValue(), startSql, endSql);

        // 时段聚合：同一 (from,to) 跨多个 window 求和；首行作累加器，后续同 key 行只加 flow。
        // key 用 '\u0001' 连接 from/to——控制字符不出现在 region_id 中，避免分隔符冲突。
        Map<String, ODFlow> matrix = new LinkedHashMap<>();
        for (ODFlow row : perWindowRows) {
            String key = row.getFromRegionId() + '\u0001' + row.getToRegionId();
            ODFlow acc = matrix.get(key);
            if (acc == null) {
                matrix.put(key, row);
            } else {
                acc.setFlow(acc.getFlow() + row.getFlow());
            }
        }
        return new ArrayList<>(matrix.values());
    }

    private static String toSqlTimestamp(String iso) {
        return LocalDateTime.parse(iso).format(SQL_TS_FMT);
    }
}
