-- 04_dwd_cell_imsi_5min.sql: 5分钟粒度小区-IMSI 明细宽表（长表，一行一个 IMSI）
CREATE TABLE IF NOT EXISTS flow.dwd_cell_imsi_5min
(
    stat_time DateTime('Asia/Shanghai'),
    stat_date Date,
    city_code  String,
    lac        String,
    cell_id    String,
    region_id  String,
    imsi       String
)
ENGINE = MergeTree
PARTITION BY stat_date
ORDER BY (region_id, stat_time, imsi);
