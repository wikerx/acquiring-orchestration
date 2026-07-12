package com.scott.payment.risk.service.impl;

import com.scott.payment.component.core.util.identity.PaymentOrderNoGenerator;
import com.scott.payment.risk.api.internal.dto.RiskPaymentEvaluateRequestDTO;
import com.scott.payment.risk.api.internal.dto.RiskPaymentEvaluateResultDTO;
import com.scott.payment.risk.domain.state.RiskDecisionEnum;
import com.scott.payment.risk.domain.state.RiskReasonCodeEnum;
import com.scott.payment.risk.service.RiskEvaluationService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultRiskEvaluationService
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : 默认实时风控评估实现，位于 service-risk 服务实现层，提供可替换的最小规则骨架，后续可接入规则引擎、名单库和模型服务。
 * @status : create
 */
@Service
public class DefaultRiskEvaluationService implements RiskEvaluationService {

    /**
     * 风控流水号前缀。
     */
    private static final String RISK_RECORD_PREFIX = "RK";

    /**
     * 来源阻断关键词，当前仅用于骨架验证，正式名单应来自配置或数据库。
     */
    private static final String BLOCKED_SOURCE_KEYWORD = "blocked";

    /**
     * 骨架阶段的 IP 阻断名单，正式名单应迁移到名单服务或风控规则库。
     */
    private static final Set<String> BLOCKED_PAYER_IPS = Set.of("127.0.0.2");

    /**
     * 要求 3DS 的最小金额阈值，单位为主币种单位；正式规则应按商户、币种和卡 BIN 配置。
     */
    private static final BigDecimal REQUIRE_3DS_AMOUNT = new BigDecimal("1000.00");

    /**
     * 人工复核金额阈值，单位为主币种单位；正式规则应按商户、币种和风险等级配置。
     */
    private static final BigDecimal MANUAL_REVIEW_AMOUNT = new BigDecimal("5000.00");

    /**
     * 执行收单支付路由前风控评估。
     *
     * @param requestDTO 支付风控评估请求
     * @return 风控决策结果
     */
    @Override
    public RiskPaymentEvaluateResultDTO evaluatePayment(RiskPaymentEvaluateRequestDTO requestDTO) {
        if (isInvalid(requestDTO)) {
            return buildResult(RiskDecisionEnum.REJECT, RiskReasonCodeEnum.PARAM_INVALID);
        }
        if (isBlockedSource(requestDTO.getSourceUrl())) {
            return buildResult(RiskDecisionEnum.REJECT, RiskReasonCodeEnum.BLOCKED_SOURCE);
        }
        if (isBlockedPayerIp(requestDTO.getPayerIp())) {
            return buildResult(RiskDecisionEnum.REJECT, RiskReasonCodeEnum.BLOCKED_IP);
        }
        if (requestDTO.getAmount().compareTo(MANUAL_REVIEW_AMOUNT) >= 0) {
            return buildResult(RiskDecisionEnum.REVIEW, RiskReasonCodeEnum.MANUAL_REVIEW_REQUIRED);
        }
        if (requestDTO.getAmount().compareTo(REQUIRE_3DS_AMOUNT) >= 0 && !hasThreeDsProof(requestDTO)) {
            return buildResult(RiskDecisionEnum.REQUIRE_3DS, RiskReasonCodeEnum.THREE_DS_REQUIRED);
        }
        return buildResult(RiskDecisionEnum.PASS, RiskReasonCodeEnum.NONE);
    }

    private boolean isInvalid(RiskPaymentEvaluateRequestDTO requestDTO) {
        return requestDTO == null
                || !StringUtils.hasText(requestDTO.getMerchantId())
                || !StringUtils.hasText(requestDTO.getMerchantOrderNo())
                || !StringUtils.hasText(requestDTO.getCurrency())
                || requestDTO.getAmount() == null
                || requestDTO.getAmount().compareTo(BigDecimal.ZERO) <= 0;
    }

    private boolean isBlockedSource(String sourceUrl) {
        return StringUtils.hasText(sourceUrl)
                && sourceUrl.toLowerCase(Locale.ROOT).contains(BLOCKED_SOURCE_KEYWORD);
    }

    private boolean isBlockedPayerIp(String payerIp) {
        return StringUtils.hasText(payerIp) && BLOCKED_PAYER_IPS.contains(payerIp.trim());
    }

    private boolean hasThreeDsProof(RiskPaymentEvaluateRequestDTO requestDTO) {
        return StringUtils.hasText(requestDTO.getThreeDsEci())
                || StringUtils.hasText(requestDTO.getThreeDsVersion())
                || StringUtils.hasText(requestDTO.getThreeDsTransactionId());
    }

    private RiskPaymentEvaluateResultDTO buildResult(RiskDecisionEnum decisionEnum, RiskReasonCodeEnum reasonCodeEnum) {
        RiskPaymentEvaluateResultDTO resultDTO = new RiskPaymentEvaluateResultDTO();
        resultDTO.setRiskRecordNo(PaymentOrderNoGenerator.nextOrderNo(RISK_RECORD_PREFIX));
        resultDTO.setDecision(decisionEnum.getCode());
        resultDTO.setReasonCode(reasonCodeEnum.getCode());
        resultDTO.setReasonMessage(reasonCodeEnum.getMessage());
        resultDTO.setDecisionTime(LocalDateTime.now());
        return resultDTO;
    }
}
