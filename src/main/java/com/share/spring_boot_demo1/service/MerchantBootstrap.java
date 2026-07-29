package com.share.spring_boot_demo1.service;

import com.share.spring_boot_demo1.security.MerchantSecurityProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * 从运行环境安全创建首个商家主管账号。
 *
 * <p>数据库迁移只创建商户、角色和仓库，不包含明文或固定密码。
 * 当系统已存在主管账号时，本组件不会再创建第二个账号。</p>
 */
@Component
public class MerchantBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(MerchantBootstrap.class);

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final MerchantSecurityProperties properties;

    public MerchantBootstrap(
            JdbcTemplate jdbc,
            PasswordEncoder passwordEncoder,
            MerchantSecurityProperties properties
    ) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    /**
     * 应用启动后幂等执行首次建号；未配置 bootstrap 参数时直接跳过。
     */
    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        String email = trimToNull(properties.bootstrapEmail());
        String password = trimToNull(properties.bootstrapPassword());
        if (email == null && password == null) {
            return;
        }
        if (email == null || password == null || password.length() < 12) {
            throw new IllegalStateException(
                    "MERCHANT_BOOTSTRAP_EMAIL and a 12+ character MERCHANT_BOOTSTRAP_PASSWORD are both required");
        }
        String normalizedEmail = email.toLowerCase(Locale.ROOT);
        String name = trimToNull(properties.bootstrapName());
        List<Long> existingBootstrapUsers = jdbc.queryForList("""
                SELECT id
                FROM merchant_users
                WHERE email = ? AND deleted_at IS NULL
                """, Long.class, normalizedEmail);
        if (!existingBootstrapUsers.isEmpty()) {
            // 仅在尚未完成首次改密时同步展示名称，避免启动配置覆盖用户后续修改。
            jdbc.update("""
                    UPDATE merchant_users
                    SET name = ?
                    WHERE id = ? AND force_password_change = TRUE
                    """, name == null ? "商家主管" : name, existingBootstrapUsers.get(0));
            return;
        }
        Integer existingOwners = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM merchant_users merchant_user
                JOIN merchant_user_roles role_record ON role_record.merchant_user_id = merchant_user.id
                WHERE role_record.role_code = 'OWNER'
                  AND merchant_user.deleted_at IS NULL
                """, Integer.class);
        if (existingOwners != null && existingOwners > 0) {
            return;
        }
        Long merchantId = jdbc.queryForObject(
                "SELECT id FROM merchants WHERE code = 'SUPER_MALL' AND status = 'ACTIVE'",
                Long.class
        );
        // 初始账号同时拥有三个角色，首次登录必须改密后才能进入稳定使用状态。
        jdbc.update("""
                INSERT INTO merchant_users
                    (merchant_id, email, name, password_hash, status, force_password_change)
                VALUES (?, ?, ?, ?, 'ACTIVE', TRUE)
                """,
                merchantId,
                normalizedEmail,
                name == null ? "商家主管" : name,
                passwordEncoder.encode(password)
        );
        Long userId = jdbc.queryForObject(
                "SELECT id FROM merchant_users WHERE email = ?",
                Long.class,
                normalizedEmail
        );
        jdbc.update("""
                INSERT INTO merchant_user_roles (merchant_user_id, role_code)
                VALUES (?, 'OWNER'), (?, 'OPERATOR'), (?, 'WAREHOUSE')
                """, userId, userId, userId);
        log.info("Created the initial merchant owner account for {}", normalizedEmail);
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
