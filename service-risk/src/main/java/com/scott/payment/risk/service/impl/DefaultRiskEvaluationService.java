package com.scott.payment.risk.service.impl;

import com.scott.payment.component.core.util.identity.PaymentOrderNoGenerator;
import com.scott.payment.risk.api.internal.dto.RiskPaymentEvaluateRequestDTO;
import com.scott.payment.risk.api.internal.dto.RiskPaymentEvaluateResultDTO;
import com.scott.payment.risk.domain.state.RiskDecisionEnum;
import com.scott.payment.risk.domain.state.RiskReasonCodeEnum;
import com.scott.payment.risk.service.RiskEvaluationService;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
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
     * 当前内置风控规则数量，用于日志展示评估覆盖范围。
     */
    private static final int BUILT_IN_RULE_COUNT = 5;

    /**
     * 执行收单支付路由前风控评估。
     *
     * @param requestDTO 支付风控评估请求
     * @return 风控决策结果
     */
    @Override
    public RiskPaymentEvaluateResultDTO evaluatePayment(RiskPaymentEvaluateRequestDTO requestDTO) {
        long startNanos = System.nanoTime();
        log.info("event=RISK_EVALUATION_START stage=ACCEPT merchantId: {} merchantOrderNo: {} transactionType: {} paymentMethod: {} amount: {} currency: {} ruleCount: {}",
                requestDTO == null ? null : requestDTO.getMerchantId(),
                requestDTO == null ? null : requestDTO.getMerchantOrderNo(),
                requestDTO == null ? null : requestDTO.getTransactionType(),
                requestDTO == null ? null : requestDTO.getPaymentMethod(),
                requestDTO == null ? null : requestDTO.getAmount(),
                requestDTO == null ? null : requestDTO.getCurrency(),
                BUILT_IN_RULE_COUNT);
        RiskPaymentEvaluateResultDTO resultDTO;
        String hitRuleId;
        String hitRuleType;
        if (isInvalid(requestDTO)) {
            resultDTO = buildResult(RiskDecisionEnum.REJECT, RiskReasonCodeEnum.PARAM_INVALID);
            hitRuleId = "RISK_PARAM_INVALID";
            hitRuleType = "PARAM_VALIDATION";
        } else if (isBlockedSource(requestDTO.getSourceUrl())) {
            resultDTO = buildResult(RiskDecisionEnum.REJECT, RiskReasonCodeEnum.BLOCKED_SOURCE);
            hitRuleId = "RISK_BLOCKED_SOURCE";
            hitRuleType = "SOURCE_BLOCKLIST";
        } else if (isBlockedPayerIp(requestDTO.getPayerIp())) {
            resultDTO = buildResult(RiskDecisionEnum.REJECT, RiskReasonCodeEnum.BLOCKED_IP);
            hitRuleId = "RISK_BLOCKED_PAYER_IP";
            hitRuleType = "IP_BLOCKLIST";
        } else if (requestDTO.getAmount().compareTo(MANUAL_REVIEW_AMOUNT) >= 0) {
            resultDTO = buildResult(RiskDecisionEnum.REVIEW, RiskReasonCodeEnum.MANUAL_REVIEW_REQUIRED);
            hitRuleId = "RISK_AMOUNT_REVIEW";
            hitRuleType = "AMOUNT_THRESHOLD";
        } else if (requestDTO.getAmount().compareTo(REQUIRE_3DS_AMOUNT) >= 0 && !hasThreeDsProof(requestDTO)) {
            resultDTO = buildResult(RiskDecisionEnum.REQUIRE_3DS, RiskReasonCodeEnum.THREE_DS_REQUIRED);
            hitRuleId = "RISK_AMOUNT_3DS";
            hitRuleType = "AUTHENTICATION_REQUIREMENT";
        } else {
            resultDTO = buildResult(RiskDecisionEnum.PASS, RiskReasonCodeEnum.NONE);
            hitRuleId = "NONE";
            hitRuleType = "NO_RULE_HIT";
        }
        log.info("event=RISK_EVALUATION_END stage=DECISION merchantId: {} merchantOrderNo: {} transactionType: {} amount: {} currency: {} ruleCount: {} hitRuleId: {} hitRuleType: {} decision: {} rejectReasonCode: {} durationMs: {}",
                requestDTO == null ? null : requestDTO.getMerchantId(),
                requestDTO == null ? null : requestDTO.getMerchantOrderNo(),
                requestDTO == null ? null : requestDTO.getTransactionType(),
                requestDTO == null ? null : requestDTO.getAmount(),
                requestDTO == null ? null : requestDTO.getCurrency(),
                BUILT_IN_RULE_COUNT,
                hitRuleId,
                hitRuleType,
                resultDTO.getDecision(),
                resultDTO.getReasonCode(),
                elapsedMillis(startNanos));
        return resultDTO;
    }

    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
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

    /**
     * 执行 is Blocked Payer Ip 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：风控服务层；输入来源、输出结构和异常语义由 DefaultRiskEvaluationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param payerIp payer Ip 输入值，含义由调用方法名称和所属业务对象限定
     * @return 满足当前业务条件时返回 true，否则返回 false
     */
    private boolean isBlockedPayerIp(String payerIp) {
        return StringUtils.hasText(payerIp) && BLOCKED_PAYER_IPS.contains(payerIp.trim());
    }

    /**
     * 执行 has Three Ds Proof 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：风控服务层；输入来源、输出结构和异常语义由 DefaultRiskEvaluationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param requestDTO 内部客户端请求 DTO，携带跨服务调用所需的交易、金额和商户维度字段
     * @return 满足当前业务条件时返回 true，否则返回 false
     */
    private boolean hasThreeDsProof(RiskPaymentEvaluateRequestDTO requestDTO) {
        return StringUtils.hasText(requestDTO.getThreeDsEci())
                || StringUtils.hasText(requestDTO.getThreeDsVersion())
                || StringUtils.hasText(requestDTO.getThreeDsTransactionId());
    }

    /**
     * 执行 build Result 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：风控服务层；输入来源、输出结构和异常语义由 DefaultRiskEvaluationService 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param decisionEnum decision Enum 输入值，含义由调用方法名称和所属业务对象限定
     * @param reasonCodeEnum reason Code Enum 输入值，含义由调用方法名称和所属业务对象限定
     * @return 转换或构建后的目标对象
     */
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
