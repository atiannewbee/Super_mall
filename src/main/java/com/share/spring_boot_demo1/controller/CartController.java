package com.share.spring_boot_demo1.controller;

import com.share.spring_boot_demo1.dto.CartItemRequest;
import com.share.spring_boot_demo1.dto.CartItemUpdateRequest;
import com.share.spring_boot_demo1.dto.CartResponse;
import com.share.spring_boot_demo1.security.CurrentUser;
import com.share.spring_boot_demo1.service.CartService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前消费者购物车接口。
 */
@RestController
@RequestMapping("/api/cart")
public class CartController {
    private final CartService service;

    public CartController(CartService service) {
        this.service = service;
    }

    @GetMapping
    public CartResponse get(@AuthenticationPrincipal Jwt jwt) {
        return service.get(CurrentUser.id(jwt));
    }

    @PostMapping("/items")
    public CartResponse add(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CartItemRequest request) {
        return service.add(CurrentUser.id(jwt), request);
    }

    @PatchMapping("/items/{itemId}")
    public CartResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable long itemId,
                               @Valid @RequestBody CartItemUpdateRequest request) {
        return service.update(CurrentUser.id(jwt), itemId, request);
    }

    @DeleteMapping("/items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal Jwt jwt, @PathVariable long itemId) {
        service.remove(CurrentUser.id(jwt), itemId);
    }

    @DeleteMapping("/items")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(@AuthenticationPrincipal Jwt jwt) {
        service.clear(CurrentUser.id(jwt));
    }
}
