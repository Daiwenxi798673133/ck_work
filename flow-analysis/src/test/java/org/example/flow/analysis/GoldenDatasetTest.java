package org.example.flow.analysis;

import org.example.flow.constant.SqlConst;
import org.example.flow.model.enums.Direction;
import org.example.flow.model.enums.Granularity;
import org.example.flow.model.enums.PortraitDimension;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 黄金小数据集测试：在隔离库 {@code flow_test} 内灌入 5 个用户 × 2 区域 × 3 个相邻小时窗口的
 * 手工 fixture，运行 <b>实际 SqlConst 的 SQL</b>（仅把库名 "flow." 替换为 "flow_test."），
 * 断言 OD / inflow / outflow / retained / population / portrait 等于<b>手算期望值</b>。
 *
 * <h2>fixture 轨迹表（granularity = hour；RA='A'、RB='B'；窗口相邻 1 小时）</h2>
 * <pre>
 *   W0 = 2026-06-01 00:00:00
 *   W1 = 2026-06-01 01:00:00
 *   W2 = 2026-06-01 02:00:00
 *
 *   | user | W0 | W1 | W2 | gender | 说明                                                            |
 *   |------|----|----|----|--------|-----------------------------------------------------------------|
 *   | u1   | A  | A  | A  | 男     | 全程留守 A：W1 retained A、W2 retained A                          |
 *   | u2   | B  | A  | A  | 女     | W1 跨区流入 A(B→A)、W2 留守 A                                      |
 *   | u3   | B  | A  | B  | 男     | W1 跨区流入 A(B→A)、W2 跨区流出 A(A→B)                            |
 *   | u4   | A  | B  | B  | 女     | W1 跨区流出 A(A→B)、W2 留守 B                                      |
 *   | u5   | A  | —  | A  | 男     | W1 缺席：离线再现(W0=A,W2=A)，transition-only(相邻窗口 INNER JOIN) |
 *   |      |    |    |    |        | 应使其不计入任何流动（W0→W1、W1→W2 均无配对）                       |
 * </pre>
 *
 * <h2>口径回顾</h2>
 * OD：相邻窗口 INNER JOIN，{@code prev.window_start = cur.window_start - INTERVAL 1 HOUR}，
 * 输出 window_start = cur.window_start（即 W1 行来自 W0→W1 转移，W2 行来自 W1→W2 转移）；
 * 因 INNER JOIN 天然 transition-only：用户须在相邻两窗口都出现才计入。
 *
 * <h2>手算期望（按上表逐窗口推导）</h2>
 * <pre>
 *   OD @W1 (W0→W1，present in both W0&W1 = u1,u2,u3,u4；u5 缺席被排除)：
 *     (A,A)=1[u1]  (B,A)=2[u2,u3]  (A,B)=1[u4]            合计 = 4
 *   OD @W2 (W1→W2，present in both W1&W2 = u1,u2,u3,u4；u5 缺席被排除)：
 *     (A,A)=2[u1,u2]  (A,B)=1[u3]  (B,B)=1[u4]            合计 = 4
 *   ⇒ 若错误地把 u5 的"离线再现"(W0=A 当成 W1=A) 计入，则 (A,A)@W2 会变 3、合计变 5。
 *
 *   trend(A)：inflow=Σ(to=A,from≠A) outflow=Σ(from=A,to≠A) retained=Σ(from=A,to=A) population=retained+inflow
 *     W1: retained=1  inflow=2  outflow=1  population=3
 *     W2: retained=2  inflow=0  outflow=1  population=2
 *   trend(B)：
 *     W1: retained=0  inflow=1  outflow=2  population=1
 *     W2: retained=1  inflow=1  outflow=0  population=2
 *
 *   portrait(IN, gender, R=A) 覆盖 [W1, W2+1h)：流入 A 的用户(cur=A,prev≠A) = u2(女,@W1), u3(男,@W1)
 *     （W2 inflow(A)=0，无新增）⇒ 桶 {男:1, 女:1}，桶和=2 == inflow(A,W1)+inflow(A,W2)=2+0=2
 *     （证明 portrait 按"流动次数计"，桶和 == 该方向 flow 总数）
 * </pre>
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("黄金小数据集（flow_test 隔离库，手算对照）")
class GoldenDatasetTest {

    private static final String RA = "A";
    private static final String RB = "B";
    private static final String HOUR = Granularity.HOUR.getValue();

    private static final String W0 = "2026-06-01 00:00:00";
    private static final String W1 = "2026-06-01 01:00:00";
    private static final String W2 = "2026-06-01 02:00:00";
    // 查询区间 [W1, END)：cur 窗口覆盖 W1、W2（各自 prev 为 W0、W1）
    private static final String START = W1;
    private static final String END = "2026-06-01 03:00:00";

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeAll
    void setUp() {
        jdbc.execute("CREATE DATABASE IF NOT EXISTS flow_test");

        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS flow_test.dws_user_window_loc
                (
                    imsi         String,
                    granularity  Enum8('hour' = 1, 'day' = 2),
                    window_start DateTime('Asia/Shanghai'),
                    region_id    String
                )
                ENGINE = MergeTree
                ORDER BY (granularity, window_start, imsi)""");

        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS flow_test.dim_user_profile
                (
                    imsi           String,
                    gender         Enum8('男' = 1, '女' = 2),
                    age            UInt8,
                    age_group      String,
                    is_resident    UInt8,
                    home_region_id String
                )
                ENGINE = MergeTree
                ORDER BY imsi""");

        jdbc.execute("TRUNCATE TABLE flow_test.dws_user_window_loc");
        jdbc.execute("TRUNCATE TABLE flow_test.dim_user_profile");

        // dws fixture：14 行（u5 在 W1 缺席，仅 2 行）。每行 ('imsi','hour','window','region')
        jdbc.update("""
                INSERT INTO flow_test.dws_user_window_loc (imsi, granularity, window_start, region_id) VALUES
                ('u1','hour','2026-06-01 00:00:00','A'),
                ('u1','hour','2026-06-01 01:00:00','A'),
                ('u1','hour','2026-06-01 02:00:00','A'),
                ('u2','hour','2026-06-01 00:00:00','B'),
                ('u2','hour','2026-06-01 01:00:00','A'),
                ('u2','hour','2026-06-01 02:00:00','A'),
                ('u3','hour','2026-06-01 00:00:00','B'),
                ('u3','hour','2026-06-01 01:00:00','A'),
                ('u3','hour','2026-06-01 02:00:00','B'),
                ('u4','hour','2026-06-01 00:00:00','A'),
                ('u4','hour','2026-06-01 01:00:00','B'),
                ('u4','hour','2026-06-01 02:00:00','B'),
                ('u5','hour','2026-06-01 00:00:00','A'),
                ('u5','hour','2026-06-01 02:00:00','A')""");

        // profile fixture：5 行（gender 用于 portrait 断言）
        jdbc.update("""
                INSERT INTO flow_test.dim_user_profile
                    (imsi, gender, age, age_group, is_resident, home_region_id) VALUES
                ('u1','男',30,'26-35',1,'A'),
                ('u2','女',22,'18-25',1,'B'),
                ('u3','男',40,'36-45',0,'B'),
                ('u4','女',50,'46-60',1,'A'),
                ('u5','男',16,'<18',0,'A')""");
    }

    @AfterAll
    void tearDown() {
        jdbc.execute("DROP DATABASE IF EXISTS flow_test");
    }

    // ───────────────── OD：逐 (from,to) 流量 + transition-only 排除离线再现 ─────────────────

    @Test
    @DisplayName("OD：W1/W2 各 (from,to) flow 与窗口合计 == 手算值")
    void od_perPairAndWindowTotals() {
        Map<String, Long> od = queryOd(); // key = "window|from|to"

        // W1 (W0→W1)
        assertEquals(1L, od.getOrDefault(key(W1, "A", "A"), 0L), "OD @W1 (A→A) 应为 1（u1 留守 A）");
        assertEquals(2L, od.getOrDefault(key(W1, "B", "A"), 0L), "OD @W1 (B→A) 应为 2（u2,u3 流入 A）");
        assertEquals(1L, od.getOrDefault(key(W1, "A", "B"), 0L), "OD @W1 (A→B) 应为 1（u4 流出 A）");
        assertEquals(4L, windowTotal(od, W1), "OD @W1 合计应为 4（u1..u4 各 1 次；u5 缺席被 transition-only 排除）");

        // W2 (W1→W2)
        assertEquals(1L, od.getOrDefault(key(W2, "A", "B"), 0L), "OD @W2 (A→B) 应为 1（u3 流出 A）");
        assertEquals(1L, od.getOrDefault(key(W2, "B", "B"), 0L), "OD @W2 (B→B) 应为 1（u4 留守 B）");
        assertEquals(4L, windowTotal(od, W2), "OD @W2 合计应为 4");
    }

    @Test
    @DisplayName("transition-only：离线再现用户 u5 不计入任何流动（OD (A→A)@W2 == 2 而非 3）")
    void od_offlineReappearanceExcluded() {
        Map<String, Long> od = queryOd();
        // u5 在 W1 缺席、W2 又出现在 A。若用"最后已知位置"误把 W0(A)→W2(A) 当转移，(A→A)@W2 会变 3。
        // transition-only(相邻窗口 INNER JOIN) 要求 u5 在 W1 也出现，故 u5 被排除，(A→A)@W2 == 2（仅 u1,u2）。
        assertEquals(2L, od.getOrDefault(key(W2, "A", "A"), 0L),
                "OD (A→A)@W2 应为 2（仅 u1,u2 真实留守）；u5 离线再现必须被排除，不得为 3");
    }

    // ───────────────── trend：inflow / outflow / retained / population ─────────────────

    @Test
    @DisplayName("trend(A)：W1/W2 的 population/inflow/outflow/retained == 手算值")
    void trend_regionA() {
        Map<String, long[]> t = queryTrend(RA); // key=window → [population, inflow, outflow, retained]

        long[] w1 = t.get(W1);
        assertEquals(3L, w1[0], "trend(A)@W1 population 应为 3（retained1+inflow2）");
        assertEquals(2L, w1[1], "trend(A)@W1 inflow 应为 2（u2,u3 由 B 流入）");
        assertEquals(1L, w1[2], "trend(A)@W1 outflow 应为 1（u4 流向 B）");
        assertEquals(1L, w1[3], "trend(A)@W1 retained 应为 1（u1 留守）");

        long[] w2 = t.get(W2);
        assertEquals(2L, w2[0], "trend(A)@W2 population 应为 2（retained2+inflow0）");
        assertEquals(0L, w2[1], "trend(A)@W2 inflow 应为 0");
        assertEquals(1L, w2[2], "trend(A)@W2 outflow 应为 1（u3 流向 B）");
        assertEquals(2L, w2[3], "trend(A)@W2 retained 应为 2（u1,u2 留守）");
    }

    @Test
    @DisplayName("trend(B)：W1/W2 的 population/inflow/outflow/retained == 手算值")
    void trend_regionB() {
        Map<String, long[]> t = queryTrend(RB);

        long[] w1 = t.get(W1);
        assertEquals(1L, w1[0], "trend(B)@W1 population 应为 1（retained0+inflow1）");
        assertEquals(1L, w1[1], "trend(B)@W1 inflow 应为 1（u4 由 A 流入）");
        assertEquals(2L, w1[2], "trend(B)@W1 outflow 应为 2（u2,u3 流向 A）");
        assertEquals(0L, w1[3], "trend(B)@W1 retained 应为 0");

        long[] w2 = t.get(W2);
        assertEquals(2L, w2[0], "trend(B)@W2 population 应为 2（retained1+inflow1）");
        assertEquals(1L, w2[1], "trend(B)@W2 inflow 应为 1（u3 由 A 流入）");
        assertEquals(0L, w2[2], "trend(B)@W2 outflow 应为 0");
        assertEquals(1L, w2[3], "trend(B)@W2 retained 应为 1（u4 留守）");
    }

    @Test
    @DisplayName("守恒：trend 每窗口 population == retained + inflow")
    void trend_populationInvariant() {
        for (String region : List.of(RA, RB)) {
            Map<String, long[]> t = queryTrend(region);
            for (Map.Entry<String, long[]> e : t.entrySet()) {
                long[] v = e.getValue(); // [population, inflow, outflow, retained]
                assertEquals(v[3] + v[1], v[0],
                        "trend(" + region + ")@" + e.getKey() + " population 应等于 retained+inflow");
            }
        }
    }

    // ───────────────── portrait：gender 桶和 == 该方向 flow 总数（按流动次数计）─────────────────

    @Test
    @DisplayName("portrait(IN, gender, A)：男=1 女=1，桶和=2 == inflow(A) 总数")
    void portrait_inGenderRegionA() {
        Map<String, Long> buckets = queryPortraitInGender(RA);

        assertEquals(1L, buckets.getOrDefault("男", 0L), "portrait(IN,gender,A) 男桶应为 1（u3 男，@W1 流入）");
        assertEquals(1L, buckets.getOrDefault("女", 0L), "portrait(IN,gender,A) 女桶应为 1（u2 女，@W1 流入）");

        long sum = buckets.values().stream().mapToLong(Long::longValue).sum();
        assertEquals(2L, sum, "portrait 桶之和应 == 区间内 inflow(A) 总数 2（按流动次数计）");
    }

    // ───────────────── helpers：用真实 SqlConst SQL，仅替换库名 ─────────────────

    /** 运行真实 OD_HOUR SQL（flow.→flow_test.），返回 key="window|from|to" → flow。 */
    private Map<String, Long> queryOd() {
        String sql = SqlConst.od(Granularity.HOUR).replace("flow.", "flow_test.");
        // 参数顺序同 OdService：?1=granularity ?2=start(>=) ?3=end(<)
        List<String[]> rows = jdbc.query(sql,
                (rs, n) -> new String[]{
                        rs.getString("from_region"),
                        rs.getString("to_region"),
                        rs.getString("window_start").replace('T', ' '),
                        String.valueOf(rs.getLong("flow"))},
                HOUR, START, END);
        Map<String, Long> map = new HashMap<>();
        for (String[] r : rows) {
            map.put(key(r[2], r[0], r[1]), Long.parseLong(r[3]));
        }
        return map;
    }

    /** 运行真实 TREND_HOUR SQL（flow.→flow_test.），返回 window → [population, inflow, outflow, retained]。 */
    private Map<String, long[]> queryTrend(String regionId) {
        String sql = SqlConst.trend(Granularity.HOUR).replace("flow.", "flow_test.");
        // 参数顺序同 TrendService：?1..?10=regionId、?11=granularity、?12=start、?13=end、?14=regionId、?15=regionId
        Object[] args = {
                regionId, regionId, regionId, regionId, regionId,
                regionId, regionId, regionId, regionId, regionId,
                HOUR, START, END, regionId, regionId
        };
        List<Object[]> rows = jdbc.query(sql,
                (rs, n) -> new Object[]{
                        rs.getString("window_start").replace('T', ' '),
                        rs.getLong("population"),
                        rs.getLong("inflow"),
                        rs.getLong("outflow"),
                        rs.getLong("retained")},
                args);
        Map<String, long[]> map = new HashMap<>();
        for (Object[] r : rows) {
            map.put((String) r[0], new long[]{(Long) r[1], (Long) r[2], (Long) r[3], (Long) r[4]});
        }
        return map;
    }

    /** 运行真实 PORTRAIT_IN_HOUR(gender) SQL（flow.→flow_test.），返回 bucket → cnt。 */
    private Map<String, Long> queryPortraitInGender(String regionId) {
        String sql = SqlConst.portrait(Direction.IN, Granularity.HOUR, PortraitDimension.GENDER)
                .replace("flow.", "flow_test.");
        // 参数顺序同 PortraitService：?1=granularity ?2=start ?3=end ?4=R ?5=R
        List<Object[]> rows = jdbc.query(sql,
                (rs, n) -> new Object[]{rs.getString("bucket"), rs.getLong("cnt")},
                HOUR, START, END, regionId, regionId);
        Map<String, Long> map = new HashMap<>();
        for (Object[] r : rows) {
            map.put((String) r[0], (Long) r[1]);
        }
        return map;
    }

    private static String key(String window, String from, String to) {
        return window + "|" + from + "|" + to;
    }

    private static long windowTotal(Map<String, Long> od, String window) {
        return od.entrySet().stream()
                .filter(e -> e.getKey().startsWith(window + "|"))
                .mapToLong(Map.Entry::getValue)
                .sum();
    }
}
