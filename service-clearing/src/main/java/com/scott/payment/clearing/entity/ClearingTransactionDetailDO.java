package com.scott.payment.clearing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingTransactionDetailDO
 * @date : 2026-08-26 10:35
 * @email : scott_x@163.com
 * @description : 动作级不可变交易清分明细实体，只保存本金、费用和返费原子事实，禁止混入保证金或结算汇率。
 * @status : create
 */
@Data
@TableName("transaction_clearing_detail")
public class ClearingTransactionDetailDO {
    /** 数据库自增主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 清分明细业务号，全局唯一。 */
    private String clearingDetailNo;
    /** 动作财务状态业务号。 */
    private String financeStateId;
    /** 动作交易号。 */
    private String transactionId;
    /** 动作操作号。 */
    private String operationId;
    /** 关联源交易号；无源动作时为空。 */
    private String sourceTransactionId;
    /** 返费引用的原清分明细号；非返费行时为空。 */
    private String sourceClearingDetailNo;
    /** 结算冲正引用的原结算结果项号；普通清分行时为空。 */
    private String sourceSettlementResultItemNo;
    /** 平台商户号。 */
    private String merchantId;
    /** 交易清分时冻结的支付类型，用于结算分组统计。 */
    private String paymentType;
    /** 交易清分时冻结的支付方式，用于结算分组统计。 */
    private String paymentMethod;
    /** 平台统一交易类型。 */
    private String transactionType;
    /** 当前清分修订号。 */
    private Integer clearingRevision;
    /** 当前修订内稳定行号。 */
    private Integer lineNo;
    /** 原子项目类型：本金、费用或返费。 */
    private String itemType;
    /** 费用类别稳定编码；本金行为空。 */
    private String feeCategory;
    /** 风险服务类型；非风险费时为空。 */
    private String riskServiceType;
    /** 原子项目稳定编码。 */
    private String itemCode;
    /** 形成事实时冻结的展示名称。 */
    private String itemName;
    /** 相对商户结算资金的方向。 */
    private String direction;
    /** 商户标签 ISO 币种。 */
    private String labelCurrency;
    /** 商户标签金额，十进制主单位。 */
    private BigDecimal labelAmount;
    /** 标签币种 ISO exponent。 */
    private Integer labelCurrencyExponent;
    /** 同一费用规则拆分组件的稳定分组号。 */
    private String feeGroupNo;
    /** 费用组内组件序号。 */
    private Integer componentNo;
    /** 组件类型，例如 PERCENTAGE 或 FIXED_USD。 */
    private String componentType;
    /** 组件计费基础币种。 */
    private String basisCurrency;
    /** 组件计费基础金额，十进制主单位。 */
    private BigDecimal basisAmount;
    /** 计费基础币种 ISO exponent。 */
    private Integer basisCurrencyExponent;
    /** 本原子行有符号金额的绝对值，十进制主单位。 */
    private BigDecimal amount;
    /** 本原子行 ISO 币种。 */
    private String currency;
    /** 本原子行币种 ISO exponent。 */
    private Integer currencyExponent;
    /** 冻结费用方案 ID。 */
    private Long feePlanId;
    /** 冻结不可变费用版本 ID。 */
    private Long feePlanVersionId;
    /** 冻结费用版本号。 */
    private Integer feePlanVersionNo;
    /** 命中的原子费用规则 ID；本金行为空。 */
    private Long feeRuleId;
    /** 命中的阶梯行 ID；非阶梯规则时为空。 */
    private Long feeRuleTierId;
    /** 收费触发时点。 */
    private String chargeTrigger;
    /** 费用计算模式。 */
    private String feeMode;
    /** 阶梯累计期间键，格式 yyyy-MM；非阶梯规则时为空。 */
    private String tierPeriodKey;
    /** 阶梯累计指标 COUNT 或 AMOUNT。 */
    private String tierMetric;
    /** 计费前期间累计笔数。 */
    private Long tierCountBefore;
    /** 当前动作累计笔数增量。 */
    private Long tierCountDelta;
    /** 计费后期间累计笔数。 */
    private Long tierCountAfter;
    /** 计费前期间累计 USD 金额，十进制主单位。 */
    private BigDecimal tierAmountUsdBefore;
    /** 当前动作累计 USD 金额增量，十进制主单位。 */
    private BigDecimal tierAmountUsdDelta;
    /** 计费后期间累计 USD 金额，十进制主单位。 */
    private BigDecimal tierAmountUsdAfter;
    /** 标签金额百分比费率。 */
    private BigDecimal percentageRate;
    /** 固定单笔费，币种固定为 USD。 */
    private BigDecimal fixedAmountUsd;
    /** 最低费用限制，币种固定为 USD。 */
    private BigDecimal minimumAmountUsd;
    /** 最高费用限制，币种固定为 USD。 */
    private BigDecimal maximumAmountUsd;
    /** 跨币种限额是否已求值的稳定状态。 */
    private String limitEvaluationStatus;
    /** 实际命中的最低或最高限制；未命中时为空。 */
    private String appliedLimit;
    /** 金额舍入模式。 */
    private String roundingMode;
    /** 可审计计算公式快照，不包含密钥或持卡人信息。 */
    private String formulaSnapshot;
    /** 命中费用规则不可变 JSON 快照。 */
    private String ruleSnapshotJson;
    /** 规范化费用版本快照 SHA-256。 */
    private String feeSnapshotHash;
    /** 最早可结算业务日。 */
    private LocalDate settlementEligibleDate;
    /** 修订记录状态，旧修订保留为非活动审计事实。 */
    private String recordStatus;
    /** 动作季度分片时间。 */
    private LocalDateTime transactionDateTime;
    /** 动作 UTC 业务时间。 */
    private LocalDateTime transactionUtcTime;
    /** 动作业务 IANA 时区。 */
    private String transactionTimeZone;
    /** 创建 UTC 时间。 */
    private LocalDateTime createTime;
    /** 最后更新 UTC 时间。 */
    private LocalDateTime updateTime;
}
