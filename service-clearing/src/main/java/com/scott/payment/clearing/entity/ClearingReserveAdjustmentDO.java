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
 * @classname : ClearingReserveAdjustmentDO
 * @date : 2026-08-26 19:00
 * @email : scott_x@163.com
 * @description : 保证金差额调整的固定表审批事实；冻结标签币种、状态版本、提交人和复核执行结果。
 * @status : create
 */
@Data
@TableName("clearing_reserve_adjustment")
public class ClearingReserveAdjustmentDO {
    /** 数据库自增主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 调整申请业务号。 */
    private String adjustmentNo;
    /** 调用方幂等键，全局唯一。 */
    private String requestKey;
    /** 原保证金状态业务号。 */
    private String reserveStateId;
    /** 原支付交易号。 */
    private String originalTransactionId;
    /** 原支付季度分片时间。 */
    private LocalDateTime originalTransactionDateTime;
    /** 平台商户号。 */
    private String merchantId;
    /** 原保证金标签 ISO 币种，审批期间不可变。 */
    private String reserveCurrency;
    /** 保证金币种 ISO exponent。 */
    private Integer reserveCurrencyExponent;
    /** 调整方向：DEBIT 增加负债，CREDIT 减少负债。 */
    private String direction;
    /** 标签币种正数调整金额。 */
    private BigDecimal adjustmentAmount;
    /** DEBIT 调整后的计划释放日；CREDIT 时为空。 */
    private LocalDate requestedReleaseDate;
    /** 提交时冻结的原保证金状态 CAS 版本。 */
    private Long expectedReserveStateVersion;
    /** 运营提交原因，不得包含敏感支付数据。 */
    private String reason;
    /** service-admin 注入的可信提交人。 */
    private String submitOperator;
    /** service-admin 注入的可信复核人；待复核时为空。 */
    private String reviewOperator;
    /** 复核意见；待复核时为空。 */
    private String reviewComment;
    /** 申请状态，流转受 Maker-Checker 和版本 CAS 保护。 */
    private String adjustmentStatus;
    /** 批准资金化后生成的独立调整动作号；未执行时为空。 */
    private String executionTransactionId;
    /** 保证金状态资金化修订；未执行时为空。 */
    private Integer sourceRevision;
    /** 复核 UTC 时间；待复核时为空。 */
    private LocalDateTime reviewTime;
    /** 资金化 UTC 时间；拒绝或待复核时为空。 */
    private LocalDateTime executedTime;
    /** 申请状态乐观锁版本。 */
    private Long version;
    /** 创建 UTC 时间。 */
    private LocalDateTime createTime;
    /** 最后更新 UTC 时间。 */
    private LocalDateTime updateTime;
}
