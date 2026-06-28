-- 02_dim_cell.sql: 小区维度表
CREATE TABLE IF NOT EXISTS flow.dim_cell
(
    cell_id   String,
    region_id String,
    lac       String,
    city_code String
)
ENGINE = MergeTree
ORDER BY cell_id;
