package com.share.spring_boot_demo1;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证公开商品目录的筛选、排序、分页和商品聚合响应。
 */
@SpringBootTest
@AutoConfigureMockMvc
class CatalogApiIntegrationTests {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void searchesFiltersSortsAndReturnsSkuOptions() throws Exception {
        mockMvc.perform(get("/api/products")
                        .param("category", "phones")
                        .param("deal", "true")
                        .param("sort", "price-desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].slug").value("aether-x1-pro"));

        mockMvc.perform(get("/api/products/aether-x1-pro"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryId").value("phones"))
                .andExpect(jsonPath("$.isFeatured").value(true))
                .andExpect(jsonPath("$.isNew").value(true))
                .andExpect(jsonPath("$.isDeal").value(true))
                .andExpect(jsonPath("$.features.length()").value(3))
                .andExpect(jsonPath("$.skus.length()").value(2))
                .andExpect(jsonPath("$.skus[0].skuCode").value("aether-x1-256-black"))
                .andExpect(jsonPath("$.skus[0].options.颜色").value("曜石黑"))
                .andExpect(jsonPath("$.skus[0].options.存储容量").value("256GB"));

        mockMvc.perform(get("/api/products").param("minPrice", "500").param("maxPrice", "100"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PRICE_RANGE"));
    }

    @Test
    void unknownProductReturnsStableErrorContract() throws Exception {
        mockMvc.perform(get("/api/products/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/api/products/not-found"));
    }
}
