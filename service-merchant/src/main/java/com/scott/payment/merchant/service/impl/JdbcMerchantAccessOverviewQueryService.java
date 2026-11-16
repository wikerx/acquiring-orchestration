package com.scott.payment.merchant.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.merchant.service.MerchantAccessOverviewQueryService;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 基于管理主库读取商户接入配置统计，避免审核完成后的复制延迟造成错误提示。
 */
@Service
public class JdbcMerchantAccessOverviewQueryService implements MerchantAccessOverviewQueryService {

    private static final String MERCHANT_PROFILE_SQL = """
            SELECT onboarding_status, review_status, activation_status,
                   settlement_currency, risk_level, gmt_modified AS last_updated_time
            FROM base_merchant_info
            WHERE merchant_id = :merchantId AND deleted = 0
            LIMIT 1
            """;
    private static final String SETTLEMENT_SUMMARY_SQL = """
            SELECT profile.target_currency,
                   profile.business_time_zone,
                   TIME_FORMAT(profile.daily_cutoff_time, '%H:%i:%s') AS daily_cutoff_time,
                   profile.processing_mode,
                   version.initial_delay_unit,
                   version.initial_delay_days,
                   version.regular_delay_days,
                   version.settlement_frequency,
                   version.frequency_day,
                   CASE
                       WHEN profile.update_time IS NULL THEN version.update_time
                       WHEN version.update_time IS NULL THEN profile.update_time
                       WHEN profile.update_time >= version.update_time THEN profile.update_time
                       ELSE version.update_time
                   END AS last_updated_time
            FROM base_merchant_info merchant
            LEFT JOIN merchant_settlement_profile profile
              ON profile.merchant_id = merchant.merchant_id
             AND profile.profile_status = 'ACTIVE'
             AND profile.active_slot = 1
             AND profile.effective_date <= CURRENT_DATE
             AND (profile.expire_date IS NULL OR profile.expire_date >= CURRENT_DATE)
            LEFT JOIN fee_plan plan
              ON plan.plan_type = 'MERCHANT'
             AND plan.merchant_id = merchant.merchant_id
             AND plan.status = 'ENABLED'
             AND plan.deleted = 0
            LEFT JOIN fee_plan_version version
              ON version.id = plan.current_version_id
             AND version.version_status = 'ACTIVE'
             AND version.deleted = 0
            WHERE merchant.merchant_id = :merchantId
              AND merchant.deleted = 0
            LIMIT 1
            """;
    private static final String FEE_SUMMARY_SQL = """
            SELECT version.version_no, version.version_status, version.update_time AS last_updated_time
            FROM fee_plan plan
            JOIN fee_plan_version version
              ON version.id = plan.current_version_id
             AND version.deleted = 0
            WHERE plan.plan_type = 'MERCHANT'
              AND plan.merchant_id = :merchantId
              AND plan.status = 'ENABLED'
              AND plan.deleted = 0
            LIMIT 1
            """;
    private static final String ACTIVE_BINDING_COUNT_SQL = """
            SELECT COUNT(1)
            FROM merchant_channel_mid_binding binding
            JOIN channel_mid_config mid
              ON mid.id = binding.mid_config_id
             AND mid.mid_status = 1
             AND mid.business_type = 'ACQUIRING'
             AND mid.deleted = 0
             AND (mid.effective_time IS NULL OR mid.effective_time <= :now)
             AND (mid.expire_time IS NULL OR mid.expire_time > :now)
            JOIN channel_info channel
              ON channel.id = mid.channel_id
             AND channel.channel_status = 1
             AND channel.support_acquiring = 1
             AND channel.deleted = 0
            WHERE binding.merchant_id = :merchantId
              AND binding.binding_status = 1
              AND binding.deleted = 0
              AND (binding.effective_time IS NULL OR binding.effective_time <= :now)
              AND (binding.expire_time IS NULL OR binding.expire_time > :now)
            """;
    private static final String PAYMENT_CAPABILITY_SQL = """
            SELECT DISTINCT capability.payment_method AS payment_type,
                   CASE WHEN UPPER(capability.payment_method) = 'BANK_CARD'
                        THEN brand.card_brand ELSE capability.payment_method END AS payment_method,
                   currency.currency_code AS currency,
                   GREATEST(
                       binding.update_time,
                       mid.update_time,
                       channel.update_time,
                       capability.update_time,
                       currency.update_time,
                       COALESCE(brand.update_time, currency.update_time)
                   ) AS last_updated_time
            FROM merchant_channel_mid_binding binding
            JOIN channel_mid_config mid
              ON mid.id = binding.mid_config_id
             AND mid.mid_status = 1
             AND mid.business_type = 'ACQUIRING'
             AND mid.deleted = 0
             AND (mid.effective_time IS NULL OR mid.effective_time <= :now)
             AND (mid.expire_time IS NULL OR mid.expire_time > :now)
            JOIN channel_info channel
              ON channel.id = mid.channel_id
             AND channel.channel_status = 1
             AND channel.support_acquiring = 1
             AND channel.deleted = 0
            JOIN channel_payment_capability capability
              ON capability.channel_id = channel.id
             AND capability.business_type = 'ACQUIRING'
             AND capability.capability_status = 1
             AND capability.deleted = 0
            JOIN channel_capability_currency currency
              ON currency.capability_id = capability.id
             AND currency.currency_status = 1
             AND currency.deleted = 0
            LEFT JOIN channel_capability_card_brand brand
              ON brand.capability_id = capability.id
             AND UPPER(capability.payment_method) = 'BANK_CARD'
             AND brand.brand_status = 1
             AND brand.deleted = 0
            WHERE binding.merchant_id = :merchantId
              AND binding.binding_status = 1
              AND binding.deleted = 0
              AND (binding.effective_time IS NULL OR binding.effective_time <= :now)
              AND (binding.expire_time IS NULL OR binding.expire_time > :now)
              AND (
                    mid.payment_method_scope IS NULL
                    OR TRIM(mid.payment_method_scope) = ''
                    OR UPPER(TRIM(mid.payment_method_scope)) = 'ALL'
                    OR FIND_IN_SET(UPPER(capability.payment_method),
                                   REPLACE(UPPER(mid.payment_method_scope), ' ', '')) > 0
                  )
              AND (
                    mid.currency_scope IS NULL
                    OR TRIM(mid.currency_scope) = ''
                    OR UPPER(TRIM(mid.currency_scope)) = 'ALL'
                    OR FIND_IN_SET(UPPER(currency.currency_code),
                                   REPLACE(UPPER(mid.currency_scope), ' ', '')) > 0
                  )
              AND (
                    UPPER(capability.payment_method) <> 'BANK_CARD'
                    OR (
                        brand.card_brand IS NOT NULL
                        AND (
                            mid.card_brand_scope IS NULL
                            OR TRIM(mid.card_brand_scope) = ''
                            OR UPPER(TRIM(mid.card_brand_scope)) = 'ALL'
                            OR FIND_IN_SET(UPPER(brand.card_brand),
                                           REPLACE(UPPER(mid.card_brand_scope), ' ', '')) > 0
                        )
                    )
                  )
            ORDER BY payment_type, payment_method, currency
            """;

    private static final String IP_WHITELIST_SUMMARY_SQL = """
            SELECT COUNT(1) AS total_count,
                   COALESCE(SUM(CASE WHEN approval_status = 1 AND status = 1 THEN 1 ELSE 0 END), 0) AS active_count,
                   COALESCE(SUM(CASE WHEN approval_status = 0 THEN 1 ELSE 0 END), 0) AS pending_count,
                   COALESCE(SUM(CASE WHEN approval_status = 2 THEN 1 ELSE 0 END), 0) AS rejected_count,
                   COALESCE(SUM(CASE WHEN approval_status = 1 AND status = 0 THEN 1 ELSE 0 END), 0) AS disabled_count,
                   MAX(gmt_modified) AS last_updated_time
            FROM merchant_ip_whitelist
            WHERE merchant_id = :merchantId AND deleted = 0
            """;
    private static final String SOURCE_URL_SUMMARY_SQL = """
            SELECT COUNT(1) AS total_count,
                   COALESCE(SUM(CASE WHEN approval_status = 1 AND status = 1
                                      AND (effective_time IS NULL OR effective_time <= CURRENT_TIMESTAMP(3))
                                      AND (expire_time IS NULL OR expire_time > CURRENT_TIMESTAMP(3))
                               THEN 1 ELSE 0 END), 0) AS active_count,
                   COALESCE(SUM(CASE WHEN approval_status = 0 THEN 1 ELSE 0 END), 0) AS pending_count,
                   COALESCE(SUM(CASE WHEN approval_status = 2 THEN 1 ELSE 0 END), 0) AS rejected_count,
                   COALESCE(SUM(CASE WHEN approval_status = 1 AND status = 0 THEN 1 ELSE 0 END), 0) AS disabled_count,
                   MAX(update_time) AS last_updated_time
            FROM risk_rule_source_url
            WHERE merchant_id = :merchantId AND deleted = 0
            """;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    /**
     * 创建接入概览查询服务。
     *
     * @param jdbcTemplate 管理数据源 JDBC 模板
     */
    public JdbcMerchantAccessOverviewQueryService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @DS(DataSourceName.MASTER)
    public MerchantProfileFacts findMerchantProfile(String merchantId) {
        return jdbcTemplate.query(MERCHANT_PROFILE_SQL, params(merchantId), (rs, rowNum) ->
                new MerchantProfileFacts(
                        rs.getString("onboarding_status"),
                        rs.getString("review_status"),
                        rs.getString("activation_status"),
                        rs.getString("settlement_currency"),
                        nullableInteger(rs.getObject("risk_level")),
                        localDateTime(rs.getTimestamp("last_updated_time"))))
                .stream().findFirst().orElse(null);
    }

    @Override
    @DS(DataSourceName.MASTER)
    public SettlementFacts findSettlement(String merchantId) {
        return jdbcTemplate.query(SETTLEMENT_SUMMARY_SQL, params(merchantId), (rs, rowNum) ->
                new SettlementFacts(
                        rs.getString("target_currency"),
                        rs.getString("business_time_zone"),
                        rs.getString("daily_cutoff_time"),
                        rs.getString("processing_mode"),
                        rs.getString("initial_delay_unit"),
                        nullableInteger(rs.getObject("initial_delay_days")),
                        nullableInteger(rs.getObject("regular_delay_days")),
                        rs.getString("settlement_frequency"),
                        nullableInteger(rs.getObject("frequency_day")),
                        localDateTime(rs.getTimestamp("last_updated_time"))))
                .stream().findFirst().orElse(null);
    }

    @Override
    @DS(DataSourceName.MASTER)
    public FeeFacts findFee(String merchantId) {
        return jdbcTemplate.query(FEE_SUMMARY_SQL, params(merchantId), (rs, rowNum) ->
                new FeeFacts(
                        nullableInteger(rs.getObject("version_no")),
                        rs.getString("version_status"),
                        localDateTime(rs.getTimestamp("last_updated_time"))))
                .stream().findFirst().orElse(null);
    }

    @Override
    @DS(DataSourceName.MASTER)
    public long countActivePaymentBindings(String merchantId, LocalDateTime now) {
        Long count = jdbcTemplate.queryForObject(
                ACTIVE_BINDING_COUNT_SQL, params(merchantId).addValue("now", now), Long.class);
        return count == null ? 0 : count;
    }

    @Override
    @DS(DataSourceName.MASTER)
    public List<PaymentCapabilityFacts> listPaymentCapabilities(String merchantId, LocalDateTime now) {
        return jdbcTemplate.query(
                PAYMENT_CAPABILITY_SQL,
                params(merchantId).addValue("now", now),
                (rs, rowNum) -> new PaymentCapabilityFacts(
                        rs.getString("payment_type"),
                        rs.getString("payment_method"),
                        rs.getString("currency"),
                        localDateTime(rs.getTimestamp("last_updated_time"))));
    }

    @Override
    @DS(DataSourceName.MASTER)
    public AccessRuleFacts summarizeIpWhitelists(String merchantId) {
        return requireFacts(jdbcTemplate.queryForObject(
                IP_WHITELIST_SUMMARY_SQL, params(merchantId), factsMapper()));
    }

    @Override
    @DS(DataSourceName.MASTER)
    public AccessRuleFacts summarizeSourceUrls(String merchantId) {
        return requireFacts(jdbcTemplate.queryForObject(
                SOURCE_URL_SUMMARY_SQL, params(merchantId), factsMapper()));
    }

    /**
     * 创建限定当前商户的命名参数，所有统计查询均禁止由页面扩大数据范围。
     */
    private MapSqlParameterSource params(String merchantId) {
        return new MapSqlParameterSource("merchantId", merchantId);
    }

    /**
     * 将聚合查询结果转换为稳定的非空统计事实。
     */
    private RowMapper<AccessRuleFacts> factsMapper() {
        return (rs, rowNum) -> {
            Timestamp lastUpdatedTime = rs.getTimestamp("last_updated_time");
            return new AccessRuleFacts(
                    rs.getLong("total_count"),
                    rs.getLong("active_count"),
                    rs.getLong("pending_count"),
                    rs.getLong("rejected_count"),
                    rs.getLong("disabled_count"),
                    lastUpdatedTime == null ? null : lastUpdatedTime.toLocalDateTime());
        };
    }

    /**
     * 聚合查询理论上始终返回一行；异常空结果回退为零值，避免页面伪造配置事实。
     */
    private AccessRuleFacts requireFacts(AccessRuleFacts facts) {
        return facts == null ? new AccessRuleFacts(0, 0, 0, 0, 0, null) : facts;
    }

    /** JDBC 数值列为空时保留 null，避免把未配置值误报为 0。 */
    private Integer nullableInteger(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    /** 将 JDBC 时间戳转换为项目统一的 LocalDateTime。 */
    private LocalDateTime localDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }
}
