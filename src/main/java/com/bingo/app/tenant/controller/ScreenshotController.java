package com.bingo.app.tenant.controller;

import com.bingo.app.common.dto.ApiResponse;
import com.bingo.app.infrastructure.security.UserPrincipal;
import com.bingo.app.infrastructure.storage.LocalScreenshotStorage;
import com.bingo.app.master.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import java.net.MalformedURLException;

@RestController
@RequestMapping("/api/v1/screenshots")
@RequiredArgsConstructor
public class ScreenshotController {

    private final LocalScreenshotStorage screenshotStorage;
    private final UserRepository userRepository;

    @PostMapping("/upload")
    public ApiResponse<String> upload(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam("file") MultipartFile file) {
        // Every agent gets their own folder: data/screenshots/<agent-name>/...
        var user = principal.getUser();
        String agentFolder = user.getAdminUserId() == null ? "unsorted"
                : userRepository.findById(user.getAdminUserId())
                        .map(a -> a.getBusinessName() != null && !a.getBusinessName().isBlank()
                                ? a.getBusinessName()
                                : a.getUsername() != null ? a.getUsername() : "agent-" + a.getId())
                        .orElse("agent-" + user.getAdminUserId());
        String filename = screenshotStorage.store(file, agentFolder);
        return ApiResponse.ok("Screenshot uploaded", "/api/v1/screenshots/" + filename);
    }

    @GetMapping("/**")
    public ResponseEntity<UrlResource> serve(
            @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest request) throws MalformedURLException {
        String prefix = "/api/v1/screenshots/";
        String filepath = request.getRequestURI().substring(request.getRequestURI().indexOf(prefix) + prefix.length());
        String decoded = java.net.URLDecoder.decode(filepath, java.nio.charset.StandardCharsets.UTF_8);

        // Ownership check: resolve the allowed folder for this user
        var user = principal.getUser();
        String allowedFolder = user.getAdminUserId() == null ? "unsorted"
                : userRepository.findById(user.getAdminUserId())
                        .map(a -> a.getBusinessName() != null && !a.getBusinessName().isBlank()
                                ? a.getBusinessName()
                                : a.getUsername() != null ? a.getUsername() : "agent-" + a.getId())
                        .orElse("agent-" + user.getAdminUserId());

        // Extract the first path segment (agent folder name)
        String folderSegment = decoded.contains("/") ? decoded.substring(0, decoded.indexOf('/')) : "";
        if (!folderSegment.equals(allowedFolder) && !"unsorted".equals(folderSegment)) {
            return ResponseEntity.status(403).build();
        }

        var stored = screenshotStorage.load(decoded);
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
