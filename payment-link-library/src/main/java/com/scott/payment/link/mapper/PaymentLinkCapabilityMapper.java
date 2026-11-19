package com.scott.payment.link.mapper;

import com.scott.payment.component.db.route.model.MerchantRouteProfile.RouteOption;
import org.apache.ibatis.annotations.*;
import lombok.Data;
import java.util.List;

/** 主库有界读取商户展示能力；不读取 MID 密钥、请求 URL 或渠道敏感配置。 */
public interface PaymentLinkCapabilityMapper {
    String JOINS = " FROM merchant_channel_mid_binding b JOIN channel_mid_config m ON m.id = b.mid_config_id AND m.deleted = 0 AND m.mid_status = 1 "
            + " JOIN channel_info c ON c.id = m.channel_id AND c.deleted = 0 AND c.channel_status = 1 AND c.support_acquiring = 1 "
            + " JOIN channel_payment_capability p ON p.channel_id = c.id AND p.deleted = 0 AND p.capability_status = 1 AND p.business_type = 'ACQUIRING' ";
    String SCOPE = " b.merchant_id = #{merchantId} AND b.deleted = 0 AND b.binding_status = 1 ";
    @Select("SELECT b.binding_status, b.effective_time AS binding_effective_time, b.expire_time AS binding_expire_time, "
            + "m.business_type, m.mid_status, m.effective_time AS mid_effective_time, m.expire_time AS mid_expire_time, "
            + "m.payment_method_scope, m.card_brand_scope, m.currency_scope, m.transaction_type_scope, "
            + "c.channel_code, c.channel_status, c.support_acquiring, p.id AS capability_id, p.capability_status, "
            + "p.payment_method AS capability_payment_method, p.transaction_type AS capability_transaction_type, "
            + "p.support_3ds AS capability_support3ds " + JOINS + " WHERE " + SCOPE + " ORDER BY b.id, p.sort_order, p.id")
    List<RouteOption> routes(@Param("merchantId") String merchantId);
    @Select("SELECT p.id AS capability_id, v.currency_code AS code " + JOINS
            + " JOIN channel_capability_currency v ON v.capability_id = p.id AND v.deleted = 0 AND v.currency_status = 1 WHERE "
            + SCOPE + " ORDER BY v.currency_code")
    List<Value> currencies(@Param("merchantId") String merchantId);
    @Select("SELECT p.id AS capability_id, v.card_brand AS code " + JOINS
            + " JOIN channel_capability_card_brand v ON v.capability_id = p.id AND v.deleted = 0 AND v.brand_status = 1 WHERE "
            + SCOPE + " ORDER BY v.sort_order, v.id")
    List<Value> brands(@Param("merchantId") String merchantId);
    @Data class Value { private Long capabilityId; private String code; }
}
