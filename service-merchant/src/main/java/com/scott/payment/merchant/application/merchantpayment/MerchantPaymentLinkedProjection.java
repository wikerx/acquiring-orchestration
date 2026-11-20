package com.scott.payment.merchant.application.merchantpayment;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.merchant.client.admin.MerchantPaymentGovernanceClient;
import com.scott.payment.merchant.client.admin.MerchantPaymentGovernanceClient.Identity;
import com.scott.payment.merchant.dto.MerchantFinanceDTOs.CurrentFeeResponse;
import com.scott.payment.merchant.dto.MerchantFinanceDTOs.FeeRuleResponse;
import com.scott.payment.merchant.dto.merchantpayment.MerchantPaymentModels.AccessSummary;
import com.scott.payment.merchant.dto.merchantpayment.MerchantPaymentModels.AuthorizationCoverage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 旧页面保留原事实，新的授权相关投影只接受权威端返回。 */
@Service
@DS(DataSourceName.MASTER)
public class MerchantPaymentLinkedProjection {
    private static final Logger LOG = LoggerFactory.getLogger(MerchantPaymentLinkedProjection.class);
    private static final String HISTORY_PENDING = "HISTORY_PENDING";
    private static final String EVALUATED = "EVALUATED";
    private static final String UNAVAILABLE = "UNAVAILABLE";
    private static final Set<String> ACTIVE_MODES = Set.of("ENFORCED", "LEGACY_OBSERVE", "HOLD");
    private final NamedParameterJdbcTemplate jdbc;
    private final MerchantPaymentGovernanceClient owner;

    public MerchantPaymentLinkedProjection(NamedParameterJdbcTemplate jdbc, MerchantPaymentGovernanceClient owner) {
        this.jdbc = jdbc;
        this.owner = owner;
    }

    public AccessSummary overview(String merchant) {
        String scope = eligibility(merchant);
        if (!EVALUATED.equals(scope)) {
            return AccessSummary.unavailable(scope);
        }
        try {
            AccessSummary summary = owner.accessSummary(merchant, actor(merchant));
            return summary == null ? AccessSummary.unavailable(UNAVAILABLE) : summary;
        } catch (RuntimeException exception) {
            unavailable(merchant, exception);
            return AccessSummary.unavailable(UNAVAILABLE);
        }
    }

    public CurrentFeeResponse currentFee(String merchant, CurrentFeeResponse source) {
        if (source == null) {
            return null;
        }

        // 原费率结果跨请求缓存，只装饰本次返回的副本，避免把授权状态写进费用缓存。
        CurrentFeeResponse response = new CurrentFeeResponse();
        BeanUtils.copyProperties(source, response);
        String scope = eligibility(merchant);
        response.setAuthorizationAssessmentStatus(scope);
        if (!EVALUATED.equals(scope)) {
            return response;
        }
        try {
            var authority = owner.feeCoverage(merchant, actor(merchant));
            response.setAuthorizationAssessmentStatus(authority.assessmentStatus());
            if (!EVALUATED.equals(authority.assessmentStatus())) {
                return response;
            }
            Map<String, List<AtomicFeeDimension>> dimensions = activeFeeDimensions(merchant, source.getVersionNo());
            if (source.getRules().stream().anyMatch(rule -> !dimensions.containsKey(String.valueOf(rule.getId())))) {
                // 两次读取之间费率切换时只显示已读快照，不能拼接另一版的范围。
                response.setAuthorizationAssessmentStatus(UNAVAILABLE);
                return response;
            }
            response.setRules(source.getRules().stream().map(rule ->
                    decorate(rule, dimensions.get(String.valueOf(rule.getId())), authority.methods())).toList());
            return response;
        } catch (RuntimeException exception) {
            unavailable(merchant, exception);
            response.setAuthorizationAssessmentStatus(UNAVAILABLE);
            return response;
        }
    }

    private String eligibility(String merchant) {
        try {
            var modes = jdbc.query("""
                    SELECT payment_admission_mode, payment_baseline_batch_ref
                    FROM base_merchant_info WHERE merchant_id = :merchant AND deleted = 0
                    """, Map.of("merchant", merchant), (row, index) -> new Mode(row.getString(1), row.getString(2)));
            if (modes.isEmpty()) {
                return UNAVAILABLE;
            }
            Mode mode = modes.get(0);
            if (mode.code() == null || "LEGACY_OBSERVE".equals(mode.code()) && mode.baseline() == null) {
                return HISTORY_PENDING;
            }
            return ACTIVE_MODES.contains(mode.code()) ? EVALUATED : UNAVAILABLE;
        } catch (BadSqlGrammarException exception) {
            if (exception.getSQLException() != null && exception.getSQLException().getErrorCode() == 1054) {
                // 扩展列发布之前仍使用原历史口径，不影响旧库运行。
                return HISTORY_PENDING;
            }
            unavailable(merchant, exception);
            return UNAVAILABLE;
        } catch (DataAccessException exception) {
            unavailable(merchant, exception);
            return UNAVAILABLE;
        }
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

    private static Identity actor(String merchant) {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null || !"MERCHANT".equals(account.getAppCode()) || !merchant.equals(account.getMerchantId())
                || account.getAccountId() == null || account.getUserId() == null) {
            throw new ServiceException(ApiResultEnum.FORBIDDEN);
        }
        return new Identity(account.getAccountId(), account.getUserId());
    }

    private static void unavailable(String merchant, Exception exception) {
        LOG.warn("商户支付方式联动评估不可用, event: MERCHANT_PAYMENT_LINKED_PROJECTION_UNAVAILABLE, merchantId: {}, exceptionType: {}",
                merchant, exception.getClass().getSimpleName());
    }

    private record Mode(String code, String baseline) {}
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
