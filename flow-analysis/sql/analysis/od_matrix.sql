-- od_matrix.sql : OD（Origin-Destination）矩阵
-- =====================================================================
-- 口径（全系统唯一，禁止第二套）：
--   * 数据源 = flow.dws_user_window_loc（loc = 窗口内最后一个 5min 切片所在 region，argMax 末 slice）。
--   * transition-only（方案 A）：只统计相邻窗口都在线的用户。
--     dws 表「在线才有行」，相邻窗口自连接用 INNER JOIN，天然只保留两窗口都在网的用户，
--     离线/上线不计入流动；不引入 OFFLINE 伪区域。
--   * OD 按 window 维度统计：from_region = 上一窗口所在区，to_region = 当前窗口所在区。
--     对角线 from==to 即 retained（留存），非对角线为跨区流动。
--   * step（相邻窗口间隔）随 granularity 变化：hour → INTERVAL 1 HOUR；day → INTERVAL 1 DAY。
--     INTERVAL 步长不能用 ? 参数化 → SqlConst 提供 OD_HOUR / OD_DAY 两个常量（仅 step 不同）。
-- 参数（ClickHouse JDBC 位置参数 ?，按出现顺序）：
--   ?1 = cur.granularity（'hour' | 'day'）
--   ?2 = 时间段起点 window_start >= ?（含，yyyy-MM-dd HH:mm:ss，Asia/Shanghai）
--   ?3 = 时间段终点 window_start <  ?（不含）
-- 输出列（对齐 model/ODFlow，region name 由 service 用 RegionDef.regionName 补）：
--   from_region, to_region, window_start, flow
-- =====================================================================
SELECT prev.region_id   AS from_region,
       cur.region_id    AS to_region,
       cur.window_start AS window_start,
       count()          AS flow
FROM flow.dws_user_window_loc cur
INNER JOIN flow.dws_user_window_loc prev
        ON cur.imsi = prev.imsi
       AND cur.granularity = prev.granularity
       AND prev.window_start = cur.window_start - INTERVAL 1 HOUR   -- day 时为 INTERVAL 1 DAY
WHERE cur.granularity = ?
  AND cur.window_start >= ?
  AND cur.window_start <  ?
GROUP BY from_region, to_region, window_start
ORDER BY window_start, from_region, to_region;
