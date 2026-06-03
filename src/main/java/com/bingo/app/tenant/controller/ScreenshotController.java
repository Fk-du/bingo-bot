package com.bingo.app.tenant.controller;

import com.bingo.app.common.dto.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/screenshots")
public class ScreenshotController {

    @PostMapping("/upload")
    public ApiResponse<String> requestUploadUrl() {
        return ApiResponse.ok("Not yet implemented: Object storage integration pending");
    }
}
