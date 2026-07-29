package com.share.spring_boot_demo1.controller;

import com.share.spring_boot_demo1.common.PageResponse;
import com.share.spring_boot_demo1.dto.BrandResponse;
import com.share.spring_boot_demo1.dto.ProductResponse;
import com.share.spring_boot_demo1.service.CatalogService;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * 面向消费者公开的品牌和商品目录查询接口。
 */
@Validated
@RestController
@RequestMapping("/api")
public class CatalogController {
    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/brands")
    public List<BrandResponse> brands() {
        return catalogService.getBrands();
    }

    @GetMapping("/products")
    public PageResponse<ProductResponse> products(
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(required = false) @Size(max = 80) String category,
            @RequestParam(required = false) @Size(max = 100) String brand,
            @RequestParam(required = false) Boolean featured,
            @RequestParam(name = "new", required = false) Boolean newArrival,
            @RequestParam(required = false) Boolean deal,
            @RequestParam(required = false) @DecimalMin("0.00") BigDecimal minPrice,
            @RequestParam(required = false) @DecimalMin("0.00") BigDecimal maxPrice,
            @RequestParam(defaultValue = "recommended") String sort,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return catalogService.search(q, category, brand, featured, newArrival, deal, minPrice, maxPrice, sort,
                page, size);
    }

    @GetMapping("/products/{identifier}")
    public ProductResponse product(@PathVariable @Size(max = 120) String identifier) {
        return catalogService.getProduct(identifier);
    }
}
