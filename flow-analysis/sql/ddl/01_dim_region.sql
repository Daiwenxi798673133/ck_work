-- 01_dim_region.sql: 区域维度表
CREATE TABLE IF NOT EXISTS flow.dim_region
(
    region_id   String,
    region_name String,
    city_code   String,
    lac         String,
    cell_count  UInt8
)
ENGINE = MergeTree
ORDER BY region_id;
