-- trend.sql : 指定 region 的流入/流出/留存/人口时序（严格从 OD 派生，保证守恒 C1）
-- =====================================================================
-- 口径（全系统唯一，禁止第二套）：
--   * 不用「出现过」口径单独算 inflow，全部从 OD（od_matrix 逻辑）派生：
--       inflow   = sumIf(flow, to_region   = R AND from_region != R)   -- 别区 → R
--       outflow  = sumIf(flow, from_region = R AND to_region   != R)   -- R → 别区
--       retained = sumIf(flow, from_region = R AND to_region    = R)   -- R 留守（OD 对角线）
--       population = retained + inflow                                   -- 本窗口到达 R 的总人次
--   * transition-only / loc=argMax末slice / step 随 granularity 见 od_matrix.sql；本文件用 INTERVAL 1 HOUR。
--     SqlConst 提供 TREND_HOUR / TREND_DAY 两个常量（仅内层 step 不同）。
--   * flow 为「流动次数」口径（OD 行计数），与 portrait 按流动次数计可严格对账。
-- 参数（位置参数 ?，按 SQL 文本出现顺序）：
--   ?1=R(inflow.to)  ?2=R(inflow.from!=)
--   ?3=R(outflow.from)  ?4=R(outflow.to!=)
--   ?5=R(retained.from)  ?6=R(retained.to)
--   ?7=R(pop.retained.from) ?8=R(pop.retained.to) ?9=R(pop.inflow.to) ?10=R(pop.inflow.from!=)
--   ?11=granularity  ?12=start(>=)  ?13=end(<)  ?14=R(cur) ?15=R(prev)
--   内层 (cur.region_id=R OR prev.region_id=R) 把 OD 限定在与 R 相关的行：
--     既提速，又使不存在的 region 自然返回空结果集（0 行，不报错）。
-- 输出列（对齐 model/FlowTrendPoint）：window_start, population, inflow, outflow, retained
-- =====================================================================
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
           AND prev.window_start = cur.window_start - INTERVAL 1 HOUR
    WHERE cur.granularity = ?
      AND cur.window_start >= ?
      AND cur.window_start <  ?
      AND (cur.region_id = ? OR prev.region_id = ?)
    GROUP BY from_region, to_region, window_start
) od
GROUP BY window_start
ORDER BY window_start;
