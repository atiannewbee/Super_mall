package com.share.spring_boot_demo1.controller;

import com.share.spring_boot_demo1.dto.UpdateProfileRequest;
import com.share.spring_boot_demo1.dto.UserProfileResponse;
import com.share.spring_boot_demo1.security.CurrentUser;
import com.share.spring_boot_demo1.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前消费者个人资料接口。
 */
@RestController
@RequestMapping("/api/me/profile")
public class ProfileController {
    private final UserService userService;

    public ProfileController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public UserProfileResponse getProfile(@AuthenticationPrincipal Jwt jwt) {
        return userService.profile(CurrentUser.id(jwt));
    }

    @PutMapping
    public UserProfileResponse updateProfile(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        return userService.updateProfile(CurrentUser.id(jwt), request);
    }
}
