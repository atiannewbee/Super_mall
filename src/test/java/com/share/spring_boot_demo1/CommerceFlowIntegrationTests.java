package com.share.spring_boot_demo1;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import com.share.spring_boot_demo1.service.OrderExpirationService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 覆盖消费者从购物车下单、库存预占到支付、物流、收货和售后的主交易流程。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CommerceFlowIntegrationTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private OrderExpirationService orderExpirationService;

    @Test
    void completesCustomerFlowWithInventoryAndOwnershipProtection() throws Exception {
        int stockBefore = jdbc.queryForObject(
                "SELECT available_quantity FROM sku_inventory WHERE sku_id = 1001", Integer.class);
        String token = register("flow-" + System.nanoTime() + "@example.com", "流程用户");

        JsonNode address = body(mockMvc.perform(post("/api/me/addresses")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"name":"张三","phone":"13800138000","province":"广东省","city":"深圳市",
                                 "district":"南山区","detail":"科技园 1 号","tag":"家","isDefault":true}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isDefault").value(true))
                .andReturn());
        long addressId = address.get("id").longValue();

        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"skuCode\":\"aether-x1-256-black\",\"quantity\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selectedCount").value(2))
                .andExpect(jsonPath("$.selectedSubtotal").value(8598.00));

        String idempotencyKey = "checkout-" + System.nanoTime();
        String createBody = objectMapper.writeValueAsString(Map.of(
                "addressId", addressId,
                "paymentChannel", "ALIPAY",
                "invoiceRequired", false
        ));
        JsonNode order = body(mockMvc.perform(post("/api/orders")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType("application/json")
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("pending-payment"))
                .andExpect(jsonPath("$.total").value(8598.00))
                .andReturn());
        String orderNo = order.get("orderNo").textValue();
        long orderItemId = order.get("items").get(0).get("id").longValue();

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType("application/json")
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderNo").value(orderNo));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE order_no = ?", Integer.class, orderNo))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT available_quantity FROM sku_inventory WHERE sku_id = 1001",
                Integer.class)).isEqualTo(stockBefore - 2);
        assertThat(jdbc.queryForObject("SELECT locked_quantity FROM sku_inventory WHERE sku_id = 1001",
                Integer.class)).isGreaterThanOrEqualTo(2);

        mockMvc.perform(post("/api/orders/{orderNo}/payments/sandbox", orderNo)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"channel\":\"ALIPAY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.amount").value(8598.00));

        JsonNode afterSale = body(mockMvc.perform(post("/api/after-sales")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"orderNo":"%s","type":"REFUND_ONLY","reasonCode":"NO_LONGER_NEEDED",
                                 "reasonDescription":"不再需要","items":[{"orderItemId":%d,"quantity":1}]}
                                """.formatted(orderNo, orderItemId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("requested"))
                .andExpect(jsonPath("$.requestedAmount").value(4299.00))
                .andReturn());
        String afterSaleNo = afterSale.get("afterSaleNo").textValue();

        mockMvc.perform(post("/api/after-sales/{number}/cancel", afterSaleNo)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("cancelled"));

        String otherToken = register("other-" + System.nanoTime() + "@example.com", "其他用户");
        mockMvc.perform(get("/api/orders/{orderNo}", orderNo).header("Authorization", bearer(otherToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    void expiresUnpaidOrderAndReleasesLockedInventory() throws Exception {
        int availableBefore = jdbc.queryForObject(
                "SELECT available_quantity FROM sku_inventory WHERE sku_id = 1015", Integer.class);
        int lockedBefore = jdbc.queryForObject(
                "SELECT locked_quantity FROM sku_inventory WHERE sku_id = 1015", Integer.class);
        String token = register("expiry-" + System.nanoTime() + "@example.com", "超时用户");

        JsonNode address = body(mockMvc.perform(post("/api/me/addresses")
                        .header("Authorization", bearer(token)).contentType("application/json")
                        .content("""
                                {"name":"李四","phone":"13900139000","province":"广东省","city":"深圳市",
                                 "district":"福田区","detail":"中心区 2 号","isDefault":true}
                                """))
                .andExpect(status().isCreated()).andReturn());
        mockMvc.perform(post("/api/cart/items").header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"skuCode\":\"homehub-mini-white\",\"quantity\":1}"))
                .andExpect(status().isOk());
        JsonNode order = body(mockMvc.perform(post("/api/orders")
                        .header("Authorization", bearer(token)).header("Idempotency-Key", "expiry-" + System.nanoTime())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "addressId", address.get("id").longValue(),
                                "paymentChannel", "ALIPAY",
                                "invoiceRequired", false))))
                .andExpect(status().isCreated()).andReturn());
        String orderNo = order.get("orderNo").textValue();

        jdbc.update("UPDATE orders SET created_at = CURRENT_TIMESTAMP(3) - INTERVAL 2 HOUR WHERE order_no = ?", orderNo);
        orderExpirationService.expirePendingOrders();

        mockMvc.perform(get("/api/orders/{orderNo}", orderNo).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("cancelled"))
                .andExpect(jsonPath("$.paymentStatus").value("closed"));
        assertThat(jdbc.queryForObject("SELECT available_quantity FROM sku_inventory WHERE sku_id = 1015",
                Integer.class)).isEqualTo(availableBefore);
        assertThat(jdbc.queryForObject("SELECT locked_quantity FROM sku_inventory WHERE sku_id = 1015",
                Integer.class)).isEqualTo(lockedBefore);
    }

    private String register(String email, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("""
                                {"name":"%s","email":"%s","password":"SecurePass123"}
                                """.formatted(name, email)))
                .andExpect(status().isCreated())
                .andReturn();
        return body(result).get("accessToken").textValue();
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
