package com.scott.payment.capability;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 主库支付方式授权和冻结范围的只读访问。 */
@Repository
public class MerchantAdmissionReader {

    private final JdbcTemplate jdbcTemplate;

    public MerchantAdmissionReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 一次读取商户的准入模式和方式列表，不将运营投影作为付款授权。 */
    @DS(DataSourceName.MASTER)
    public MerchantAuthority read(String merchantId) {
        List<MerchantMode> modes = jdbcTemplate.query("""
                SELECT payment_admission_mode, payment_baseline_batch_ref
                FROM base_merchant_info
                WHERE merchant_id = ? AND deleted = 0
                """, (rs, row) -> new MerchantMode(rs.getString(1), rs.getString(2)), merchantId);
        if (modes.isEmpty()) {
            return new MerchantAuthority(null, Map.of());
        }
        MerchantMode mode = modes.get(0);
        Map<String, ItemAuthority> items = new HashMap<>();
        jdbcTemplate.query("""
                SELECT item_key, authorization_status, source, version, effective_time, expire_time
                FROM merchant_payment_authorization
                WHERE merchant_id = ?
                """, rs -> {
            MerchantAdmissionPolicy.Grant grant = new MerchantAdmissionPolicy.Grant(
                    rs.getString("authorization_status"), rs.getString("source"), rs.getLong("version"),
                    rs.getObject("effective_time", LocalDateTime.class),
                    rs.getObject("expire_time", LocalDateTime.class));
            items.put(rs.getString("item_key"), new ItemAuthority(grant, false));
        }, merchantId);
        if ("LEGACY_OBSERVE".equals(mode.code()) && mode.baselineBatchRef() != null) {
            jdbcTemplate.query("""
                    SELECT target_key
                    FROM merchant_review_record
                    WHERE merchant_id = ? AND review_scope = 'LEGACY_SCOPE'
                      AND request_no = ? AND review_action = 'BASELINE_APPROVE' AND to_status = 'APPROVED'
                    """, rs -> {
                String itemKey = rs.getString("target_key");
                if (MerchantAdmissionPolicy.isItemKey(itemKey)) {
                    items.compute(itemKey, (key, prior) -> new ItemAuthority(
                            prior == null ? null : prior.grant(), true));
                }
            }, merchantId, mode.baselineBatchRef());
        }
        return new MerchantAuthority(mode.code(), Map.copyOf(items));
    }

    private record MerchantMode(String code, String baselineBatchRef) {
    }

    /** 读取当前生效版本的主交易费率维度，保留零费率与缺失规则之间的区别。 */
    @DS(DataSourceName.MASTER)
    public List<FeeCoverage> transactionFeeCoverage(String merchantId) {
        return jdbcTemplate.query("""
                SELECT rule.transaction_type, rule.payment_type, rule.payment_method
                FROM fee_plan plan
                JOIN fee_plan_version version
                  ON version.id = plan.current_version_id AND version.plan_id = plan.id
                 AND version.version_no = plan.current_version_no
                 AND version.version_status = 'ACTIVE' AND version.deleted = 0
                JOIN fee_rule rule
                  ON rule.plan_version_id = version.id AND rule.deleted = 0
                WHERE plan.merchant_id = ? AND plan.plan_type = 'MERCHANT'
                  AND plan.deleted = 0 AND plan.status = 'ENABLED'
                  AND rule.fee_category = 'TRANSACTION_FEE' AND rule.risk_service_type = 'NONE'
                """, (result, row) -> new FeeCoverage(result.getString(1), result.getString(2), result.getString(3)),
                merchantId);
    }

    /** 任一语言状态冲突时暂不把该规范方式视作平台已启用。 */
    @DS(DataSourceName.MASTER)
    public Set<String> enabledDirectory() {
        List<String> keys = jdbcTemplate.queryForList("""
                SELECT CONCAT(IF(data.dict_type = 'card_brand', 'CARD_BRAND:', 'METHOD:'),
                              UPPER(data.dict_value)) AS item_key
                FROM sys_dict_data data
                JOIN sys_dict_type type ON type.dict_type = data.dict_type AND type.deleted = 0 AND type.status = 1
                WHERE data.dict_type IN ('acquiring_payment_method', 'card_brand') AND data.deleted = 0
                GROUP BY item_key
                HAVING MIN(data.status) = 1 AND MAX(data.status) = 1
                """, String.class);
        boolean cardEnabled = keys.contains("METHOD:BANK_CARD");
        return keys.stream().filter(MerchantAdmissionPolicy::isItemKey)
                .filter(key -> cardEnabled || !key.startsWith("CARD_BRAND:"))
                .collect(Collectors.toUnmodifiableSet());
    }

    /** 读到的是独立的当前批准和历史冻结事实。 */
    public record ItemAuthority(MerchantAdmissionPolicy.Grant grant, boolean frozenLegacyScope) {
    }

    /** 商户准入模式和按规范方式键读取的事实。 */
    public record MerchantAuthority(String mode, Map<String, ItemAuthority> items) {
    }

    /** 原生效费率的覆盖维度，不重新计算或冻结金额。 */
    public record FeeCoverage(String action, String paymentType, String paymentMethod) {
        /**
         * 判断当前授权的生效和失效边界是否覆盖请求时间。
         */
        public boolean covers(String transactionType, String actualType, String actualMethod) {
            return matches(action, transactionType)
                    && matches(paymentType, actualType) && matches(paymentMethod, actualMethod);
        }

        private static boolean matches(String configured, String requested) {
            return "ALL".equalsIgnoreCase(configured) || requested.equalsIgnoreCase(configured);
        }
    }
}
