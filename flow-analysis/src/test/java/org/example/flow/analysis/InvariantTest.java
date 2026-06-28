package org.example.flow.analysis;

import org.example.flow.constant.SqlConst;
import org.example.flow.model.enums.Direction;
import org.example.flow.model.enums.Granularity;
import org.example.flow.model.enums.PortraitDimension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 守恒不变量测试：对<b>生产库 flow.*（只读，不写不删）</b>的全量数据，用真实 SqlConst SQL
 * 断言分析口径自洽的恒等式。固定抽样窗口 W=2026-06-01 12:00:00（prev=11:00），区间 [12:00, 13:00)。
 *
 * 不变量：
 *   1. trend 每 region：population == retained + inflow。
 *   2. gender 画像桶和 == 该方向 OD flow 总数（IN：Σ flow where to=R,from≠R）；同时 == trend.inflow。
 *   3. 每 (imsi, granularity, window_start) 的 region 唯一（loc=argMax 派生应单值）：
 *      max(uniqExact(region_id)) == 1。
 */
@SpringBootTest
@DisplayName("守恒不变量（生产库 flow.* 只读）")
class InvariantTest {

    private static final String HOUR = Granularity.HOUR.getValue();
    private static final String WINDOW_START = "2026-06-01 12:00:00";
    private static final String WINDOW_END = "2026-06-01 13:00:00";

    private static final List<String> REGIONS = List.of("310000_6254", "310000_6234", "310000_6200");

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("不变量1：trend 每 region population == retained + inflow")
    void invariant_populationEqualsRetainedPlusInflow() {
        for (String region : REGIONS) {
            long[] t = trend(region); // [population, inflow, outflow, retained]
            assertEquals(t[3] + t[1], t[0],
                    "region " + region + " @" + WINDOW_START
                            + " population 应等于 retained+inflow");
        }
    }

    @Test
    @DisplayName("不变量2：gender 画像(IN)桶和 == 该方向 OD inflow 总数 == trend.inflow")
    void invariant_portraitBucketSumEqualsDirectionalFlow() {
        for (String region : REGIONS) {
            long bucketSum = portraitInGenderSum(region);
            long odInflow = odInflow(region);
            long trendInflow = trend(region)[1];

            assertEquals(odInflow, bucketSum,
                    "region " + region + " IN/gender 画像桶之和应 == OD inflow 总数（按流动次数计）");
            assertEquals(trendInflow, bucketSum,
                    "region " + region + " IN/gender 画像桶之和应 == trend.inflow");
        }
    }

    @Test
    @DisplayName("不变量3：每 (imsi,granularity,window) 的 region 唯一（max uniqExact == 1）")
    void invariant_locationUnique() {
        Long maxDistinct = jdbc.queryForObject("""
                SELECT max(c) FROM (
                    SELECT imsi, granularity, window_start, uniqExact(region_id) AS c
                    FROM flow.dws_user_window_loc
                    GROUP BY imsi, granularity, window_start
                )""", Long.class);
        assertTrue(maxDistinct != null && maxDistinct == 1L,
                "每 (imsi,granularity,window_start) 应只有 1 个 region（实际最大 distinct=" + maxDistinct + "）");
    }

    // ───────────────── helpers：真实 SqlConst SQL，直接查 flow.*（只读）─────────────────

    /** trend(HOUR) 单窗口结果 → [population, inflow, outflow, retained]。 */
    private long[] trend(String regionId) {
        Object[] args = {
                regionId, regionId, regionId, regionId, regionId,
                regionId, regionId, regionId, regionId, regionId,
                HOUR, WINDOW_START, WINDOW_END, regionId, regionId
        };
        List<long[]> rows = jdbc.query(SqlConst.trend(Granularity.HOUR),
                (rs, n) -> new long[]{
                        rs.getLong("population"),
                        rs.getLong("inflow"),
                        rs.getLong("outflow"),
                        rs.getLong("retained")},
                args);
        return rows.isEmpty() ? new long[]{0, 0, 0, 0} : rows.get(0);
    }

    /** OD(HOUR) 中流入该 region 的总次数：Σ flow where to_region=R AND from_region≠R。 */
    private long odInflow(String regionId) {
        List<long[]> rows = jdbc.query(SqlConst.od(Granularity.HOUR),
                (rs, n) -> {
                    String from = rs.getString("from_region");
                    String to = rs.getString("to_region");
                    long flow = rs.getLong("flow");
                    return new long[]{(to.equals(regionId) && !from.equals(regionId)) ? flow : 0L};
                },
                HOUR, WINDOW_START, WINDOW_END);
        return rows.stream().mapToLong(r -> r[0]).sum();
    }

    /** portrait(IN, gender, R) 各桶 cnt 之和。 */
    private long portraitInGenderSum(String regionId) {
        List<Long> counts = jdbc.query(
                SqlConst.portrait(Direction.IN, Granularity.HOUR, PortraitDimension.GENDER),
                (rs, n) -> rs.getLong("cnt"),
                HOUR, WINDOW_START, WINDOW_END, regionId, regionId);
        return counts.stream().mapToLong(Long::longValue).sum();
    }
}
