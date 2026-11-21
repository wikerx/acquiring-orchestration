package com.scott.payment.capability.governance;

import lombok.Data;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 只读取能力评估所需的字段，由上层为本次读取选择数据源。 */
public class MerchantPaymentReadinessFacts {
    private final NamedParameterJdbcTemplate jdbc;

    public MerchantPaymentReadinessFacts(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<MerchantChannelMidBindingDO> activeBindings(String merchant, LocalDateTime now) {
        return jdbc.query("""
                SELECT id, mid_config_id FROM merchant_channel_mid_binding
                WHERE merchant_id = :merchant AND binding_status = 1 AND deleted = 0
                  AND (effective_time IS NULL OR effective_time <= :now)
                  AND (expire_time IS NULL OR expire_time > :now)
                ORDER BY id
                """, Map.of("merchant", merchant, "now", now), new BeanPropertyRowMapper<>(MerchantChannelMidBindingDO.class));
    }

    public List<ChannelMidConfigDO> mids(Set<Long> ids) {
        return jdbc.query("""
                SELECT id, channel_id, business_type, mid_status, effective_time, expire_time,
                       payment_method_scope, card_brand_scope, transaction_type_scope, currency_scope
                FROM channel_mid_config WHERE id IN (:ids) AND deleted = 0
                """, Map.of("ids", ids), new BeanPropertyRowMapper<>(ChannelMidConfigDO.class));
    }

    public List<ChannelInfoDO> channels(Set<Long> ids) {
        return jdbc.query("""
                SELECT id, channel_code, channel_status, support_acquiring, support_payout
                FROM channel_info WHERE id IN (:ids) AND deleted = 0
                """, Map.of("ids", ids), new BeanPropertyRowMapper<>(ChannelInfoDO.class));
    }

    public List<ChannelPaymentCapabilityDO> capabilities(Set<Long> ids, String type) {
        return jdbc.query("""
                SELECT id, channel_id, payment_method, transaction_type, support_incremental_authorization, sort_order
                FROM channel_payment_capability
                WHERE channel_id IN (:ids) AND business_type = :type AND capability_status = 1 AND deleted = 0
                ORDER BY sort_order, id
                """, Map.of("ids", ids, "type", type), new BeanPropertyRowMapper<>(ChannelPaymentCapabilityDO.class));
    }

    public List<ChannelCapabilityCurrencyDO> currencies(Set<Long> ids) {
        return jdbc.query("""
                SELECT capability_id, currency_code FROM channel_capability_currency
                WHERE capability_id IN (:ids) AND currency_status = 1 AND deleted = 0
                """, Map.of("ids", ids), new BeanPropertyRowMapper<>(ChannelCapabilityCurrencyDO.class));
    }

    public List<ChannelCapabilityCardBrandDO> cardBrands(Set<Long> ids) {
        return jdbc.query("""
                SELECT capability_id, card_brand FROM channel_capability_card_brand
                WHERE capability_id IN (:ids) AND brand_status = 1 AND deleted = 0
                """, Map.of("ids", ids), new BeanPropertyRowMapper<>(ChannelCapabilityCardBrandDO.class));
    }

    public List<FeeRuleDO> feeRules(long versionId) {
        return jdbc.query("""
                SELECT fee_category, risk_service_type, transaction_type, payment_type, payment_method
                FROM fee_rule WHERE plan_version_id = :versionId AND deleted = 0
                ORDER BY sort_no, id
                """, Map.of("versionId", versionId), new BeanPropertyRowMapper<>(FeeRuleDO.class));
    }

    @Data
    public static class MerchantChannelMidBindingDO {
        private Long id;
        private Long midConfigId;
    }

    @Data
    public static class ChannelMidConfigDO {
        private Long id;
        private Long channelId;
        private String businessType;
        private Integer midStatus;
        private LocalDateTime effectiveTime;
        private LocalDateTime expireTime;
        private String paymentMethodScope;
        private String cardBrandScope;
        private String transactionTypeScope;
        private String currencyScope;
    }

    @Data
    public static class ChannelInfoDO {
        private Long id;
        private String channelCode;
        private Integer channelStatus;
        private Integer supportAcquiring;
        private Integer supportPayout;
    }

    @Data
    public static class ChannelPaymentCapabilityDO {
        private Long id;
        private Long channelId;
        private String paymentMethod;
        private String transactionType;
        private Integer supportIncrementalAuthorization;
        private Integer sortOrder;
    }

    @Data
    public static class ChannelCapabilityCurrencyDO {
        private Long capabilityId;
        private String currencyCode;
    }

    @Data
    public static class ChannelCapabilityCardBrandDO {
        private Long capabilityId;
        private String cardBrand;
    }

    @Data
    public static class FeeRuleDO {
        private String feeCategory;
        private String riskServiceType;
        private String transactionType;
        private String paymentType;
        private String paymentMethod;
    }
}
