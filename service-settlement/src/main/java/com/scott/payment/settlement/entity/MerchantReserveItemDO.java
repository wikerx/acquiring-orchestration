package com.scott.payment.settlement.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantReserveItemDO
 * @date : 2026-09-01 00:00
 * @email : scott_x@163.com
 * @description : 商户保证金累计聚合；所有金额保持原支付标签币种，通过动作唯一键、行锁和 version CAS 资金化，不参与结算汇率转换。
 * @status : create
 */
@Data
public class MerchantReserveItemDO {
    /** 保证金聚合数据库主键。 */
    private Long id;
    /** 全局保证金业务号，不允许为空。 */
    private String reserveNo;
    /** 对应保证金资金账户主键。 */
    private Long accountId;
    /** 保证金所属平台商户号。 */
    private String merchantId;
    /** 来源支付交易号；无交易来源的纯调整允许为空。 */
    private String sourceTransactionId;
    /** 来源清分或调整业务号，用于审计追踪。 */
    private String sourceBusinessNo;
    /** 原支付标签 ISO 币种，所有累计金额共用该币种。 */
    private String currency;
    /** 历史累计扣留金额，非负且不因释放而回减。 */
    private BigDecimal retainedAmount;
    /** 历史累计退款返还金额，非负。 */
    private BigDecimal returnedAmount;
    /** 历史累计到期释放金额，非负。 */
    private BigDecimal releasedAmount;
    /** 历史累计 DEBIT 调整金额，表示减少商户保证金权益。 */
    private BigDecimal debitAdjustmentAmount;
    /** 历史累计 CREDIT 调整金额，表示增加商户保证金权益。 */
    private BigDecimal creditAdjustmentAmount;
    /** 历史累计冲正金额，用于抵消原保证金动作。 */
    private BigDecimal reversedAmount;
    /** 保证金聚合状态，由剩余可释放金额和释放进度决定。 */
    private String reserveStatus;
    /** 预计可释放业务日期；无固定释放日时允许为空。 */
    private LocalDate expectedReleaseDate;
    /** 已执行释放的结算批次号；未释放时允许为空。 */
    private String releaseBatchNo;
    /** 聚合更新乐观锁版本，不允许为空。 */
    private Long version;
    /** 聚合首次创建时间，数据库精度为毫秒。 */
    private LocalDateTime createTime;
    /** 聚合最近更新时间，数据库精度为毫秒。 */
    private LocalDateTime updateTime;
}
