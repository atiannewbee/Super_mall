package com.share.spring_boot_demo1;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证商家订单租户隔离、角色权限、拣货发货状态机、库存查询和审计记录。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MerchantOperationsIntegrationTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void merchantProcessesPaidOrderAndCustomerSeesShipment() throws Exception {
        CustomerOrder customerOrder = createCustomerOrder(true, "aether-x1-256-black");
        String merchantToken = createMerchantAndLogin("WAREHOUSE");

        mockMvc.perform(get("/api/merchant/dashboard")
                        .header("Authorization", bearer(merchantToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unfulfilledOrders").isNumber())
                .andExpect(jsonPath("$.todayPaidAmount").isNumber());

        mockMvc.perform(get("/api/merchant/orders")
                        .header("Authorization", bearer(merchantToken))
                        .param("fulfillmentStatus", "unfulfilled"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.orderNo == '%s')]".formatted(customerOrder.orderNo())).exists());

        mockMvc.perform(post("/api/merchant/orders/{orderNo}/picking", customerOrder.orderNo())
                        .header("Authorization", bearer(merchantToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fulfillmentStatus").value("picking"));

        mockMvc.perform(post("/api/merchant/orders/{orderNo}/picking", customerOrder.orderNo())
                        .header("Authorization", bearer(merchantToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_PICKABLE"));

        mockMvc.perform(post("/api/merchant/orders/{orderNo}/ship", customerOrder.orderNo())
                        .header("Authorization", bearer(merchantToken))
                        .contentType("application/json")
                        .content("""
                                {"carrierCode":"SF","carrierName":"顺丰速运","trackingNo":"SF-%d"}
                                """.formatted(System.nanoTime())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("shipped"))
                .andExpect(jsonPath("$.fulfillmentStatus").value("shipped"))
                .andExpect(jsonPath("$.shipment.carrierName").value("顺丰速运"))
                .andExpect(jsonPath("$.shipment.trackingNo").isNotEmpty());

        mockMvc.perform(post("/api/merchant/orders/{orderNo}/ship", customerOrder.orderNo())
                        .header("Authorization", bearer(merchantToken))
                        .contentType("application/json")
                        .content("""
                                {"carrierCode":"SF","carrierName":"顺丰速运","trackingNo":"DUPLICATE"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_SHIPPABLE"));

        mockMvc.perform(get("/api/orders/{orderNo}", customerOrder.orderNo())
                        .header("Authorization", bearer(customerOrder.customerToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("shipped"))
                .andExpect(jsonPath("$.fulfillmentStatus").value("shipped"))
                .andExpect(jsonPath("$.shipment.carrierName").value("顺丰速运"))
                .andExpect(jsonPath("$.shipment.events[0].eventCode").value("SHIPPED"))
                .andExpect(jsonPath("$.timeline[?(@.operatorType == 'merchant')]").exists());

        Integer auditCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM merchant_operation_logs
                WHERE resource_type = 'ORDER' AND resource_id = ?
                """, Integer.class, customerOrder.orderNo());
        assertThat(auditCount).isEqualTo(2);
    }

    @Test
    void unpaidOrdersAndInsufficientRolesCannotBeFulfilled() throws Exception {
        CustomerOrder unpaidOrder = createCustomerOrder(false, "homehub-mini-white");
        String operatorToken = createMerchantAndLogin("OPERATOR");

        mockMvc.perform(post("/api/merchant/orders/{orderNo}/picking", unpaidOrder.orderNo())
                        .header("Authorization", bearer(operatorToken)))
                .andExpect(status().isForbidden());

        String warehouseToken = createMerchantAndLogin("WAREHOUSE");
        mockMvc.perform(post("/api/merchant/orders/{orderNo}/picking", unpaidOrder.orderNo())
                        .header("Authorization", bearer(warehouseToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_PICKABLE"));
    }

    @Test
    void merchantInventoryIsScopedAndSearchable() throws Exception {
        String merchantToken = createMerchantAndLogin("OWNER");

        mockMvc.perform(get("/api/merchant/inventory")
                        .header("Authorization", bearer(merchantToken))
                        .param("query", "aether-x1-256-black"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].skuCode").value("aether-x1-256-black"))
                .andExpect(jsonPath("$.items[0].availableQuantity").isNumber());
    }

    @Test
    void ownerCreatesUpdatesAndSoftDeletesProductFromInventory() throws Exception {
        String merchantToken = createMerchantAndLogin("OWNER");
        long categoryId = jdbc.queryForObject(
                "SELECT id FROM categories WHERE status = 'ACTIVE' ORDER BY id LIMIT 1",
                Long.class
        );
        String skuCode = "merchant-product-" + System.nanoTime();
        String createBody = """
                {"name":"商家新增测试商品","categoryId":%d,
                 "imageUrl":"https://images.example.com/merchant-product.jpg",
                 "tagline":"库存中心直接维护","description":"最小单 SKU 商品",
                 "status":"ACTIVE","skuCode":"%s","skuLabel":"标准款",
                 "price":199.00,"originalPrice":229.00,"availableQuantity":12}
                """.formatted(categoryId, skuCode);

        mockMvc.perform(post("/api/merchant/products")
                        .header("Authorization", bearer(merchantToken))
                        .contentType("application/json")
                        .content(createBody))
                .andExpect(status().isCreated());

        JsonNode created = body(mockMvc.perform(get("/api/merchant/inventory")
                        .header("Authorization", bearer(merchantToken))
                        .param("query", skuCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].productStatus").value("active"))
                .andExpect(jsonPath("$.items[0].availableQuantity").value(12))
                .andReturn()).get("items").get(0);
        long productId = created.get("productId").longValue();
        long skuId = created.get("skuId").longValue();

        mockMvc.perform(put("/api/merchant/products/{productId}/skus/{skuId}", productId, skuId)
                        .header("Authorization", bearer(merchantToken))
                        .contentType("application/json")
                        .content(createBody
                                .replace("商家新增测试商品", "商家修改测试商品")
                                .replace("199.00", "188.00")
                                .replace("\"availableQuantity\":12", "\"availableQuantity\":7")))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/merchant/inventory")
                        .header("Authorization", bearer(merchantToken))
                        .param("query", skuCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].productName").value("商家修改测试商品"))
                .andExpect(jsonPath("$.items[0].price").value(188.00))
                .andExpect(jsonPath("$.items[0].availableQuantity").value(7));

        mockMvc.perform(delete("/api/merchant/products/{productId}", productId)
                        .header("Authorization", bearer(merchantToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/merchant/inventory")
                        .header("Authorization", bearer(merchantToken))
                        .param("query", skuCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        Integer auditCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM merchant_operation_logs
                WHERE resource_type = 'PRODUCT' AND resource_id = ?
                """, Integer.class, String.valueOf(productId));
        assertThat(auditCount).isEqualTo(3);
    }

    private CustomerOrder createCustomerOrder(boolean pay, String skuCode) throws Exception {
        String customerToken = registerCustomer();
        JsonNode address = body(mockMvc.perform(post("/api/me/addresses")
                        .header("Authorization", bearer(customerToken))
                        .contentType("application/json")
                        .content("""
                                {"name":"订单收货人","phone":"13800138000","province":"广东省","city":"深圳市",
                                 "district":"南山区","detail":"科技园 1 号","isDefault":true}
                                """))
                .andExpect(status().isCreated())
                .andReturn());
        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", bearer(customerToken))
                        .contentType("application/json")
                        .content("""
                                {"skuCode":"%s","quantity":1}
                                """.formatted(skuCode)))
                .andExpect(status().isOk());
        JsonNode order = body(mockMvc.perform(post("/api/orders")
                        .header("Authorization", bearer(customerToken))
                        .header("Idempotency-Key", "merchant-order-" + System.nanoTime())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "addressId", address.get("id").longValue(),
                                "paymentChannel", "ALIPAY",
                                "invoiceRequired", false
                        ))))
                .andExpect(status().isCreated())
                .andReturn());
        String orderNo = order.get("orderNo").textValue();
        if (pay) {
            mockMvc.perform(post("/api/orders/{orderNo}/payments/sandbox", orderNo)
                            .header("Authorization", bearer(customerToken))
                            .contentType("application/json")
                            .content("{\"channel\":\"ALIPAY\"}"))
                    .andExpect(status().isOk());
        }
        return new CustomerOrder(customerToken, orderNo);
    }

    private String registerCustomer() throws Exception {
        return body(mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("""
                                {"name":"商家流程消费者","email":"merchant-customer-%d@example.com",
                                 "password":"SecurePass123"}
                                """.formatted(System.nanoTime())))
                .andExpect(status().isCreated())
                .andReturn()).get("accessToken").textValue();
    }

    private String createMerchantAndLogin(String role) throws Exception {
        String email = "merchant-operation-" + System.nanoTime() + "@example.com";
        long merchantId = jdbc.queryForObject(
                "SELECT id FROM merchants WHERE code = 'SUPER_MALL'", Long.class);
        jdbc.update("""
                INSERT INTO merchant_users
                    (merchant_id, email, name, password_hash, status, force_password_change)
                VALUES (?, ?, '运营测试', ?, 'ACTIVE', FALSE)
                """, merchantId, email, passwordEncoder.encode("MerchantPass123!"));
        long userId = jdbc.queryForObject(
                "SELECT id FROM merchant_users WHERE email = ?", Long.class, email);
        jdbc.update(
                "INSERT INTO merchant_user_roles (merchant_user_id, role_code) VALUES (?, ?)",
                userId, role
        );
        return body(mockMvc.perform(post("/api/merchant/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"email":"%s","password":"MerchantPass123!"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn()).get("accessToken").textValue();
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record CustomerOrder(String customerToken, String orderNo) {
    }
}
