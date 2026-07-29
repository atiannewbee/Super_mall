package com.share.spring_boot_demo1.service;

import com.share.spring_boot_demo1.common.ApiException;
import com.share.spring_boot_demo1.dto.MerchantLoginRequest;
import com.share.spring_boot_demo1.dto.MerchantPasswordChangeRequest;
import com.share.spring_boot_demo1.dto.MerchantProfileResponse;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * 商家员工认证与账号资料服务。
 *
 * <p>商家账号不与消费者用户表混用。认证成功后同时返回员工信息、所属商户和角色，
 * 后续接口必须同时校验 userId 与 merchantId，防止员工跨商户访问数据。</p>
 */
@Service
public class MerchantAuthService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;

    public MerchantAuthService(JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 校验商家员工密码、员工状态、商户状态和角色，并记录最后登录时间。
     */
    @Transactional
    public AuthenticatedMerchant authenticate(MerchantLoginRequest request) {
        MerchantAccount account = findByEmail(request.email().trim().toLowerCase(Locale.ROOT));
        if (account == null || !passwordEncoder.matches(request.password(), account.passwordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "账号或密码错误");
        }
        ensureAvailable(account);
        List<String> roles = roles(account.id());
        if (roles.isEmpty()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "MERCHANT_ROLE_REQUIRED", "商家账号尚未分配权限");
        }
        jdbc.update("UPDATE merchant_users SET last_login_at = CURRENT_TIMESTAMP(3) WHERE id = ?", account.id());
        return new AuthenticatedMerchant(account, roles);
    }

    /**
     * 查询当前商家员工资料，并再次确认令牌商户范围与数据库一致。
     */
    @Transactional(readOnly = true)
    public MerchantProfileResponse profile(long userId, long merchantId) {
        MerchantAccount account = findById(userId);
        if (account == null || account.merchantId() != merchantId) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "MERCHANT_USER_NOT_FOUND", "商家登录用户不存在");
        }
        ensureAvailable(account);
        return toProfile(account, roles(account.id()));
    }

    public MerchantProfileResponse toProfile(AuthenticatedMerchant authenticated) {
        return toProfile(authenticated.account(), authenticated.roles());
    }

    /**
     * 修改当前商家员工密码。
     *
     * <p>更新 tokenVersion 会使所有旧令牌在下一次请求时立即失效，调用方需要重新登录。</p>
     */
    @Transactional
    public void changePassword(long userId, long merchantId, MerchantPasswordChangeRequest request) {
        List<String> hashes = jdbc.queryForList("""
                SELECT password_hash
                FROM merchant_users
                WHERE id = ?
                  AND merchant_id = ?
                  AND status = 'ACTIVE'
                  AND deleted_at IS NULL
                FOR UPDATE
                """, String.class, userId, merchantId);
        if (hashes.isEmpty()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "MERCHANT_USER_NOT_FOUND", "商家登录用户不存在");
        }
        String currentHash = hashes.get(0);
        if (!passwordEncoder.matches(request.currentPassword(), currentHash)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CURRENT_PASSWORD", "当前密码不正确");
        }
        if (passwordEncoder.matches(request.newPassword(), currentHash)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_UNCHANGED", "新密码不能与当前密码相同");
        }
        // 密码哈希、首次改密标记和令牌版本必须在同一事务内更新。
        jdbc.update("""
                UPDATE merchant_users
                SET password_hash = ?,
                    force_password_change = FALSE,
                    token_version = token_version + 1
                WHERE id = ? AND merchant_id = ?
                """, passwordEncoder.encode(request.newPassword()), userId, merchantId);
    }

    private MerchantProfileResponse toProfile(MerchantAccount account, List<String> roles) {
        return new MerchantProfileResponse(
                account.id(),
                account.merchantId(),
                account.merchantCode(),
                account.merchantName(),
                account.email(),
                account.name(),
                roles,
                account.forcePasswordChange()
        );
    }

    private MerchantAccount findByEmail(String email) {
        return queryOne("""
                SELECT merchant_user.id,
                       merchant_user.merchant_id,
                       merchant.code AS merchant_code,
                       merchant.name AS merchant_name,
                       merchant.status AS merchant_status,
                       merchant_user.email,
                       merchant_user.name,
                       merchant_user.password_hash,
                       merchant_user.status,
                       merchant_user.token_version,
                       merchant_user.force_password_change
                FROM merchant_users merchant_user
                JOIN merchants merchant ON merchant.id = merchant_user.merchant_id
                WHERE LOWER(merchant_user.email) = ?
                  AND merchant_user.deleted_at IS NULL
                """, email);
    }

    private MerchantAccount findById(long userId) {
        return queryOne("""
                SELECT merchant_user.id,
                       merchant_user.merchant_id,
                       merchant.code AS merchant_code,
                       merchant.name AS merchant_name,
                       merchant.status AS merchant_status,
                       merchant_user.email,
                       merchant_user.name,
                       merchant_user.password_hash,
                       merchant_user.status,
                       merchant_user.token_version,
                       merchant_user.force_password_change
                FROM merchant_users merchant_user
                JOIN merchants merchant ON merchant.id = merchant_user.merchant_id
                WHERE merchant_user.id = ?
                  AND merchant_user.deleted_at IS NULL
                """, userId);
    }

    private MerchantAccount queryOne(String sql, Object parameter) {
        try {
            return jdbc.queryForObject(sql, (result, rowNumber) -> new MerchantAccount(
                    result.getLong("id"),
                    result.getLong("merchant_id"),
                    result.getString("merchant_code"),
                    result.getString("merchant_name"),
                    result.getString("merchant_status"),
                    result.getString("email"),
                    result.getString("name"),
                    result.getString("password_hash"),
                    result.getString("status"),
                    result.getInt("token_version"),
                    result.getBoolean("force_password_change")
            ), parameter);
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    private List<String> roles(long userId) {
        return jdbc.queryForList("""
                SELECT role_code
                FROM merchant_user_roles
                WHERE merchant_user_id = ?
                ORDER BY role_code
                """, String.class, userId);
    }

    private void ensureAvailable(MerchantAccount account) {
        if (!"ACTIVE".equals(account.status()) || !"ACTIVE".equals(account.merchantStatus())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_UNAVAILABLE", "商家账号当前不可用");
        }
    }

    /**
     * 已完成身份与角色校验的商家登录结果。
     */
    public record AuthenticatedMerchant(MerchantAccount account, List<String> roles) {
        public AuthenticatedMerchant {
            roles = List.copyOf(roles);
        }
    }

    /**
     * 商家员工与所属商户的内部数据库快照，不直接暴露给 Controller。
     */
    public record MerchantAccount(
            long id,
            long merchantId,
            String merchantCode,
            String merchantName,
            String merchantStatus,
            String email,
            String name,
            String passwordHash,
            String status,
            int tokenVersion,
            boolean forcePasswordChange
    ) {
    }
}
