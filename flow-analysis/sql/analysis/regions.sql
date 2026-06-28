-- regions.sql : 区域维度列表，供 GET /api/regions
-- =====================================================================
-- 口径：直接读 flow.dim_region（静态维度，3 个 region），无参数。
--   输出列对齐 model/RegionVO：region_id, region_name, lac, cell_count。
-- =====================================================================
SELECT region_id,
       region_name,
       lac,
       cell_count
FROM flow.dim_region
ORDER BY region_id;
