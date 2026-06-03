package com.bingo.app.master.controller;

import com.bingo.app.master.dto.request.BroadcastRequest;
import com.bingo.app.common.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/broadcast")
public class BroadcastController {

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<String> broadcast(@Valid @RequestBody BroadcastRequest request) {
        return ApiResponse.ok("Broadcast sent to " + request.target());
    }
}
