package org.example.flow.controller;

import org.example.flow.model.ApiResp;
import org.example.flow.model.FlowTrendPoint;
import org.example.flow.service.TrendService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/flow")
public class TrendController {

    private final TrendService trendService;

    public TrendController(TrendService trendService) {
        this.trendService = trendService;
    }

    @GetMapping("/trend")
    public ApiResp<List<FlowTrendPoint>> trend(
            @RequestParam String regionId,
            @RequestParam String granularity,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime start,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime end) {
        return ApiResp.ok(trendService.trend(regionId, granularity, start, end));
    }
}
