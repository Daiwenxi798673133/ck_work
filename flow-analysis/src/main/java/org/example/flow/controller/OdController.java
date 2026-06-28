package org.example.flow.controller;

import org.example.flow.model.ApiResp;
import org.example.flow.model.ODFlow;
import org.example.flow.model.enums.Granularity;
import org.example.flow.service.OdService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 区域 OD 矩阵查询端点：{@code GET /api/flow/od?granularity=hour|day&start=&end=}。
 * 返回矩阵含对角线（from==to 的 retained），便于前端画 N×N；桑基图前端自行过滤 from!=to。
 */
@RestController
@RequestMapping("/api/flow")
public class OdController {

    private final OdService odService;

    public OdController(OdService odService) {
        this.odService = odService;
    }

    @GetMapping("/od")
    public ApiResp<List<ODFlow>> od(@RequestParam String granularity,
                                    @RequestParam String start,
                                    @RequestParam String end) {
        try {
            Granularity g = parseGranularity(granularity);
            return ApiResp.ok(odService.odMatrix(g, start, end));
        } catch (IllegalArgumentException | DateTimeParseException ex) {
            return ApiResp.error(400, "invalid parameter: " + ex.getMessage());
        }
    }

    /**
     * "hour"/"day"（大小写不敏感）→ {@link Granularity}。
     * Spring 默认枚举绑定按 name()（HOUR/DAY）匹配，与 API 契约的小写 value 不符，故自行解析。
     */
    private static Granularity parseGranularity(String raw) {
        if (raw != null) {
            for (Granularity g : Granularity.values()) {
                if (g.getValue().equalsIgnoreCase(raw)) {
                    return g;
                }
            }
        }
        throw new IllegalArgumentException("granularity must be 'hour' or 'day', got: " + raw);
    }
}
