package com.scott.payment.risk.service;

import com.scott.payment.risk.domain.RiskListMatch;
import com.scott.payment.risk.domain.state.RiskDecisionEnum;
import com.scott.payment.risk.domain.state.RiskReasonCodeEnum;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 统一解析规则动作、风险等级和最终跳过原因。
 *
 * <p>该组件不查询外部状态，也不修改规则明细，可在串行和并行评估完成后确定相同结果。</p>
 */
public class RiskDecisionResolver {

    /**
     * {@code MATCH_PASS}常量，统一 风控结论解析器 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String MATCH_PASS = "PASS";

    /**
     * {@code EFFECT_ALLOW}常量，统一 风控结论解析器 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String EFFECT_ALLOW = "ALLOW";

    /**
     * {@code EFFECT_BLOCK}常量，统一 风控结论解析器 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String EFFECT_BLOCK = "BLOCK";

    /**
     * {@code EFFECT_REVIEW}常量，统一 风控结论解析器 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String EFFECT_REVIEW = "REVIEW";

    /**
     * {@code EFFECT_CHALLENGE}常量，统一 风控结论解析器 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String EFFECT_CHALLENGE = "CHALLENGE";

    /**
     * {@code EFFECT_NONE}常量，统一 风控结论解析器 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String EFFECT_NONE = "NONE";

    /**
     * {@code RISK_LEVEL_LOW}常量，统一 风控结论解析器 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String RISK_LEVEL_LOW = "LOW";

    /**
     * {@code RISK_LEVEL_MEDIUM}常量，统一 风控结论解析器 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String RISK_LEVEL_MEDIUM = "MEDIUM";

    /**
     * {@code RISK_LEVEL_HIGH}常量，统一 风控结论解析器 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String RISK_LEVEL_HIGH = "HIGH";

    /**
     * {@code RISK_LEVEL_CRITICAL}常量，统一 风控结论解析器 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String RISK_LEVEL_CRITICAL = "CRITICAL";

    /**
     * 将规则动作解析为统一决策枚举，并兼容历史 {@code FORCE_3DS} 动作。
     *
     * @param match 待解析规则明细
     * @param defaultDecision 动作缺失或未知时使用的默认决策
     * @return 已知统一决策或调用方指定默认值
     */
    public RiskDecisionEnum resolveDecision(RiskListMatch match, RiskDecisionEnum defaultDecision) {
        if (match == null || !StringUtils.hasText(match.getDecisionAction())) {
            return defaultDecision;
        }
        String action = match.getDecisionAction().trim().toUpperCase(Locale.ROOT);
        if (RiskDecisionEnum.REJECT.getCode().equals(action)) {
            return RiskDecisionEnum.REJECT;
        }
        if (RiskDecisionEnum.REVIEW.getCode().equals(action)) {
            return RiskDecisionEnum.REVIEW;
        }
        if (RiskDecisionEnum.REQUIRE_3DS.getCode().equals(action) || "FORCE_3DS".equals(action)) {
            return RiskDecisionEnum.REQUIRE_3DS;
        }
        if (RiskDecisionEnum.SKIP.getCode().equals(action)) {
            return RiskDecisionEnum.SKIP;
        }
        return RiskDecisionEnum.PASS.getCode().equals(action)
                ? RiskDecisionEnum.PASS
                : defaultDecision;
    }

    /**
     * 按阶段既定顺序返回首条阻断、复核、挑战或执行错误明细。
     *
     * @param details 已按执行顺序排列的规则明细
     * @return 首条需要停止后续评估的明细
     */
    public Optional<RiskListMatch> firstBlockingDetail(List<RiskListMatch> details) {
        if (details == null) {
            return Optional.empty();
        }
        return details.stream()
                .filter(this::requiresEvaluationStop)
                .filter(detail -> !RiskDecisionEnum.PASS.getCode().equalsIgnoreCase(detail.getDecisionAction()))
                .findFirst();
    }

    /**
     * 判断是否存在明确允许交易继续的规则结果。
     *
     * @param details 规则执行明细
     * @return 存在真实 PASS 命中或通过结果时返回 {@code true}
     */
    public boolean hasExplicitAllow(List<RiskListMatch> details) {
        return details != null && details.stream().anyMatch(detail -> detail != null
                && RiskDecisionEnum.PASS.getCode().equalsIgnoreCase(detail.getDecisionAction())
                && (detail.actualHit() || MATCH_PASS.equalsIgnoreCase(detail.getMatchResult())));
    }

    /**
     * 根据已审计的跳过原因选择稳定的最终原因码。
     *
     * @param details 全部规则执行明细
     * @return 按未命中、输入缺失、未配置、未启用顺序选择的原因码
     */
    public RiskReasonCodeEnum resolveSkipReason(List<RiskListMatch> details) {
        if (hasReason(details, RiskReasonCodeEnum.NO_MATCH)) {
            return RiskReasonCodeEnum.NO_MATCH;
        }
        if (hasReason(details, RiskReasonCodeEnum.INPUT_MISSING)) {
            return RiskReasonCodeEnum.INPUT_MISSING;
        }
        if (hasReason(details, RiskReasonCodeEnum.NOT_CONFIGURED)) {
            return RiskReasonCodeEnum.NOT_CONFIGURED;
        }
        if (hasReason(details, RiskReasonCodeEnum.NOT_ENABLED)) {
            return RiskReasonCodeEnum.NOT_ENABLED;
        }
        return RiskReasonCodeEnum.NO_APPLICABLE_RULE;
    }

    /**
     * 计算已执行节点中的最高风险等级，供后续 3DS 规则判断。
     *
     * @param details 全部规则执行明细
     * @return CRITICAL、HIGH、MEDIUM 或 LOW
     */
    public String highestRiskLevel(List<RiskListMatch> details) {
        if (details == null) {
            return RISK_LEVEL_LOW;
        }
        return details.stream()
                .map(RiskListMatch::getRiskLevel)
                .filter(StringUtils::hasText)
                .max(Comparator.comparingInt(this::riskWeight))
                .orElse(RISK_LEVEL_LOW);
    }

    /**
     * 将风险等级归入黑白名单的强、优先或弱仲裁带。
     *
     * @param riskLevel 规则风险等级
     * @return 用于黑白名单固定优先级仲裁的风险带
     */
    public RiskBand riskBand(String riskLevel) {
        int weight = riskWeight(riskLevel);
        if (weight >= 4) {
            return RiskBand.STRONG;
        }
        if (weight >= 3) {
            return RiskBand.PRIORITY;
        }
        return RiskBand.WEAK;
    }

    /**
     * 将规则动作映射为流程影响。
     *
     * @param match 规则执行明细
     * @return ALLOW、BLOCK、REVIEW、CHALLENGE 或 NONE
     */
    public String decisionEffect(RiskListMatch match) {
        RiskDecisionEnum decisionEnum = resolveDecision(match, RiskDecisionEnum.PASS);
        if (RiskDecisionEnum.REJECT == decisionEnum) {
            return EFFECT_BLOCK;
        }
        if (RiskDecisionEnum.REVIEW == decisionEnum) {
            return EFFECT_REVIEW;
        }
        if (RiskDecisionEnum.REQUIRE_3DS == decisionEnum) {
            return EFFECT_CHALLENGE;
        }
        return RiskDecisionEnum.PASS == decisionEnum ? EFFECT_ALLOW : EFFECT_NONE;
    }

    /**
     * 判断规则明细是否要求停止后续阶段。
     *
     * @param detail 已按阶段补齐的规则明细
     * @return 实际命中或执行错误时返回 {@code true}
     */
    private boolean requiresEvaluationStop(RiskListMatch detail) {
        return detail != null
                && (detail.actualHit() || "ERROR".equalsIgnoreCase(detail.getMatchResult()));
    }

    /**
     * 判断明细中是否已记录指定稳定原因码。
     *
     * @param details 全部规则执行明细
     * @param reasonCode 目标原因码
     * @return 至少一条明细匹配时返回 {@code true}
     */
    private boolean hasReason(List<RiskListMatch> details, RiskReasonCodeEnum reasonCode) {
        return details != null && details.stream().anyMatch(detail -> detail != null
                && reasonCode.getCode().equalsIgnoreCase(detail.getReasonCode()));
    }

    /**
     * 将文本风险等级转换为稳定比较权重。
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

    /**
     * 黑白名单固定优先级仲裁使用的风险带。
     */
    public enum RiskBand {
        /**
         * STRONG 枚举值，表示当前枚举定义中的一个受控业务取值。
         * <p>
         * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
         * </p>
         */
        STRONG,
        /**
         * PRIORITY 枚举值，表示当前枚举定义中的一个受控业务取值。
         * <p>
         * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
         * </p>
         */
        PRIORITY,
        WEAK
    }
}
