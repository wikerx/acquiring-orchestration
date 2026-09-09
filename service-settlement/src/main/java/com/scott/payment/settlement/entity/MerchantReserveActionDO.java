package com.scott.payment.settlement.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantReserveActionDO
 * @date : 2026-09-01 00:00
 * @email : scott_x@163.com
 * @description : 保证金聚合的不可变动作记录；HOLD、RETURN、RELEASE、ADJUSTMENT 和 REVERSAL 以动作号及来源唯一约束保证只资金化一次。
 * @status : create
 */
@Data
public class MerchantReserveActionDO {
    /** 保证金动作数据库主键，插入前允许为空。 */
    private Long id;
    /** 全局稳定动作号，也是动作级数据库幂等身份。 */
    private String reserveActionNo;
    /** 被更新的保证金聚合项主键。 */
    private Long reserveItemId;
    /** 保证金聚合业务号，不允许为空。 */
    private String reserveNo;
    /** 动作所属正式结算或冲正批次号。 */
    private String settlementBatchNo;
    /** 来源结算候选主键；不依赖候选的冲正场景允许为空。 */
    private Long candidateId;
    /** 清分保证金明细业务号，用于来源事实去重。 */
    private String sourceReserveDetailNo;
    /** HOLD、RETURN、RELEASE、ADJUSTMENT 或 REVERSAL。 */
    private String actionType;
    /** CREDIT 增加商户保证金权益，DEBIT 减少权益。 */
    private String direction;
    /** 原支付标签 ISO 币种，不参与结算汇率换算。 */
    private String currency;
    /** 非负保证金发生额，单位由 currency 的 ISO exponent 决定。 */
    private BigDecimal amount;
    /** REVERSAL 指向的原保证金动作主键；非冲正动作允许为空。 */
    private Long reversalOfActionId;
    /** 保证金动作业务发生时间，数据库精度为毫秒。 */
    private LocalDateTime actionTime;
    /** 动作记录创建时间，数据库精度为毫秒。 */
    private LocalDateTime createTime;
}
