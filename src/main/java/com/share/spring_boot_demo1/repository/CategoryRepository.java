package com.share.spring_boot_demo1.repository;

import com.share.spring_boot_demo1.entity.Category;
import com.share.spring_boot_demo1.entity.CategoryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 分类 JPA 仓储。
 */
@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findByParentIdIsNullAndStatusAndDeletedAtIsNullOrderBySortOrderAsc(CategoryStatus status);
}
