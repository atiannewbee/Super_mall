package com.share.spring_boot_demo1.dto;

import com.share.spring_boot_demo1.entity.Gender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 当前消费者可自行更新的个人资料。
 */
public record UpdateProfileRequest(
        @NotBlank(message = "昵称不能为空")
        @Size(max = 50, message = "昵称不能超过50个字符") String name,

        @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确") String phone,

        @Size(max = 500, message = "头像地址不能超过500个字符") String avatarUrl,
        LocalDate birthday,
        Gender gender
) {
}
