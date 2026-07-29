package com.share.spring_boot_demo1.service;

import com.share.spring_boot_demo1.config.OrderProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 待支付订单自动关闭任务。
 *
 * <p>外部支付查询和关闭在短事务之外执行；只有渠道确认已无待支付交易后，
 * 才在独立数据库事务中锁定订单、释放库存并关闭订单。</p>
 */
@Service
public class OrderExpirationService {
    private static final Logger log = LoggerFactory.getLogger(OrderExpirationService.class);
    private final NamedParameterJdbcTemplate jdbc;
    private final OrderProperties properties;
    private final PaymentService paymentService;
    private final TransactionTemplate transactions;

    public OrderExpirationService(
            NamedParameterJdbcTemplate jdbc,
            OrderProperties properties,
            PaymentService paymentService,
            PlatformTransactionManager transactionManager
    ) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.paymentService = paymentService;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /**
     * 分批扫描超过支付时限的订单，单笔失败不会阻塞其他订单。
     */
    @Scheduled(
            fixedDelayString = "${app.order.expiration-scan-interval:PT1M}",
            initialDelayString = "${app.order.expiration-scan-interval:PT1M}"
    )
    public void expirePendingOrders() {
        LocalDateTime cutoff = LocalDateTime.now().minus(properties.pendingPaymentTtl());
        List<ExpiredOrder> orders = jdbc.query("""
                SELECT id, order_no FROM orders
                WHERE order_status = 'PENDING_PAYMENT' AND payment_status = 'UNPAID' AND created_at < :cutoff
                ORDER BY created_at LIMIT 100
                """, Map.of("cutoff", cutoff),
                (rs, rowNum) -> new ExpiredOrder(rs.getLong("id"), rs.getString("order_no")));
        int expiredCount = 0;
        for (ExpiredOrder order : orders) {
            try {
                // 先处理外部渠道，避免持有数据库锁等待网络响应。
                if (!paymentService.closePendingForExpiration(order.number())) {
                    continue;
                }
                Boolean expired = transactions.execute(status -> expireIfStillPending(order, cutoff));
                if (Boolean.TRUE.equals(expired)) {
                    expiredCount++;
                }
            } catch (RuntimeException exception) {
                log.warn(
                        "Skipped expiration for order {} because its provider payment could not be safely closed",
                        order.number(),
                        exception
                );
            }
        }
        if (expiredCount > 0) {
            log.info("Expired {} unpaid orders and released their inventory", expiredCount);
        }
    }

    private boolean expireIfStillPending(ExpiredOrder candidate, LocalDateTime cutoff) {
        // 再次在锁内检查订单和支付状态，消除扫描后用户恰好完成付款的竞态窗口。
        List<ExpiredOrder> locked = jdbc.query("""
                SELECT o.id, o.order_no FROM orders o
                WHERE o.id = :orderId AND o.order_status = 'PENDING_PAYMENT'
                    AND o.payment_status = 'UNPAID' AND o.created_at < :cutoff
                    AND NOT EXISTS (
                        SELECT 1 FROM payments p
                        WHERE p.order_id = o.id AND p.status = 'PENDING'
                    )
                FOR UPDATE
                """, Map.of("orderId", candidate.id(), "cutoff", cutoff),
                (rs, rowNum) -> new ExpiredOrder(rs.getLong("id"), rs.getString("order_no")));
        if (locked.isEmpty()) {
            return false;
        }
        expire(locked.get(0));
        return true;
    }

    private void expire(ExpiredOrder order) {
        List<OrderSku> items = jdbc.query("""
                SELECT sku_id, quantity FROM order_items WHERE order_id = :orderId AND sku_id IS NOT NULL
                """, Map.of("orderId", order.id()),
                (rs, rowNum) -> new OrderSku(rs.getLong("sku_id"), rs.getInt("quantity")));
        // 条件更新保证只能释放真实存在的预占库存，账目异常时整笔关单事务回滚。
        for (OrderSku item : items) {
            int changed = jdbc.update("""
                    UPDATE sku_inventory SET available_quantity = available_quantity + :quantity,
                        locked_quantity = locked_quantity - :quantity, version = version + 1
                    WHERE sku_id = :skuId AND locked_quantity >= :quantity
                    """, Map.of("quantity", item.quantity(), "skuId", item.skuId()));
            if (changed == 0) {
                throw new IllegalStateException("Locked inventory is inconsistent for expired order " + order.number());
            }
            jdbc.update("""
                    INSERT INTO inventory_transactions
                        (sku_id, transaction_type, available_delta, locked_delta, reference_type, reference_no, note)
                    VALUES (:skuId, 'UNLOCK', :quantity, -:quantity, 'ORDER', :orderNo, '待支付订单超时释放库存')
                    """, Map.of("skuId", item.skuId(), "quantity", item.quantity(), "orderNo", order.number()));
        }
        jdbc.update("""
                UPDATE payments SET status = 'CLOSED', closed_at = CURRENT_TIMESTAMP(3)
                WHERE order_id = :orderId AND status = 'PENDING'
                """, Map.of("orderId", order.id()));
        jdbc.update("""
                UPDATE orders SET order_status = 'CANCELLED', payment_status = 'CLOSED',
                    cancelled_at = CURRENT_TIMESTAMP(3), version = version + 1 WHERE id = :orderId
                """, Map.of("orderId", order.id()));
        insertHistory(order.id(), "ORDER", "PENDING_PAYMENT", "CANCELLED", "待支付订单已超时关闭");
        insertHistory(order.id(), "PAYMENT", "UNPAID", "CLOSED", "支付窗口已关闭");
    }

    private void insertHistory(long orderId, String type, String from, String to, String note) {
        jdbc.update("""
                INSERT INTO order_status_history
                    (order_id, status_type, from_status, to_status, note, operator_type)
                VALUES (:orderId, :type, :fromStatus, :toStatus, :note, 'SYSTEM')
                """, new MapSqlParameterSource("orderId", orderId).addValue("type", type)
                .addValue("fromStatus", from).addValue("toStatus", to).addValue("note", note));
    }

    private record ExpiredOrder(long id, String number) {
    }

    private record OrderSku(long skuId, int quantity) {
    }
}
