package com.bingo.app.tenant.controller;

import com.bingo.app.common.dto.ApiResponse;
import com.bingo.app.infrastructure.storage.LocalScreenshotStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.MalformedURLException;

@RestController
@RequestMapping("/api/v1/screenshots")
@RequiredArgsConstructor
public class ScreenshotController {

    private final LocalScreenshotStorage screenshotStorage;

    @PostMapping("/upload")
    public ApiResponse<String> upload(@RequestParam("file") MultipartFile file) {
        String filename = screenshotStorage.store(file);
        return ApiResponse.ok("Screenshot uploaded", "/api/v1/screenshots/" + filename);
    }

    @GetMapping("/{filename:.+}")
    public ResponseEntity<UrlResource> serve(@PathVariable String filename) throws MalformedURLException {
        var stored = screenshotStorage.load(filename);
        if (stored == null) {
            return ResponseEntity.notFound().build();
        }
        UrlResource resource = new UrlResource(stored.path().toUri());
        return ResponseEntity.ok()
                .contentType(stored.mediaType())
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .body(resource);
    }
}
