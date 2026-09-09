package com.scott.payment.risk.domain;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RiskListMatch
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 运行时风控命中结果。
 * @status : create
 */
@Data
public class RiskListMatch implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 数据库规则主键；系统兜底规则没有持久化主键时允许为空。
     */
    private Long ruleId;

    /**
     * 风控模块类型：WHITE、BLACK、AML、RULE 或 SYSTEM。
     */
    private String moduleType;

    /**
     * 风控功能稳定编码，用于策略编排和审计聚合。
     */
    private String functionCode;

    /**
     * 风控功能展示名称。
     */
    private String functionName;

    /**
     * 发生命中的业务元素，例如 IP、CARD_BIN、EMAIL 或 AMOUNT。
     */
    private String hitElement;

    /**
     * 命中值的脱敏表示；禁止保存原始卡号、邮箱、IP 规则值或凭据。
     */
    private String hitValueMasked;

    /**
     * 规则配置的风险等级。
     */
    private String riskLevel;

    /**
     * 规则配置的决策动作。
     */
    private String decisionAction;

    /**
     * 可用于内部审计的命中原因摘要。
     */
    private String decisionReason;

    /**
     * 频率规则时间窗口，单位秒；非频率规则允许为空。
     */
    private Integer timeWindowSeconds;

    /**
     * 频率规则触发阈值，单位笔；非频率规则允许为空。
     */
    private Integer thresholdCount;

    /**
     * 规则元素配置 JSON 的受控快照，不得包含敏感原值。
     */
    private String elementsJson;

    /**
     * 频率窗口评估后的当前笔数。
     */
    private Long currentCount;

    /**
     * 累计金额规则阈值，使用交易币种单位和数据库 6 位金额精度。
     */
    private BigDecimal amountLimit;

    /**
     * 累计金额规则评估后的当前金额，使用交易币种单位。
     */
    private BigDecimal currentAmount;

    /**
     * 风控执行阶段编码，用于后台按交易链路顺序展示。
     */
    private String stageCode;

    /**
     * 风控执行阶段名称。
     */
    private String stageName;

    /**
     * 风控执行阶段顺序，数字越小越靠前。
     */
    private Integer stageOrder;

    /**
     * 当前规则匹配结果：HIT、MISS、PASS、SKIPPED。
     */
    private String matchResult;

    /**
     * 当前明细对整体决策的影响：ALLOW、BLOCK、REVIEW、CHALLENGE、NONE。
     */
    private String decisionEffect;

    /**
     * 创建系统骨架规则命中明细，用于没有数据库 ruleId 的兜底风控决策。
     *
     * @return 默认标记为 HIT 的系统规则明细
     */
    public static RiskListMatch system(String functionCode,
                                       String functionName,
                                       String hitElement,
                                       String hitValueMasked,
                                       String riskLevel,
                                       String decisionAction,
                                       String decisionReason) {
        RiskListMatch match = new RiskListMatch();
        match.setModuleType(RiskModuleTypeEnum.SYSTEM.getCode());
        match.setFunctionCode(functionCode);
        match.setFunctionName(functionName);
        match.setHitElement(hitElement);
        match.setHitValueMasked(hitValueMasked);
        match.setRiskLevel(riskLevel);
        match.setDecisionAction(decisionAction);
        match.setDecisionReason(decisionReason);
        match.setMatchResult("HIT");
        match.setDecisionEffect(decisionEffectOf(decisionAction));
        return match;
    }

    /**
     * 创建风控阶段检查点，记录阶段结果而不伪造数据库规则。
     *
     * @return 系统级阶段明细
     */
    public static RiskListMatch checkpoint(String stageCode,
                                           String stageName,
                                           int stageOrder,
                                           String matchResult,
                                           String decisionAction,
                                           String decisionReason) {
        RiskListMatch match = new RiskListMatch();
        match.setModuleType(RiskModuleTypeEnum.SYSTEM.getCode());
        match.setFunctionCode(stageCode);
        match.setFunctionName(stageName);
        match.setHitElement(stageCode);
        match.setHitValueMasked(matchResult);
        match.setRiskLevel("LOW");
        match.setDecisionAction(decisionAction);
        match.setDecisionReason(decisionReason);
        match.markStage(stageCode, stageName, stageOrder, matchResult, decisionEffectOf(decisionAction));
        return match;
    }

    /**
     * 写入阶段编码、顺序、匹配结果及其对最终决策的影响。
     *
     * @return 当前明细，便于构建链式审计结果
     */
    public RiskListMatch markStage(String stageCode,
                                   String stageName,
                                   int stageOrder,
                                   String matchResult,
                                   String decisionEffect) {
        this.stageCode = stageCode;
        this.stageName = stageName;
        this.stageOrder = stageOrder;
        this.matchResult = matchResult;
        this.decisionEffect = decisionEffect;
        return this;
    }

    /**
     * 判断当前明细是否为真实命中；历史未设置 matchResult 的记录按命中兼容。
     */
    public boolean actualHit() {
        return matchResult == null || "HIT".equalsIgnoreCase(matchResult);
    }

    /**
     * 将规则动作映射为统一决策影响，未知动作不改变整体决策。
     */
    private static String decisionEffectOf(String decisionAction) {
        if (decisionAction == null) {
            return "NONE";
        }
        String normalized = decisionAction.trim().toUpperCase();
        return switch (normalized) {
            case "REJECT" -> "BLOCK";
            case "REVIEW" -> "REVIEW";
            case "REQUIRE_3DS", "FORCE_3DS" -> "CHALLENGE";
            case "PASS" -> "ALLOW";
            default -> "NONE";
        };
    }
}
