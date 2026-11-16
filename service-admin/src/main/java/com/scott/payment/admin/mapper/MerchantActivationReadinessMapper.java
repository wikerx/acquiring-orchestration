package com.scott.payment.admin.mapper;

import lombok.Data;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 商户最终开通的风险、商户限额、渠道限额和 3DS 只读投影。
 *
 * <p>查询条件与 service-risk 运行时的 GLOBAL/商户规则生效语义保持一致，不承担规则写入。</p>
 */
public interface MerchantActivationReadinessMapper {

    /** 查询审核通过且启用的 OpenAPI 请求 IP 白名单数量。 */
    @Select("""
            SELECT COUNT(1)
            FROM merchant_ip_whitelist
            WHERE merchant_id = #{merchantId}
              AND approval_status = 1
              AND status = 1
              AND deleted = 0
            """)
    long countActiveIpWhitelists(@Param("merchantId") String merchantId);

    /** 查询当前商户是否启用指定可选风控能力。 */
    @Select("""
            SELECT COUNT(1)
            FROM risk_merchant_function_config
            WHERE merchant_id = #{merchantId}
              AND module_type = 'RULE'
              AND function_code = #{functionCode}
              AND enabled = 1
              AND deleted = 0
            """)
    long countEnabledRiskFunction(@Param("merchantId") String merchantId,
                                  @Param("functionCode") String functionCode);

    /** 查询当前有效且审核通过的商户来源网址规则数量。 */
    @Select("""
            SELECT COUNT(1)
            FROM risk_rule_source_url
            WHERE merchant_id = #{merchantId}
              AND approval_status = 1
              AND status = 1
              AND deleted = 0
              AND (effective_time IS NULL OR effective_time <= #{now})
              AND (expire_time IS NULL OR expire_time > #{now})
            """)
    long countActiveSourceUrlRules(@Param("merchantId") String merchantId,
                                   @Param("now") LocalDateTime now);

    /** 查询运行时可命中的全部商户限额规则。 */
    @Select("""
            SELECT merchant_scope, merchant_id, limit_type, currency, amount_min, amount_max
            FROM risk_rule_merchant_limit
            WHERE deleted = 0
              AND status = 1
              AND (effective_time IS NULL OR effective_time <= #{now})
              AND (expire_time IS NULL OR expire_time > #{now})
              AND (
                    merchant_scope = 'GLOBAL'
                    OR (
                        merchant_scope = 'MERCHANT'
                        AND merchant_id = #{merchantId}
                        AND EXISTS (
                            SELECT 1
                            FROM risk_merchant_function_config config
                            WHERE config.merchant_id = #{merchantId}
                              AND config.module_type = 'RULE'
                              AND config.function_code = 'merchantLimit'
                              AND config.enabled = 1
                              AND config.deleted = 0
                        )
                    )
                  )
            """)
    List<MerchantLimitRow> selectActiveMerchantLimits(@Param("merchantId") String merchantId,
                                                       @Param("now") LocalDateTime now);

    /** 查询指定渠道集合当前启用的收单限额规则。 */
    @Select("""
            <script>
            SELECT channel_id, payment_method, card_brand, limit_type, limit_currency
            FROM channel_limit_rule
            WHERE deleted = 0
              AND rule_status = 1
              AND business_type = 'ACQUIRING'
              AND channel_id IN
              <foreach collection="channelIds" item="channelId" open="(" separator="," close=")">
                  #{channelId}
              </foreach>
            </script>
            """)
    List<ChannelLimitRow> selectActiveChannelLimits(@Param("channelIds") List<Long> channelIds);

    /** 查询运行时可命中的全部 3DS 规则。 */
    @Select("""
            SELECT merchant_scope, merchant_id, channel_code, payment_method, card_brand,
                   amount_match_type, currency, risk_condition, trigger_action
            FROM risk_rule_3ds
            WHERE deleted = 0
              AND status = 1
              AND trigger_action IN ('FORCE_3DS', 'SKIP_3DS')
              AND (effective_time IS NULL OR effective_time <= #{now})
              AND (expire_time IS NULL OR expire_time > #{now})
              AND (
                    merchant_scope = 'GLOBAL'
                    OR (
                        merchant_scope = 'MERCHANT'
                        AND merchant_id = #{merchantId}
                        AND EXISTS (
                            SELECT 1
                            FROM risk_merchant_function_config config
                            WHERE config.merchant_id = #{merchantId}
                              AND config.module_type = 'RULE'
                              AND config.function_code = 'threeDs'
                              AND config.enabled = 1
                              AND config.deleted = 0
                        )
                    )
                  )
            """)
    List<ThreeDsRuleRow> selectActiveThreeDsRules(@Param("merchantId") String merchantId,
                                                   @Param("now") LocalDateTime now);

    /** 商户限额匹配字段。 */
    @Data
    class MerchantLimitRow {
        private String merchantScope;
        private String merchantId;
        private String limitType;
        private String currency;
        private BigDecimal amountMin;
        private BigDecimal amountMax;
    }

    /** 渠道限额匹配字段。 */
    @Data
    class ChannelLimitRow {
        private Long channelId;
        private String paymentMethod;
        private String cardBrand;
        private String limitType;
        private String limitCurrency;
    }

    /** 3DS 策略匹配字段。 */
    @Data
    class ThreeDsRuleRow {
        private String merchantScope;
        private String merchantId;
        private String channelCode;
        private String paymentMethod;
        private String cardBrand;
        private String amountMatchType;
        private String currency;
        private String riskCondition;
        private String triggerAction;
    }
}
