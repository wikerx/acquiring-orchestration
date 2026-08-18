package com.scott.payment.component.mq.message;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RiskAuditHitMessage
 * @date : 2026-08-01 14:50
 * @email : scott_x@163.com
 * @description : 风控评估审计命中明细 MQ DTO，只承载已脱敏规则快照，不暴露 Risk 服务领域对象
 * @status : create
 */
@Data
public class RiskAuditHitMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 数据库规则主键；系统检查点允许为空。 */
    private Long ruleId;

    /** 风控模块类型：WHITE、BLACK、AML、RULE 或 SYSTEM。 */
    private String moduleType;

    /** 风控功能稳定编码。 */
    private String functionCode;

    /** 风控功能展示名称。 */
    private String functionName;

    /** 发生检查或命中的业务元素。 */
    private String hitElement;

    /** 命中值的脱敏表示，禁止保存原始卡号、邮箱、IP 规则值或凭据。 */
    private String hitValueMasked;

    /** 规则配置的风险等级。 */
    private String riskLevel;

    /** 规则配置的决策动作。 */
    private String decisionAction;

    /** 可用于内部审计的命中原因摘要。 */
    private String decisionReason;

    /** 频率规则时间窗口，单位秒；非频率规则允许为空。 */
    private Integer timeWindowSeconds;

    /** 频率规则触发阈值，单位笔；非频率规则允许为空。 */
    private Integer thresholdCount;

    /** 受控规则元素 JSON，不得包含敏感原值。 */
    private String elementsJson;

    /** 频率窗口评估后的当前笔数。 */
    private Long currentCount;

    /** 累计金额规则阈值，单位为交易币种主单位。 */
    private BigDecimal amountLimit;

    /** 累计金额规则评估后的当前金额，单位为交易币种主单位。 */
    private BigDecimal currentAmount;

    /** 风控执行阶段编码。 */
    private String stageCode;

    /** 风控执行阶段名称。 */
    private String stageName;

    /** 风控执行阶段顺序，数值越小越靠前。 */
    private Integer stageOrder;

    /** 当前规则匹配结果：HIT、MISS、PASS 或 SKIPPED。 */
    private String matchResult;

    /** 当前明细对最终决策的影响。 */
    private String decisionEffect;
}
