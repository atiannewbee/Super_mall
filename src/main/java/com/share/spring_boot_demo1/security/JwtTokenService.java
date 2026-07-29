package com.share.spring_boot_demo1.security;

import com.share.spring_boot_demo1.dto.AuthResponse;
import com.share.spring_boot_demo1.dto.UserProfileResponse;
import com.share.spring_boot_demo1.entity.User;
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
 * 为消费者账号签发短期访问令牌。
 *
 * <p>令牌只保存稳定的用户 ID 和 USER 权限，不放入昵称、邮箱等可变资料；
 * 当前用户资料由数据库读取并随登录响应返回。</p>
 */
@Service
public class JwtTokenService {
    private final JwtEncoder jwtEncoder;
    private final SecurityProperties properties;

    public JwtTokenService(@Qualifier("jwtEncoder") JwtEncoder jwtEncoder, SecurityProperties properties) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
    }

    /**
     * 签发消费者访问令牌并返回当前用户摘要。
     */
    public AuthResponse issue(User user) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(properties.accessTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.jwtIssuer())
                .audience(List.of(properties.jwtAudience()))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(user.getId().toString())
                .claim("scope", "USER")
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AuthResponse(token, "Bearer", properties.accessTokenTtl().toSeconds(), UserProfileResponse.from(user));
    }
}
