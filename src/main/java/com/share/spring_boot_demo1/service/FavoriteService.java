package com.share.spring_boot_demo1.service;

import com.share.spring_boot_demo1.common.ApiException;
import com.share.spring_boot_demo1.common.PageResponse;
import com.share.spring_boot_demo1.dto.ProductResponse;
import com.share.spring_boot_demo1.repository.CatalogQueryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 商品收藏服务；所有读写都以 userId 限定数据归属。
 */
@Service
public class FavoriteService {
    private final NamedParameterJdbcTemplate jdbc;
    private final CatalogQueryRepository catalog;

    public FavoriteService(NamedParameterJdbcTemplate jdbc, CatalogQueryRepository catalog) {
        this.jdbc = jdbc;
        this.catalog = catalog;
    }

    /**
     * 分页返回当前用户收藏的有效商品。
     */
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> list(long userId, int page, int size) {
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM product_favorites WHERE user_id = :userId",
                Map.of("userId", userId), Long.class);
        List<Long> ids = jdbc.queryForList("""
                SELECT product_id FROM product_favorites
                WHERE user_id = :userId ORDER BY created_at DESC LIMIT :limit OFFSET :offset
                """, Map.of("userId", userId, "limit", size, "offset", page * size), Long.class);
        return PageResponse.of(catalog.findProductsByIds(ids), page, size, total == null ? 0 : total);
    }

    /**
     * 幂等添加收藏，重复请求不会生成重复记录。
     */
    @Transactional
    public void add(long userId, long productId) {
        if (!catalog.existsActiveProduct(productId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "商品不存在或已下架");
        }
        jdbc.update("""
                INSERT IGNORE INTO product_favorites (user_id, product_id) VALUES (:userId, :productId)
                """, Map.of("userId", userId, "productId", productId));
    }

    /**
     * 移除收藏；目标不存在时同样按幂等成功处理。
     */
    @Transactional
    public void remove(long userId, long productId) {
        jdbc.update("DELETE FROM product_favorites WHERE user_id = :userId AND product_id = :productId",
                Map.of("userId", userId, "productId", productId));
    }
}
