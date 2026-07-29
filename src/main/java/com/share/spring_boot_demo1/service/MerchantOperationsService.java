package com.share.spring_boot_demo1.service;

import com.share.spring_boot_demo1.common.ApiException;
import com.share.spring_boot_demo1.common.PageResponse;
import com.share.spring_boot_demo1.dto.MerchantDashboardResponse;
import com.share.spring_boot_demo1.dto.MerchantInventoryResponse;
import com.share.spring_boot_demo1.dto.MerchantOrderResponse;
import com.share.spring_boot_demo1.dto.MerchantProductRequest;
import com.share.spring_boot_demo1.dto.MerchantShipRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 商家工作台、订单履约与库存查询服务。
 *
 * <p>merchantId 是租户边界，所有订单、仓库和商品查询都必须携带该条件。
 * merchantUserId 仅表示操作人，用于状态历史和审计日志，不能替代租户条件。</p>
 */
@Service
public class MerchantOperationsService {
    /** 可售库存小于等于该值时标记为低库存。 */
    private static final int LOW_STOCK_THRESHOLD = 10;
    private static final Set<String> FULFILLMENT_FILTERS = Set.of(
            "UNFULFILLED", "PICKING", "SHIPPED", "DELIVERED", "RETURNED"
    );

    private final NamedParameterJdbcTemplate jdbc;

    public MerchantOperationsService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /*
     * 商品增改删必须同时维护 products、product_skus、sku_inventory 和封面图。
     * 所有查询都带 merchantId；删除只写 deleted_at，不能破坏历史订单中的商品快照。
     */

    /**
     * 汇总当前商户的待支付、待履约、今日发货与低库存指标。
     */
    @Transactional(readOnly = true)
    public MerchantDashboardResponse dashboard(long merchantId) {
        DashboardRow row = jdbc.queryForObject("""
                SELECT
                    SUM(order_status = 'PENDING_PAYMENT') AS pending_payment_orders,
                    SUM(payment_status = 'PAID' AND fulfillment_status = 'UNFULFILLED') AS unfulfilled_orders,
                    SUM(fulfillment_status = 'PICKING') AS picking_orders,
                    SUM(shipped_at >= CURRENT_DATE AND shipped_at < CURRENT_DATE + INTERVAL 1 DAY) AS shipped_today,
                    COALESCE(SUM(CASE
                        WHEN paid_at >= CURRENT_DATE AND paid_at < CURRENT_DATE + INTERVAL 1 DAY
                        THEN paid_amount ELSE 0 END), 0) AS today_paid_amount
                FROM orders
                WHERE merchant_id = :merchantId
                """, Map.of("merchantId", merchantId), (result, rowNumber) -> new DashboardRow(
                result.getLong("pending_payment_orders"),
                result.getLong("unfulfilled_orders"),
                result.getLong("picking_orders"),
                result.getLong("shipped_today"),
                result.getBigDecimal("today_paid_amount")
        ));
        Long lowStock = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM sku_inventory inventory
                JOIN product_skus sku ON sku.id = inventory.sku_id
                JOIN products product ON product.id = sku.product_id
                WHERE product.merchant_id = :merchantId
                  AND product.deleted_at IS NULL
                  AND sku.deleted_at IS NULL
                  AND inventory.available_quantity <= :threshold
                """, Map.of("merchantId", merchantId, "threshold", LOW_STOCK_THRESHOLD), Long.class);
        if (row == null) {
            row = new DashboardRow(0, 0, 0, 0, BigDecimal.ZERO);
        }
        return new MerchantDashboardResponse(
                row.pendingPaymentOrders(),
                row.unfulfilledOrders(),
                row.pickingOrders(),
                row.shippedToday(),
                lowStock == null ? 0 : lowStock,
                row.todayPaidAmount() == null ? BigDecimal.ZERO : row.todayPaidAmount()
        );
    }

    /**
     * 在当前商户范围内按履约状态或收件信息分页检索订单。
     */
    @Transactional(readOnly = true)
    public PageResponse<MerchantOrderResponse> listOrders(
            long merchantId,
            String fulfillmentStatus,
            String query,
            int page,
            int size
    ) {
        String normalizedStatus = normalizeFulfillmentStatus(fulfillmentStatus);
        String normalizedQuery = trimToNull(query);
        String statusCondition;
        if ("UNFULFILLED".equals(normalizedStatus)) {
            // “待履约”只包含已支付且正在处理的订单，排除未支付、取消和关闭订单。
            statusCondition = """
                     AND fulfillment_status = :fulfillmentStatus
                     AND payment_status = 'PAID'
                     AND order_status = 'PROCESSING'
                    """;
        } else {
            statusCondition = normalizedStatus == null ? "" : " AND fulfillment_status = :fulfillmentStatus";
        }
        String queryCondition = normalizedQuery == null ? "" : """
                 AND (order_no LIKE CONCAT('%', :query, '%')
                      OR recipient_name LIKE CONCAT('%', :query, '%')
                      OR recipient_phone LIKE CONCAT('%', :query, '%'))
                """;
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("merchantId", merchantId)
                .addValue("fulfillmentStatus", normalizedStatus)
                .addValue("query", normalizedQuery)
                .addValue("limit", size)
                .addValue("offset", page * size);
        String where = " WHERE merchant_id = :merchantId" + statusCondition + queryCondition;
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM orders" + where, parameters, Long.class);
        List<String> orderNumbers = jdbc.queryForList("""
                SELECT order_no
                FROM orders
                """ + where + """
                 ORDER BY created_at DESC, id DESC
                 LIMIT :limit OFFSET :offset
                """, parameters, String.class);
        List<MerchantOrderResponse> orders = orderNumbers.stream()
                .map(orderNo -> getOrder(merchantId, orderNo))
                .toList();
        return PageResponse.of(orders, page, size, total == null ? 0 : total);
    }

    /**
     * 查询商户自己的订单聚合详情；跨商户订单统一表现为不存在。
     */
    @Transactional(readOnly = true)
    public MerchantOrderResponse getOrder(long merchantId, String orderNo) {
        List<OrderRow> rows = jdbc.query("""
                SELECT id, order_no, order_status, payment_status, fulfillment_status, after_sale_status,
                       item_count, subtotal_amount, delivery_fee, discount_amount, payable_amount,
                       paid_amount, currency, payment_channel, buyer_note, recipient_name, recipient_phone,
                       recipient_province, recipient_city, recipient_district, recipient_detail,
                       recipient_tag, created_at, paid_at, shipped_at
                FROM orders
                WHERE merchant_id = :merchantId AND order_no = :orderNo
                """, Map.of("merchantId", merchantId, "orderNo", orderNo), (result, rowNumber) -> new OrderRow(
                result.getLong("id"),
                result.getString("order_no"),
                result.getString("order_status"),
                result.getString("payment_status"),
                result.getString("fulfillment_status"),
                result.getString("after_sale_status"),
                result.getInt("item_count"),
                result.getBigDecimal("subtotal_amount"),
                result.getBigDecimal("delivery_fee"),
                result.getBigDecimal("discount_amount"),
                result.getBigDecimal("payable_amount"),
                result.getBigDecimal("paid_amount"),
                result.getString("currency"),
                result.getString("payment_channel"),
                result.getString("buyer_note"),
                result.getString("recipient_name"),
                result.getString("recipient_phone"),
                result.getString("recipient_province"),
                result.getString("recipient_city"),
                result.getString("recipient_district"),
                result.getString("recipient_detail"),
                result.getString("recipient_tag"),
                result.getObject("created_at", LocalDateTime.class),
                result.getObject("paid_at", LocalDateTime.class),
                result.getObject("shipped_at", LocalDateTime.class)
        ));
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "订单不存在");
        }
        OrderRow order = rows.get(0);
        return order.toResponse(orderItems(order.id()), shipment(order.id()), timeline(order.id()));
    }

    /**
     * 将已支付待履约订单推进为拣货中，并记录操作人。
     */
    @Transactional
    public MerchantOrderResponse startPicking(long merchantId, long merchantUserId, String orderNo) {
        LockedOrder order = lockOrder(merchantId, orderNo);
        if (!"PAID".equals(order.paymentStatus())
                || !"PROCESSING".equals(order.orderStatus())
                || !"UNFULFILLED".equals(order.fulfillmentStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "ORDER_NOT_PICKABLE", "当前订单不能开始拣货");
        }
        jdbc.update("""
                UPDATE orders
                SET fulfillment_status = 'PICKING', version = version + 1
                WHERE id = :orderId
                """, Map.of("orderId", order.id()));
        statusEvent(order.id(), "FULFILLMENT", "UNFULFILLED", "PICKING", "商家开始拣货", merchantUserId);
        operationLog(merchantId, merchantUserId, "ORDER_PICKING_STARTED", "ORDER", orderNo);
        return getOrder(merchantId, orderNo);
    }

    /**
     * 从拣货中推进到已发货，同时创建物流单、首条物流事件和审计记录。
     */
    @Transactional
    public MerchantOrderResponse ship(
            long merchantId,
            long merchantUserId,
            String orderNo,
            MerchantShipRequest request
    ) {
        LockedOrder order = lockOrder(merchantId, orderNo);
        if (!"PAID".equals(order.paymentStatus())
                || !"PROCESSING".equals(order.orderStatus())
                || !"PICKING".equals(order.fulfillmentStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "ORDER_NOT_SHIPPABLE", "当前订单不能发货");
        }
        long warehouseId = resolveWarehouse(merchantId, request.warehouseId());
        String shipmentNo = newBusinessNo("SHP", 12);
        GeneratedKeyHolder shipmentKey = new GeneratedKeyHolder();
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("shipmentNo", shipmentNo)
                .addValue("orderId", order.id())
                .addValue("warehouseId", warehouseId)
                .addValue("carrierCode", request.carrierCode().trim().toUpperCase(Locale.ROOT))
                .addValue("carrierName", request.carrierName().trim())
                .addValue("trackingNo", request.trackingNo().trim());
        jdbc.update("""
                INSERT INTO shipments
                    (shipment_no, order_id, warehouse_id, carrier_code, carrier_name, tracking_no,
                     status, shipped_at)
                VALUES
                    (:shipmentNo, :orderId, :warehouseId, :carrierCode, :carrierName, :trackingNo,
                     'SHIPPED', CURRENT_TIMESTAMP(3))
                """, parameters, shipmentKey, new String[]{"id"});
        long shipmentId = shipmentKey.getKey().longValue();
        jdbc.update("""
                INSERT INTO shipment_events
                    (shipment_id, event_code, description, location, occurred_at)
                SELECT :shipmentId, 'SHIPPED', '商家已发货，等待物流揽收', warehouse.name,
                       CURRENT_TIMESTAMP(3)
                FROM warehouses warehouse
                WHERE warehouse.id = :warehouseId
                """, Map.of("shipmentId", shipmentId, "warehouseId", warehouseId));
        jdbc.update("""
                UPDATE orders
                SET order_status = 'SHIPPED',
                    fulfillment_status = 'SHIPPED',
                    shipped_at = CURRENT_TIMESTAMP(3),
                    version = version + 1
                WHERE id = :orderId
                """, Map.of("orderId", order.id()));
        statusEvent(order.id(), "FULFILLMENT", "PICKING", "SHIPPED", "商家已发货", merchantUserId);
        statusEvent(order.id(), "ORDER", "PROCESSING", "SHIPPED", "订单等待收货", merchantUserId);
        operationLog(merchantId, merchantUserId, "ORDER_SHIPPED", "ORDER", orderNo);
        return getOrder(merchantId, orderNo);
    }

    /**
     * 查询当前商户商品的 SKU 库存，可选只看低库存。
     */
    @Transactional(readOnly = true)
    public PageResponse<MerchantInventoryResponse> listInventory(
            long merchantId,
            String query,
            boolean lowStock,
            int page,
            int size
    ) {
        String normalizedQuery = trimToNull(query);
        String queryCondition = normalizedQuery == null ? "" : """
                 AND (sku.sku_code LIKE CONCAT('%', :query, '%')
                      OR sku.label LIKE CONCAT('%', :query, '%')
                      OR product.name LIKE CONCAT('%', :query, '%'))
                """;
        String stockCondition = lowStock ? " AND inventory.available_quantity <= :threshold" : "";
        String where = """
                 WHERE product.merchant_id = :merchantId
                   AND product.deleted_at IS NULL
                   AND sku.deleted_at IS NULL
                """ + queryCondition + stockCondition;
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("merchantId", merchantId)
                .addValue("query", normalizedQuery)
                .addValue("threshold", LOW_STOCK_THRESHOLD)
                .addValue("limit", size)
                .addValue("offset", page * size);
        Long total = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM product_skus sku
                JOIN products product ON product.id = sku.product_id
                JOIN sku_inventory inventory ON inventory.sku_id = sku.id
                """ + where, parameters, Long.class);
        List<MerchantInventoryResponse> items = jdbc.query("""
                SELECT sku.id AS sku_id, product.id AS product_id, product.category_id,
                       product.name AS product_name, product.slug AS product_slug,
                       product.tagline, product.description, product.status AS product_status,
                       sku.sku_code, sku.label AS sku_label, cover.image_url,
                       sku.price, sku.original_price, inventory.available_quantity,
                       inventory.locked_quantity, inventory.sold_quantity, inventory.updated_at
                FROM product_skus sku
                JOIN products product ON product.id = sku.product_id
                JOIN sku_inventory inventory ON inventory.sku_id = sku.id
                LEFT JOIN product_images cover ON cover.id = (
                    SELECT image.id
                    FROM product_images image
                    WHERE image.product_id = product.id
                    ORDER BY (image.image_type = 'COVER') DESC, image.sort_order, image.id
                    LIMIT 1
                )
                """ + where + """
                 ORDER BY inventory.available_quantity, product.id, sku.sort_order, sku.id
                 LIMIT :limit OFFSET :offset
                """, parameters, (result, rowNumber) -> new MerchantInventoryResponse(
                result.getLong("sku_id"),
                result.getLong("product_id"),
                result.getLong("category_id"),
                result.getString("product_name"),
                result.getString("product_slug"),
                result.getString("tagline"),
                result.getString("description"),
                external(result.getString("product_status")),
                result.getString("sku_code"),
                result.getString("sku_label"),
                result.getString("image_url"),
                result.getBigDecimal("price"),
                result.getBigDecimal("original_price"),
                result.getInt("available_quantity"),
                result.getInt("locked_quantity"),
                result.getLong("sold_quantity"),
                result.getInt("available_quantity") <= LOW_STOCK_THRESHOLD,
                result.getObject("updated_at", LocalDateTime.class)
        ));
        return PageResponse.of(items, page, size, total == null ? 0 : total);
    }

    /**
     * 用一个事务创建商品、默认 SKU、库存和封面图，任一步失败都会整体回滚。
     */
    @Transactional
    public void createProduct(long merchantId, long merchantUserId, MerchantProductRequest request) {
        String status = request.status().trim().toUpperCase(Locale.ROOT);
        MapSqlParameterSource product = productParameters(merchantId, request, status)
                .addValue("slug", "product-" + UUID.randomUUID().toString().replace("-", ""));
        GeneratedKeyHolder productKey = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO products
                    (merchant_id, category_id, slug, name, tagline, description, status,
                     base_price, original_price, published_at)
                VALUES
                    (:merchantId, :categoryId, :slug, :name, :tagline, :description, :status,
                     :price, :originalPrice,
                     CASE WHEN :status = 'ACTIVE' THEN CURRENT_TIMESTAMP(3) ELSE NULL END)
                """, product, productKey, new String[]{"id"});
        long productId = productKey.getKey().longValue();

        GeneratedKeyHolder skuKey = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO product_skus
                    (product_id, sku_code, label, price, original_price, status)
                VALUES
                    (:productId, :skuCode, :skuLabel, :price, :originalPrice, :skuStatus)
                """, skuParameters(productId, request, status), skuKey, new String[]{"id"});
        long skuId = skuKey.getKey().longValue();
        jdbc.update("""
                INSERT INTO sku_inventory (sku_id, available_quantity)
                VALUES (:skuId, :quantity)
                """, Map.of("skuId", skuId, "quantity", request.availableQuantity()));
        replaceCover(productId, request.imageUrl(), request.name());
        inventoryLog(skuId, request.availableQuantity(), merchantUserId, productId, "新建商品初始库存");
        operationLog(merchantId, merchantUserId, "PRODUCT_CREATED", "PRODUCT", String.valueOf(productId));
    }

    /**
     * 修改前锁定当前商户的库存行，防止并发调整覆盖库存。
     */
    @Transactional
    public void updateProduct(
            long merchantId,
            long merchantUserId,
            long productId,
            long skuId,
            MerchantProductRequest request
    ) {
        List<Integer> quantities = jdbc.queryForList("""
                SELECT inventory.available_quantity
                FROM products product
                JOIN product_skus sku ON sku.product_id = product.id
                JOIN sku_inventory inventory ON inventory.sku_id = sku.id
                WHERE product.id = :productId
                  AND sku.id = :skuId
                  AND product.merchant_id = :merchantId
                  AND product.deleted_at IS NULL
                  AND sku.deleted_at IS NULL
                FOR UPDATE
                """, Map.of(
                "productId", productId,
                "skuId", skuId,
                "merchantId", merchantId
        ), Integer.class);
        if (quantities.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "商品或 SKU 不存在");
        }
        String status = request.status().trim().toUpperCase(Locale.ROOT);
        MapSqlParameterSource product = productParameters(merchantId, request, status)
                .addValue("productId", productId);
        jdbc.update("""
                UPDATE products
                SET category_id = :categoryId, name = :name, tagline = :tagline,
                    description = :description, status = :status, original_price = :originalPrice,
                    published_at = CASE
                        WHEN :status = 'ACTIVE' THEN COALESCE(published_at, CURRENT_TIMESTAMP(3))
                        ELSE published_at
                    END,
                    version = version + 1
                WHERE id = :productId AND merchant_id = :merchantId AND deleted_at IS NULL
                """, product);
        MapSqlParameterSource sku = skuParameters(productId, request, status)
                .addValue("skuId", skuId);
        jdbc.update("""
                UPDATE product_skus
                SET sku_code = :skuCode, label = :skuLabel, price = :price,
                    original_price = :originalPrice, status = :skuStatus, version = version + 1
                WHERE id = :skuId AND product_id = :productId AND deleted_at IS NULL
                """, sku);
        jdbc.update("""
                UPDATE products
                SET base_price = (
                    SELECT MIN(price) FROM product_skus
                    WHERE product_id = :productId AND deleted_at IS NULL
                )
                WHERE id = :productId
                """, Map.of("productId", productId));
        jdbc.update("""
                UPDATE sku_inventory
                SET available_quantity = :quantity, version = version + 1
                WHERE sku_id = :skuId
                """, Map.of("skuId", skuId, "quantity", request.availableQuantity()));
        replaceCover(productId, request.imageUrl(), request.name());
        int delta = request.availableQuantity() - quantities.get(0);
        if (delta != 0) {
            inventoryLog(skuId, delta, merchantUserId, productId, "商家调整可售库存");
        }
        operationLog(merchantId, merchantUserId, "PRODUCT_UPDATED", "PRODUCT", String.valueOf(productId));
    }

    /**
     * 软删除商品及其 SKU，并清除购物车中的失效引用。
     */
    @Transactional
    public void deleteProduct(long merchantId, long merchantUserId, long productId) {
        List<Long> products = jdbc.queryForList("""
                SELECT id FROM products
                WHERE id = :productId AND merchant_id = :merchantId AND deleted_at IS NULL
                FOR UPDATE
                """, Map.of("productId", productId, "merchantId", merchantId), Long.class);
        if (products.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "商品不存在");
        }
        jdbc.update("""
                DELETE FROM shopping_cart_items
                WHERE sku_id IN (SELECT id FROM product_skus WHERE product_id = :productId)
                """, Map.of("productId", productId));
        jdbc.update("""
                UPDATE product_skus
                SET status = 'INACTIVE', deleted_at = CURRENT_TIMESTAMP(3), version = version + 1
                WHERE product_id = :productId AND deleted_at IS NULL
                """, Map.of("productId", productId));
        jdbc.update("""
                UPDATE products
                SET status = 'INACTIVE', deleted_at = CURRENT_TIMESTAMP(3), version = version + 1
                WHERE id = :productId AND merchant_id = :merchantId
                """, Map.of("productId", productId, "merchantId", merchantId));
        operationLog(merchantId, merchantUserId, "PRODUCT_DELETED", "PRODUCT", String.valueOf(productId));
    }

    private LockedOrder lockOrder(long merchantId, String orderNo) {
        // merchantId 同时用于行锁查询，既串行化状态变更，也阻止跨商户操作。
        List<LockedOrder> orders = jdbc.query("""
                SELECT id, order_status, payment_status, fulfillment_status
                FROM orders
                WHERE merchant_id = :merchantId AND order_no = :orderNo
                FOR UPDATE
                """, Map.of("merchantId", merchantId, "orderNo", orderNo), (result, rowNumber) -> new LockedOrder(
                result.getLong("id"),
                result.getString("order_status"),
                result.getString("payment_status"),
                result.getString("fulfillment_status")
        ));
        if (orders.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "订单不存在");
        }
        return orders.get(0);
    }

    private long resolveWarehouse(long merchantId, Long requestedWarehouseId) {
        // 未指定仓库时选择当前商户的默认有效仓库，指定时也必须验证归属。
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("merchantId", merchantId)
                .addValue("warehouseId", requestedWarehouseId);
        String requestedCondition = requestedWarehouseId == null
                ? " AND code = 'DEFAULT' ORDER BY id LIMIT 1"
                : " AND id = :warehouseId";
        List<Long> warehouses = jdbc.queryForList("""
                SELECT id
                FROM warehouses
                WHERE merchant_id = :merchantId AND status = 'ACTIVE'
                """ + requestedCondition, parameters, Long.class);
        if (warehouses.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "WAREHOUSE_NOT_FOUND", "发货仓库不存在或不可用");
        }
        return warehouses.get(0);
    }

    private List<MerchantOrderResponse.Item> orderItems(long orderId) {
        return jdbc.query("""
                SELECT id, product_id, sku_id, product_slug, product_name, sku_code, sku_label,
                       image_url, unit_price, quantity, line_amount
                FROM order_items
                WHERE order_id = :orderId
                ORDER BY id
                """, Map.of("orderId", orderId), (result, rowNumber) -> new MerchantOrderResponse.Item(
                result.getLong("id"),
                (Long) result.getObject("product_id"),
                (Long) result.getObject("sku_id"),
                result.getString("product_slug"),
                result.getString("product_name"),
                result.getString("sku_code"),
                result.getString("sku_label"),
                result.getString("image_url"),
                result.getBigDecimal("unit_price"),
                result.getInt("quantity"),
                result.getBigDecimal("line_amount")
        ));
    }

    private MerchantOrderResponse.Shipment shipment(long orderId) {
        List<ShipmentRow> shipments = jdbc.query("""
                SELECT shipment.id, shipment.shipment_no, shipment.warehouse_id,
                       warehouse.name AS warehouse_name, shipment.carrier_code, shipment.carrier_name,
                       shipment.tracking_no, shipment.status, shipment.shipped_at
                FROM shipments shipment
                JOIN warehouses warehouse ON warehouse.id = shipment.warehouse_id
                WHERE shipment.order_id = :orderId
                ORDER BY shipment.id DESC
                LIMIT 1
                """, Map.of("orderId", orderId), (result, rowNumber) -> new ShipmentRow(
                result.getLong("id"),
                result.getString("shipment_no"),
                result.getLong("warehouse_id"),
                result.getString("warehouse_name"),
                result.getString("carrier_code"),
                result.getString("carrier_name"),
                result.getString("tracking_no"),
                result.getString("status"),
                result.getObject("shipped_at", LocalDateTime.class)
        ));
        if (shipments.isEmpty()) {
            return null;
        }
        ShipmentRow shipment = shipments.get(0);
        List<MerchantOrderResponse.ShipmentEvent> events = jdbc.query("""
                SELECT event_code, description, location, occurred_at
                FROM shipment_events
                WHERE shipment_id = :shipmentId
                ORDER BY occurred_at DESC, id DESC
                """, Map.of("shipmentId", shipment.id()), (result, rowNumber) ->
                new MerchantOrderResponse.ShipmentEvent(
                        result.getString("event_code"),
                        result.getString("description"),
                        result.getString("location"),
                        result.getObject("occurred_at", LocalDateTime.class)
                ));
        return new MerchantOrderResponse.Shipment(
                shipment.shipmentNo(),
                shipment.warehouseId(),
                shipment.warehouseName(),
                shipment.carrierCode(),
                shipment.carrierName(),
                shipment.trackingNo(),
                external(shipment.status()),
                shipment.shippedAt(),
                events
        );
    }

    private List<MerchantOrderResponse.TimelineEvent> timeline(long orderId) {
        return jdbc.query("""
                SELECT status_type, from_status, to_status, note, operator_type, created_at
                FROM order_status_history
                WHERE order_id = :orderId
                ORDER BY created_at, id
                """, Map.of("orderId", orderId), (result, rowNumber) ->
                new MerchantOrderResponse.TimelineEvent(
                        external(result.getString("status_type")),
                        external(result.getString("from_status")),
                        external(result.getString("to_status")),
                        result.getString("note"),
                        external(result.getString("operator_type")),
                        result.getObject("created_at", LocalDateTime.class)
                ));
    }

    private void statusEvent(
            long orderId,
            String type,
            String from,
            String to,
            String note,
            long merchantUserId
    ) {
        jdbc.update("""
                INSERT INTO order_status_history
                    (order_id, status_type, from_status, to_status, note, operator_type,
                     merchant_operator_id)
                VALUES
                    (:orderId, :type, :fromStatus, :toStatus, :note, 'MERCHANT', :merchantUserId)
                """, new MapSqlParameterSource()
                .addValue("orderId", orderId)
                .addValue("type", type)
                .addValue("fromStatus", from)
                .addValue("toStatus", to)
                .addValue("note", note)
                .addValue("merchantUserId", merchantUserId));
    }

    private MapSqlParameterSource productParameters(
            long merchantId,
            MerchantProductRequest request,
            String status
    ) {
        return new MapSqlParameterSource()
                .addValue("merchantId", merchantId)
                .addValue("categoryId", request.categoryId())
                .addValue("name", request.name().trim())
                .addValue("tagline", trimToNull(request.tagline()))
                .addValue("description", trimToNull(request.description()))
                .addValue("status", status)
                .addValue("price", request.price())
                .addValue("originalPrice", request.originalPrice());
    }

    private MapSqlParameterSource skuParameters(
            long productId,
            MerchantProductRequest request,
            String productStatus
    ) {
        return new MapSqlParameterSource()
                .addValue("productId", productId)
                .addValue("skuCode", request.skuCode().trim())
                .addValue("skuLabel", request.skuLabel().trim())
                .addValue("price", request.price())
                .addValue("originalPrice", request.originalPrice())
                .addValue("skuStatus", "ACTIVE".equals(productStatus) ? "ACTIVE" : "INACTIVE");
    }

    private void replaceCover(long productId, String imageUrl, String productName) {
        jdbc.update("""
                DELETE FROM product_images
                WHERE product_id = :productId AND image_type = 'COVER'
                """, Map.of("productId", productId));
        jdbc.update("""
                INSERT INTO product_images (product_id, image_url, alt_text, image_type)
                VALUES (:productId, :imageUrl, :altText, 'COVER')
                """, Map.of(
                "productId", productId,
                "imageUrl", imageUrl.trim(),
                "altText", productName.trim()
        ));
    }

    private void inventoryLog(
            long skuId,
            int delta,
            long merchantUserId,
            long productId,
            String note
    ) {
        jdbc.update("""
                INSERT INTO inventory_transactions
                    (sku_id, transaction_type, available_delta, reference_type, reference_no,
                     note, merchant_created_by)
                VALUES
                    (:skuId, 'ADJUST', :delta, 'PRODUCT', :productId, :note, :merchantUserId)
                """, new MapSqlParameterSource()
                .addValue("skuId", skuId)
                .addValue("delta", delta)
                .addValue("productId", String.valueOf(productId))
                .addValue("note", note)
                .addValue("merchantUserId", merchantUserId));
    }

    private void operationLog(
            long merchantId,
            long merchantUserId,
            String action,
            String resourceType,
            String resourceId
    ) {
        // 审计日志只记录稳定业务动作和引用号，避免写入收件电话等敏感信息。
        jdbc.update("""
                INSERT INTO merchant_operation_logs
                    (merchant_id, merchant_user_id, action, resource_type, resource_id)
                VALUES
                    (:merchantId, :merchantUserId, :action, :resourceType, :resourceId)
                """, Map.of(
                "merchantId", merchantId,
                "merchantUserId", merchantUserId,
                "action", action,
                "resourceType", resourceType,
                "resourceId", resourceId
        ));
    }

    private String normalizeFulfillmentStatus(String status) {
        String normalized = trimToNull(status);
        if (normalized == null || "all".equalsIgnoreCase(normalized)) {
            return null;
        }
        normalized = normalized.replace('-', '_').toUpperCase(Locale.ROOT);
        if (!FULFILLMENT_FILTERS.contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FULFILLMENT_STATUS", "不支持该履约状态筛选");
        }
        return normalized;
    }

    private static String external(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String newBusinessNo(String prefix, int randomLength) {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String random = UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, randomLength)
                .toUpperCase(Locale.ROOT);
        return prefix + time + random;
    }

    private record DashboardRow(
            long pendingPaymentOrders,
            long unfulfilledOrders,
            long pickingOrders,
            long shippedToday,
            BigDecimal todayPaidAmount
    ) {
    }

    private record LockedOrder(
            long id,
            String orderStatus,
            String paymentStatus,
            String fulfillmentStatus
    ) {
    }

    private record ShipmentRow(
            long id,
            String shipmentNo,
            long warehouseId,
            String warehouseName,
            String carrierCode,
            String carrierName,
            String trackingNo,
            String status,
            LocalDateTime shippedAt
    ) {
    }

    private record OrderRow(
            long id,
            String orderNo,
            String status,
            String paymentStatus,
            String fulfillmentStatus,
            String afterSaleStatus,
            int itemCount,
            BigDecimal subtotal,
            BigDecimal deliveryFee,
            BigDecimal discount,
            BigDecimal total,
            BigDecimal paidAmount,
            String currency,
            String paymentMethod,
            String buyerNote,
            String recipientName,
            String recipientPhone,
            String province,
            String city,
            String district,
            String detail,
            String tag,
            LocalDateTime createdAt,
            LocalDateTime paidAt,
            LocalDateTime shippedAt
    ) {
        private MerchantOrderResponse toResponse(
                List<MerchantOrderResponse.Item> items,
                MerchantOrderResponse.Shipment shipment,
                List<MerchantOrderResponse.TimelineEvent> timeline
        ) {
            String visibleFulfillmentStatus = "CANCELLED".equals(status)
                    ? "not-required"
                    : external(fulfillmentStatus);
            return new MerchantOrderResponse(
                    id,
                    orderNo,
                    external(status),
                    external(paymentStatus),
                    visibleFulfillmentStatus,
                    external(afterSaleStatus),
                    itemCount,
                    subtotal,
                    deliveryFee,
                    discount,
                    total,
                    paidAmount,
                    currency,
                    paymentMethod,
                    buyerNote,
                    new MerchantOrderResponse.Recipient(
                            recipientName, recipientPhone, province, city, district, detail, tag
                    ),
                    items,
                    shipment,
                    timeline,
                    createdAt,
                    paidAt,
                    shippedAt
            );
        }
    }
}
