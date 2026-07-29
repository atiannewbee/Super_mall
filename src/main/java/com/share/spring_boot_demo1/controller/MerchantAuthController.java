package com.share.spring_boot_demo1.controller;

import com.share.spring_boot_demo1.dto.MerchantAuthResponse;
import com.share.spring_boot_demo1.dto.MerchantLoginRequest;
import com.share.spring_boot_demo1.dto.MerchantPasswordChangeRequest;
import com.share.spring_boot_demo1.dto.MerchantProfileResponse;
import com.share.spring_boot_demo1.security.CurrentMerchant;
import com.share.spring_boot_demo1.security.MerchantJwtTokenService;
import com.share.spring_boot_demo1.service.MerchantAuthService;
import jakarta.validation.Valid;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 独立商家登录、当前员工资料和改密接口。
 */
@RestController
@RequestMapping("/api/merchant")
public class MerchantAuthController {
    private final MerchantAuthService merchantAuthService;
    private final MerchantJwtTokenService merchantJwtTokenService;

    public MerchantAuthController(
            MerchantAuthService merchantAuthService,
            MerchantJwtTokenService merchantJwtTokenService
    ) {
        this.merchantAuthService = merchantAuthService;
        this.merchantJwtTokenService = merchantJwtTokenService;
    }

    @PostMapping("/auth/login")
    public MerchantAuthResponse login(@Valid @RequestBody MerchantLoginRequest request) {
        return merchantJwtTokenService.issue(merchantAuthService.authenticate(request));
    }

    @GetMapping("/me")
    public MerchantProfileResponse me(@AuthenticationPrincipal Jwt jwt) {
        return merchantAuthService.profile(
                CurrentMerchant.userId(jwt),
                CurrentMerchant.merchantId(jwt)
        );
    }

    /**
     * 修改密码后旧令牌立即失效，前端应清理会话并重新登录。
     */
    @PostMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody MerchantPasswordChangeRequest request
    ) {
        merchantAuthService.changePassword(
                CurrentMerchant.userId(jwt),
                CurrentMerchant.merchantId(jwt),
                request
        );
    }
}
