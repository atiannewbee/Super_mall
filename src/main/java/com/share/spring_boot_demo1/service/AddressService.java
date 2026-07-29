package com.share.spring_boot_demo1.service;

import com.share.spring_boot_demo1.common.ApiException;
import com.share.spring_boot_demo1.dto.AddressRequest;
import com.share.spring_boot_demo1.dto.AddressResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 管理消费者收货地址，并保证每个用户最多只有一个默认地址。
 */
@Service
public class AddressService {
    private static final int MAX_ADDRESSES = 20;
    private final NamedParameterJdbcTemplate jdbc;

    public AddressService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 查询当前用户未软删除的地址列表。
     */
    @Transactional(readOnly = true)
    public List<AddressResponse> list(long userId) {
        return jdbc.query("""
                SELECT id, recipient_name, phone, province, city, district, detail, postal_code, tag, is_default
                FROM user_addresses
                WHERE user_id = :userId AND deleted_at IS NULL
                ORDER BY is_default DESC, updated_at DESC, id DESC
                """, Map.of("userId", userId), (rs, rowNum) -> new AddressResponse(
                rs.getLong("id"), rs.getString("recipient_name"), rs.getString("phone"),
                rs.getString("province"), rs.getString("city"), rs.getString("district"),
                rs.getString("detail"), rs.getString("postal_code"), rs.getString("tag"),
                rs.getBoolean("is_default")
        ));
    }

    /**
     * 新建地址；首个地址或显式指定的地址会成为默认地址。
     */
    @Transactional
    public AddressResponse create(long userId, AddressRequest request) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM user_addresses WHERE user_id = :userId AND deleted_at IS NULL
                """, Map.of("userId", userId), Integer.class);
        if (count != null && count >= MAX_ADDRESSES) {
            throw new ApiException(HttpStatus.CONFLICT, "ADDRESS_LIMIT_REACHED", "收货地址最多保存 20 个");
        }
        boolean makeDefault = request.isDefault() || count == null || count == 0;
        if (makeDefault) {
            clearDefaults(userId);
        }
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        MapSqlParameterSource params = addressParams(userId, request).addValue("isDefault", makeDefault);
        jdbc.update("""
                INSERT INTO user_addresses
                    (user_id, recipient_name, phone, province, city, district, detail, postal_code, tag, is_default)
                VALUES
                    (:userId, :name, :phone, :province, :city, :district, :detail, :postalCode, :tag, :isDefault)
                """, params, keyHolder, new String[]{"id"});
        return getRequired(userId, keyHolder.getKey().longValue());
    }

    /**
     * 更新属于当前用户的地址，并维护默认地址唯一性。
     */
    @Transactional
    public AddressResponse update(long userId, long addressId, AddressRequest request) {
        getRequired(userId, addressId);
        if (request.isDefault()) {
            clearDefaults(userId);
        }
        MapSqlParameterSource params = addressParams(userId, request)
                .addValue("addressId", addressId).addValue("isDefault", request.isDefault());
        jdbc.update("""
                UPDATE user_addresses SET recipient_name = :name, phone = :phone, province = :province,
                    city = :city, district = :district, detail = :detail, postal_code = :postalCode,
                    tag = :tag, is_default = CASE WHEN :isDefault THEN TRUE ELSE is_default END
                WHERE id = :addressId AND user_id = :userId AND deleted_at IS NULL
                """, params);
        return getRequired(userId, addressId);
    }

    /**
     * 把指定地址设为当前用户的唯一默认地址。
     */
    @Transactional
    public void setDefault(long userId, long addressId) {
        getRequired(userId, addressId);
        clearDefaults(userId);
        jdbc.update("""
                UPDATE user_addresses SET is_default = TRUE
                WHERE id = :addressId AND user_id = :userId AND deleted_at IS NULL
                """, Map.of("addressId", addressId, "userId", userId));
    }

    /**
     * 软删除地址；历史订单仍保留创建订单时保存的收件快照。
     */
    @Transactional
    public void delete(long userId, long addressId) {
        AddressResponse address = getRequired(userId, addressId);
        jdbc.update("""
                UPDATE user_addresses SET deleted_at = CURRENT_TIMESTAMP(3), is_default = FALSE
                WHERE id = :addressId AND user_id = :userId AND deleted_at IS NULL
                """, Map.of("addressId", addressId, "userId", userId));
        if (address.isDefault()) {
            jdbc.update("""
                    UPDATE user_addresses SET is_default = TRUE
                    WHERE id = (SELECT id FROM (SELECT id FROM user_addresses
                        WHERE user_id = :userId AND deleted_at IS NULL ORDER BY updated_at DESC, id DESC LIMIT 1) candidate)
                    """, Map.of("userId", userId));
        }
    }

    /**
     * 查询并校验地址归属，找不到时抛出稳定业务错误码。
     */
    @Transactional(readOnly = true)
    public AddressResponse getRequired(long userId, long addressId) {
        List<AddressResponse> addresses = jdbc.query("""
                SELECT id, recipient_name, phone, province, city, district, detail, postal_code, tag, is_default
                FROM user_addresses WHERE id = :addressId AND user_id = :userId AND deleted_at IS NULL
                """, Map.of("addressId", addressId, "userId", userId), (rs, rowNum) -> new AddressResponse(
                rs.getLong("id"), rs.getString("recipient_name"), rs.getString("phone"),
                rs.getString("province"), rs.getString("city"), rs.getString("district"),
                rs.getString("detail"), rs.getString("postal_code"), rs.getString("tag"),
                rs.getBoolean("is_default")
        ));
        if (addresses.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "ADDRESS_NOT_FOUND", "收货地址不存在");
        }
        return addresses.get(0);
    }

    private void clearDefaults(long userId) {
        jdbc.update("UPDATE user_addresses SET is_default = FALSE WHERE user_id = :userId AND deleted_at IS NULL",
                Map.of("userId", userId));
    }

    private MapSqlParameterSource addressParams(long userId, AddressRequest request) {
        return new MapSqlParameterSource()
                .addValue("userId", userId).addValue("name", request.name().trim())
                .addValue("phone", request.phone().trim()).addValue("province", request.province().trim())
                .addValue("city", request.city().trim()).addValue("district", request.district().trim())
                .addValue("detail", request.detail().trim()).addValue("postalCode", blankToNull(request.postalCode()))
                .addValue("tag", blankToNull(request.tag()));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
