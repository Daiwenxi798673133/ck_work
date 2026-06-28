package org.example.flow.constant;

import java.util.*;

/**
 * 区域 / 小区静态定义。
 * city_code=310000（上海），3 个 region，8 个 cell。
 * 所有数据与 ClickHouse dim_region/dim_cell 表保持一致。
 */
public final class RegionDef {

    /** region_id → lac */
    private static final Map<String, String> REGION_LAC = new LinkedHashMap<>();
    /** region_id → 中文名 */
    private static final Map<String, String> REGION_NAME = new LinkedHashMap<>();
    /** region_id → cell_id 列表（有序） */
    private static final Map<String, List<String>> REGION_CELLS = new LinkedHashMap<>();
    /** cell_id → region_id（反向索引，O(1) 查找） */
    private static final Map<String, String> CELL_REGION = new LinkedHashMap<>();

    static {
        register("310000_6254", "陆家嘴", "6254",
                "310000_6254_140854179",
                "310000_6254_140854180",
                "310000_6254_140854181");

        register("310000_6234", "人民广场", "6234",
                "310000_6234_140854182",
                "310000_6234_140854183");

        register("310000_6200", "徐家汇", "6200",
                "310000_6200_140854184",
                "310000_6200_140854185",
                "310000_6200_140854186");
    }

    private RegionDef() {
        throw new UnsupportedOperationException("utility class");
    }

    private static void register(String regionId, String regionName, String lac, String... cellIds) {
        REGION_LAC.put(regionId, lac);
        REGION_NAME.put(regionId, regionName);
        List<String> cells = Collections.unmodifiableList(Arrays.asList(cellIds));
        REGION_CELLS.put(regionId, cells);
        for (String cell : cellIds) {
            CELL_REGION.put(cell, regionId);
        }
    }

    // ───────────────── 查询方法 ─────────────────

    /** 根据 cell_id 返回所属 region_id，不存在时返回 null。 */
    public static String regionOfCell(String cellId) {
        return CELL_REGION.get(cellId);
    }

    /** 根据 cell_id 返回所属 region 的 lac，不存在时返回 null。 */
    public static String lacOfCell(String cellId) {
        String regionId = CELL_REGION.get(cellId);
        return regionId == null ? null : REGION_LAC.get(regionId);
    }

    /** 返回所有 region_id 列表（顺序与定义一致）。 */
    public static List<String> allRegions() {
        return Collections.unmodifiableList(new ArrayList<>(REGION_LAC.keySet()));
    }

    /** 返回所有 cell_id 列表（按 region 定义顺序）。 */
    public static List<String> allCells() {
        return Collections.unmodifiableList(new ArrayList<>(CELL_REGION.keySet()));
    }

    /** 根据 region_id 返回中文名，不存在时返回 null。 */
    public static String regionName(String regionId) {
        return REGION_NAME.get(regionId);
    }

    /** 根据 region_id 返回 lac，不存在时返回 null。 */
    public static String lacOfRegion(String regionId) {
        return REGION_LAC.get(regionId);
    }

    /** 根据 region_id 返回该 region 下所有 cell_id 列表。 */
    public static List<String> cellsOfRegion(String regionId) {
        return REGION_CELLS.getOrDefault(regionId, Collections.emptyList());
    }
}
