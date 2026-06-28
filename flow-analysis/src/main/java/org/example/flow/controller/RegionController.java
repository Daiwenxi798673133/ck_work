package org.example.flow.controller;

import org.example.flow.model.ApiResp;
import org.example.flow.model.RegionVO;
import org.example.flow.service.RegionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/regions")
public class RegionController {

    private final RegionService regionService;

    public RegionController(RegionService regionService) {
        this.regionService = regionService;
    }

    @GetMapping
    public ApiResp<List<RegionVO>> regions() {
        return ApiResp.ok(regionService.listRegions());
    }
}
