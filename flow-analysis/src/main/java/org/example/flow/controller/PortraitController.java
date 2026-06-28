package org.example.flow.controller;

import org.example.flow.model.ApiResp;
import org.example.flow.model.PortraitResult;
import org.example.flow.service.PortraitService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 流入/流出人群画像分布端点。SQL/口径全部在 {@link PortraitService}，本类只做参数透传。
 */
@RestController
@RequestMapping("/api/flow")
public class PortraitController {

    private final PortraitService portraitService;

    public PortraitController(PortraitService portraitService) {
        this.portraitService = portraitService;
    }

    /**
     * GET /api/flow/portrait?regionId=&granularity=&start=&end=&direction=&dimension=
     *
     * @param regionId    目标 region_id
     * @param granularity hour | day
     * @param start       ISO 起点（{@code >=}）
     * @param end         ISO 终点（{@code <}）
     * @param direction   in | out
     * @param dimension   gender | age_group | is_resident
     */
    @GetMapping("/portrait")
    public ApiResp<PortraitResult> portrait(
            @RequestParam String regionId,
            @RequestParam String granularity,
            @RequestParam String start,
            @RequestParam String end,
            @RequestParam String direction,
            @RequestParam String dimension) {
        PortraitResult result =
                portraitService.portrait(regionId, granularity, start, end, direction, dimension);
        return ApiResp.ok(result);
    }
}
