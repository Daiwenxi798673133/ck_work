-- portrait.sql : 流入/流出人群画像分布（用户集合来自 OD，与 trend 同源）
-- =====================================================================
-- 口径（全系统唯一，禁止第二套）：
--   * 用户集合必须来自 OD 自连接的流入/流出行，不得另起「出现过」口径：
--       direction=in  → to_region=R AND from_region!=R，取 cur.imsi（流入 R 的人）
--       direction=out → from_region=R AND to_region!=R，取 prev.imsi（流出 R 的人）
--   * 计数口径 = 「按流动次数计」(OD 行计数，不去重)，使各 bucket 之和 == 该方向 flow 总次数，
--     可与 trend 的 inflow/outflow 严格对账。（若改按去重用户则用 uniqExact(imsi)，本任务统一不去重。）
--   * loc=argMax末slice / transition-only / step 随 granularity 见 od_matrix.sql；本文件 INTERVAL 1 HOUR。
--     SqlConst 提供 PORTRAIT_IN_HOUR / PORTRAIT_IN_DAY / PORTRAIT_OUT_HOUR / PORTRAIT_OUT_DAY。
--   * 维度列（gender / age_group / is_resident）是列名不能用 ? 参数化，
--     由 service 用 %s 注入（来源 PortraitDimension 枚举，白名单安全，非用户原串）。
--   * ratio 由 service 计算：count / Σcount（total=0 时 ratio=0）。
-- 参数（位置参数 ?，按出现顺序）：?1=granularity ?2=start(>=) ?3=end(<) ?4=R ?5=R(另一端 != / =)
-- 输出列（对齐 model/PortraitBucket 的 bucket/count）：bucket, cnt
-- =====================================================================

-- ── direction=in（流入 R）──────────────────────────────────────────
SELECT p.%s AS bucket, count() AS cnt
FROM (
    SELECT cur.imsi AS imsi
    FROM flow.dws_user_window_loc cur
    INNER JOIN flow.dws_user_window_loc prev
            ON cur.imsi = prev.imsi
           AND cur.granularity = prev.granularity
           AND prev.window_start = cur.window_start - INTERVAL 1 HOUR
    WHERE cur.granularity = ?
      AND cur.window_start >= ?
      AND cur.window_start <  ?
      AND cur.region_id  = ?
      AND prev.region_id != ?
) f
INNER JOIN flow.dim_user_profile p ON f.imsi = p.imsi
GROUP BY bucket
ORDER BY bucket;

-- ── direction=out（流出 R）─────────────────────────────────────────
SELECT p.%s AS bucket, count() AS cnt
FROM (
    SELECT prev.imsi AS imsi
    FROM flow.dws_user_window_loc cur
    INNER JOIN flow.dws_user_window_loc prev
            ON cur.imsi = prev.imsi
           AND cur.granularity = prev.granularity
           AND prev.window_start = cur.window_start - INTERVAL 1 HOUR
    WHERE cur.granularity = ?
      AND cur.window_start >= ?
      AND cur.window_start <  ?
      AND prev.region_id  = ?
      AND cur.region_id  != ?
) f
INNER JOIN flow.dim_user_profile p ON f.imsi = p.imsi
GROUP BY bucket
ORDER BY bucket;
