package com.scott.payment.risk.service.impl;

import com.scott.payment.risk.api.internal.dto.RiskThreeDsPolicyRequestDTO;
import com.scott.payment.risk.api.internal.dto.RiskThreeDsPolicyResultDTO;
import com.scott.payment.risk.domain.RiskListMatch;
import com.scott.payment.risk.repository.RiskListRuntimeRepository;
import com.scott.payment.risk.service.RiskThreeDsPolicyService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Optional;

/**
 * 默认路由后 3DS 策略服务，仅执行规则读取，不创建累计限额或频控预占。
 */
@Service
public class DefaultRiskThreeDsPolicyService implements RiskThreeDsPolicyService {

    /**
     * 规则命中后强制执行 3DS 的策略动作。
     */
    private static final String ACTION_FORCE_THREE_DS = "FORCE_3DS";

    /**
     * 规则命中后允许跳过 3DS 的策略动作。
     */
    private static final String ACTION_SKIP_THREE_DS = "SKIP_3DS";

    /**
     * 没有适用 3DS 规则或规则动作不可识别时的稳定返回值。
     */
    private static final String ACTION_NONE = "NONE";

    /**
     * 通用风控决策要求 3DS 时使用的决策编码。
     */
    private static final String DECISION_REQUIRE_THREE_DS = "REQUIRE_3DS";

    /**
     * 只读运行时规则仓储。
     */
    private final RiskListRuntimeRepository riskListRuntimeRepository;

    /**
     * 创建路由后 3DS 策略服务。
     *
     * @param riskListRuntimeRepository 只读规则查询仓储
     */
    public DefaultRiskThreeDsPolicyService(RiskListRuntimeRepository riskListRuntimeRepository) {
        this.riskListRuntimeRepository = riskListRuntimeRepository;
    }

    /**
     * 查询最高优先级适用规则，不执行任何计数、预占、回滚或交易状态写入。
     *
     * @param requestDTO 已完成路由的交易维度
     * @return 强制、跳过或未配置的 3DS 策略
     */
    @Override
    public RiskThreeDsPolicyResultDTO evaluate(RiskThreeDsPolicyRequestDTO requestDTO) {
        Optional<RiskListMatch> matched = riskListRuntimeRepository.findThreeDsRule(
                requestDTO.getMerchantId(),
                requestDTO.getChannelCode(),
                requestDTO.getPaymentMethod(),
                requestDTO.getCardBrand(),
                requestDTO.getAmount(),
                requestDTO.getCurrency(),
                requestDTO.getCurrentRiskLevel());
        if (matched.isEmpty()) {
            return result(false, ACTION_NONE, null, null);
        }
        RiskListMatch rule = matched.get();
        String action = normalizedAction(rule);
        boolean required = ACTION_FORCE_THREE_DS.equals(action)
                || DECISION_REQUIRE_THREE_DS.equalsIgnoreCase(rule.getDecisionAction());
        return result(required, required ? ACTION_FORCE_THREE_DS : action, rule.getRuleId(), rule.getDecisionReason());
    }

    /**
     * 将数据库命中字段收敛为稳定策略动作。
     */
    private String normalizedAction(RiskListMatch rule) {
        if (!StringUtils.hasText(rule.getHitElement())) {
            return ACTION_NONE;
        }
        String action = rule.getHitElement().trim().toUpperCase(Locale.ROOT);
        return ACTION_FORCE_THREE_DS.equals(action) || ACTION_SKIP_THREE_DS.equals(action)
                ? action : ACTION_NONE;
    }

    /**
     * 构造只读策略结果。
     */
    private RiskThreeDsPolicyResultDTO result(boolean required, String action, Long ruleId, String reason) {
        RiskThreeDsPolicyResultDTO result = new RiskThreeDsPolicyResultDTO();
        result.setRequired(required);
        result.setAction(action);
        result.setRuleId(ruleId);
        result.setReason(reason);
        return result;
    }
}
