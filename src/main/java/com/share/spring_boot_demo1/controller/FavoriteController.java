package com.share.spring_boot_demo1.controller;

import com.share.spring_boot_demo1.common.PageResponse;
import com.share.spring_boot_demo1.dto.ProductResponse;
import com.share.spring_boot_demo1.security.CurrentUser;
import com.share.spring_boot_demo1.service.FavoriteService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前消费者的商品收藏接口。
 */
@Validated
@RestController
@RequestMapping("/api/me/favorites")
public class FavoriteController {
    private final FavoriteService service;

    public FavoriteController(FavoriteService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<ProductResponse> list(@AuthenticationPrincipal Jwt jwt,
                                              @RequestParam(defaultValue = "0") @Min(0) int page,
                                              @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(CurrentUser.id(jwt), page, size);
    }

    @PostMapping("/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void add(@AuthenticationPrincipal Jwt jwt, @PathVariable long productId) {
        service.add(CurrentUser.id(jwt), productId);
    }

    @DeleteMapping("/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal Jwt jwt, @PathVariable long productId) {
        service.remove(CurrentUser.id(jwt), productId);
    }
}
