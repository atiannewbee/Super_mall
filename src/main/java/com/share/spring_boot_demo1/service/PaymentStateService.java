package com.share.spring_boot_demo1.service;

import com.share.spring_boot_demo1.common.ApiException;
import com.share.spring_boot_demo1.payment.AlipayNotification;
import com.share.spring_boot_demo1.payment.PaymentRecord;
import com.share.spring_boot_demo1.payment.ProviderPaymentResult;
import com.share.spring_boot_demo1.payment.ProviderPaymentStatus;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 支付、订单与库存状态的事务服务。
 *
 * <p>外部渠道结果无论来自异步通知还是主动查询，最终都汇聚到本服务。
 * 同一订单始终先锁订单行再锁支付行，保持固定锁顺序并降低死锁风险。</p>
 */
@Service
public class PaymentStateService {
    private static final RowMapper<PaymentRecord> PAYMENT_MAPPER = (rs, rowNum) -> new PaymentRecord(
            rs.getLong("id"),
            rs.getString("payment_no"),
            rs.getLong("order_id"),
            rs.getLong("user_id"),
            rs.getString("order_no"),
            rs.getString("channel"),
            rs.getString("status"),
            rs.getBigDecimal("amount"),
            rs.getString("currency"),
            rs.getString("provider_trade_no"),
            rs.getObject("paid_at", LocalDateTime.class),
            rs.getObject("expires_at", LocalDateTime.class)
    );

    private static final String PAYMENT_SELECT = """
            SELECT p.id, p.payment_no, p.order_id, o.user_id, o.order_no, p.channel, p.status,
                   p.amount, p.currency, p.provider_trade_no, p.paid_at, p.expires_at
            FROM payments p
            JOIN orders o ON o.id = p.order_id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public PaymentStateService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 为待支付订单创建支付宝支付单；已有同渠道待支付单时直接复用。
     */
    @Transactional
    public PaymentRecord createAlipayPayment(long userId, String orderNo, LocalDateTime expiresAt) {
        LockedOrder order = lockOwnedOrder(userId, orderNo);
        if (!"PENDING_PAYMENT".equals(order.orderStatus()) || !"UNPAID".equals(order.paymentStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "ORDER_NOT_PAYABLE", "当前订单状态不能支付");
        }

        List<PaymentRecord> existing = jdbc.query(PAYMENT_SELECT + """
                WHERE p.order_id = :orderId AND p.status = 'PENDING'
                ORDER BY p.id DESC LIMIT 1 FOR UPDATE
                """, Map.of("orderId", order.id()), PAYMENT_MAPPER);
        if (!existing.isEmpty()) {
            PaymentRecord pending = existing.get(0);
            if ("ALIPAY".equals(pending.channel())) {
                return pending;
            }
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "PAYMENT_ALREADY_IN_PROGRESS",
                    "订单已有其他支付正在进行"
            );
        }

        String paymentNo = newPaymentNo();
        jdbc.update("""
                INSERT INTO payments
                    (payment_no, order_id, channel, status, amount, currency, expires_at)
                VALUES
                    (:paymentNo, :orderId, 'ALIPAY', 'PENDING', :amount, :currency, :expiresAt)
                """, new MapSqlParameterSource("paymentNo", paymentNo)
                .addValue("orderId", order.id())
                .addValue("amount", order.payableAmount())
                .addValue("currency", order.currency())
                .addValue("expiresAt", expiresAt));
        jdbc.update("""
                UPDATE orders SET payment_channel = 'ALIPAY', version = version + 1 WHERE id = :orderId
                """, Map.of("orderId", order.id()));
        return findRequired(paymentNo);
    }

    /**
     * 查询属于当前消费者的支付记录。
     */
    @Transactional(readOnly = true)
    public PaymentRecord findOwned(long userId, String paymentNo) {
        List<PaymentRecord> payments = jdbc.query(PAYMENT_SELECT + """
                WHERE p.payment_no = :paymentNo AND o.user_id = :userId
                """, Map.of("paymentNo", paymentNo, "userId", userId), PAYMENT_MAPPER);
        if (payments.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "支付记录不存在");
        }
        return payments.get(0);
    }

    /**
     * 校验支付单、有效期和订单状态，防止已关闭交易被再次拉起。
     */
    @Transactional(readOnly = true)
    public PaymentRecord findLaunchable(String paymentNo) {
        PaymentRecord payment = findRequired(paymentNo);
        if (!"ALIPAY".equals(payment.channel()) || !"PENDING".equals(payment.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "PAYMENT_NOT_LAUNCHABLE", "当前支付记录不能再次发起");
        }
        if (payment.expiresAt() != null && !payment.expiresAt().isAfter(LocalDateTime.now())) {
            throw new ApiException(HttpStatus.GONE, "PAYMENT_EXPIRED", "支付记录已经过期");
        }
        Integer payable = jdbc.queryForObject("""
                SELECT COUNT(*) FROM orders
                WHERE id = :orderId AND order_status = 'PENDING_PAYMENT' AND payment_status = 'UNPAID'
                """, Map.of("orderId", payment.orderId()), Integer.class);
        if (payable == null || payable != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "ORDER_NOT_PAYABLE", "当前订单状态不能支付");
        }
        return payment;
    }

    @Transactional(readOnly = true)
    public List<PaymentRecord> pendingForOwnedOrder(long userId, String orderNo) {
        assertOwnedOrder(userId, orderNo);
        return pendingForOrder(orderNo);
    }

    @Transactional(readOnly = true)
    public List<PaymentRecord> pendingForOrder(String orderNo) {
        return jdbc.query(PAYMENT_SELECT + """
                WHERE o.order_no = :orderNo AND p.status = 'PENDING'
                ORDER BY p.id
                """, Map.of("orderNo", orderNo), PAYMENT_MAPPER);
    }

    /**
     * 幂等应用已验签的支付宝异步通知。
     */
    @Transactional
    public PaymentRecord applyNotification(AlipayNotification notification) {
        PaymentRecord payment = lockOrderThenPayment(notification.paymentNo());
        requireAlipay(payment);
        validateAmount(payment, notification.amount());
        validateProviderTradeNo(payment, notification.providerTradeNo());

        // 通知 ID 具有唯一约束；重复通知不会再次推进状态或扣减库存。
        int inserted = jdbc.update("""
                INSERT IGNORE INTO payment_notifications
                    (payment_id, channel, notification_id, event_type, provider_trade_no, payload_hash)
                VALUES
                    (:paymentId, 'ALIPAY', :notificationId, :eventType, :tradeNo, :payloadHash)
                """, new MapSqlParameterSource("paymentId", payment.id())
                .addValue("notificationId", notification.notificationId())
                .addValue("eventType", notification.eventType())
                .addValue("tradeNo", notification.providerTradeNo())
                .addValue("payloadHash", notification.payloadHash()));
        if (inserted == 0) {
            return findRequired(payment.paymentNo());
        }
        applyLocked(payment, new ProviderPaymentResult(
                notification.status(),
                notification.providerTradeNo(),
                notification.amount(),
                null,
                null
        ), "支付宝异步通知");
        return findRequired(payment.paymentNo());
    }

    /**
     * 应用主动查询或关闭接口返回的渠道状态。
     */
    @Transactional
    public PaymentRecord applyProviderResult(String paymentNo, ProviderPaymentResult result, String source) {
        PaymentRecord payment = lockOrderThenPayment(paymentNo);
        requireAlipay(payment);
        if (result.status() == ProviderPaymentStatus.SUCCESS && result.amount() == null) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "ALIPAY_AMOUNT_MISSING",
                    "支付宝成功结果缺少交易金额"
            );
        }
        if (result.amount() != null) {
            validateAmount(payment, result.amount());
        }
        validateProviderTradeNo(payment, result.providerTradeNo());
        applyLocked(payment, result, source);
        jdbc.update("""
                UPDATE payments SET last_queried_at = CURRENT_TIMESTAMP(3) WHERE id = :paymentId
                """, Map.of("paymentId", payment.id()));
        return findRequired(paymentNo);
    }

    private void applyLocked(PaymentRecord payment, ProviderPaymentResult result, String source) {
        switch (result.status()) {
            case SUCCESS -> completePayment(payment, result, source);
            case CLOSED -> closePayment(payment, result);
            case FAILED -> failPayment(payment, result);
            case PENDING, NOT_FOUND, UNKNOWN -> updateProviderReference(payment, result);
        }
    }

    private void completePayment(PaymentRecord payment, ProviderPaymentResult result, String source) {
        // SUCCESS 重放按幂等成功处理；从 CLOSED/FAILED 逆转为成功必须进入人工核对。
        if ("SUCCESS".equals(payment.status())) {
            return;
        }
        if (!"PENDING".equals(payment.status())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "PAYMENT_STATE_CONFLICT",
                    "支付宝已返回成功，但本地支付记录已经关闭，请人工核对并退款"
            );
        }
        if (result.providerTradeNo() == null || result.providerTradeNo().isBlank()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "ALIPAY_TRADE_NO_MISSING", "支付宝成功结果缺少交易号");
        }

        LockedOrder order = lockOrder(payment.orderId());
        if (!"PENDING_PAYMENT".equals(order.orderStatus()) || !"UNPAID".equals(order.paymentStatus())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "ORDER_PAYMENT_STATE_CONFLICT",
                    "订单状态与支付宝成功结果冲突，请人工核对"
            );
        }

        // 支付成功后把预占库存转为已售库存；条件更新失败表示库存账已损坏，整笔事务回滚。
        List<OrderSku> items = jdbc.query("""
                SELECT sku_id, quantity FROM order_items
                WHERE order_id = :orderId AND sku_id IS NOT NULL ORDER BY id
                """, Map.of("orderId", order.id()),
                (rs, rowNum) -> new OrderSku(rs.getLong("sku_id"), rs.getInt("quantity")));
        for (OrderSku item : items) {
            int changed = jdbc.update("""
                    UPDATE sku_inventory
                    SET locked_quantity = locked_quantity - :quantity,
                        sold_quantity = sold_quantity + :quantity,
                        version = version + 1
                    WHERE sku_id = :skuId AND locked_quantity >= :quantity
                    """, Map.of("quantity", item.quantity(), "skuId", item.skuId()));
            if (changed == 0) {
                throw new IllegalStateException(
                        "Locked inventory is inconsistent for paid order " + payment.orderNo()
                );
            }
            jdbc.update("""
                    INSERT INTO inventory_transactions
                        (sku_id, transaction_type, available_delta, locked_delta,
                         reference_type, reference_no, note)
                    VALUES
                        (:skuId, 'DEDUCT', 0, -:quantity, 'ORDER', :orderNo, '支付宝支付成功扣减库存')
                    """, Map.of(
                    "skuId", item.skuId(),
                    "quantity", item.quantity(),
                    "orderNo", payment.orderNo()
            ));
        }

        jdbc.update("""
                UPDATE payments
                SET status = 'SUCCESS', provider_trade_no = :tradeNo, failure_code = NULL,
                    failure_message = NULL, paid_at = CURRENT_TIMESTAMP(3)
                WHERE id = :paymentId
                """, Map.of("tradeNo", result.providerTradeNo(), "paymentId", payment.id()));
        jdbc.update("""
                UPDATE orders
                SET order_status = 'PROCESSING', payment_status = 'PAID',
                    paid_amount = payable_amount, payment_channel = 'ALIPAY',
                    paid_at = CURRENT_TIMESTAMP(3), version = version + 1
                WHERE id = :orderId
                """, Map.of("orderId", order.id()));
        statusEvent(order.id(), "PAYMENT", "UNPAID", "PAID", source + "确认支付成功");
        statusEvent(order.id(), "ORDER", "PENDING_PAYMENT", "PROCESSING", "订单进入备货");
    }

    private void closePayment(PaymentRecord payment, ProviderPaymentResult result) {
        if (!"PENDING".equals(payment.status())) {
            return;
        }
        jdbc.update("""
                UPDATE payments
                SET status = 'CLOSED', provider_trade_no = COALESCE(:tradeNo, provider_trade_no),
                    closed_at = CURRENT_TIMESTAMP(3), failure_code = :failureCode,
                    failure_message = :failureMessage
                WHERE id = :paymentId
                """, new MapSqlParameterSource("tradeNo", result.providerTradeNo())
                .addValue("failureCode", trim(result.failureCode(), 50))
                .addValue("failureMessage", trim(result.failureMessage(), 255))
                .addValue("paymentId", payment.id()));
    }

    private void failPayment(PaymentRecord payment, ProviderPaymentResult result) {
        if (!"PENDING".equals(payment.status())) {
            return;
        }
        jdbc.update("""
                UPDATE payments
                SET status = 'FAILED', provider_trade_no = COALESCE(:tradeNo, provider_trade_no),
                    failure_code = :failureCode, failure_message = :failureMessage
                WHERE id = :paymentId
                """, new MapSqlParameterSource("tradeNo", result.providerTradeNo())
                .addValue("failureCode", trim(result.failureCode(), 50))
                .addValue("failureMessage", trim(result.failureMessage(), 255))
                .addValue("paymentId", payment.id()));
    }

    private void updateProviderReference(PaymentRecord payment, ProviderPaymentResult result) {
        if (result.providerTradeNo() == null || result.providerTradeNo().isBlank()) {
            return;
        }
        jdbc.update("""
                UPDATE payments SET provider_trade_no = COALESCE(provider_trade_no, :tradeNo)
                WHERE id = :paymentId
                """, Map.of("tradeNo", result.providerTradeNo(), "paymentId", payment.id()));
    }

    private void statusEvent(long orderId, String type, String from, String to, String note) {
        jdbc.update("""
                INSERT INTO order_status_history
                    (order_id, status_type, from_status, to_status, note, operator_type)
                VALUES
                    (:orderId, :type, :fromStatus, :toStatus, :note, 'SYSTEM')
                """, new MapSqlParameterSource("orderId", orderId)
                .addValue("type", type)
                .addValue("fromStatus", from)
                .addValue("toStatus", to)
                .addValue("note", note));
    }

    private PaymentRecord findRequired(String paymentNo) {
        List<PaymentRecord> payments = jdbc.query(PAYMENT_SELECT + """
                WHERE p.payment_no = :paymentNo
                """, Map.of("paymentNo", paymentNo), PAYMENT_MAPPER);
        if (payments.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "支付记录不存在");
        }
        return payments.get(0);
    }

    private PaymentRecord lockRequired(String paymentNo) {
        List<PaymentRecord> payments = jdbc.query(PAYMENT_SELECT + """
                WHERE p.payment_no = :paymentNo FOR UPDATE
                """, Map.of("paymentNo", paymentNo), PAYMENT_MAPPER);
        if (payments.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "支付记录不存在");
        }
        return payments.get(0);
    }

    private PaymentRecord lockOrderThenPayment(String paymentNo) {
        PaymentRecord preview = findRequired(paymentNo);
        // 所有支付状态入口采用相同的“订单 -> 支付单”锁顺序，避免并发通知与关单互锁。
        lockOrder(preview.orderId());
        return lockRequired(paymentNo);
    }

    private LockedOrder lockOwnedOrder(long userId, String orderNo) {
        List<LockedOrder> orders = jdbc.query("""
                SELECT id, order_status, payment_status, payable_amount, currency
                FROM orders WHERE order_no = :orderNo AND user_id = :userId FOR UPDATE
                """, Map.of("orderNo", orderNo, "userId", userId), (rs, rowNum) -> new LockedOrder(
                rs.getLong("id"),
                rs.getString("order_status"),
                rs.getString("payment_status"),
                rs.getBigDecimal("payable_amount"),
                rs.getString("currency")
        ));
        if (orders.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "订单不存在");
        }
        return orders.get(0);
    }

    private LockedOrder lockOrder(long orderId) {
        return jdbc.queryForObject("""
                SELECT id, order_status, payment_status, payable_amount, currency
                FROM orders WHERE id = :orderId FOR UPDATE
                """, Map.of("orderId", orderId), (rs, rowNum) -> new LockedOrder(
                rs.getLong("id"),
                rs.getString("order_status"),
                rs.getString("payment_status"),
                rs.getBigDecimal("payable_amount"),
                rs.getString("currency")
        ));
    }

    private void assertOwnedOrder(long userId, String orderNo) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM orders WHERE order_no = :orderNo AND user_id = :userId
                """, Map.of("orderNo", orderNo, "userId", userId), Integer.class);
        if (count == null || count != 1) {
            throw new ApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "订单不存在");
        }
    }

    private void requireAlipay(PaymentRecord payment) {
        if (!"ALIPAY".equals(payment.channel())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAYMENT_CHANNEL_MISMATCH", "支付渠道不匹配");
        }
    }

    private void validateAmount(PaymentRecord payment, BigDecimal providerAmount) {
        // 永远以本地创建支付单时保存的金额为准，不信任通知或前端金额。
        if (providerAmount == null || payment.amount().compareTo(providerAmount) != 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAYMENT_AMOUNT_MISMATCH", "支付通知金额与订单不一致");
        }
    }

    private void validateProviderTradeNo(PaymentRecord payment, String providerTradeNo) {
        // 一个本地支付单一旦绑定渠道交易号，后续任何不同交易号都视为冲突。
        if (providerTradeNo != null
                && payment.providerTradeNo() != null
                && !payment.providerTradeNo().equals(providerTradeNo)) {
            throw new ApiException(HttpStatus.CONFLICT, "PROVIDER_TRADE_NO_MISMATCH", "支付宝交易号不一致");
        }
    }

    private String newPaymentNo() {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String random = UUID.randomUUID().toString().replace("-", "")
                .substring(0, 14).toUpperCase(Locale.ROOT);
        return "PAY" + time + random;
    }

    private String trim(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private record LockedOrder(
            long id,
            String orderStatus,
            String paymentStatus,
            BigDecimal payableAmount,
            String currency
    ) {
    }

    private record OrderSku(long skuId, int quantity) {
    }
}
