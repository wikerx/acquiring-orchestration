package com.scott.payment.settlement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementResultItemDO
 * @date : 2026-08-26 23:30
 * @email : scott_x@163.com
 * @description : 结算不可变结果明细；TRACE 仅审计费用组件，FINANCIAL_COMPONENT 才参与批次汇总。
 * @status : create
 */
@Data
@TableName("settlement_result_item")
public class SettlementResultItemDO {
    /** 结果明细数据库主键，插入前允许为空。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 批次内稳定结果明细号，数据库必须唯一。 */
    private String settlementResultItemNo;
    /** 所属正式结算批次号。 */
    private String settlementBatchNo;
    /** 来源结算候选主键；批次净入账行允许为空。 */
    private Long candidateId;
    /** 候选或批次内确定性结果行序号。 */
    private Integer resultLineNo;
    /** 结果所属平台商户号。 */
    private String merchantId;
    /** 目标结算资金账户主键。 */
    private Long settlementAccountId;
    /** 来源事实类型，例如 TRANSACTION_CLEARING 或 RESERVE_CLEARING。 */
    private String sourceDetailType;
    /** 来源清分明细业务号，用于结果追溯。 */
    private String sourceDetailNo;
    /** 冲正结果指向的原结果项主键；普通结果为空。 */
    private Long reversalOfResultItemId;
    /** 来源平台交易号；纯保证金结果允许为空。 */
    private String sourceTransactionId;
    /** 来源交易分片时间；纯保证金结果允许为空。 */
    private LocalDateTime sourceTransactionDateTime;
    /** 费用组业务号；非费用结果允许为空。 */
    private String feeGroupNo;
    /** PRINCIPAL、FEE、RESERVE、ADJUSTMENT 或 NET_POSTING 等。 */
    private String resultItemType;
    /** FINANCIAL_COMPONENT 参与净额，TRACE 仅保留费用组件审计。 */
    private String resultRole;
    /** 平台支付类型快照；非交易结果允许为空或约定值。 */
    private String paymentType;
    /** 平台支付方式快照；非交易结果允许为空或约定值。 */
    private String paymentMethod;
    /** 平台交易类型快照；纯保证金结果不得伪造交易类型。 */
    private String transactionType;
    /** 费用类别；非费用结果允许为空。 */
    private String feeCategory;
    /** CREDIT 或 DEBIT，金额字段本身保持非负。 */
    private String direction;
    /** 换算前非负原币种金额。 */
    private BigDecimal sourceAmount;
    /** 原金额 ISO 币种。 */
    private String sourceCurrency;
    /** 原币种 ISO 小数位。 */
    private Integer sourceCurrencyExponent;
    /** 使用的批次锁定汇率行主键；同币种恒等换算也必须可审计。 */
    private Long settlementBatchRateId;
    /** 统一直接汇率换算后的未舍入高精度目标金额。 */
    private BigDecimal unroundedTargetAmount;
    /** 按目标币种 exponent 和 roundingMode 舍入后的非负金额。 */
    private BigDecimal targetAmount;
    /** 统一目标 ISO 结算币种。 */
    private String targetCurrency;
    /** 目标币种 ISO 小数位。 */
    private Integer targetCurrencyExponent;
    /** NONE、MINIMUM 或 MAXIMUM，表示费用组最终限额命中。 */
    private String appliedLimit;
    /** 费用组最低限额换算后的目标币种金额；无最低限额时为空。 */
    private BigDecimal minimumTargetAmount;
    /** 费用组最高限额换算后的目标币种金额；无最高限额时为空。 */
    private BigDecimal maximumTargetAmount;
    /** 最终舍入模式名称，不允许隐式使用默认模式。 */
    private String roundingMode;
    /** 清分公式及结算换算审计快照，不用于二次执行代码。 */
    private String formulaSnapshot;
    /** NET_POSTING 资金流水幂等键；其他结果项允许为空。 */
    private String ledgerIdempotencyKey;
    /** 结果项创建时间，数据库精度为毫秒。 */
    private LocalDateTime createTime;
}
