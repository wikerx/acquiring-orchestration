package com.scott.payment.risk.service;

import com.scott.payment.component.mq.message.RiskAuditHitMessage;
import com.scott.payment.component.mq.message.RiskEvaluationAuditMessage;
import com.scott.payment.risk.api.internal.dto.RiskPaymentEvaluateRequestDTO;
import com.scott.payment.risk.domain.RiskEvaluationOutcome;
import com.scott.payment.risk.domain.RiskListMatch;
import com.scott.payment.risk.domain.state.RiskDecisionEnum;
import com.scott.payment.risk.repository.RiskAuditRecordPublisher;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 将风控评估结果转换为跨服务审计消息并交给可靠发布器。
 *
 * <p>该服务只接收已经脱敏的规则明细，不读取原始卡号、CVV、邮箱、电话或密钥。</p>
 */
public class RiskEvaluationAuditService {

    /**
     * {@code EFFECT_ALLOW}常量，统一 {@code RiskEvaluationAuditService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String EFFECT_ALLOW = "ALLOW";

    /**
     * {@code RISK_LEVEL_LOW}常量，统一 {@code RiskEvaluationAuditService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String RISK_LEVEL_LOW = "LOW";

    /**
     * {@code RISK_LEVEL_MEDIUM}常量，统一 {@code RiskEvaluationAuditService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String RISK_LEVEL_MEDIUM = "MEDIUM";

    /**
     * {@code RISK_LEVEL_HIGH}常量，统一 {@code RiskEvaluationAuditService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String RISK_LEVEL_HIGH = "HIGH";

    /**
     * {@code RISK_LEVEL_CRITICAL}常量，统一 {@code RiskEvaluationAuditService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String RISK_LEVEL_CRITICAL = "CRITICAL";

    private final RiskAuditRecordPublisher auditRecordPublisher;

    /**
     * 创建风控评估审计转换服务。
     *
     * @param auditRecordPublisher 可空的可靠审计发布器，隔离测试未装配时跳过发布
     */
    public RiskEvaluationAuditService(RiskAuditRecordPublisher auditRecordPublisher) {
        this.auditRecordPublisher = auditRecordPublisher;
    }

    /**
     * 发布最终决策和全部脱敏节点明细。
     *
     * @param requestDTO 本次支付风控请求的交易标识和金额信息
     * @param outcome 最终决策及脱敏规则明细
     */
    public void publish(RiskPaymentEvaluateRequestDTO requestDTO, RiskEvaluationOutcome outcome) {
        if (auditRecordPublisher == null
                || requestDTO == null
                || outcome == null
                || outcome.getResult() == null) {
            return;
        }
        RiskEvaluationAuditMessage message = new RiskEvaluationAuditMessage();
        message.setRiskRecordNo(outcome.getResult().getRiskRecordNo());
        message.setMerchantId(requestDTO.getMerchantId());
        message.setMerchantOrderNo(requestDTO.getMerchantOrderNo());
        message.setPaymentOrderNo(requestDTO.getTransactionId());
        message.setTransactionAmount(requestDTO.getAmount());
        message.setTransactionCurrency(requestDTO.getCurrency());
        message.setRiskLevel(highestRiskLevel(outcome.getHits()));
        message.setDecisionResult(outcome.getResult().getDecision());
        message.setDecisionReasonCode(outcome.getResult().getReasonCode());
        message.setDecisionReason(outcome.getResult().getReasonMessage());
        message.setHitCount(hitCount(outcome.getHits(), outcome.getResult().getDecision()));
        message.setEvaluationTime(outcome.getResult().getDecisionTime());
        message.setHits(outcome.getHits().stream().map(this::toAuditHitMessage).toList());
        auditRecordPublisher.publish(message);
    }

    /**
     * 将 Risk 内部明细转换为不依赖 Risk 领域包的 MQ 协议对象。
     *
     * @param match 已完成脱敏的风控执行明细
     * @return 仅包含审计字段的 MQ 命中明细
     */
    private RiskAuditHitMessage toAuditHitMessage(RiskListMatch match) {
        RiskAuditHitMessage message = new RiskAuditHitMessage();
        message.setRuleId(match.getRuleId());
        message.setModuleType(match.getModuleType());
        message.setFunctionCode(match.getFunctionCode());
        message.setFunctionName(match.getFunctionName());
        message.setHitElement(match.getHitElement());
        message.setHitValueMasked(match.getHitValueMasked());
        message.setRiskLevel(match.getRiskLevel());
        message.setDecisionAction(match.getDecisionAction());
        message.setReasonCode(match.getReasonCode());
        message.setDecisionReason(match.getDecisionReason());
        message.setTimeWindowSeconds(match.getTimeWindowSeconds());
        message.setThresholdCount(match.getThresholdCount());
        message.setElementsJson(match.getElementsJson());
        message.setCurrentCount(match.getCurrentCount());
        message.setAmountLimit(match.getAmountLimit());
        message.setCurrentAmount(match.getCurrentAmount());
        message.setStageCode(match.getStageCode());
        message.setStageName(match.getStageName());
        message.setStageOrder(match.getStageOrder());
        message.setMatchResult(match.getMatchResult());
        message.setDecisionEffect(match.getDecisionEffect());
        return message;
    }

    /**
     * 统计最终放行决策中实际触发且产生允许效果的规则数量。
     *
     * @param matches 全部评估明细
     * @param finalDecision 最终风控决策
     * @return 非放行决策或无明细时返回零
     */
    private int hitCount(List<RiskListMatch> matches, String finalDecision) {
        if (!RiskDecisionEnum.PASS.getCode().equalsIgnoreCase(finalDecision)
                || matches == null
                || matches.isEmpty()) {
            return 0;
        }
        return (int) matches.stream()
                .filter(this::countsAsTriggeredRule)
                .count();
    }

    /**
     * 判断规则是否应计入最终放行决策的真实触发数量。
     *
     * @param match 风控执行明细
     * @return 实际命中且产生允许效果时返回 {@code true}
     */
    private boolean countsAsTriggeredRule(RiskListMatch match) {
        return match != null
                && match.actualHit()
                && EFFECT_ALLOW.equalsIgnoreCase(match.getDecisionEffect());
    }

    /**
     * 计算审计明细中的最高风险等级。
     *
     * @param matches 全部风控执行明细
     * @return CRITICAL、HIGH、MEDIUM 或 LOW
     */
    private String highestRiskLevel(List<RiskListMatch> matches) {
        if (matches == null) {
            return RISK_LEVEL_LOW;
        }
        return matches.stream()
                .map(RiskListMatch::getRiskLevel)
                .filter(StringUtils::hasText)
                .max(Comparator.comparingInt(this::riskWeight))
                .orElse(RISK_LEVEL_LOW);
    }

    /**
     * 将文本风险等级转换为审计排序权重。
     *
     * @param riskLevel 规则风险等级
     * @return CRITICAL 为 4、HIGH 为 3、MEDIUM 为 2，其余为 1
     */
    private int riskWeight(String riskLevel) {
        if (!StringUtils.hasText(riskLevel)) {
            return 1;
        }
        return switch (riskLevel.trim().toUpperCase(Locale.ROOT)) {
            case RISK_LEVEL_CRITICAL -> 4;
            case RISK_LEVEL_HIGH -> 3;
            case RISK_LEVEL_MEDIUM -> 2;
            default -> 1;
        };
    }
}
