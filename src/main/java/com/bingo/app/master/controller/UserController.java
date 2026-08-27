package com.bingo.app.master.controller;

import com.bingo.app.infrastructure.security.UserPrincipal;
import com.bingo.app.common.dto.ApiResponse;
import com.bingo.app.master.dto.response.UserProfileResponse;
import com.bingo.app.master.entity.User;
import com.bingo.app.master.repository.UserRepository;
import com.bingo.app.master.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserProfileService userProfileService;
    private final UserRepository userRepository;

    @GetMapping("/me")
    public ApiResponse<UserProfileResponse> me(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(userProfileService.buildProfile(principal.getUser()));
    }

    @PutMapping("/me")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<UserProfileResponse> updateMe(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody Map<String, String> body) {
        User user = principal.getUser();
        if (body.containsKey("depositAccountInfo")) {
            user.setDepositAccountInfo(body.get("depositAccountInfo"));
        }
        if (body.containsKey("businessName")) {
            user.setBusinessName(body.get("businessName"));
        }
        userRepository.save(user);
        return ApiResponse.ok("Profile updated", userProfileService.buildProfile(user));
    }
}
