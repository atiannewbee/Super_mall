package com.share.spring_boot_demo1.dto;

import com.share.spring_boot_demo1.entity.Category;

/**
 * 可直接用于前端导航的递归分类树节点。
 */
public record CategoryResponse(
        Long id,
        Long parentId,
        String slug,
        String name,
        String icon,
        String description
) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getParentId(),
                category.getSlug(),
                category.getName(),
                category.getIcon(),
                category.getDescription()
        );
    }

}
