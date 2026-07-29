package com.share.spring_boot_demo1.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * 消费者端 JWT 与跨域配置。
 */
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
        String jwtSecret,
        String jwtIssuer,
        String jwtAudience,
        Duration accessTokenTtl,
        List<String> corsAllowedOrigins
) {
    public SecurityProperties {
        corsAllowedOrigins = corsAllowedOrigins == null ? List.of() : List.copyOf(corsAllowedOrigins);
    }
}
