package com.share.spring_boot_demo1.controller;

import com.share.spring_boot_demo1.dto.AddressRequest;
import com.share.spring_boot_demo1.dto.AddressResponse;
import com.share.spring_boot_demo1.security.CurrentUser;
import com.share.spring_boot_demo1.service.AddressService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.List;

/**
 * 当前消费者的收货地址接口。
 */
@RestController
@RequestMapping("/api/me/addresses")
public class AddressController {
    private final AddressService service;

    public AddressController(AddressService service) {
        this.service = service;
    }

    @GetMapping
    public List<AddressResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return service.list(CurrentUser.id(jwt));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AddressResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AddressRequest request) {
        return service.create(CurrentUser.id(jwt), request);
    }

    @PutMapping("/{addressId}")
    public AddressResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable long addressId,
                                  @Valid @RequestBody AddressRequest request) {
        return service.update(CurrentUser.id(jwt), addressId, request);
    }

    @PatchMapping("/{addressId}/default")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setDefault(@AuthenticationPrincipal Jwt jwt, @PathVariable long addressId) {
        service.setDefault(CurrentUser.id(jwt), addressId);
    }

    @DeleteMapping("/{addressId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable long addressId) {
        service.delete(CurrentUser.id(jwt), addressId);
    }
}
