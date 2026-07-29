package com.share.spring_boot_demo1.security;

import com.share.spring_boot_demo1.entity.UserStatus;
import com.share.spring_boot_demo1.repository.UserRepository;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 配置消费者端与商家端两套彼此隔离的无状态安全链。
 *
 * <p>过滤器链的顺序是安全边界的一部分：商家路径必须先由专用链匹配，
 * 否则可能落入消费者端的默认链。两端分别使用独立密钥和 JWT 校验器，
 * 即使令牌声明结构相似也不能跨端复用。</p>
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    SecretKey jwtSecretKey(SecurityProperties properties) {
        return secretKey(properties.jwtSecret(), "JWT_SECRET");
    }

    @Bean
    SecretKey merchantJwtSecretKey(MerchantSecurityProperties properties) {
        return secretKey(properties.jwtSecret(), "MERCHANT_JWT_SECRET");
    }

    @Bean
    JwtEncoder jwtEncoder(@Qualifier("jwtSecretKey") SecretKey key) {
        return NimbusJwtEncoder.withSecretKey(key)
                .algorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    JwtEncoder merchantJwtEncoder(@Qualifier("merchantJwtSecretKey") SecretKey key) {
        return NimbusJwtEncoder.withSecretKey(key)
                .algorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    JwtDecoder jwtDecoder(
            @Qualifier("jwtSecretKey") SecretKey key,
            SecurityProperties properties,
            UserRepository userRepository
    ) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        OAuth2TokenValidator<Jwt> issuer = JwtValidators.createDefaultWithIssuer(properties.jwtIssuer());
        OAuth2TokenValidator<Jwt> audience = token -> token.getAudience().contains(properties.jwtAudience())
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid audience", null));
        // JWT 签名有效不代表账号仍可用；每次请求都查询账号状态，以支持封禁立即生效。
        OAuth2TokenValidator<Jwt> activeUser = token -> {
            try {
                long userId = Long.parseLong(token.getSubject());
                boolean active = userRepository.findByIdAndDeletedAtIsNull(userId)
                        .map(user -> user.getStatus() == UserStatus.ACTIVE)
                        .orElse(false);
                return active ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(
                                new OAuth2Error("invalid_token", "User account is unavailable", null));
            } catch (NumberFormatException exception) {
                return OAuth2TokenValidatorResult.failure(
                        new OAuth2Error("invalid_token", "Invalid subject", null));
            }
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuer, audience, activeUser));
        return decoder;
    }

    @Bean
    JwtDecoder merchantJwtDecoder(
            @Qualifier("merchantJwtSecretKey") SecretKey key,
            MerchantSecurityProperties properties,
            JdbcTemplate jdbc
    ) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        OAuth2TokenValidator<Jwt> issuer = JwtValidators.createDefaultWithIssuer(properties.jwtIssuer());
        OAuth2TokenValidator<Jwt> audience = token -> token.getAudience().contains(properties.jwtAudience())
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid audience", null));
        // tokenVersion 在改密或管理员撤权时递增，使此前签发的商家令牌立即失效。
        OAuth2TokenValidator<Jwt> activeMerchantUser = token -> {
            try {
                long userId = Long.parseLong(token.getSubject());
                Object merchantIdClaim = token.getClaim("merchant_id");
                Object tokenVersionClaim = token.getClaim("token_version");
                long merchantId = Long.parseLong(String.valueOf(merchantIdClaim));
                int tokenVersion = Integer.parseInt(String.valueOf(tokenVersionClaim));
                Integer count = jdbc.queryForObject("""
                        SELECT COUNT(*)
                        FROM merchant_users merchant_user
                        JOIN merchants merchant ON merchant.id = merchant_user.merchant_id
                        WHERE merchant_user.id = ?
                          AND merchant_user.merchant_id = ?
                          AND merchant_user.token_version = ?
                          AND merchant_user.status = 'ACTIVE'
                          AND merchant.status = 'ACTIVE'
                          AND merchant_user.deleted_at IS NULL
                        """, Integer.class, userId, merchantId, tokenVersion);
                return count != null && count == 1
                        ? OAuth2TokenValidatorResult.success()
                        : invalidToken("Merchant account is unavailable");
            } catch (RuntimeException exception) {
                return invalidToken("Invalid merchant identity");
            }
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuer, audience, activeMerchantUser));
        return decoder;
    }

    /**
     * 商家接口专用过滤器链，必须先于消费者默认链执行。
     */
    @Bean
    @Order(1)
    SecurityFilterChain merchantSecurityFilterChain(
            HttpSecurity http,
            ApiAuthenticationEntryPoint authenticationEntryPoint,
            ApiAccessDeniedHandler accessDeniedHandler,
            @Qualifier("merchantJwtDecoder") JwtDecoder merchantJwtDecoder
    ) throws Exception {
        http
                .securityMatcher("/api/merchant/**")
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/merchant/auth/login").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.decoder(merchantJwtDecoder))
                        .authenticationEntryPoint(authenticationEntryPoint)
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                );
        return http.build();
    }

    /**
     * 消费者接口过滤器链；未被商家链匹配的请求最终进入这里。
     */
    @Bean
    @Order(2)
    SecurityFilterChain customerSecurityFilterChain(
            HttpSecurity http,
            ApiAuthenticationEntryPoint authenticationEntryPoint,
            ApiAccessDeniedHandler accessDeniedHandler,
            @Qualifier("jwtDecoder") JwtDecoder jwtDecoder
    ) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/auth/register", "/api/auth/login", "/actuator/health", "/error").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/payments/alipay/notify").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/payments/alipay/*/launch").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/categories/**", "/api/brands/**", "/api/products/**").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.decoder(jwtDecoder))
                        .authenticationEntryPoint(authenticationEntryPoint)
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                );
        return http.build();
    }

    private static SecretKey secretKey(String secret, String environmentName) {
        // HS256 的共享密钥至少要求 32 字节，按 UTF-8 字节数而不是 Java 字符数校验。
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(environmentName + " must contain at least 32 UTF-8 bytes");
        }
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    private static OAuth2TokenValidatorResult invalidToken(String description) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", description, null));
    }

    /**
     * CORS 仅开放前端实际使用的请求头；JWT 不使用 Cookie，因此禁止携带凭据。
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(SecurityProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.corsAllowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key"));
        configuration.setExposedHeaders(List.of("Location"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
