package com.share.spring_boot_demo1;

import com.share.spring_boot_demo1.payment.AlipayGateway;
import com.share.spring_boot_demo1.payment.AlipayNotification;
import com.share.spring_boot_demo1.payment.PaymentOrder;
import com.share.spring_boot_demo1.payment.ProviderPaymentResult;
import com.share.spring_boot_demo1.payment.ProviderPaymentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证支付宝支付单、主动查询、异步通知幂等、金额校验和库存扣减的一致性。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(AlipayPaymentFlowIntegrationTests.PaymentTestConfiguration.class)
class AlipayPaymentFlowIntegrationTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void completesAlipayFlowAndDeduplicatesProviderNotification() throws Exception {
        int lockedBefore = jdbc.queryForObject(
                "SELECT locked_quantity FROM sku_inventory WHERE sku_id = 1001",
                Integer.class
        );
        int soldBefore = jdbc.queryForObject(
                "SELECT sold_quantity FROM sku_inventory WHERE sku_id = 1001",
                Integer.class
        );
        String token = register("alipay-" + System.nanoTime() + "@example.com");
        long addressId = createAddress(token);
        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skuCode\":\"aether-x1-256-black\",\"quantity\":1}"))
                .andExpect(status().isOk());

        JsonNode order = body(mockMvc.perform(post("/api/orders")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", "alipay-order-" + System.nanoTime())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "addressId", addressId,
                                "paymentChannel", "ALIPAY",
                                "invoiceRequired", false
                        ))))
                .andExpect(status().isCreated())
                .andReturn());
        String orderNo = order.get("orderNo").textValue();

        JsonNode launch = body(mockMvc.perform(post("/api/orders/{orderNo}/payments", orderNo)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":\"ALIPAY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("pending"))
                .andExpect(jsonPath("$.action").value("redirect"))
                .andExpect(jsonPath("$.launchUrl").isNotEmpty())
                .andReturn());
        String paymentNo = launch.get("paymentNo").textValue();

        mockMvc.perform(post("/api/orders/{orderNo}/payments", orderNo)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":\"ALIPAY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentNo").value(paymentNo));

        mockMvc.perform(get("/api/payments/alipay/{paymentNo}/launch", paymentNo))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(paymentNo)));

        mockMvc.perform(post("/api/payments/alipay/notify")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("out_trade_no=" + paymentNo
                                + "&trade_no=2026072322000000000001"
                                + "&trade_status=TRADE_SUCCESS"
                                + "&total_amount=0.01"
                                + "&notify_id=tampered-amount"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("failure"));
        assertThat(jdbc.queryForObject(
                "SELECT status FROM payments WHERE payment_no = ?",
                String.class,
                paymentNo
        )).isEqualTo("PENDING");

        String notification = "out_trade_no=" + paymentNo
                + "&trade_no=2026072322000000000001"
                + "&trade_status=TRADE_SUCCESS"
                + "&total_amount=4299.00"
                + "&notify_id=notify-1";
        mockMvc.perform(post("/api/payments/alipay/notify")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content(notification))
                .andExpect(status().isOk())
                .andExpect(content().string("success"));
        mockMvc.perform(post("/api/payments/alipay/notify")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content(notification))
                .andExpect(status().isOk())
                .andExpect(content().string("success"));

        mockMvc.perform(get("/api/orders/{orderNo}", orderNo)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("processing"))
                .andExpect(jsonPath("$.paymentStatus").value("paid"))
                .andExpect(jsonPath("$.paidAmount").value(4299.00));
        mockMvc.perform(get("/api/payments/{paymentNo}", paymentNo)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.amount").value(4299.00));

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM payment_notifications WHERE notification_id = 'notify-1'",
                Integer.class
        )).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT locked_quantity FROM sku_inventory WHERE sku_id = 1001",
                Integer.class
        )).isEqualTo(lockedBefore);
        assertThat(jdbc.queryForObject(
                "SELECT sold_quantity FROM sku_inventory WHERE sku_id = 1001",
                Integer.class
        )).isEqualTo(soldBefore + 1);

        mockMvc.perform(get("/api/payments/{paymentNo}", paymentNo))
                .andExpect(status().isUnauthorized());
        String otherToken = register("alipay-other-" + System.nanoTime() + "@example.com");
        mockMvc.perform(get("/api/payments/{paymentNo}", paymentNo)
                        .header("Authorization", bearer(otherToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));
    }

    @Test
    void closesProviderPaymentBeforeCancellingOrderAndUnlockingInventory() throws Exception {
        int availableBefore = jdbc.queryForObject(
                "SELECT available_quantity FROM sku_inventory WHERE sku_id = 1015",
                Integer.class
        );
        int lockedBefore = jdbc.queryForObject(
                "SELECT locked_quantity FROM sku_inventory WHERE sku_id = 1015",
                Integer.class
        );
        String token = register("alipay-cancel-" + System.nanoTime() + "@example.com");
        long addressId = createAddress(token);
        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skuCode\":\"homehub-mini-white\",\"quantity\":1}"))
                .andExpect(status().isOk());
        JsonNode order = body(mockMvc.perform(post("/api/orders")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", "alipay-cancel-" + System.nanoTime())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "addressId", addressId,
                                "paymentChannel", "ALIPAY",
                                "invoiceRequired", false
                        ))))
                .andExpect(status().isCreated())
                .andReturn());
        String orderNo = order.get("orderNo").textValue();
        JsonNode payment = body(mockMvc.perform(post("/api/orders/{orderNo}/payments", orderNo)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":\"ALIPAY\"}"))
                .andExpect(status().isOk())
                .andReturn());

        mockMvc.perform(post("/api/orders/{orderNo}/cancel", orderNo)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("cancelled"))
                .andExpect(jsonPath("$.paymentStatus").value("closed"));

        assertThat(jdbc.queryForObject(
                "SELECT status FROM payments WHERE payment_no = ?",
                String.class,
                payment.get("paymentNo").textValue()
        )).isEqualTo("CLOSED");
        assertThat(jdbc.queryForObject(
                "SELECT available_quantity FROM sku_inventory WHERE sku_id = 1015",
                Integer.class
        )).isEqualTo(availableBefore);
        assertThat(jdbc.queryForObject(
                "SELECT locked_quantity FROM sku_inventory WHERE sku_id = 1015",
                Integer.class
        )).isEqualTo(lockedBefore);
    }

    private String register(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"支付宝测试用户","email":"%s","password":"SecurePass123"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();
        return body(result).get("accessToken").textValue();
    }

    private long createAddress(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/me/addresses")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"张三","phone":"13800138000","province":"广东省","city":"深圳市",
                                 "district":"南山区","detail":"科技园 1 号","isDefault":true}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return body(result).get("id").longValue();
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class PaymentTestConfiguration {
        @Bean
        @Primary
        AlipayGateway testAlipayGateway() {
            return new AlipayGateway() {
                @Override
                public boolean isConfigured() {
                    return true;
                }

                @Override
                public String createPagePayment(PaymentOrder payment) {
                    return "<html><body><form>" + payment.paymentNo() + "</form></body></html>";
                }

                @Override
                public ProviderPaymentResult query(String paymentNo) {
                    return new ProviderPaymentResult(
                            ProviderPaymentStatus.PENDING, null, null, null, null
                    );
                }

                @Override
                public ProviderPaymentResult close(String paymentNo) {
                    return ProviderPaymentResult.closed(null);
                }

                @Override
                public AlipayNotification verifyNotification(Map<String, String> parameters) {
                    return new AlipayNotification(
                            parameters.get("out_trade_no"),
                            parameters.get("trade_no"),
                            ProviderPaymentStatus.SUCCESS,
                            new BigDecimal(parameters.get("total_amount")),
                            parameters.get("notify_id"),
                            parameters.get("trade_status"),
                            "0".repeat(64)
                    );
                }
            };
        }
    }
}
