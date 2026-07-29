package com.share.spring_boot_demo1.service;

import com.share.spring_boot_demo1.common.ApiException;
import com.share.spring_boot_demo1.common.PageResponse;
import com.share.spring_boot_demo1.dto.AfterSaleCreateRequest;
import com.share.spring_boot_demo1.dto.AfterSaleResponse;
import com.share.spring_boot_demo1.dto.ReturnShipmentRequest;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 消费者售后申请服务。
 *
 * <p>申请金额根据订单项成交单价和申请数量计算，不接受客户端传入金额。
 * order_items.after_sale_quantity 记录已占用的售后数量，防止同一商品被重复超量申请。</p>
 */
@Service
public class AfterSaleService {
    private static final Set<String> ELIGIBLE_ORDER_STATUSES = Set.of("PROCESSING", "SHIPPED", "COMPLETED", "AFTER_SALE");
    private static final Set<String> FILTER_STATUSES = Set.of(
            "REQUESTED", "REVIEWING", "APPROVED", "REJECTED", "RETURNING", "REFUNDING", "COMPLETED", "CANCELLED"
    );
    private final NamedParameterJdbcTemplate jdbc;

    public AfterSaleService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 创建售后申请并把相应订单商品数量标记为已申请。
     */
    @Transactional
    public AfterSaleResponse create(long userId, AfterSaleCreateRequest request) {
        List<OrderState> orders = jdbc.query("""
                SELECT id, order_status, payment_status FROM orders
                WHERE order_no = :orderNo AND user_id = :userId FOR UPDATE
                """, Map.of("orderNo", request.orderNo(), "userId", userId),
                (rs, rowNum) -> new OrderState(rs.getLong("id"), rs.getString("order_status"),
                        rs.getString("payment_status")));
        if (orders.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "订单不存在");
        }
        OrderState order = orders.get(0);
        if (!"PAID".equals(order.paymentStatus()) || !ELIGIBLE_ORDER_STATUSES.contains(order.orderStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "ORDER_NOT_AFTER_SALE_ELIGIBLE", "当前订单不能申请售后");
        }

        Set<Long> requestedIds = new HashSet<>();
        List<RequestedItem> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        // 在订单锁内逐项锁定并校验剩余可申请数量，避免并发售后超出购买数量。
        for (AfterSaleCreateRequest.Item requested : request.items()) {
            if (!requestedIds.add(requested.orderItemId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "DUPLICATE_AFTER_SALE_ITEM", "同一订单商品不能重复提交");
            }
            List<RequestedItem> matches = jdbc.query("""
                    SELECT oi.id, oi.product_name, oi.sku_label, oi.image_url, oi.unit_price,
                           oi.quantity, oi.after_sale_quantity
                    FROM order_items oi JOIN orders o ON o.id = oi.order_id
                    WHERE oi.id = :itemId AND oi.order_id = :orderId AND o.user_id = :userId FOR UPDATE
                    """, Map.of("itemId", requested.orderItemId(), "orderId", order.id(), "userId", userId),
                    (rs, rowNum) -> new RequestedItem(rs.getLong("id"), rs.getString("product_name"),
                            rs.getString("sku_label"), rs.getString("image_url"), rs.getBigDecimal("unit_price"),
                            rs.getInt("quantity"), rs.getInt("after_sale_quantity"), requested.quantity()));
            if (matches.isEmpty()) {
                throw new ApiException(HttpStatus.NOT_FOUND, "ORDER_ITEM_NOT_FOUND", "订单商品不存在");
            }
            RequestedItem item = matches.get(0);
            if (item.afterSaleQuantity() + requested.quantity() > item.orderQuantity()) {
                throw new ApiException(HttpStatus.CONFLICT, "AFTER_SALE_QUANTITY_EXCEEDED",
                        item.productName() + " 可申请售后数量不足");
            }
            items.add(item);
            // 退款申请金额仅使用订单成交单价计算，忽略客户端任何金额概念。
            total = total.add(item.requestedAmount());
        }

        String afterSaleNo = newBusinessNo();
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("afterSaleNo", afterSaleNo).addValue("orderId", order.id())
                .addValue("userId", userId).addValue("type", request.type())
                .addValue("reasonCode", trimToNull(request.reasonCode()))
                .addValue("reason", request.reasonDescription().trim()).addValue("amount", total)
                .addValue("note", trimToNull(request.customerNote()));
        jdbc.update("""
                INSERT INTO after_sale_requests
                    (after_sale_no, order_id, user_id, type, reason_code, reason_description,
                     requested_amount, customer_note)
                VALUES (:afterSaleNo, :orderId, :userId, :type, :reasonCode, :reason, :amount, :note)
                """, parameters, keyHolder, new String[]{"id"});
        long afterSaleId = keyHolder.getKey().longValue();
        for (RequestedItem item : items) {
            jdbc.update("""
                    INSERT INTO after_sale_items (after_sale_id, order_item_id, quantity, requested_amount)
                    VALUES (:afterSaleId, :itemId, :quantity, :amount)
                    """, Map.of("afterSaleId", afterSaleId, "itemId", item.id(),
                    "quantity", item.requestQuantity(), "amount", item.requestedAmount()));
            jdbc.update("""
                    UPDATE order_items SET after_sale_quantity = after_sale_quantity + :quantity WHERE id = :itemId
                    """, Map.of("quantity", item.requestQuantity(), "itemId", item.id()));
        }
        jdbc.update("""
                UPDATE orders SET order_status = 'AFTER_SALE', after_sale_status = 'REQUESTED', version = version + 1
                WHERE id = :orderId
                """, Map.of("orderId", order.id()));
        afterSaleEvent(afterSaleId, null, "REQUESTED", "用户提交售后申请", userId);
        orderEvent(order.id(), "AFTER_SALE", "NONE", "REQUESTED", "已提交售后申请", userId);
        if (!"AFTER_SALE".equals(order.orderStatus())) {
            orderEvent(order.id(), "ORDER", order.orderStatus(), "AFTER_SALE", "订单进入售后流程", userId);
        }
        return getRequired(userId, afterSaleNo);
    }

    /**
     * 按售后状态分页查询当前用户申请。
     */
    @Transactional(readOnly = true)
    public PageResponse<AfterSaleResponse> list(long userId, String status, int page, int size) {
        String normalized = normalizeStatus(status);
        String condition = normalized == null ? "" : " AND status = :status";
        MapSqlParameterSource parameters = new MapSqlParameterSource("userId", userId)
                .addValue("status", normalized).addValue("limit", size).addValue("offset", page * size);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM after_sale_requests WHERE user_id = :userId" + condition,
                parameters, Long.class);
        List<String> numbers = jdbc.queryForList("""
                SELECT after_sale_no FROM after_sale_requests WHERE user_id = :userId
                """ + condition + " ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset",
                parameters, String.class);
        return PageResponse.of(numbers.stream().map(number -> getRequired(userId, number)).toList(), page, size,
                total == null ? 0 : total);
    }

    /**
     * 查询售后详情、申请商品和状态时间线。
     */
    @Transactional(readOnly = true)
    public AfterSaleResponse getRequired(long userId, String afterSaleNo) {
        List<AfterSaleRow> rows = jdbc.query("""
                SELECT a.id, a.after_sale_no, o.order_no, a.type, a.status, a.reason_code,
                       a.reason_description, a.requested_amount, a.refunded_amount, a.customer_note,
                       a.admin_note, a.return_carrier, a.return_tracking_no, a.created_at, a.approved_at,
                       a.completed_at, a.cancelled_at
                FROM after_sale_requests a JOIN orders o ON o.id = a.order_id
                WHERE a.after_sale_no = :number AND a.user_id = :userId
                """, Map.of("number", afterSaleNo, "userId", userId), (rs, rowNum) -> new AfterSaleRow(
                rs.getLong("id"), rs.getString("after_sale_no"), rs.getString("order_no"),
                rs.getString("type"), rs.getString("status"), rs.getString("reason_code"),
                rs.getString("reason_description"), rs.getBigDecimal("requested_amount"),
                rs.getBigDecimal("refunded_amount"), rs.getString("customer_note"), rs.getString("admin_note"),
                rs.getString("return_carrier"), rs.getString("return_tracking_no"),
                rs.getObject("created_at", LocalDateTime.class), rs.getObject("approved_at", LocalDateTime.class),
                rs.getObject("completed_at", LocalDateTime.class), rs.getObject("cancelled_at", LocalDateTime.class)
        ));
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "AFTER_SALE_NOT_FOUND", "售后申请不存在");
        }
        AfterSaleRow row = rows.get(0);
        List<AfterSaleResponse.Item> items = jdbc.query("""
                SELECT ai.id, ai.order_item_id, oi.product_name, oi.sku_label, oi.image_url,
                       ai.quantity, ai.requested_amount
                FROM after_sale_items ai JOIN order_items oi ON oi.id = ai.order_item_id
                WHERE ai.after_sale_id = :id ORDER BY ai.id
                """, Map.of("id", row.id()), (rs, rowNum) -> new AfterSaleResponse.Item(
                rs.getLong("id"), rs.getLong("order_item_id"), rs.getString("product_name"),
                rs.getString("sku_label"), rs.getString("image_url"), rs.getInt("quantity"),
                rs.getBigDecimal("requested_amount")
        ));
        List<AfterSaleResponse.Event> events = jdbc.query("""
                SELECT from_status, to_status, description, operator_type, created_at
                FROM after_sale_events WHERE after_sale_id = :id ORDER BY created_at, id
                """, Map.of("id", row.id()), (rs, rowNum) -> new AfterSaleResponse.Event(
                external(rs.getString("from_status")), external(rs.getString("to_status")),
                rs.getString("description"), external(rs.getString("operator_type")),
                rs.getObject("created_at", LocalDateTime.class)
        ));
        return row.toResponse(items, events);
    }

    /**
     * 取消尚未进入实际退货或退款阶段的申请，并归还已占用的售后数量。
     */
    @Transactional
    public AfterSaleResponse cancel(long userId, String afterSaleNo) {
        LockedAfterSale afterSale = lockOwned(userId, afterSaleNo);
        if (!Set.of("REQUESTED", "REVIEWING").contains(afterSale.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "AFTER_SALE_NOT_CANCELLABLE", "当前售后状态不能取消");
        }
        List<AfterSaleItemQuantity> itemQuantities = jdbc.query("""
                SELECT order_item_id, quantity FROM after_sale_items WHERE after_sale_id = :id FOR UPDATE
                """, Map.of("id", afterSale.id()), (rs, rowNum) ->
                new AfterSaleItemQuantity(rs.getLong("order_item_id"), rs.getInt("quantity")));
        for (AfterSaleItemQuantity item : itemQuantities) {
            int changed = jdbc.update("""
                    UPDATE order_items SET after_sale_quantity = after_sale_quantity - :quantity
                    WHERE id = :itemId AND after_sale_quantity >= :quantity
                    """, Map.of("quantity", item.quantity(), "itemId", item.orderItemId()));
            if (changed == 0) {
                throw new IllegalStateException("After-sale quantity is inconsistent for " + afterSaleNo);
            }
        }
        jdbc.update("""
                UPDATE after_sale_requests SET status = 'CANCELLED', cancelled_at = CURRENT_TIMESTAMP(3)
                WHERE id = :id
                """, Map.of("id", afterSale.id()));
        afterSaleEvent(afterSale.id(), afterSale.status(), "CANCELLED", "用户取消售后申请", userId);

        Integer active = jdbc.queryForObject("""
                SELECT COUNT(*) FROM after_sale_requests
                WHERE order_id = :orderId AND status NOT IN ('REJECTED', 'COMPLETED', 'CANCELLED')
                """, Map.of("orderId", afterSale.orderId()), Integer.class);
        if (active == null || active == 0) {
            // 订单没有其他进行中售后时，根据支付、履约和完成时间恢复原主流程状态。
            OrderRestoreState state = jdbc.queryForObject("""
                    SELECT payment_status, fulfillment_status, completed_at FROM orders WHERE id = :orderId FOR UPDATE
                    """, Map.of("orderId", afterSale.orderId()), (rs, rowNum) -> new OrderRestoreState(
                    rs.getString("payment_status"), rs.getString("fulfillment_status"),
                    rs.getObject("completed_at", LocalDateTime.class)));
            String restored = state.completedAt() != null ? "COMPLETED"
                    : "SHIPPED".equals(state.fulfillmentStatus()) ? "SHIPPED"
                    : "PAID".equals(state.paymentStatus()) ? "PROCESSING" : "PENDING_PAYMENT";
            jdbc.update("""
                    UPDATE orders SET order_status = :status, after_sale_status = 'NONE', version = version + 1
                    WHERE id = :orderId
                    """, Map.of("status", restored, "orderId", afterSale.orderId()));
            orderEvent(afterSale.orderId(), "AFTER_SALE", "REQUESTED", "NONE", "售后申请已取消", userId);
            orderEvent(afterSale.orderId(), "ORDER", "AFTER_SALE", restored, "订单恢复原流程", userId);
        }
        return getRequired(userId, afterSaleNo);
    }

    /**
     * 为已批准的退货申请登记用户寄回物流。
     */
    @Transactional
    public AfterSaleResponse submitReturnShipment(long userId, String afterSaleNo, ReturnShipmentRequest request) {
        LockedAfterSale afterSale = lockOwned(userId, afterSaleNo);
        if (!Set.of("APPROVED", "RETURNING").contains(afterSale.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "RETURN_SHIPMENT_NOT_ALLOWED", "当前售后状态不能填写退货物流");
        }
        jdbc.update("""
                UPDATE after_sale_requests SET return_carrier = :carrier, return_tracking_no = :tracking,
                    status = 'RETURNING' WHERE id = :id
                """, Map.of("carrier", request.carrier().trim(), "tracking", request.trackingNo().trim(),
                "id", afterSale.id()));
        if (!"RETURNING".equals(afterSale.status())) {
            afterSaleEvent(afterSale.id(), afterSale.status(), "RETURNING", "用户已寄回商品", userId);
        }
        return getRequired(userId, afterSaleNo);
    }

    private LockedAfterSale lockOwned(long userId, String number) {
        // 售后状态变更前锁定申请，并在 SQL 中校验用户归属。
        List<LockedAfterSale> rows = jdbc.query("""
                SELECT id, order_id, status FROM after_sale_requests
                WHERE after_sale_no = :number AND user_id = :userId FOR UPDATE
                """, Map.of("number", number, "userId", userId),
                (rs, rowNum) -> new LockedAfterSale(rs.getLong("id"), rs.getLong("order_id"), rs.getString("status")));
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "AFTER_SALE_NOT_FOUND", "售后申请不存在");
        }
        return rows.get(0);
    }

    private void afterSaleEvent(long id, String from, String to, String description, long userId) {
        MapSqlParameterSource parameters = new MapSqlParameterSource("id", id)
                .addValue("fromStatus", from).addValue("toStatus", to)
                .addValue("description", description).addValue("userId", userId);
        jdbc.update("""
                INSERT INTO after_sale_events
                    (after_sale_id, from_status, to_status, description, operator_type, operator_id)
                VALUES (:id, :fromStatus, :toStatus, :description, 'USER', :userId)
                """, parameters);
    }

    private void orderEvent(long orderId, String type, String from, String to, String note, long userId) {
        MapSqlParameterSource parameters = new MapSqlParameterSource("orderId", orderId)
                .addValue("type", type).addValue("fromStatus", from).addValue("toStatus", to)
                .addValue("note", note).addValue("userId", userId);
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
        if (!FILTER_STATUSES.contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_AFTER_SALE_STATUS", "不支持该售后状态筛选");
        }
        return normalized;
    }

    private String newBusinessNo() {
        return "AS" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + UUID.randomUUID().toString().replace("-", "").substring(0, 14).toUpperCase(Locale.ROOT);
    }

    private static String external(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record OrderState(long id, String orderStatus, String paymentStatus) {
    }

    private record RequestedItem(long id, String productName, String skuLabel, String image, BigDecimal unitPrice,
                                 int orderQuantity, int afterSaleQuantity, int requestQuantity) {
        private BigDecimal requestedAmount() {
            return unitPrice.multiply(BigDecimal.valueOf(requestQuantity));
        }
    }

    private record LockedAfterSale(long id, long orderId, String status) {
    }

    private record OrderRestoreState(String paymentStatus, String fulfillmentStatus, LocalDateTime completedAt) {
    }

    private record AfterSaleItemQuantity(long orderItemId, int quantity) {
    }

    private record AfterSaleRow(
            long id, String number, String orderNo, String type, String status, String reasonCode,
            String reason, BigDecimal requestedAmount, BigDecimal refundedAmount, String customerNote,
            String adminNote, String returnCarrier, String returnTrackingNo, LocalDateTime createdAt,
            LocalDateTime approvedAt, LocalDateTime completedAt, LocalDateTime cancelledAt
    ) {
        private AfterSaleResponse toResponse(List<AfterSaleResponse.Item> items, List<AfterSaleResponse.Event> events) {
            return new AfterSaleResponse(id, number, orderNo, external(type), external(status), reasonCode, reason,
                    requestedAmount, refundedAmount, customerNote, adminNote, returnCarrier, returnTrackingNo,
                    items, events, createdAt, approvedAt, completedAt, cancelledAt);
        }
    }
}
