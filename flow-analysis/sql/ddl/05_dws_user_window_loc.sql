-- 05_dws_user_window_loc.sql: 用户窗口位置派生汇总表
CREATE TABLE IF NOT EXISTS flow.dws_user_window_loc
(
    imsi         String,
    granularity  Enum8('hour' = 1, 'day' = 2),
    window_start DateTime('Asia/Shanghai'),
    region_id    String
)
ENGINE = MergeTree
ORDER BY (granularity, window_start, imsi);
