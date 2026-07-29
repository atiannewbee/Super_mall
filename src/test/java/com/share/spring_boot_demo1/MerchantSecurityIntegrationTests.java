package com.share.spring_boot_demo1;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证商家登录、消费者/商家令牌隔离、账号锁定与改密撤销令牌。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MerchantSecurityIntegrationTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void merchantLoginUsesDedicatedIdentityAndTokenBoundary() throws Exception {
        String email = "merchant-security-" + System.nanoTime() + "@example.com";
        createMerchantUser(email, "MerchantPass123!", "OWNER", "WAREHOUSE");

        JsonNode login = body(mockMvc.perform(post("/api/merchant/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"email":"%s","password":"MerchantPass123!"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.merchantCode").value("SUPER_MALL"))
                .andExpect(jsonPath("$.user.roles[0]").exists())
                .andReturn());
        String merchantToken = login.get("accessToken").textValue();

        String hash = jdbc.queryForObject(
                "SELECT password_hash FROM merchant_users WHERE email = ?", String.class, email);
        assertThat(hash).startsWith("{bcrypt}").doesNotContain("MerchantPass123!");

        mockMvc.perform(get("/api/merchant/me").header("Authorization", bearer(merchantToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));

        mockMvc.perform(get("/api/cart").header("Authorization", bearer(merchantToken)))
                .andExpect(status().isUnauthorized());

        String customerToken = registerCustomer();
        mockMvc.perform(get("/api/merchant/me").header("Authorization", bearer(customerToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void lockingMerchantAccountImmediatelyRevokesExistingToken() throws Exception {
        String email = "merchant-locked-" + System.nanoTime() + "@example.com";
        createMerchantUser(email, "MerchantPass123!", "WAREHOUSE");
        String token = body(mockMvc.perform(post("/api/merchant/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"email":"%s","password":"MerchantPass123!"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn()).get("accessToken").textValue();

        jdbc.update("UPDATE merchant_users SET status = 'LOCKED' WHERE email = ?", email);

        mockMvc.perform(get("/api/merchant/me").header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changingPasswordRevokesOldTokenAndClearsBootstrapFlag() throws Exception {
        String email = "merchant-password-" + System.nanoTime() + "@example.com";
        createMerchantUser(email, "MerchantPass123!", "OWNER");
        jdbc.update("UPDATE merchant_users SET force_password_change = TRUE WHERE email = ?", email);
        String token = body(mockMvc.perform(post("/api/merchant/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"email":"%s","password":"MerchantPass123!"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.forcePasswordChange").value(true))
                .andReturn()).get("accessToken").textValue();

        mockMvc.perform(post("/api/merchant/me/password")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"currentPassword":"MerchantPass123!","newPassword":"NewMerchantPass456!"}
                                """))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/merchant/me").header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/merchant/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"email":"%s","password":"MerchantPass123!"}
                                """.formatted(email)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/merchant/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"email":"%s","password":"NewMerchantPass456!"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.forcePasswordChange").value(false));
    }

    private void createMerchantUser(String email, String password, String... roles) {
        long merchantId = jdbc.queryForObject(
                "SELECT id FROM merchants WHERE code = 'SUPER_MALL'", Long.class);
        jdbc.update("""
                INSERT INTO merchant_users
                    (merchant_id, email, name, password_hash, status, force_password_change)
                VALUES (?, ?, '商家安全测试', ?, 'ACTIVE', FALSE)
                """, merchantId, email, passwordEncoder.encode(password));
        long userId = jdbc.queryForObject(
                "SELECT id FROM merchant_users WHERE email = ?", Long.class, email);
        for (String role : roles) {
            jdbc.update(
                    "INSERT INTO merchant_user_roles (merchant_user_id, role_code) VALUES (?, ?)",
                    userId, role
            );
        }
    }

    private String registerCustomer() throws Exception {
        String email = "customer-boundary-" + System.nanoTime() + "@example.com";
        return body(mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("""
                                {"name":"消费者边界测试","email":"%s","password":"SecurePass123"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn()).get("accessToken").textValue();
    }

    private JsonNode body(org.springframework.test.web.servlet.MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
