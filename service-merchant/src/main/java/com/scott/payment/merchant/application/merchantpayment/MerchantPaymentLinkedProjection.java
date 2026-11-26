package com.scott.payment.merchant.application.merchantpayment;

import com.scott.payment.capability.governance.MerchantPaymentGovernanceService;
import com.scott.payment.merchant.dto.MerchantFinanceDTOs.CurrentFeeResponse;
import com.scott.payment.merchant.dto.MerchantFinanceDTOs.FeeRuleResponse;
import com.scott.payment.capability.governance.MerchantPaymentModels.AccessSummary;
import com.scott.payment.capability.governance.MerchantPaymentModels.AuthorizationCoverage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/** 旧页面保留原事实，新的授权相关投影只接受权威端返回。 */
@Service
public class MerchantPaymentLinkedProjection {
    private static final Logger LOG = LoggerFactory.getLogger(MerchantPaymentLinkedProjection.class);
    private static final String HISTORY_PENDING = "HISTORY_PENDING";
    private static final String EVALUATED = "EVALUATED";
    private static final String UNAVAILABLE = "UNAVAILABLE";
    private final NamedParameterJdbcTemplate jdbc;
    private final MerchantPaymentGovernanceService governance;
    private final MerchantPaymentReads reads;

    public MerchantPaymentLinkedProjection(NamedParameterJdbcTemplate jdbc, MerchantPaymentGovernanceService governance,
                                           MerchantPaymentReads reads) {
        this.jdbc = jdbc;
        this.governance = governance;
        this.reads = reads;
    }

    /**
     * 读取商户接入概览及授权状态，必要时以主库版本校正。
     */
    public AccessSummary overview(String merchant) {
        try {
            return reads.read(merchant, scope -> governance.accessSummary(scope, merchant));
        } catch (BadSqlGrammarException exception) {
            if (exception.getSQLException() != null && exception.getSQLException().getErrorCode() == 1054) {
                return AccessSummary.unavailable(HISTORY_PENDING);
            }
            unavailable(merchant, exception);
            return AccessSummary.unavailable(UNAVAILABLE);
        } catch (RuntimeException exception) {
            unavailable(merchant, exception);
            return AccessSummary.unavailable(UNAVAILABLE);
        }
    }

    /**
     * 组合当前商户生效费率和支付方式授权的展示投影。
     */
    public CurrentFeeResponse currentFee(String merchant, CurrentFeeResponse source) {
        if (source == null) {
            return null;
        }

        // 原费率结果跨请求缓存，只装饰本次返回的副本，避免把授权状态写进费用缓存。
        CurrentFeeResponse response = new CurrentFeeResponse();
        BeanUtils.copyProperties(source, response);
        response.setRules(source.getRules().stream().map(MerchantPaymentLinkedProjection::copyFeeRuleForDisplay).toList());
        try {
            return reads.read(merchant, scope -> {
                var authority = governance.feeAuthorizations(scope, merchant);
                response.setAuthorizationAssessmentStatus(authority.assessmentStatus());
                boolean hasPendingCoverage = HISTORY_PENDING.equals(authority.assessmentStatus())
                        && authority.methods() != null && !authority.methods().isEmpty();
                if (!EVALUATED.equals(authority.assessmentStatus()) && !hasPendingCoverage) {
                    return response;
                }
                Map<String, List<AtomicFeeDimension>> dimensions = activeFeeDimensions(merchant, source.getVersionNo());
                if (source.getRules().stream().anyMatch(rule -> !dimensions.containsKey(String.valueOf(rule.getId())))) {
                    // 两次读取之间费率切换时只显示已读快照，不能拼接另一版的范围。
                    response.setAuthorizationAssessmentStatus(UNAVAILABLE);
                    return response;
                }
                response.setRules(response.getRules().stream().map(rule ->
                        decorate(rule, dimensions.get(String.valueOf(rule.getId())), authority.methods())).toList());
                return response;
            });
        } catch (BadSqlGrammarException exception) {
            if (exception.getSQLException() != null && exception.getSQLException().getErrorCode() == 1054) {
                response.setAuthorizationAssessmentStatus(HISTORY_PENDING);
                return response;
            }
            unavailable(merchant, exception);
            response.setAuthorizationAssessmentStatus(UNAVAILABLE);
            return response;
        } catch (RuntimeException exception) {
            unavailable(merchant, exception);
            response.setAuthorizationAssessmentStatus(UNAVAILABLE);
            return response;
        }
    }

    /**
     * 在请求级副本中补充精确金额，避免把展示字段写回跨版本费率缓存。
     */
    private static FeeRuleResponse copyFeeRuleForDisplay(FeeRuleResponse source) {
        FeeRuleResponse rule = new FeeRuleResponse();
        BeanUtils.copyProperties(source, rule);
        if (source.getFixedAmountUsd() != null) {
            rule.setFixedAmountUsdExact(source.getFixedAmountUsd().toPlainString());
        }
        return rule;
    }

    private Map<String, List<AtomicFeeDimension>> activeFeeDimensions(String merchant, Integer versionNo) {
        if (versionNo == null) {
            return Map.of();
        }
        var rows = jdbc.query("""
                SELECT r.id, r.rule_group_code, r.payment_type, r.payment_method
                FROM fee_plan p JOIN fee_plan_version v ON v.id = p.current_version_id AND v.plan_id = p.id
                JOIN fee_rule r ON r.plan_version_id = v.id AND r.deleted = 0
                WHERE p.plan_type = 'MERCHANT' AND p.merchant_id = :merchant AND p.deleted = 0 AND p.status = 'ENABLED'
                  AND v.version_no = :version AND v.version_status = 'ACTIVE' AND v.deleted = 0
                ORDER BY r.sort_no, r.id
                """, Map.of("merchant", merchant, "version", versionNo), (row, index) ->
                new AtomicFeeDimension(row.getString("id"), row.getString("rule_group_code"),
                        row.getString("payment_type"), row.getString("payment_method")));
        return groupDimensions(rows);
    }

    static Map<String, List<AtomicFeeDimension>> groupDimensions(List<AtomicFeeDimension> rows) {
        var grouped = rows.stream().collect(Collectors.groupingBy(row -> row.group() == null || row.group().isBlank()
                        ? new Group(null, row.id()) : new Group(row.group(), null), LinkedHashMap::new, Collectors.toList()));
        return grouped.values().stream().collect(Collectors.toMap(group -> group.get(0).id(), group -> group));
    }

    static FeeRuleResponse decorate(FeeRuleResponse source, List<AtomicFeeDimension> dimensions,
                                    List<AuthorizationCoverage> methods) {
        FeeRuleResponse rule = new FeeRuleResponse();
        BeanUtils.copyProperties(source, rule);
        rule.setPaymentMethodCoverage(methods.stream()
                .filter(method -> dimensions.stream().anyMatch(dimension -> dimension.matches(method.itemKey())))
                .toList());
        return rule;
    }

    private static void unavailable(String merchant, Exception exception) {
        LOG.warn("商户支付方式联动评估不可用, event: MERCHANT_PAYMENT_LINKED_PROJECTION_UNAVAILABLE, merchantId: {}, exceptionType: {}",
                merchant, exception.getClass().getSimpleName());
    }

    private record Group(String code, String original) {}

    record AtomicFeeDimension(String id, String group, String type, String method) {
        boolean matches(String itemKey) {
            if (itemKey == null || !(itemKey.startsWith("CARD_BRAND:") || itemKey.startsWith("METHOD:"))) {
                return false;
            }
            boolean card = itemKey.startsWith("CARD_BRAND:");
            String code = itemKey.substring(itemKey.indexOf(':') + 1);
            return dimension(type, card ? "BANK_CARD" : code) && dimension(method, card ? code : "ALL");
        }

        private static boolean dimension(String configured, String value) {
            String code = configured == null ? "" : configured.trim().toUpperCase(Locale.ROOT);
            return "ALL".equals(code) || value.equals(code);
        }
    }
}
