package com.share.spring_boot_demo1.controller;

import com.share.spring_boot_demo1.dto.AuthResponse;
import com.share.spring_boot_demo1.dto.LoginRequest;
import com.share.spring_boot_demo1.dto.RegisterRequest;
import com.share.spring_boot_demo1.security.JwtTokenService;
import com.share.spring_boot_demo1.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 消费者注册与登录入口。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserService userService;
    private final JwtTokenService jwtTokenService;

    public AuthController(UserService userService, JwtTokenService jwtTokenService) {
        this.userService = userService;
        this.jwtTokenService = jwtTokenService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return jwtTokenService.issue(userService.register(request));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return jwtTokenService.issue(userService.authenticate(request));
    }
}
