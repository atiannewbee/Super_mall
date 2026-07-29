package com.share.spring_boot_demo1.service;

import com.share.spring_boot_demo1.common.ApiException;
import com.share.spring_boot_demo1.dto.LoginRequest;
import com.share.spring_boot_demo1.dto.RegisterRequest;
import com.share.spring_boot_demo1.dto.UpdateProfileRequest;
import com.share.spring_boot_demo1.dto.UserProfileResponse;
import com.share.spring_boot_demo1.entity.User;
import com.share.spring_boot_demo1.entity.UserStatus;
import com.share.spring_boot_demo1.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * 消费者账号注册、登录和个人资料服务。
 */
@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 注册新用户；邮箱会统一规范化，密码只保存自适应哈希。
     */
    @Transactional
    public User register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        String phone = normalizeNullable(request.phone());
        if (email == null && phone == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IDENTITY_REQUIRED", "邮箱和手机号至少填写一项");
        }
        if (email != null && userRepository.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_EXISTS", "该邮箱已注册");
        }
        if (phone != null && userRepository.existsByPhone(phone)) {
            throw new ApiException(HttpStatus.CONFLICT, "PHONE_EXISTS", "该手机号已注册");
        }
        User user = new User(
                request.name().trim(), email, phone, passwordEncoder.encode(request.password())
        );
        return userRepository.save(user);
    }

    /**
     * 校验邮箱、密码与账号状态，失败时返回统一认证错误，避免枚举账号。
     */
    @Transactional(readOnly = true)
    public User authenticate(LoginRequest request) {
        String account = request.account().trim();
        User user = account.contains("@")
                ? userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(account.toLowerCase(Locale.ROOT)).orElse(null)
                : userRepository.findByPhoneAndDeletedAtIsNull(account).orElse(null);
        if (user == null || user.getPasswordHash() == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "账号或密码错误");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_UNAVAILABLE", "账号当前不可用");
        }
        return user;
    }

    /**
     * 查询有效用户；软删除或不存在统一按未找到处理。
     */
    @Transactional(readOnly = true)
    public User getRequired(long userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "登录用户不存在"));
    }

    /**
     * 返回脱敏后的当前用户资料。
     */
    @Transactional(readOnly = true)
    public UserProfileResponse profile(long userId) {
        return UserProfileResponse.from(getRequired(userId));
    }

    /**
     * 更新允许用户自行维护的资料字段。
     */
    @Transactional
    public UserProfileResponse updateProfile(long userId, UpdateProfileRequest request) {
        User user = getRequired(userId);
        String phone = normalizeNullable(request.phone());
        if (user.getEmail() == null && phone == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IDENTITY_REQUIRED", "手机号是当前账号唯一登录方式，不能清空");
        }
        if (phone != null && !phone.equals(user.getPhone()) && userRepository.existsByPhone(phone)) {
            throw new ApiException(HttpStatus.CONFLICT, "PHONE_EXISTS", "该手机号已被使用");
        }
        user.updateProfile(
                request.name().trim(), phone, normalizeNullable(request.avatarUrl()), request.birthday(), request.gender()
        );
        return UserProfileResponse.from(user);
    }

    private static String normalizeEmail(String email) {
        String normalized = normalizeNullable(email);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private static String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
