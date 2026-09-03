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
 * @classname : ClearingReserveDetailDO
 * @date : 2026-08-26 10:35
 * @email : scott_x@163.com
 * @description : 独立保证金清分明细实体，始终使用原支付标签币种并引用原HOLD事实，不包含费用或汇率字段。
 * @status : create
 */
@Data
@TableName("transaction_reserve_clearing_detail")
public class ClearingReserveDetailDO {
    /** 数据库自增主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 保证金明细业务号，全局唯一。 */
    private String reserveClearingDetailNo;
    /** 当前动作财务状态号。 */
    private String financeStateId;
    /** 形成该事实的动作交易号。 */
    private String transactionId;
    /** 形成该事实的操作号。 */
    private String operationId;
    /** 原支付交易号。 */
    private String originalTransactionId;
    /** 原支付季度分片时间。 */
    private LocalDateTime originalTransactionDateTime;
    /** 被返还、释放或调整引用的源保证金明细号；HOLD 时为空。 */
    private String sourceReserveDetailNo;
    /** 平台商户号。 */
    private String merchantId;
    /** 保证金事实形成时冻结的支付类型。 */
    private String paymentType;
    /** 保证金事实形成时冻结的支付方式。 */
    private String paymentMethod;
    /** 形成该保证金事实的平台统一交易类型。 */
    private String transactionType;
    /** 当前动作清分修订号。 */
    private Integer clearingRevision;
    /** 当前修订内稳定行号。 */
    private Integer lineNo;
    /** 保证金动作类型：HOLD、RETURN、RELEASE 或 ADJUSTMENT。 */
    private String reserveActionType;
    /** 稳定项目编码。 */
    private String itemCode;
    /** 形成事实时冻结的展示名称。 */
    private String itemName;
    /** 相对商户资金的方向。 */
    private String direction;
    /** 原支付标签 ISO 币种，保证金生命周期禁止换币。 */
    private String reserveCurrency;
    /** 保证金币种 ISO exponent。 */
    private Integer reserveCurrencyExponent;
    /** 保证金计算基础金额，标签币种十进制主单位。 */
    private BigDecimal basisAmount;
    /** 计提时使用的保证金比例。 */
    private BigDecimal reserveRate;
    /** 本行扣留金额；非 HOLD 时为零。 */
    private BigDecimal retainedAmount;
    /** 本行退款返还金额；非 RETURN 时为零。 */
    private BigDecimal returnedAmount;
    /** 本行到期释放金额；非 RELEASE 时为零。 */
    private BigDecimal releasedAmount;
    /** 本行人工调整金额，始终为正数，方向由 direction 表示。 */
    private BigDecimal adjustmentAmount;
    /** 本行提交后的剩余保证金，标签币种十进制主单位。 */
    private BigDecimal remainingAmount;
    /** 冻结费用方案 ID。 */
    private Long feePlanId;
    /** 冻结费用方案版本 ID。 */
    private Long feePlanVersionId;
    /** 冻结费用版本号。 */
    private Integer feePlanVersionNo;
    /** 保证金配置快照 SHA-256。 */
    private String reserveSnapshotHash;
    /** 保证金计算基础口径。 */
    private String reserveBasis;
    /** 保证金留存周期单位。 */
    private String reserveDelayUnit;
    /** 保证金留存天数。 */
    private Integer reserveDelayDays;
    /** 金额舍入模式。 */
    private String roundingMode;
    /** 可审计计算公式快照，不包含密钥或持卡人信息。 */
    private String formulaSnapshot;
    /** 计划释放业务日；无需释放时为空。 */
    private LocalDate expectedReserveReleaseDate;
    /** 修订记录状态，旧修订保留为非活动审计事实。 */
    private String recordStatus;
    /** 形成该事实的动作季度分片时间。 */
    private LocalDateTime transactionDateTime;
    /** 形成该事实的动作 UTC 时间。 */
    private LocalDateTime transactionUtcTime;
    /** 形成该事实的动作业务 IANA 时区。 */
    private String transactionTimeZone;
    /** 创建 UTC 时间。 */
    private LocalDateTime createTime;
    /** 最后更新 UTC 时间。 */
    private LocalDateTime updateTime;
}
