package com.share.spring_boot_demo1.dto;

import com.share.spring_boot_demo1.entity.User;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 消费者公开资料响应，不包含密码哈希和内部账号字段。
 */
public record UserProfileResponse(
        Long id,
        String name,
        String email,
        String phone,
        String avatarUrl,
        LocalDate birthday,
        String gender,
        LocalDateTime createdAt
) {
    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                user.getId(), user.getName(), user.getEmail(), user.getPhone(), user.getAvatarUrl(),
                user.getBirthday(), user.getGender().name(), user.getCreatedAt()
        );
    }
}
