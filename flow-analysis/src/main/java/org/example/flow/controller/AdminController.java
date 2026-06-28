package org.example.flow.controller;

import org.example.flow.datagen.DataGenerator;
import org.example.flow.datagen.GenResult;
import org.example.flow.model.ApiResp;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminController {

    private final DataGenerator dataGenerator;

    public AdminController(DataGenerator dataGenerator) {
        this.dataGenerator = dataGenerator;
    }

    @PostMapping("/api/admin/init-data")
    public ApiResp<GenResult> initData() {
        GenResult result = dataGenerator.generate();
        return ApiResp.ok(result);
    }
}
