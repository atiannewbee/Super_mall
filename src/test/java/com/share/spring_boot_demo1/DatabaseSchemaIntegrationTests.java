package com.share.spring_boot_demo1;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 Flyway 迁移结果中的关键表、约束、默认值和商家数据边界。
 */
@SpringBootTest
class DatabaseSchemaIntegrationTests {

    private static final Set<String> EXPECTED_BUSINESS_TABLES = Set.of(
            "users", "user_addresses",
            "categories", "brands", "products", "product_images", "product_features",
            "product_attributes", "product_attribute_values", "product_skus", "sku_attribute_values",
            "sku_inventory", "inventory_transactions",
            "shopping_carts", "shopping_cart_items", "product_favorites",
            "orders", "order_items", "order_status_history",
            "payments", "payment_notifications", "shipments", "shipment_events",
            "after_sale_requests", "after_sale_items", "after_sale_events",
            "merchants", "merchant_users", "merchant_user_roles", "warehouses",
            "merchant_operation_logs"
    );

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayCreatesEveryBusinessTable() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()",
                String.class
        );

        assertTrue(tables.contains("flyway_schema_history"), "Flyway history table is missing");
        assertTrue(tables.containsAll(EXPECTED_BUSINESS_TABLES),
                () -> "Missing business tables: " + EXPECTED_BUSINESS_TABLES.stream()
                        .filter(table -> !tables.contains(table))
                        .toList());
    }

    @Test
    void seedCatalogMatchesTheVueStorefront() {
        assertEquals(5, count("categories"));
        assertEquals(8, count("brands"));
        assertEquals(8, count("products"));
        assertEquals(16, count("product_skus"));
        assertEquals(16, count("sku_inventory"));
        assertEquals(24, count("product_features"));

        Integer skusWithoutInventory = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product_skus sku LEFT JOIN sku_inventory inventory " +
                        "ON inventory.sku_id = sku.id WHERE inventory.sku_id IS NULL",
                Integer.class
        );
        assertEquals(0, skusWithoutInventory);

        Integer unavailableBlueArcPods = jdbcTemplate.queryForObject(
                "SELECT inventory.available_quantity FROM product_skus sku " +
                        "JOIN sku_inventory inventory ON inventory.sku_id = sku.id " +
                        "WHERE sku.sku_code = 'arcpods-pro-blue'",
                Integer.class
        );
        assertEquals(0, unavailableBlueArcPods);
    }

    @Test
    void defaultMerchantOwnsTheExistingCatalogAndOrders() {
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM merchants WHERE code = 'SUPER_MALL' AND status = 'ACTIVE'",
                Integer.class
        ));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM warehouses WHERE code = 'DEFAULT' AND status = 'ACTIVE'",
                Integer.class
        ));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM products WHERE merchant_id IS NULL",
                Integer.class
        ));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE merchant_id IS NULL",
                Integer.class
        ));
    }

    @Test
    void moneyColumnsAndCriticalIndexesArePresent() {
        Integer decimalMoneyColumn = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns " +
                        "WHERE table_schema = DATABASE() AND table_name = 'orders' " +
                        "AND column_name = 'payable_amount' AND data_type = 'decimal' " +
                        "AND numeric_precision = 12 AND numeric_scale = 2",
                Integer.class
        );
        assertEquals(1, decimalMoneyColumn);

        assertEquals(1, indexCount("products", "uk_products_slug", true));
        assertEquals(1, indexCount("product_skus", "uk_product_skus_code", true));
        assertEquals(1, indexCount("orders", "uk_orders_order_no", true));
        assertEquals(1, indexCount("orders", "uk_orders_user_idempotency", true));
        assertEquals(1, indexCount("orders", "idx_orders_user_status_created", false));
        assertEquals(1, indexCount("payments", "uk_payments_payment_no", true));
        assertEquals(1, indexCount(
                "payment_notifications", "uk_payment_notifications_channel_event", true
        ));
    }

    @Test
    @Transactional
    void databaseRejectsUserWithoutEmailOrPhone() {
        assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update("INSERT INTO users (name) VALUES (?)", "无登录身份用户")
        );
    }

    @Test
    @Transactional
    void databaseRejectsNegativeInventory() {
        assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update(
                        "UPDATE sku_inventory SET available_quantity = -1 WHERE sku_id = ?",
                        1001L
                )
        );
    }

    private int count(String table) {
        Integer result = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return result == null ? 0 : result;
    }

    private int indexCount(String table, String index, boolean unique) {
        Integer result = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics " +
                        "WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ? AND non_unique = ?",
                Integer.class,
                table,
                index,
                unique ? 0 : 1
        );
        return result == null ? 0 : result;
    }
}
