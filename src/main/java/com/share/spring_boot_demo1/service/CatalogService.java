package com.share.spring_boot_demo1.service;

import com.share.spring_boot_demo1.common.ApiException;
import com.share.spring_boot_demo1.common.PageResponse;
import com.share.spring_boot_demo1.dto.BrandResponse;
import com.share.spring_boot_demo1.dto.ProductResponse;
import com.share.spring_boot_demo1.repository.CatalogQueryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商品目录的只读业务门面，负责分页参数和查询条件校验。
 */
@Service
@Transactional(readOnly = true)
public class CatalogService {
    private final CatalogQueryRepository repository;

    public CatalogService(CatalogQueryRepository repository) {
        this.repository = repository;
    }

    /**
     * 返回当前有上架商品的品牌。
     */
    public List<BrandResponse> getBrands() {
        return repository.findBrands();
    }

    /**
     * 按关键字、分类、品牌、价格和白名单排序分页检索商品。
     */
    public PageResponse<ProductResponse> search(
            String query, String category, String brand, Boolean featured, Boolean newArrival, Boolean deal,
            BigDecimal minPrice, BigDecimal maxPrice, String sort, int page, int size
    ) {
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PRICE_RANGE", "最低价格不能高于最高价格");
        }
        return repository.searchProducts(query, category, brand, featured, newArrival, deal, minPrice, maxPrice,
                sort, page, size);
    }

    /**
     * 按数字 ID 或 slug 查询商品详情。
     */
    public ProductResponse getProduct(String identifier) {
        return repository.findProduct(identifier);
    }
}
