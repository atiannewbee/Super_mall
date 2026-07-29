package com.share.spring_boot_demo1.repository;

import com.share.spring_boot_demo1.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 消费者账号 JPA 仓储；常规查询默认排除软删除记录。
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailIgnoreCaseAndDeletedAtIsNull(String email);
    Optional<User> findByPhoneAndDeletedAtIsNull(String phone);
    Optional<User> findByIdAndDeletedAtIsNull(Long id);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByPhone(String phone);
}
