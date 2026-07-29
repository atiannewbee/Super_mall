package com.share.spring_boot_demo1.service;

import com.share.spring_boot_demo1.common.ApiException;
import com.share.spring_boot_demo1.common.PageResponse;
import com.share.spring_boot_demo1.config.PaymentProperties;
import com.share.spring_boot_demo1.dto.AddressResponse;
import com.share.spring_boot_demo1.dto.CreateOrderRequest;
import com.share.spring_boot_demo1.dto.OrderResponse;
import com.share.spring_boot_demo1.dto.PaymentRequest;
import com.share.spring_boot_demo1.dto.PaymentResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 消费者订单服务，负责下单、库存预占、取消、模拟支付和确认收货。
 *
 * <p>金额始终由服务端根据数据库中的 SKU 单价计算；收货地址和商品信息在下单时保存快照，
 * 避免后续修改资料或商品后破坏历史订单。所有库存变化与订单状态变化处于同一事务中。</p>
 */
@Service
public class OrderService {
    /** 满足该商品金额时免基础运费。 */
    private static final BigDecimal FREE_DELIVERY_THRESHOLD = new BigDecimal("99.00");
    /** 未达到免邮门槛时收取的固定运费。 */
    private static final BigDecimal STANDARD_DELIVERY_FEE = new BigDecimal("10.00");
    private static final Set<String> ORDER_FILTERS = Set.of(
            "PENDING_PAYMENT", "PROCESSING", "SHIPPED", "COMPLETED", "CANCELLED", "AFTER_SALE"
    );

    private final NamedParameterJdbcTemplate jdbc;
    private final AddressService addressService;
    private final PaymentProperties paymentProperties;

    public OrderService(NamedParameterJdbcTemplate jdbc, AddressService addressService,
                        PaymentProperties paymentProperties) {
        this.jdbc = jdbc;
        this.addressService = addressService;
        this.paymentProperties = paymentProperties;
    }

    /**
     * 从已选购物车项创建待支付订单。
     *
     * <p>Idempotency-Key 只在同一用户范围内生效，重复提交会返回第一次创建的订单。
     * 库存通过带 {@code available_quantity >= quantity} 条件的更新原子预占，
     * 任何 SKU 不足都会回滚此前已完成的预占。</p>
     */
    @Transactional
    public OrderResponse create(long userId, CreateOrderRequest request, String idempotencyKey) {
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        if (normalizedKey != null) {
            List<String> existing = jdbc.queryForList("""
                    SELECT order_no FROM orders WHERE user_id = :userId AND idempotency_key = :key
                    """, Map.of("userId", userId, "key", normalizedKey), String.class);
            if (!existing.isEmpty()) {
                return getRequired(userId, existing.get(0));
            }
        }

        AddressResponse address = addressService.getRequired(userId, request.addressId());
        List<CheckoutItem> items = selectedCartItems(userId);
        if (items.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "EMPTY_CHECKOUT", "请选择需要结算的购物车商品");
        }

        BigDecimal subtotal = items.stream().map(CheckoutItem::lineAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal deliveryFee = subtotal.compareTo(FREE_DELIVERY_THRESHOLD) >= 0
                ? BigDecimal.ZERO.setScale(2) : STANDARD_DELIVERY_FEE;
        BigDecimal discount = BigDecimal.ZERO.setScale(2);
        BigDecimal payable = subtotal.add(deliveryFee).subtract(discount);
        int itemCount = items.stream().mapToInt(CheckoutItem::quantity).sum();
        String orderNo = newBusinessNo("SM", 12);

        // 使用“条件更新 + 受影响行数”代替先查后改，防止并发下单造成超卖。
        for (CheckoutItem item : items) {
            int changed = jdbc.update("""
                    UPDATE sku_inventory
                    SET available_quantity = available_quantity - :quantity,
                        locked_quantity = locked_quantity + :quantity,
                        version = version + 1
                    WHERE sku_id = :skuId AND available_quantity >= :quantity
                    """, Map.of("quantity", item.quantity(), "skuId", item.skuId()));
            if (changed == 0) {
                throw new ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK",
                        item.productName() + "（" + item.skuLabel() + "）库存不足");
            }
        }

        GeneratedKeyHolder orderKey = new GeneratedKeyHolder();
        // 订单保存地址快照；用户之后编辑或删除地址不会改变历史订单。
        MapSqlParameterSource orderParams = new MapSqlParameterSource()
                .addValue("orderNo", orderNo).addValue("userId", userId).addValue("key", normalizedKey)
                .addValue("addressId", address.id()).addValue("itemCount", itemCount)
                .addValue("subtotal", subtotal).addValue("deliveryFee", deliveryFee)
                .addValue("discount", discount).addValue("payable", payable)
                .addValue("paymentChannel", request.paymentChannel())
                .addValue("buyerNote", trimToNull(request.buyerNote()))
                .addValue("invoiceRequired", request.invoiceRequired()).addValue("name", address.name())
                .addValue("phone", address.phone()).addValue("province", address.province())
                .addValue("city", address.city()).addValue("district", address.district())
                .addValue("detail", address.detail()).addValue("tag", address.tag());
        jdbc.update("""
                INSERT INTO orders
                    (merchant_id, order_no, user_id, idempotency_key, address_id, item_count, subtotal_amount, delivery_fee,
                     discount_amount, payable_amount, payment_channel, buyer_note, invoice_required,
                     recipient_name, recipient_phone, recipient_province, recipient_city, recipient_district,
                     recipient_detail, recipient_tag)
                VALUES
                    ((SELECT id FROM merchants WHERE code = 'SUPER_MALL'), :orderNo, :userId, :key,
                     :addressId, :itemCount, :subtotal, :deliveryFee, :discount,
                     :payable, :paymentChannel, :buyerNote, :invoiceRequired, :name, :phone, :province,
                     :city, :district, :detail, :tag)
                """, orderParams, orderKey, new String[]{"id"});
        long orderId = orderKey.getKey().longValue();

        // 订单项保存商品名称、SKU 标签和成交单价快照，保证售后与对账可追溯。
        for (CheckoutItem item : items) {
            jdbc.update("""
                    INSERT INTO order_items
                        (order_id, product_id, sku_id, product_slug, product_name, sku_code, sku_label,
                         image_url, unit_price, quantity, line_amount)
                    VALUES
                        (:orderId, :productId, :skuId, :slug, :name, :skuCode, :skuLabel, :image,
                         :unitPrice, :quantity, :lineAmount)
                    """, new MapSqlParameterSource()
                    .addValue("orderId", orderId).addValue("productId", item.productId())
                    .addValue("skuId", item.skuId()).addValue("slug", item.productSlug())
                    .addValue("name", item.productName()).addValue("skuCode", item.skuCode())
                    .addValue("skuLabel", item.skuLabel()).addValue("image", item.image())
                    .addValue("unitPrice", item.unitPrice()).addValue("quantity", item.quantity())
                    .addValue("lineAmount", item.lineAmount()));
            inventoryEvent(item.skuId(), "LOCK", -item.quantity(), item.quantity(), orderNo,
                    "创建订单锁定库存", userId);
        }
        statusEvent(orderId, "ORDER", null, "PENDING_PAYMENT", "订单已创建", userId);
        jdbc.update("""
                DELETE ci FROM shopping_cart_items ci JOIN shopping_carts c ON c.id = ci.cart_id
                WHERE c.user_id = :userId AND ci.selected = TRUE
                """, Map.of("userId", userId));
        return getRequired(userId, orderNo);
    }

    /**
     * 按订单主状态分页查询当前用户订单。
     */
    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> list(long userId, String status, int page, int size) {
        String normalized = normalizeStatus(status);
        String condition = normalized == null ? "" : " AND order_status = :status";
        MapSqlParameterSource parameters = new MapSqlParameterSource("userId", userId)
                .addValue("status", normalized).addValue("limit", size).addValue("offset", page * size);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE user_id = :userId" + condition,
                parameters, Long.class);
        List<String> orderNumbers = jdbc.queryForList("""
                SELECT order_no FROM orders WHERE user_id = :userId
                """ + condition + " ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset",
                parameters, String.class);
        List<OrderResponse> orders = orderNumbers.stream().map(orderNo -> getRequired(userId, orderNo)).toList();
        return PageResponse.of(orders, page, size, total == null ? 0 : total);
    }

    /**
     * 查询订单聚合详情，包括商品快照、物流与完整状态时间线。
     */
    @Transactional(readOnly = true)
    public OrderResponse getRequired(long userId, String orderNo) {
        List<OrderRow> rows = jdbc.query("""
                SELECT id, order_no, address_id, order_status, payment_status, fulfillment_status,
                       after_sale_status, item_count, subtotal_amount, delivery_fee, discount_amount,
                       payable_amount, paid_amount, currency, payment_channel, buyer_note,
                       recipient_name, recipient_phone, recipient_province, recipient_city,
                       recipient_district, recipient_detail, recipient_tag, created_at, paid_at,
                       shipped_at, completed_at, cancelled_at
                FROM orders WHERE order_no = :orderNo AND user_id = :userId
                """, Map.of("orderNo", orderNo, "userId", userId), (rs, rowNum) -> new OrderRow(
                rs.getLong("id"), rs.getString("order_no"), (Long) rs.getObject("address_id"),
                rs.getString("order_status"), rs.getString("payment_status"),
                rs.getString("fulfillment_status"), rs.getString("after_sale_status"),
                rs.getInt("item_count"), rs.getBigDecimal("subtotal_amount"),
                rs.getBigDecimal("delivery_fee"), rs.getBigDecimal("discount_amount"),
                rs.getBigDecimal("payable_amount"), rs.getBigDecimal("paid_amount"),
                rs.getString("currency"), rs.getString("payment_channel"), rs.getString("buyer_note"),
                rs.getString("recipient_name"), rs.getString("recipient_phone"),
                rs.getString("recipient_province"), rs.getString("recipient_city"),
                rs.getString("recipient_district"), rs.getString("recipient_detail"),
                rs.getString("recipient_tag"), rs.getObject("created_at", LocalDateTime.class),
                rs.getObject("paid_at", LocalDateTime.class), rs.getObject("shipped_at", LocalDateTime.class),
                rs.getObject("completed_at", LocalDateTime.class), rs.getObject("cancelled_at", LocalDateTime.class)
        ));
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "订单不存在");
        }
        OrderRow row = rows.get(0);
        List<OrderResponse.Item> items = orderItems(row.id());
        List<OrderResponse.TimelineEvent> timeline = jdbc.query("""
                SELECT status_type, from_status, to_status, note, operator_type, created_at
                FROM order_status_history WHERE order_id = :orderId ORDER BY created_at, id
                """, Map.of("orderId", row.id()), (rs, rowNum) -> new OrderResponse.TimelineEvent(
                external(rs.getString("status_type")), external(rs.getString("from_status")),
                external(rs.getString("to_status")), rs.getString("note"),
                external(rs.getString("operator_type")), rs.getObject("created_at", LocalDateTime.class)
        ));
        return row.toResponse(items, shipment(row.id()), timeline);
    }

    /**
     * 取消尚未支付且没有进行中真实支付交易的订单，并释放全部预占库存。
     */
    @Transactional
    public OrderResponse cancel(long userId, String orderNo) {
        LockedOrder order = lockOwnedOrder(userId, orderNo);
        if (!"PENDING_PAYMENT".equals(order.orderStatus()) || !"UNPAID".equals(order.paymentStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "ORDER_NOT_CANCELLABLE", "当前订单状态不能直接取消");
        }
        Integer pendingPayments = jdbc.queryForObject("""
                SELECT COUNT(*) FROM payments WHERE order_id = :orderId AND status = 'PENDING'
                """, Map.of("orderId", order.id()), Integer.class);
        if (pendingPayments != null && pendingPayments > 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "PAYMENT_CLOSE_REQUIRED",
                    "支付交易仍在进行，请稍后重新取消订单"
            );
        }
        // 只有 locked_quantity 足够时才释放，异常说明库存账与订单项已不一致，必须回滚并报警。
        for (OrderResponse.Item item : orderItems(order.id())) {
            int changed = jdbc.update("""
                    UPDATE sku_inventory SET available_quantity = available_quantity + :quantity,
                        locked_quantity = locked_quantity - :quantity, version = version + 1
                    WHERE sku_id = :skuId AND locked_quantity >= :quantity
                    """, Map.of("quantity", item.quantity(), "skuId", item.skuId()));
            if (changed == 0) {
                throw new IllegalStateException("Locked inventory is inconsistent for order " + orderNo);
            }
            inventoryEvent(item.skuId(), "UNLOCK", item.quantity(), -item.quantity(), orderNo,
                    "取消订单释放库存", userId);
        }
        jdbc.update("""
                UPDATE orders SET order_status = 'CANCELLED', payment_status = 'CLOSED',
                    cancelled_at = CURRENT_TIMESTAMP(3), version = version + 1 WHERE id = :orderId
                """, Map.of("orderId", order.id()));
        statusEvent(order.id(), "ORDER", "PENDING_PAYMENT", "CANCELLED", "用户取消订单", userId);
        statusEvent(order.id(), "PAYMENT", "UNPAID", "CLOSED", "订单关闭", userId);
        return getRequired(userId, orderNo);
    }

    /**
     * 仅供本地联调的受控模拟支付；生产关闭开关后不可调用。
     */
    @Transactional
    public PaymentResponse paySandbox(long userId, String orderNo, PaymentRequest request) {
        if (!paymentProperties.sandboxEnabled()) {
            throw new ApiException(HttpStatus.NOT_IMPLEMENTED, "PAYMENT_PROVIDER_NOT_CONFIGURED",
                    "真实支付渠道尚未配置，不能模拟支付成功");
        }
        LockedOrder order = lockOwnedOrder(userId, orderNo);
        if (!"PENDING_PAYMENT".equals(order.orderStatus()) || !"UNPAID".equals(order.paymentStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "ORDER_NOT_PAYABLE", "当前订单状态不能支付");
        }
        Integer pendingPayments = jdbc.queryForObject("""
                SELECT COUNT(*) FROM payments WHERE order_id = :orderId AND status = 'PENDING'
                """, Map.of("orderId", order.id()), Integer.class);
        if (pendingPayments != null && pendingPayments > 0) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "PAYMENT_ALREADY_IN_PROGRESS",
                    "订单已有真实支付正在进行，不能再使用模拟支付"
            );
        }
        String paymentNo = newBusinessNo("PAY", 14);
        for (OrderResponse.Item item : orderItems(order.id())) {
            int changed = jdbc.update("""
                    UPDATE sku_inventory SET locked_quantity = locked_quantity - :quantity,
                        sold_quantity = sold_quantity + :quantity, version = version + 1
                    WHERE sku_id = :skuId AND locked_quantity >= :quantity
                    """, Map.of("quantity", item.quantity(), "skuId", item.skuId()));
            if (changed == 0) {
                throw new IllegalStateException("Locked inventory is inconsistent for order " + orderNo);
            }
            inventoryEvent(item.skuId(), "DEDUCT", 0, -item.quantity(), orderNo,
                    "沙箱支付完成扣减库存", userId);
        }
        jdbc.update("""
                INSERT INTO payments (payment_no, order_id, channel, status, amount, currency,
                                      provider_trade_no, paid_at)
                VALUES (:paymentNo, :orderId, :channel, 'SUCCESS', :amount, 'CNY', :tradeNo,
                        CURRENT_TIMESTAMP(3))
                """, Map.of("paymentNo", paymentNo, "orderId", order.id(), "channel", request.channel(),
                "amount", order.payableAmount(), "tradeNo", "SANDBOX-" + paymentNo));
        jdbc.update("""
                UPDATE orders SET order_status = 'PROCESSING', payment_status = 'PAID',
                    paid_amount = payable_amount, payment_channel = :channel, paid_at = CURRENT_TIMESTAMP(3),
                    version = version + 1 WHERE id = :orderId
                """, Map.of("channel", request.channel(), "orderId", order.id()));
        statusEvent(order.id(), "PAYMENT", "UNPAID", "PAID", "沙箱支付成功", userId);
        statusEvent(order.id(), "ORDER", "PENDING_PAYMENT", "PROCESSING", "订单进入备货", userId);
        return jdbc.queryForObject("""
                SELECT p.payment_no, o.order_no, p.channel, p.status, p.amount, p.currency, p.paid_at
                FROM payments p JOIN orders o ON o.id = p.order_id WHERE p.payment_no = :paymentNo
                """, Map.of("paymentNo", paymentNo), (rs, rowNum) -> new PaymentResponse(
                rs.getString("payment_no"), rs.getString("order_no"), rs.getString("channel"),
                external(rs.getString("status")), rs.getBigDecimal("amount"), rs.getString("currency"),
                rs.getObject("paid_at", LocalDateTime.class)
        ));
    }

    /**
     * 用户确认收货，同时完成订单与物流状态流转。
     */
    @Transactional
    public OrderResponse confirmReceipt(long userId, String orderNo) {
        LockedOrder order = lockOwnedOrder(userId, orderNo);
        if (!"SHIPPED".equals(order.orderStatus()) || !"SHIPPED".equals(order.fulfillmentStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "ORDER_NOT_RECEIVABLE", "订单尚未处于待收货状态");
        }
        jdbc.update("""
                UPDATE orders SET order_status = 'COMPLETED', fulfillment_status = 'DELIVERED',
                    completed_at = CURRENT_TIMESTAMP(3), version = version + 1 WHERE id = :orderId
                """, Map.of("orderId", order.id()));
        jdbc.update("""
                UPDATE shipments SET status = 'DELIVERED', delivered_at = CURRENT_TIMESTAMP(3)
                WHERE order_id = :orderId AND status <> 'DELIVERED'
                """, Map.of("orderId", order.id()));
        statusEvent(order.id(), "FULFILLMENT", "SHIPPED", "DELIVERED", "用户确认收货", userId);
        statusEvent(order.id(), "ORDER", "SHIPPED", "COMPLETED", "订单已完成", userId);
        return getRequired(userId, orderNo);
    }

    private List<CheckoutItem> selectedCartItems(long userId) {
        // 锁定结算行，避免下单事务期间同一购物车项被并发修改或删除。
        return jdbc.query("""
                SELECT ci.id AS cart_item_id, p.id AS product_id, p.slug, p.name,
                       cover.image_url, s.id AS sku_id, s.sku_code, s.label, s.price, ci.quantity
                FROM shopping_carts c
                JOIN shopping_cart_items ci ON ci.cart_id = c.id
                JOIN product_skus s ON s.id = ci.sku_id AND s.status = 'ACTIVE' AND s.deleted_at IS NULL
                JOIN products p ON p.id = s.product_id AND p.status = 'ACTIVE' AND p.deleted_at IS NULL
                LEFT JOIN product_images cover ON cover.id = (
                    SELECT pi.id FROM product_images pi WHERE pi.product_id = p.id
                    ORDER BY (pi.image_type = 'COVER') DESC, pi.sort_order, pi.id LIMIT 1)
                WHERE c.user_id = :userId AND ci.selected = TRUE
                ORDER BY ci.id FOR UPDATE
                """, Map.of("userId", userId), (rs, rowNum) -> {
            BigDecimal price = rs.getBigDecimal("price");
            int quantity = rs.getInt("quantity");
            return new CheckoutItem(rs.getLong("cart_item_id"), rs.getLong("product_id"),
                    rs.getString("slug"), rs.getString("name"), rs.getString("image_url"),
                    rs.getLong("sku_id"), rs.getString("sku_code"), rs.getString("label"), price,
                    quantity, price.multiply(BigDecimal.valueOf(quantity)));
        });
    }

    private List<OrderResponse.Item> orderItems(long orderId) {
        return jdbc.query("""
                SELECT id, product_id, sku_id, product_slug, product_name, sku_code, sku_label,
                       image_url, unit_price, quantity, line_amount, after_sale_quantity
                FROM order_items WHERE order_id = :orderId ORDER BY id
                """, Map.of("orderId", orderId), (rs, rowNum) -> new OrderResponse.Item(
                rs.getLong("id"), (Long) rs.getObject("product_id"), (Long) rs.getObject("sku_id"),
                rs.getString("product_slug"), rs.getString("product_name"), rs.getString("sku_code"),
                rs.getString("sku_label"), rs.getString("image_url"), rs.getBigDecimal("unit_price"),
                rs.getInt("quantity"), rs.getBigDecimal("line_amount"), rs.getInt("after_sale_quantity")
        ));
    }

    private OrderResponse.Shipment shipment(long orderId) {
        List<ShipmentRow> shipments = jdbc.query("""
                SELECT id, shipment_no, carrier_code, carrier_name, tracking_no, status, shipped_at, delivered_at
                FROM shipments WHERE order_id = :orderId ORDER BY id DESC LIMIT 1
                """, Map.of("orderId", orderId), (rs, rowNum) -> new ShipmentRow(
                rs.getLong("id"), rs.getString("shipment_no"), rs.getString("carrier_code"),
                rs.getString("carrier_name"), rs.getString("tracking_no"), rs.getString("status"),
                rs.getObject("shipped_at", LocalDateTime.class), rs.getObject("delivered_at", LocalDateTime.class)
        ));
        if (shipments.isEmpty()) {
            return null;
        }
        ShipmentRow shipment = shipments.get(0);
        List<OrderResponse.ShipmentEvent> events = jdbc.query("""
                SELECT event_code, description, location, occurred_at FROM shipment_events
                WHERE shipment_id = :shipmentId ORDER BY occurred_at DESC, id DESC
                """, Map.of("shipmentId", shipment.id()), (rs, rowNum) -> new OrderResponse.ShipmentEvent(
                rs.getString("event_code"), rs.getString("description"), rs.getString("location"),
                rs.getObject("occurred_at", LocalDateTime.class)
        ));
        return new OrderResponse.Shipment(shipment.shipmentNo(), shipment.carrierCode(), shipment.carrierName(),
                shipment.trackingNo(), external(shipment.status()), shipment.shippedAt(), shipment.deliveredAt(), events);
    }

    private LockedOrder lockOwnedOrder(long userId, String orderNo) {
        // 所有会改变订单状态的流程先锁订单主记录，串行化同一订单上的竞争操作。
        List<LockedOrder> orders = jdbc.query("""
                SELECT id, order_status, payment_status, fulfillment_status, payable_amount
                FROM orders WHERE order_no = :orderNo AND user_id = :userId FOR UPDATE
                """, Map.of("orderNo", orderNo, "userId", userId), (rs, rowNum) -> new LockedOrder(
                rs.getLong("id"), rs.getString("order_status"), rs.getString("payment_status"),
                rs.getString("fulfillment_status"), rs.getBigDecimal("payable_amount")
        ));
        if (orders.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "订单不存在");
        }
        return orders.get(0);
    }

    private void inventoryEvent(long skuId, String type, int availableDelta, int lockedDelta, String orderNo,
                                String note, long userId) {
        // 库存流水与库存更新同事务写入，供对账、故障恢复和运营审计使用。
        jdbc.update("""
                INSERT INTO inventory_transactions
                    (sku_id, transaction_type, available_delta, locked_delta, reference_type, reference_no,
                     note, created_by)
                VALUES (:skuId, :type, :available, :locked, 'ORDER', :orderNo, :note, :userId)
                """, Map.of("skuId", skuId, "type", type, "available", availableDelta,
                "locked", lockedDelta, "orderNo", orderNo, "note", note, "userId", userId));
    }

    private void statusEvent(long orderId, String type, String from, String to, String note, long userId) {
        // 状态历史记录业务语义，而不仅是最终状态，方便追踪每次流转由谁触发。
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("orderId", orderId).addValue("type", type).addValue("fromStatus", from)
                .addValue("toStatus", to).addValue("note", note).addValue("userId", userId);
        jdbc.update("""
                INSERT INTO order_status_history
                    (order_id, status_type, from_status, to_status, note, operator_type, operator_id)
                VALUES (:orderId, :type, :fromStatus, :toStatus, :note, 'USER', :userId)
                """, parameters);
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank() || "all".equalsIgnoreCase(status)) {
            return null;
        }
        String normalized = status.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        if (!ORDER_FILTERS.contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ORDER_STATUS", "不支持该订单状态筛选");
        }
        return normalized;
    }

    private String normalizeIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String normalized = key.trim();
        if (normalized.length() > 64) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY", "幂等键不能超过 64 个字符");
        }
        return normalized;
    }

    private static String external(String status) {
        return status == null ? null : status.toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private String newBusinessNo(String prefix, int randomLength) {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String random = UUID.randomUUID().toString().replace("-", "").substring(0, randomLength).toUpperCase(Locale.ROOT);
        return prefix + time + random;
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record CheckoutItem(long cartItemId, long productId, String productSlug, String productName,
                                String image, long skuId, String skuCode, String skuLabel, BigDecimal unitPrice,
                                int quantity, BigDecimal lineAmount) {
    }

    private record LockedOrder(long id, String orderStatus, String paymentStatus, String fulfillmentStatus,
                               BigDecimal payableAmount) {
    }

    private record ShipmentRow(long id, String shipmentNo, String carrierCode, String carrierName,
                               String trackingNo, String status, LocalDateTime shippedAt, LocalDateTime deliveredAt) {
    }

    private record OrderRow(
            long id, String orderNo, Long addressId, String status, String paymentStatus, String fulfillmentStatus,
            String afterSaleStatus, int itemCount, BigDecimal subtotal, BigDecimal deliveryFee,
            BigDecimal discount, BigDecimal total, BigDecimal paidAmount, String currency, String paymentMethod,
            String buyerNote, String recipientName, String recipientPhone, String province, String city,
            String district, String detail, String tag, LocalDateTime createdAt, LocalDateTime paidAt,
            LocalDateTime shippedAt, LocalDateTime completedAt, LocalDateTime cancelledAt
    ) {
        private OrderResponse toResponse(List<OrderResponse.Item> items, OrderResponse.Shipment shipment,
                                         List<OrderResponse.TimelineEvent> timeline) {
            return new OrderResponse(id, orderNo, external(status), external(paymentStatus),
                    external(fulfillmentStatus), external(afterSaleStatus), itemCount, subtotal, deliveryFee,
                    discount, total, paidAmount, currency, paymentMethod, buyerNote,
                    new OrderResponse.Address(addressId, recipientName, recipientPhone, province, city, district,
                            detail, tag), items, shipment, timeline, createdAt, paidAt, shippedAt, completedAt,
                    cancelledAt);
        }
    }
}
