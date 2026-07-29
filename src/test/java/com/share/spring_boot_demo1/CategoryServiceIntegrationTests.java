package com.share.spring_boot_demo1;


import com.share.spring_boot_demo1.dto.CategoryResponse;
import com.share.spring_boot_demo1.service.CategoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.share.spring_boot_demo1.entity.Category;

import java.util.List;

import  static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证可见根分类和递归 DTO 转换。
 */
@SpringBootTest
public class CategoryServiceIntegrationTests {
    private final CategoryService categoryService;

    @Autowired
    CategoryServiceIntegrationTests(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @Test
    void returnVisibleRootCategoriesInSortOrder() {
        List<CategoryResponse> categories =
                categoryService.getVisibleRootCategories();
        assertThat(categories).hasSize(5);

        assertThat(categories)
                .extracting(CategoryResponse::slug)
                .containsExactly(
                        "phones",
                        "computers",
                        "audio",
                        "smart-home",
                        "accessories"
                );

    }
}
