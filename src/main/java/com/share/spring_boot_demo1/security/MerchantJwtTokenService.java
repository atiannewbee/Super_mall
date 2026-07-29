package com.share.spring_boot_demo1.security;

import com.share.spring_boot_demo1.dto.MerchantAuthResponse;
import com.share.spring_boot_demo1.service.MerchantAuthService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * 为商家员工签发独立访问令牌。
 *
 * <p>merchantId 用于所有商家数据查询的租户隔离，tokenVersion 用于即时撤销，
 * scope 则映射为 Spring Security 的角色权限。</p>
 */
@Service
public class MerchantJwtTokenService {
    private final JwtEncoder jwtEncoder;
    private final MerchantSecurityProperties properties;
    private final MerchantAuthService merchantAuthService;

    public MerchantJwtTokenService(
            @Qualifier("merchantJwtEncoder") JwtEncoder jwtEncoder,
            MerchantSecurityProperties properties,
            MerchantAuthService merchantAuthService
    ) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.merchantAuthService = merchantAuthService;
    }

    /**
     * 根据已完成密码校验的商家身份签发令牌。
     */
    public MerchantAuthResponse issue(MerchantAuthService.AuthenticatedMerchant authenticated) {
        MerchantAuthService.MerchantAccount account = authenticated.account();
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(properties.accessTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.jwtIssuer())
                .audience(List.of(properties.jwtAudience()))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(Long.toString(account.id()))
                // 商户 ID 与员工 ID 必须同时进入令牌，禁止仅靠前端传递商户范围。
                .claim("merchant_id", account.merchantId())
                .claim("token_version", account.tokenVersion())
                .claim("scope", String.join(" ", authenticated.roles()))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new MerchantAuthResponse(
                token,
                "Bearer",
                properties.accessTokenTtl().toSeconds(),
                merchantAuthService.toProfile(authenticated)
        );
    }
}
