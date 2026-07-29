package com.share.spring_boot_demo1.service;

import com.share.spring_boot_demo1.dto.CategoryResponse;
import com.share.spring_boot_demo1.entity.Category;
import com.share.spring_boot_demo1.entity.CategoryStatus;
import com.share.spring_boot_demo1.repository.CategoryRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 分类查询服务，向前端返回已启用的树形导航。
 */
@Service
@Transactional(readOnly = true)
public class CategoryService {
    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    /**
     * 查询可见根分类并递归转换子分类 DTO。
     */
    public List<CategoryResponse> getVisibleRootCategories(){
        return categoryRepository
                .findByParentIdIsNullAndStatusAndDeletedAtIsNullOrderBySortOrderAsc(
                        CategoryStatus.ACTIVE
                ).stream()
                .map(CategoryResponse::from)
                .toList();
    }


}
