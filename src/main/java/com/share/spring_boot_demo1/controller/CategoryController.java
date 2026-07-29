package com.share.spring_boot_demo1.controller;

import com.share.spring_boot_demo1.dto.CategoryResponse;
import com.share.spring_boot_demo1.service.CategoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 面向前端导航的公开分类树接口。
 */
@RestController
@RequestMapping("/api/categories")
public class CategoryController {
    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public List<CategoryResponse> list() {
        return categoryService.getVisibleRootCategories();
    }
}
