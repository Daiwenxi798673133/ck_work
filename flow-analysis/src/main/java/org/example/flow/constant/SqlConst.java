package org.example.flow.constant;

import org.example.flow.model.enums.Direction;
import org.example.flow.model.enums.Granularity;
import org.example.flow.model.enums.PortraitDimension;

/**
 * 分析 SQL 常量（与 flow-analysis/sql/analysis/*.sql 一一对应，口径全系统唯一）。
 *
 * <p>占位符约定：
 * <ul>
 *   <li>{@code ?} 为 ClickHouse JDBC 位置参数，由 service 按各常量注释的顺序绑定。</li>
 *   <li>相邻窗口间隔 step（HOUR/DAY）随 granularity 变化、不能用 {@code ?} 参数化，
 *       故每类 SQL 拆 HOUR/DAY 两个常量（仅 INTERVAL 步长不同）。</li>
 *   <li>portrait 的维度列是列名不能用 {@code ?}，保留一个 {@code %s}，
 *       由 service 用 {@link PortraitDimension#getValue()} 注入（白名单枚举，注入安全）。</li>
 * </ul>
 */
public final class SqlConst {

    private SqlConst() {
        throw new UnsupportedOperationException("utility class");
    }

    // ───────────────── OD 矩阵（od_matrix.sql）─────────────────
    // 参数：?1=granularity ?2=start(>=) ?3=end(<)；输出 from_region,to_region,window_start,flow
    private static final String OD_TEMPLATE = """
            SELECT prev.region_id   AS from_region,
                   cur.region_id    AS to_region,
                   cur.window_start AS window_start,
                   count()          AS flow
            FROM flow.dws_user_window_loc cur
            INNER JOIN flow.dws_user_window_loc prev
                    ON cur.imsi = prev.imsi
                   AND cur.granularity = prev.granularity
                   AND prev.window_start = cur.window_start - INTERVAL 1 {STEP}
            WHERE cur.granularity = ?
              AND cur.window_start >= ?
              AND cur.window_start <  ?
            GROUP BY from_region, to_region, window_start
            ORDER BY window_start, from_region, to_region""";

    public static final String OD_HOUR = OD_TEMPLATE.replace("{STEP}", "HOUR");
    public static final String OD_DAY  = OD_TEMPLATE.replace("{STEP}", "DAY");

    // ───────────────── trend（trend.sql，从 OD 派生）─────────────────
    // 参数：?1..?10=R（见 trend.sql 注释顺序）?11=granularity ?12=start(>=) ?13=end(<) ?14=R(cur) ?15=R(prev)
    // 内层 (cur.region_id=R OR prev.region_id=R)：限定 OD 行、不存在 region 自然返回空
    // 输出 window_start,population,inflow,outflow,retained
    private static final String TREND_TEMPLATE = """
            SELECT od.window_start AS window_start,
                   sumIf(od.flow, od.from_region = ? AND od.to_region  = ?)
                     + sumIf(od.flow, od.to_region = ? AND od.from_region != ?) AS population,
                   sumIf(od.flow, od.to_region   = ? AND od.from_region != ?)   AS inflow,
                   sumIf(od.flow, od.from_region = ? AND od.to_region   != ?)   AS outflow,
                   sumIf(od.flow, od.from_region = ? AND od.to_region    = ?)   AS retained
            FROM (
                SELECT prev.region_id   AS from_region,
                       cur.region_id    AS to_region,
                       cur.window_start AS window_start,
                       count()          AS flow
                FROM flow.dws_user_window_loc cur
                INNER JOIN flow.dws_user_window_loc prev
                        ON cur.imsi = prev.imsi
                       AND cur.granularity = prev.granularity
                       AND prev.window_start = cur.window_start - INTERVAL 1 {STEP}
                WHERE cur.granularity = ?
                  AND cur.window_start >= ?
                  AND cur.window_start <  ?
                  AND (cur.region_id = ? OR prev.region_id = ?)
                GROUP BY from_region, to_region, window_start
            ) od
            GROUP BY window_start
            ORDER BY window_start""";

    public static final String TREND_HOUR = TREND_TEMPLATE.replace("{STEP}", "HOUR");
    public static final String TREND_DAY  = TREND_TEMPLATE.replace("{STEP}", "DAY");

    // ───────────────── portrait（portrait.sql）─────────────────
    // %s=维度列(白名单枚举注入)；参数 ?1=granularity ?2=start(>=) ?3=end(<) ?4=R ?5=R
    // direction=in 取 cur.imsi（to=R AND from!=R）；direction=out 取 prev.imsi（from=R AND to!=R）
    // 输出 bucket,cnt（按流动次数计，bucket 之和==该方向 flow 总次数）
    private static final String PORTRAIT_IN_TEMPLATE = """
            SELECT p.%s AS bucket, count() AS cnt
            FROM (
                SELECT cur.imsi AS imsi
                FROM flow.dws_user_window_loc cur
                INNER JOIN flow.dws_user_window_loc prev
                        ON cur.imsi = prev.imsi
                       AND cur.granularity = prev.granularity
                       AND prev.window_start = cur.window_start - INTERVAL 1 {STEP}
                WHERE cur.granularity = ?
                  AND cur.window_start >= ?
                  AND cur.window_start <  ?
                  AND cur.region_id  = ?
                  AND prev.region_id != ?
            ) f
            INNER JOIN flow.dim_user_profile p ON f.imsi = p.imsi
            GROUP BY bucket
            ORDER BY bucket""";

    private static final String PORTRAIT_OUT_TEMPLATE = """
            SELECT p.%s AS bucket, count() AS cnt
            FROM (
                SELECT prev.imsi AS imsi
                FROM flow.dws_user_window_loc cur
                INNER JOIN flow.dws_user_window_loc prev
                        ON cur.imsi = prev.imsi
                       AND cur.granularity = prev.granularity
                       AND prev.window_start = cur.window_start - INTERVAL 1 {STEP}
                WHERE cur.granularity = ?
                  AND cur.window_start >= ?
                  AND cur.window_start <  ?
                  AND prev.region_id  = ?
                  AND cur.region_id  != ?
            ) f
            INNER JOIN flow.dim_user_profile p ON f.imsi = p.imsi
            GROUP BY bucket
            ORDER BY bucket""";

    public static final String PORTRAIT_IN_HOUR  = PORTRAIT_IN_TEMPLATE.replace("{STEP}", "HOUR");
    public static final String PORTRAIT_IN_DAY   = PORTRAIT_IN_TEMPLATE.replace("{STEP}", "DAY");
    public static final String PORTRAIT_OUT_HOUR = PORTRAIT_OUT_TEMPLATE.replace("{STEP}", "HOUR");
    public static final String PORTRAIT_OUT_DAY  = PORTRAIT_OUT_TEMPLATE.replace("{STEP}", "DAY");

    // ───────────────── regions（regions.sql，无参数）─────────────────
    public static final String REGIONS = """
            SELECT region_id,
                   region_name,
                   lac,
                   cell_count
            FROM flow.dim_region
            ORDER BY region_id""";

    // ───────────────── 按 granularity / direction 选常量的便捷方法 ─────────────────

    public static String od(Granularity granularity) {
        return granularity == Granularity.DAY ? OD_DAY : OD_HOUR;
    }

    public static String trend(Granularity granularity) {
        return granularity == Granularity.DAY ? TREND_DAY : TREND_HOUR;
    }

    /** 返回已注入维度列的 portrait SQL。dimension 列名来自枚举白名单，注入安全。 */
    public static String portrait(Direction direction, Granularity granularity, PortraitDimension dimension) {
        String template;
        if (direction == Direction.OUT) {
            template = granularity == Granularity.DAY ? PORTRAIT_OUT_DAY : PORTRAIT_OUT_HOUR;
        } else {
            template = granularity == Granularity.DAY ? PORTRAIT_IN_DAY : PORTRAIT_IN_HOUR;
        }
        return String.format(template, dimension.getValue());
    }
}
