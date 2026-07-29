package com.share.spring_boot_demo1.repository;

import com.share.spring_boot_demo1.common.ApiException;
import com.share.spring_boot_demo1.common.PageResponse;
import com.share.spring_boot_demo1.dto.BrandResponse;
import com.share.spring_boot_demo1.dto.ProductResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品目录聚合查询仓储。
 *
 * <p>先分页查询商品主记录，再批量装配图片、卖点、SKU 与规格选项，
 * 避免为每个商品执行多次查询。所有筛选值通过命名参数绑定，排序字段只允许白名单映射。</p>
 */
@Repository
public class CatalogQueryRepository {
    /** 前端排序标识到可信 SQL 片段的白名单，禁止直接拼接用户输入。 */
    private static final Map<String, String> SORTS = Map.of(
            "recommended", "p.sort_order ASC, p.id ASC",
            "newest", "p.published_at DESC, p.id DESC",
            "price-asc", "p.base_price ASC, p.id ASC",
            "price-desc", "p.base_price DESC, p.id ASC",
            "sales", "p.sold_count DESC, p.id ASC",
            "rating", "p.rating DESC, p.review_count DESC"
    );

    private final NamedParameterJdbcTemplate jdbc;

    public CatalogQueryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 查询有效品牌。
     */
    public List<BrandResponse> findBrands() {
        return jdbc.query("""
                SELECT id, code, name, logo_url, description
                FROM brands
                WHERE status = 'ACTIVE' AND deleted_at IS NULL
                ORDER BY sort_order, id
                """, Map.of(), (rs, rowNum) -> new BrandResponse(
                rs.getLong("id"), rs.getString("code"), rs.getString("name"),
                rs.getString("logo_url"), rs.getString("description")
        ));
    }

    /**
     * 按条件分页检索商品，并批量装配商品详情所需的子资源。
     */
    public PageResponse<ProductResponse> searchProducts(
            String query, String category, String brand, Boolean featured, Boolean newArrival, Boolean deal,
            BigDecimal minPrice, BigDecimal maxPrice, String sort, int page, int size
    ) {
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        String where = buildWhere(query, category, brand, featured, newArrival, deal, minPrice, maxPrice, parameters);
        Long total = jdbc.queryForObject("""
                SELECT COUNT(*) FROM products p
                JOIN categories c ON c.id = p.category_id
                LEFT JOIN brands b ON b.id = p.brand_id
                """ + where, parameters, Long.class);

        parameters.addValue("limit", size).addValue("offset", page * size);
        String orderBy = SORTS.getOrDefault(sort, SORTS.get("recommended"));
        List<ProductBuilder> products = jdbc.query(baseProductSelect() + where + " ORDER BY " + orderBy +
                        " LIMIT :limit OFFSET :offset", parameters,
                (rs, rowNum) -> mapProduct(rs));
        hydrate(products);
        return PageResponse.of(products.stream().map(ProductBuilder::build).toList(), page, size,
                total == null ? 0 : total);
    }

    /**
     * 按数字 ID 或 slug 查询单个商品聚合。
     */
    public ProductResponse findProduct(String identifier) {
        MapSqlParameterSource parameters = new MapSqlParameterSource("identifier", identifier);
        String predicate = "p.slug = :identifier";
        try {
            parameters.addValue("numericId", Long.parseLong(identifier));
            predicate = "(p.slug = :identifier OR p.id = :numericId)";
        } catch (NumberFormatException ignored) {
            // A slug is the normal lookup key.
        }
        List<ProductBuilder> products = jdbc.query(baseProductSelect() + """
                 WHERE p.status = 'ACTIVE' AND p.deleted_at IS NULL
                   AND c.status = 'ACTIVE' AND c.deleted_at IS NULL
                """ + " AND " + predicate,
                parameters, (rs, rowNum) -> mapProduct(rs));
        if (products.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "商品不存在或已下架");
        }
        hydrate(products);
        return products.get(0).build();
    }

    /**
     * 判断商品是否处于可购买状态。
     */
    public boolean existsActiveProduct(long productId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM products
                WHERE id = :id AND status = 'ACTIVE' AND deleted_at IS NULL
                """, Map.of("id", productId), Integer.class);
        return count != null && count > 0;
    }

    /**
     * 批量查询商品并尽量保持调用方传入的 ID 顺序。
     */
    public List<ProductResponse> findProductsByIds(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return List.of();
        }
        List<ProductBuilder> products = jdbc.query(baseProductSelect() + """
                 WHERE p.status = 'ACTIVE' AND p.deleted_at IS NULL
                   AND c.status = 'ACTIVE' AND c.deleted_at IS NULL AND p.id IN (:productIds)
                """, Map.of("productIds", productIds), (rs, rowNum) -> mapProduct(rs));
        hydrate(products);
        Map<Long, ProductResponse> byId = new LinkedHashMap<>();
        products.stream().map(ProductBuilder::build).forEach(product -> byId.put(product.id(), product));
        return productIds.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
    }

    private String buildWhere(
            String query, String category, String brand, Boolean featured, Boolean newArrival, Boolean deal,
            BigDecimal minPrice, BigDecimal maxPrice, MapSqlParameterSource parameters
    ) {
        // 仅拼接代码内部定义的条件片段；实际筛选值全部放入 parameters。
        StringBuilder where = new StringBuilder("""
                 WHERE p.status = 'ACTIVE' AND p.deleted_at IS NULL
                   AND c.status = 'ACTIVE' AND c.deleted_at IS NULL
                """);
        if (query != null && !query.isBlank()) {
            where.append(" AND (p.name LIKE :query OR p.tagline LIKE :query OR p.description LIKE :query OR b.name LIKE :query)");
            parameters.addValue("query", "%" + query.trim() + "%");
        }
        if (category != null && !category.isBlank()) {
            where.append(" AND c.slug = :category");
            parameters.addValue("category", category.trim());
        }
        if (brand != null && !brand.isBlank()) {
            where.append(" AND (b.code = :brand OR b.name = :brand)");
            parameters.addValue("brand", brand.trim());
        }
        if (featured != null) {
            where.append(" AND p.is_featured = :featured");
            parameters.addValue("featured", featured);
        }
        if (newArrival != null) {
            where.append(" AND p.is_new = :newArrival");
            parameters.addValue("newArrival", newArrival);
        }
        if (deal != null) {
            where.append(" AND p.is_deal = :deal");
            parameters.addValue("deal", deal);
        }
        if (minPrice != null) {
            where.append(" AND p.base_price >= :minPrice");
            parameters.addValue("minPrice", minPrice);
        }
        if (maxPrice != null) {
            where.append(" AND p.base_price <= :maxPrice");
            parameters.addValue("maxPrice", maxPrice);
        }
        return where.toString();
    }

    private String baseProductSelect() {
        return """
                SELECT p.id, p.slug, p.name, b.name AS brand_name, c.slug AS category_slug,
                       p.tagline, p.description, p.base_price, p.original_price, p.rating,
                       p.review_count, p.sold_count, p.badge, p.accent_color,
                       p.is_featured, p.is_new, p.is_deal
                FROM products p
                JOIN categories c ON c.id = p.category_id
                LEFT JOIN brands b ON b.id = p.brand_id
                """;
    }

    private ProductBuilder mapProduct(ResultSet rs) throws SQLException {
        return new ProductBuilder(
                rs.getLong("id"), rs.getString("slug"), rs.getString("name"), rs.getString("brand_name"),
                rs.getString("category_slug"), rs.getString("tagline"), rs.getString("description"),
                rs.getBigDecimal("base_price"), rs.getBigDecimal("original_price"), rs.getBigDecimal("rating"),
                rs.getLong("review_count"), rs.getLong("sold_count"), rs.getString("badge"),
                rs.getString("accent_color"), rs.getBoolean("is_featured"), rs.getBoolean("is_new"),
                rs.getBoolean("is_deal")
        );
    }

    private void hydrate(List<ProductBuilder> products) {
        if (products.isEmpty()) {
            return;
        }
        // 以一次主查询加少量批量子查询装配聚合，避免典型的 N+1 读取问题。
        Map<Long, ProductBuilder> byId = new LinkedHashMap<>();
        products.forEach(product -> byId.put(product.id, product));
        Map<String, Object> parameters = Map.of("productIds", byId.keySet());

        jdbc.query("""
                SELECT product_id, image_url, image_type FROM product_images
                WHERE product_id IN (:productIds) ORDER BY product_id, sort_order, id
                """, parameters, rs -> {
            ProductBuilder product = byId.get(rs.getLong("product_id"));
            String image = rs.getString("image_url");
            product.gallery.add(image);
            if (product.image == null || "COVER".equals(rs.getString("image_type"))) {
                product.image = image;
            }
        });
        jdbc.query("""
                SELECT product_id, content FROM product_features
                WHERE product_id IN (:productIds) ORDER BY product_id, sort_order, id
                """, parameters, (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                byId.get(rs.getLong("product_id")).features.add(rs.getString("content")));

        Map<Long, SkuBuilder> skus = new LinkedHashMap<>();
        jdbc.query("""
                SELECT s.id, s.product_id, s.sku_code, s.label, s.price, s.original_price,
                       COALESCE(i.available_quantity, 0) AS stock
                FROM product_skus s
                LEFT JOIN sku_inventory i ON i.sku_id = s.id
                WHERE s.product_id IN (:productIds) AND s.status = 'ACTIVE' AND s.deleted_at IS NULL
                ORDER BY s.product_id, s.sort_order, s.id
                """, parameters, rs -> {
            SkuBuilder sku = new SkuBuilder(rs.getLong("id"), rs.getString("sku_code"),
                    rs.getString("label"), rs.getBigDecimal("price"), rs.getBigDecimal("original_price"),
                    rs.getInt("stock"));
            skus.put(sku.id, sku);
            byId.get(rs.getLong("product_id")).skus.add(sku);
        });
        if (!skus.isEmpty()) {
            jdbc.query("""
                    SELECT sav.sku_id, pa.name, pav.value
                    FROM sku_attribute_values sav
                    JOIN product_attribute_values pav ON pav.id = sav.attribute_value_id
                    JOIN product_attributes pa ON pa.id = pav.attribute_id
                    WHERE sav.sku_id IN (:skuIds)
                    ORDER BY sav.sku_id, pa.sort_order, pav.sort_order
                    """, Map.of("skuIds", skus.keySet()), (org.springframework.jdbc.core.RowCallbackHandler) rs ->
                    skus.get(rs.getLong("sku_id")).options.put(rs.getString("name"), rs.getString("value")));
        }
    }

    private static final class ProductBuilder {
        private final long id;
        private final String slug;
        private final String name;
        private final String brand;
        private final String category;
        private final String tagline;
        private final String description;
        private final BigDecimal price;
        private final BigDecimal originalPrice;
        private final BigDecimal rating;
        private final long reviewCount;
        private final long soldCount;
        private final String badge;
        private final String accent;
        private final boolean featured;
        private final boolean newArrival;
        private final boolean deal;
        private String image;
        private final List<String> gallery = new ArrayList<>();
        private final List<String> features = new ArrayList<>();
        private final List<SkuBuilder> skus = new ArrayList<>();

        private ProductBuilder(long id, String slug, String name, String brand, String category, String tagline,
                               String description, BigDecimal price, BigDecimal originalPrice, BigDecimal rating,
                               long reviewCount, long soldCount, String badge, String accent, boolean featured,
                               boolean newArrival, boolean deal) {
            this.id = id;
            this.slug = slug;
            this.name = name;
            this.brand = brand;
            this.category = category;
            this.tagline = tagline;
            this.description = description;
            this.price = price;
            this.originalPrice = originalPrice;
            this.rating = rating;
            this.reviewCount = reviewCount;
            this.soldCount = soldCount;
            this.badge = badge;
            this.accent = accent;
            this.featured = featured;
            this.newArrival = newArrival;
            this.deal = deal;
        }

        private ProductResponse build() {
            return new ProductResponse(id, slug, name, brand, category, tagline, description, price, originalPrice,
                    rating, reviewCount, soldCount, badge, accent, image, gallery, features, featured, newArrival,
                    deal, skus.stream().map(SkuBuilder::build).toList());
        }
    }

    private static final class SkuBuilder {
        private final long id;
        private final String code;
        private final String label;
        private final BigDecimal price;
        private final BigDecimal originalPrice;
        private final int stock;
        private final Map<String, String> options = new LinkedHashMap<>();

        private SkuBuilder(long id, String code, String label, BigDecimal price, BigDecimal originalPrice, int stock) {
            this.id = id;
            this.code = code;
            this.label = label;
            this.price = price;
            this.originalPrice = originalPrice;
            this.stock = stock;
        }

        private ProductResponse.Sku build() {
            return new ProductResponse.Sku(id, code, label, price, originalPrice, stock, options);
        }
    }
}
