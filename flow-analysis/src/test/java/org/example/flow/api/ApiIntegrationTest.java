package org.example.flow.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("API 集成测试（契约 + 跨 API 对账 + 边界 + 可复现）")
class ApiIntegrationTest {

    private static final String REGION        = "310000_6254";
    private static final String REGION_PEOPLE = "310000_6234";
    private static final String REGION_XJH    = "310000_6200";
    private static final String GRAN          = "hour";
    private static final String START         = "2026-06-01T00:00:00";
    private static final String END           = "2026-06-02T00:00:00";

    private static final String TREND =
            "/api/flow/trend?regionId=" + REGION + "&granularity=" + GRAN
                    + "&start=" + START + "&end=" + END;
    private static final String OD =
            "/api/flow/od?granularity=" + GRAN + "&start=" + START + "&end=" + END;

    private static final Set<String> ALL_REGIONS = Set.of(REGION, REGION_PEOPLE, REGION_XJH);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private TestRestTemplate rest;

    private ResponseEntity<String> get(String pathAndQuery) {
        return rest.getForEntity(pathAndQuery, String.class);
    }

    private JsonNode okData(String pathAndQuery) throws Exception {
        ResponseEntity<String> resp = get(pathAndQuery);
        assertEquals(HttpStatus.OK, resp.getStatusCode(),
                "期望 HTTP 200：" + pathAndQuery);
        assertNotNull(resp.getBody(), "响应体不应为 null：" + pathAndQuery);
        JsonNode root = MAPPER.readTree(resp.getBody());
        assertEquals(200, root.path("code").asInt(),
                "期望 ApiResp.code=200：" + pathAndQuery + " => " + resp.getBody());
        assertEquals("success", root.path("message").asText(),
                "期望 ApiResp.message=success：" + pathAndQuery);
        assertTrue(root.has("data"), "响应应含 data 字段：" + pathAndQuery);
        return root.path("data");
    }

    private static String portrait(String direction, String dimension) {
        return "/api/flow/portrait?regionId=" + REGION + "&granularity=" + GRAN
                + "&start=" + START + "&end=" + END
                + "&direction=" + direction + "&dimension=" + dimension;
    }

    private static long sumTrendInflow(JsonNode trendData) {
        long s = 0L;
        for (JsonNode p : trendData) {
            s += p.path("inflow").asLong();
        }
        return s;
    }

    private static long odInflowInto(JsonNode odData, String region) {
        long s = 0L;
        for (JsonNode o : odData) {
            if (region.equals(o.path("toRegionId").asText())
                    && !region.equals(o.path("fromRegionId").asText())) {
                s += o.path("flow").asLong();
            }
        }
        return s;
    }

    private static long sumBucketCount(JsonNode portraitData) {
        long s = 0L;
        for (JsonNode b : portraitData.path("buckets")) {
            s += b.path("count").asLong();
        }
        return s;
    }

    @Test
    @DisplayName("GET /api/regions → 200，data 含 3 个 region（陆家嘴/人民广场/徐家汇，cellCount 3/2/3）")
    void regions_returns200WithThreeRegions() throws Exception {
        JsonNode data = okData("/api/regions");
        assertTrue(data.isArray(), "data 应为 JSON 数组");
        assertEquals(3, data.size(), "region 数量应为 3");

        Map<String, Integer> cellCounts = new HashMap<>();
        for (JsonNode r : data) {
            String id = r.path("regionId").asText();
            cellCounts.put(id, r.path("cellCount").asInt());
            assertFalse(r.path("regionName").asText().isEmpty(),
                    "regionName 不应为空：" + r);
            assertFalse(r.path("lac").asText().isEmpty(),
                    "lac 不应为空：" + r);
        }
        assertEquals(ALL_REGIONS, cellCounts.keySet(),
                "region_id 集合应为 {310000_6254, 310000_6234, 310000_6200}");
        assertEquals(3, cellCounts.get(REGION).intValue(), "陆家嘴(6254) cellCount 应为 3");
        assertEquals(2, cellCounts.get(REGION_PEOPLE).intValue(), "人民广场(6234) cellCount 应为 2");
        assertEquals(3, cellCounts.get(REGION_XJH).intValue(), "徐家汇(6200) cellCount 应为 3");
    }

    @Test
    @DisplayName("GET /api/flow/trend → 200，data 非空（24 窗口）且每点 population==retained+inflow")
    void trend_returns200_nonEmpty_populationEqualsRetainedPlusInflow() throws Exception {
        JsonNode data = okData(TREND);
        assertTrue(data.isArray(), "trend data 应为数组");
        assertTrue(data.size() > 0, "trend data 不应为空");
        assertEquals(24, data.size(),
                "06-01 全天 hour 应为 24 个窗口（种子数据已知确定值）");

        int i = 0;
        for (JsonNode p : data) {
            long population = p.path("population").asLong();
            long retained   = p.path("retained").asLong();
            long inflow     = p.path("inflow").asLong();
            assertEquals(retained + inflow, population,
                    "窗口[" + i + "] (" + p.path("windowStart").asText()
                            + ") 守恒不变式 population==retained+inflow 不成立："
                            + "population=" + population + ", retained=" + retained
                            + ", inflow=" + inflow);
            i++;
        }
    }

    @Test
    @DisplayName("GET /api/flow/od → 200，含 9 项(3×3)，Σ(to=R,from!=R) flow == trend Σinflow")
    void od_returns200_with9Items_reconcilesWithTrendInflow() throws Exception {
        JsonNode odData = okData(OD);
        assertTrue(odData.isArray(), "od data 应为数组");
        assertEquals(9, odData.size(), "OD 矩阵应为 3×3=9 项（含对角线）");

        Set<String> froms = new HashSet<>();
        Set<String> tos   = new HashSet<>();
        Set<String> pairs = new HashSet<>();
        for (JsonNode o : odData) {
            String from = o.path("fromRegionId").asText();
            String to   = o.path("toRegionId").asText();
            froms.add(from);
            tos.add(to);
            pairs.add(from + "->" + to);
            assertFalse(o.path("fromRegionName").asText().isEmpty(),
                    "fromRegionName 不应为空：" + o);
            assertFalse(o.path("toRegionName").asText().isEmpty(),
                    "toRegionName 不应为空：" + o);
        }
        assertEquals(ALL_REGIONS, froms, "from 区域集合应为 3 个");
        assertEquals(ALL_REGIONS, tos, "to 区域集合应为 3 个");
        assertEquals(9, pairs.size(), "(from,to) 组合应有 9 个不重复项");

        long odInflow    = odInflowInto(odData, REGION);
        long trendInflow = sumTrendInflow(okData(TREND));
        assertTrue(odInflow > 0, "OD 流入 R 应 > 0（种子数据有充足跨区流动）");
        assertEquals(trendInflow, odInflow,
                "跨 API 对账失败：OD Σ(to=R,from!=R) flow(" + odInflow
                        + ") 应 == trend Σinflow(" + trendInflow + ")");
    }

    @Test
    @DisplayName("GET /api/flow/portrait(in,gender) → 200，2 桶，Σbuckets.count==total==trend Σinflow")
    void portrait_genderIn_twoBuckets_reconcilesWithTrendInflow() throws Exception {
        JsonNode data = okData(portrait("in", "gender"));
        assertEquals("in", data.path("direction").asText(), "direction 回写应为 in");
        assertEquals("gender", data.path("dimension").asText(), "dimension 回写应为 gender");

        assertEquals(2, data.path("buckets").size(), "gender 维度应为 2 桶（男/女）");

        long total    = data.path("total").asLong();
        long sumCount = sumBucketCount(data);
        assertEquals(total, sumCount,
                "portrait 契约：Σbuckets.count(" + sumCount + ") 应 == total(" + total + ")");

        long trendInflow = sumTrendInflow(okData(TREND));
        assertTrue(total > 0, "portrait total 应 > 0");
        assertEquals(trendInflow, total,
                "跨 API 对账失败：portrait(in).total(" + total
                        + ") 应 == trend Σinflow(" + trendInflow + ")");
    }

    @Test
    @DisplayName("GET /api/flow/portrait(in,age_group) → 6 桶，Σbuckets.count==total")
    void portrait_ageGroup_sixBuckets() throws Exception {
        JsonNode data = okData(portrait("in", "age_group"));
        assertEquals("age_group", data.path("dimension").asText(), "dimension 回写应为 age_group");
        assertEquals(6, data.path("buckets").size(),
                "age_group 维度应为 6 桶（<18/18-25/26-35/36-45/46-60/60+）");
        assertEquals(data.path("total").asLong(), sumBucketCount(data),
                "age_group Σbuckets.count 应 == total");
    }

    @Test
    @DisplayName("GET /api/flow/portrait(in,is_resident) → 2 桶，Σbuckets.count==total")
    void portrait_isResident_twoBuckets() throws Exception {
        JsonNode data = okData(portrait("in", "is_resident"));
        assertEquals("is_resident", data.path("dimension").asText(), "dimension 回写应为 is_resident");
        assertEquals(2, data.path("buckets").size(),
                "is_resident 维度应为 2 桶（0=非常住 / 1=常住）");
        assertEquals(data.path("total").asLong(), sumBucketCount(data),
                "is_resident Σbuckets.count 应 == total");
    }

    @Test
    @DisplayName("三向对账：OD Σ(to=R,from!=R) == Σtrend.inflow == portrait(in).total（同一数）")
    void threeWayReconciliation_odInflow_trendInflow_portraitTotal() throws Exception {
        long trendInflow   = sumTrendInflow(okData(TREND));
        long odInflow      = odInflowInto(okData(OD), REGION);
        long portraitTotal = okData(portrait("in", "gender")).path("total").asLong();

        assertTrue(trendInflow > 0, "trend Σinflow 应 > 0（种子数据保证有跨区流动）");
        assertEquals(trendInflow, odInflow,
                "三向对账：trend Σinflow(" + trendInflow + ") != OD Σ(into R)(" + odInflow + ")");
        assertEquals(trendInflow, portraitTotal,
                "三向对账：trend Σinflow(" + trendInflow
                        + ") != portrait(in).total(" + portraitTotal + ")");
    }

    @Test
    @DisplayName("边界：非法 dimension=ZZZ → HTTP 400 + ApiResp.code=400")
    void boundary_invalidDimension_returns400() throws Exception {
        ResponseEntity<String> resp = get(portrait("in", "ZZZ"));
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode(),
                "非法 dimension 应返回 HTTP 400，实际：" + resp.getStatusCode()
                        + " body=" + resp.getBody());
        JsonNode root = MAPPER.readTree(resp.getBody());
        assertEquals(400, root.path("code").asInt(),
                "ApiResp.code 应为 400：" + resp.getBody());
    }

    @Test
    @DisplayName("边界：不存在的 region → HTTP 200 + 空数组（不报错）")
    void boundary_nonexistentRegion_returns200EmptyArray() throws Exception {
        String path = "/api/flow/trend?regionId=999999_0000&granularity=" + GRAN
                + "&start=" + START + "&end=" + END;
        JsonNode data = okData(path);
        assertTrue(data.isArray(), "data 应为数组");
        assertEquals(0, data.size(),
                "不存在的 region 应返回空数组，实际 size=" + data.size());
    }

    @Test
    @DisplayName("边界：缺少必填参数 regionId → HTTP 400 + ApiResp.code=400")
    void boundary_missingParam_returns400() throws Exception {
        String path = "/api/flow/trend?granularity=" + GRAN
                + "&start=" + START + "&end=" + END;
        ResponseEntity<String> resp = get(path);
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode(),
                "缺少必填参数应返回 HTTP 400，实际：" + resp.getStatusCode()
                        + " body=" + resp.getBody());
        JsonNode root = MAPPER.readTree(resp.getBody());
        assertEquals(400, root.path("code").asInt(),
                "ApiResp.code 应为 400：" + resp.getBody());
    }

    @Test
    @DisplayName("可复现：同一 trend 请求连续 2 次，返回 JSON 数值完全一致（数据确定性）")
    void reproducibility_sameTrendRequestTwice_identicalJson() throws Exception {
        ResponseEntity<String> r1 = get(TREND);
        ResponseEntity<String> r2 = get(TREND);
        assertEquals(HttpStatus.OK, r1.getStatusCode(), "第 1 次 trend 应 HTTP 200");
        assertEquals(HttpStatus.OK, r2.getStatusCode(), "第 2 次 trend 应 HTTP 200");

        JsonNode n1 = MAPPER.readTree(r1.getBody());
        JsonNode n2 = MAPPER.readTree(r2.getBody());
        assertTrue(n1.path("data").size() > 0,
                "trend data 不应为空（否则一致性断言无意义）");
        assertEquals(n1, n2,
                "两次相同 trend 请求返回的 JSON 必须完全一致（含所有数值，验证数据确定性）");
    }
}
