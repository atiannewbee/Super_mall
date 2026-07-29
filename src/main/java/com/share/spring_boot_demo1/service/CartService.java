package com.share.spring_boot_demo1.service;

import com.share.spring_boot_demo1.common.ApiException;
import com.share.spring_boot_demo1.dto.CartItemRequest;
import com.share.spring_boot_demo1.dto.CartItemUpdateRequest;
import com.share.spring_boot_demo1.dto.CartResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 购物车业务服务。
 *
 * <p>写操作会锁定 SKU 库存记录，但不会提前占用库存；真正的库存预占发生在创建订单时。</p>
 */
@Service
public class CartService {
    private final NamedParameterJdbcTemplate jdbc;

    public CartService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 返回购物车明细与服务端计算的金额汇总。
     */
    @Transactional(readOnly = true)
    public CartResponse get(long userId) {
        List<CartResponse.Item> items = jdbc.query("""
                SELECT ci.id, p.id AS product_id, p.slug, p.name, cover.image_url, s.id AS sku_id,
                       s.sku_code, s.label, s.price, COALESCE(i.available_quantity, 0) AS stock,
                       ci.quantity, ci.selected
                FROM shopping_carts c
                JOIN shopping_cart_items ci ON ci.cart_id = c.id
                JOIN product_skus s ON s.id = ci.sku_id
                JOIN products p ON p.id = s.product_id
                LEFT JOIN product_images cover ON cover.id = (
                    SELECT pi.id FROM product_images pi WHERE pi.product_id = p.id
                    ORDER BY (pi.image_type = 'COVER') DESC, pi.sort_order, pi.id LIMIT 1)
                LEFT JOIN sku_inventory i ON i.sku_id = s.id
                WHERE c.user_id = :userId
                ORDER BY ci.updated_at DESC, ci.id DESC
                """, Map.of("userId", userId), (rs, rowNum) -> {
            BigDecimal price = rs.getBigDecimal("price");
            int quantity = rs.getInt("quantity");
            return new CartResponse.Item(rs.getLong("id"), rs.getLong("product_id"), rs.getString("slug"),
                    rs.getString("name"), rs.getString("image_url"), rs.getLong("sku_id"),
                    rs.getString("sku_code"), rs.getString("label"), price, rs.getInt("stock"), quantity,
                    rs.getBoolean("selected"), price.multiply(BigDecimal.valueOf(quantity)));
        });
        int selectedCount = items.stream().filter(CartResponse.Item::selected).mapToInt(CartResponse.Item::quantity).sum();
        BigDecimal subtotal = items.stream().filter(CartResponse.Item::selected).map(CartResponse.Item::lineAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CartResponse(items, selectedCount, subtotal);
    }

    /**
     * 新增或合并同一 SKU，并在锁定库存行后校验当前可售数量。
     */
    @Transactional
    public CartResponse add(long userId, CartItemRequest request) {
        SkuStock sku = lockSku(request.skuCode());
        long cartId = getOrCreateCart(userId);
        Integer current = jdbc.query("""
                SELECT quantity FROM shopping_cart_items WHERE cart_id = :cartId AND sku_id = :skuId FOR UPDATE
                """, Map.of("cartId", cartId, "skuId", sku.id()), rs -> rs.next() ? rs.getInt(1) : null);
        int newQuantity = (current == null ? 0 : current) + request.quantity();
        checkStock(sku, newQuantity);
        if (current == null) {
            jdbc.update("""
                    INSERT INTO shopping_cart_items (cart_id, sku_id, quantity, selected)
                    VALUES (:cartId, :skuId, :quantity, TRUE)
                    """, Map.of("cartId", cartId, "skuId", sku.id(), "quantity", newQuantity));
        } else {
            jdbc.update("""
                    UPDATE shopping_cart_items SET quantity = :quantity, selected = TRUE
                    WHERE cart_id = :cartId AND sku_id = :skuId
                    """, Map.of("quantity", newQuantity, "cartId", cartId, "skuId", sku.id()));
        }
        return get(userId);
    }

    /**
     * 更新数量或选中状态；数量校验以数据库实时库存为准。
     */
    @Transactional
    public CartResponse update(long userId, long itemId, CartItemUpdateRequest request) {
        if (request.quantity() == null && request.selected() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_UPDATE", "至少需要修改数量或选中状态");
        }
        List<ItemOwner> owners = jdbc.query("""
                SELECT ci.sku_id, s.sku_code FROM shopping_cart_items ci
                JOIN shopping_carts c ON c.id = ci.cart_id JOIN product_skus s ON s.id = ci.sku_id
                WHERE ci.id = :itemId AND c.user_id = :userId FOR UPDATE
                """, Map.of("itemId", itemId, "userId", userId),
                (rs, rowNum) -> new ItemOwner(rs.getLong("sku_id"), rs.getString("sku_code")));
        if (owners.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "CART_ITEM_NOT_FOUND", "购物车商品不存在");
        }
        if (request.quantity() != null) {
            checkStock(lockSku(owners.get(0).skuCode()), request.quantity());
            jdbc.update("UPDATE shopping_cart_items SET quantity = :quantity WHERE id = :itemId",
                    Map.of("quantity", request.quantity(), "itemId", itemId));
        }
        if (request.selected() != null) {
            jdbc.update("UPDATE shopping_cart_items SET selected = :selected WHERE id = :itemId",
                    Map.of("selected", request.selected(), "itemId", itemId));
        }
        return get(userId);
    }

    /**
     * 删除属于当前用户的购物车项。
     */
    @Transactional
    public void remove(long userId, long itemId) {
        int changed = jdbc.update("""
                DELETE ci FROM shopping_cart_items ci
                JOIN shopping_carts c ON c.id = ci.cart_id
                WHERE ci.id = :itemId AND c.user_id = :userId
                """, Map.of("itemId", itemId, "userId", userId));
        if (changed == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "CART_ITEM_NOT_FOUND", "购物车商品不存在");
        }
    }

    /**
     * 清空当前用户购物车。
     */
    @Transactional
    public void clear(long userId) {
        jdbc.update("""
                DELETE ci FROM shopping_cart_items ci JOIN shopping_carts c ON c.id = ci.cart_id
                WHERE c.user_id = :userId
                """, Map.of("userId", userId));
    }

    private long getOrCreateCart(long userId) {
        jdbc.update("INSERT IGNORE INTO shopping_carts (user_id) VALUES (:userId)", Map.of("userId", userId));
        return jdbc.queryForObject("SELECT id FROM shopping_carts WHERE user_id = :userId",
                Map.of("userId", userId), Long.class);
    }

    private SkuStock lockSku(String skuCode) {
        // 锁定 SKU 行可避免两个并发请求在同一旧库存快照上完成数量校验。
        List<SkuStock> rows = jdbc.query("""
                SELECT s.id, s.sku_code, i.available_quantity
                FROM product_skus s JOIN products p ON p.id = s.product_id
                JOIN sku_inventory i ON i.sku_id = s.id
                WHERE s.sku_code = :skuCode AND s.status = 'ACTIVE' AND s.deleted_at IS NULL
                  AND p.status = 'ACTIVE' AND p.deleted_at IS NULL FOR UPDATE
                """, Map.of("skuCode", skuCode),
                (rs, rowNum) -> new SkuStock(rs.getLong("id"), rs.getString("sku_code"),
                        rs.getInt("available_quantity")));
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "SKU_NOT_FOUND", "商品规格不存在或已下架");
        }
        return rows.get(0);
    }

    private void checkStock(SkuStock sku, int quantity) {
        if (quantity > 99) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "QUANTITY_LIMIT", "单个规格最多购买 99 件");
        }
        if (quantity > sku.stock()) {
            throw new ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK", "商品库存不足");
        }
    }

    private record SkuStock(long id, String code, int stock) {
    }

    private record ItemOwner(long skuId, String skuCode) {
    }
}
