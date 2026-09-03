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
 * @classname : ClearingReserveStateDO
 * @date : 2026-08-26 10:35
 * @email : scott_x@163.com
 * @description : 原支付保证金并发控制投影；退款必须按原支付 transaction_date_time 加行锁后再计算返还上限。
 * @status : create
 */
@Data
@TableName("transaction_reserve_clearing_state")
public class ClearingReserveStateDO {
    /** 数据库自增主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 原支付保证金状态业务号。 */
    private String reserveStateId;
    /** 原支付交易号，与分片时间共同定位状态行。 */
    private String originalTransactionId;
    /** 原支付操作号。 */
    private String operationId;
    /** 原支付动作财务状态号。 */
    private String originalFinanceStateId;
    /** 原始 HOLD 保证金明细号。 */
    private String originalHoldDetailNo;
    /** 原支付冻结的不可变费用方案版本 ID。 */
    private Long originalFeePlanVersionId;
    /** 原保证金配置快照 SHA-256。 */
    private String originalReserveSnapshotHash;
    /** 平台商户号。 */
    private String merchantId;
    /** 原支付标签 ISO 币种，整个保证金生命周期不可换币。 */
    private String reserveCurrency;
    /** 保证金币种 ISO exponent。 */
    private Integer reserveCurrencyExponent;
    /** 原保证金计提基础金额，标签币种十进制主单位。 */
    private BigDecimal originalBasisAmount;
    /** 原保证金比例。 */
    private BigDecimal originalReserveRate;
    /** 原保证金舍入规则。 */
    private String originalRoundingMode;
    /** 原累计扣留金额，标签币种十进制主单位。 */
    private BigDecimal retainedAmount;
    /** 累计退款返还金额，标签币种十进制主单位。 */
    private BigDecimal returnedAmount;
    /** 累计到期释放金额，标签币种十进制主单位。 */
    private BigDecimal releasedAmount;
    /** 经复核增加的累计保证金负债。 */
    private BigDecimal debitAdjustmentAmount;
    /** 经复核减少的累计保证金负债。 */
    private BigDecimal creditAdjustmentAmount;
    /** 当前剩余保证金负债，必须满足资金恒等式。 */
    private BigDecimal remainingAmount;
    /** 当前计划释放业务日；余额为零时允许保留审计值。 */
    private LocalDate expectedReserveReleaseDate;
    /** 保证金状态；终态不得被后续退款或释放覆盖。 */
    private String reserveStatus;
    /** 最近一次成功返还的退款动作号；从未返还时为空。 */
    private String lastReturnTransactionId;
    /** 最近一次成功返还动作的季度分片时间；从未返还时为空。 */
    private LocalDateTime lastReturnTransactionDateTime;
    /** 原支付季度分片时间。 */
    private LocalDateTime transactionDateTime;
    /** 原支付 UTC 业务时间。 */
    private LocalDateTime originalTransactionUtcTime;
    /** 原支付业务 IANA 时区。 */
    private String transactionTimeZone;
    /** 保证金状态 CAS 版本。 */
    private Long version;
    /** 创建 UTC 时间。 */
    private LocalDateTime createTime;
    /** 最后更新 UTC 时间。 */
    private LocalDateTime updateTime;
}
