package com.scott.payment.risk.service.impl;

import com.scott.payment.component.core.trace.TraceContext;
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
        log.info("event: RISK_EVALUATION_START stage=ACCEPT traceId: {} merchantId: {} merchantOrderNo: {} transactionId: {} transactionType: {} paymentMethod: {} amount: {} currency: {} ruleCount: {} payerIp: {} sourceUrl: {}",
                TraceContext.getTraceId(),
                requestDTO == null ? null : requestDTO.getMerchantId(),
                requestDTO == null ? null : requestDTO.getMerchantOrderNo(),
                requestDTO == null ? null : requestDTO.getTransactionId(),
                requestDTO == null ? null : requestDTO.getTransactionType(),
                requestDTO == null ? null : requestDTO.getPaymentMethod(),
                requestDTO == null ? null : requestDTO.getAmount(),
                requestDTO == null ? null : requestDTO.getCurrency(),
                BUILT_IN_RULE_COUNT,
                requestDTO == null ? null : requestDTO.getPayerIp(),
                maskUrl(requestDTO == null ? null : requestDTO.getSourceUrl()));
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
        log.info("event: RISK_EVALUATION_END stage=DECISION traceId: {} merchantId: {} merchantOrderNo: {} transactionId: {} transactionType: {} amount: {} currency: {} ruleCount: {} hitRuleId: {} hitRuleType: {} decision: {} rejectReasonCode: {} durationMs: {}",
                TraceContext.getTraceId(),
                requestDTO == null ? null : requestDTO.getMerchantId(),
                requestDTO == null ? null : requestDTO.getMerchantOrderNo(),
                requestDTO == null ? null : requestDTO.getTransactionId(),
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

    /**
     * 脱敏来源 URL 查询参数。
     *
     * @param sourceUrl 风控输入的来源 URL
     * @return 可写入日志的 URL 摘要
     */
    private String maskUrl(String sourceUrl) {
        if (!StringUtils.hasText(sourceUrl)) {
            return null;
        }
        int queryIndex = sourceUrl.indexOf('?');
        if (queryIndex < 0) {
            return sourceUrl;
        }
        return sourceUrl.substring(0, queryIndex) + "?...";
    }

    /**
     * 判断支付风控评估请求是否缺少最小必填字段。
     * <p>
     * 前置条件：OpenAPI 或 payment 服务已经完成基础 DTO 反序列化。
     * 该方法只检查风控决策必需的商户号、商户订单号、币种和正金额；不读取完整卡号、邮箱、手机号等敏感字段，
     * 返回 true 时上层直接给出拒绝结论并记录原因码。
     * </p>
     * @param requestDTO 支付风控评估请求
     * @return true 表示请求缺少必填字段或金额不合法
     */
    private boolean isInvalid(RiskPaymentEvaluateRequestDTO requestDTO) {
        return requestDTO == null
                || !StringUtils.hasText(requestDTO.getMerchantId())
                || !StringUtils.hasText(requestDTO.getMerchantOrderNo())
                || !StringUtils.hasText(requestDTO.getCurrency())
                || requestDTO.getAmount() == null
                || requestDTO.getAmount().compareTo(BigDecimal.ZERO) <= 0;
    }

    /**
     * 判断商户来源页面是否命中阻断关键字。
     * <p>
     * 前置条件：sourceUrl 已按日志规则去除 query 值后再打印。
     * 该方法仅基于受控关键字判断风险来源，不访问外部 IP 库或页面内容；命中后返回拒绝结论。
     * </p>
     * @param sourceUrl 商户请求来源页面 URL
     * @return true 表示来源页面命中阻断关键字
     */
    private boolean isBlockedSource(String sourceUrl) {
        return StringUtils.hasText(sourceUrl)
                && sourceUrl.toLowerCase(Locale.ROOT).contains(BLOCKED_SOURCE_KEYWORD);
    }

    /**
     * 判断 is blocked payer ip 条件是否成立，用于控制 Default Risk Evaluation Service 的后续分支。
     * <p>
     * 前置条件：调用方已准备 风控服务 判断所需的对象、枚举或配置。
     * 该方法不修改业务状态，只返回布尔判断结果供后续分支使用。
     * 异常边界：入参缺失时按当前方法实现返回 false 或抛出约定异常。
     * </p>
     * @param payerIp payer IP 输入值，参与 payerip 的查询、校验、转换、写入或日志摘要
     * @return 条件满足时返回 true，否则返回 false
     */
    private boolean isBlockedPayerIp(String payerIp) {
        return StringUtils.hasText(payerIp) && BLOCKED_PAYER_IPS.contains(payerIp.trim());
    }

    /**
     * 判断 has three ds proof 条件是否成立，用于控制 Default Risk Evaluation Service 的后续分支。
     * <p>
     * 前置条件：调用方已准备 风控服务 判断所需的对象、枚举或配置。
     * 该方法不修改业务状态，只返回布尔判断结果供后续分支使用。
     * 异常边界：入参缺失时按当前方法实现返回 false 或抛出约定异常。
     * </p>
     * @param requestDTO request DTO，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 条件满足时返回 true，否则返回 false
     */
    private boolean hasThreeDsProof(RiskPaymentEvaluateRequestDTO requestDTO) {
        return StringUtils.hasText(requestDTO.getThreeDsEci())
                || StringUtils.hasText(requestDTO.getThreeDsVersion())
                || StringUtils.hasText(requestDTO.getThreeDsTransactionId());
    }

    /**
     * 构造结果对象对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 风控服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param decisionEnum decision Enum 输入值，参与 结论enum 的查询、校验、转换、写入或日志摘要
     * @param reasonCodeEnum reason Code Enum 输入值，参与 reason编码enum 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
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
