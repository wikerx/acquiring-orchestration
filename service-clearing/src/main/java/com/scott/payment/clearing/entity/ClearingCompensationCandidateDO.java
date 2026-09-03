package com.scott.payment.clearing.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingCompensationCandidateDO
 * @date : 2026-08-27 19:46
 * @email : scott_x@163.com
 * @description : 补偿扫描的一次性数据库投影，不对应独立物理表。
 * @status : update
 */
@Data
public class ClearingCompensationCandidateDO {
    /** 动作表自增主键，仅用于稳定扫描游标。 */
    private Long operationRowId;
    /** 动作交易号。 */
    private String transactionId;
    /** 动作操作号。 */
    private String operationId;
    /** 平台商户号，不包含商户敏感资料。 */
    private String merchantId;
    /** 商户订单号，仅用于运营定位，不作为清分幂等键。 */
    private String merchantOrderNo;
    /** 退款、撤销等动作关联的源交易号；无源动作时为空。 */
    private String sourceTransactionId;
    /** 平台统一交易类型。 */
    private String transactionType;
    /** 数据库权威动作终态。 */
    private String transactionStatus;
    /** 商户标签 ISO 币种，百分比费用和保证金均以此为基础。 */
    private String labelCurrency;
    /** 商户标签币种金额，十进制主单位且不得使用浮点数。 */
    private BigDecimal labelAmount;
    /** 渠道批准币种；渠道未返回时允许为空。 */
    private String approvedCurrency;
    /** 渠道批准金额，十进制主单位；未批准时允许为空。 */
    private BigDecimal approvedAmount;
    /** 实际交易 ISO 币种。 */
    private String transactionCurrency;
    /** 实际交易金额，十进制主单位。 */
    private BigDecimal transactionAmount;
    /** 标签币种 ISO exponent。 */
    private Integer currencyExponent;
    /** 当前动作季度分片时间，数据库 DATETIME(3)。 */
    private LocalDateTime transactionDateTime;
    /** 当前动作 UTC 业务时间，数据库 DATETIME(3)。 */
    private LocalDateTime transactionUtcTime;
    /** 当前动作业务 IANA 时区。 */
    private String transactionTimeZone;
    /** 动作查询投影 CAS 版本。 */
    private Integer operationVersion;
    /** 动作表当前清分查询状态。 */
    private String operationClearingStatus;
    /** 动作财务状态业务号。 */
    private String financeStateId;
    /** 清分权威状态。 */
    private String clearingStatus;
    /** 当前有效清分修订号。 */
    private Integer clearingRevision;
    /** 已执行的业务重试次数。 */
    private Integer clearingRetryCount;
    /** 下一次允许重试 UTC 时间；无需重试时为空。 */
    private LocalDateTime nextRetryTime;
    /** 最近一次稳定失败码，不含异常堆栈或敏感正文。 */
    private String lastFailureCode;
    /** PROCESSING 租约截止 UTC 时间；非处理中状态为空。 */
    private LocalDateTime processingDeadline;
    /** 财务状态扫描快照版本，恢复时必须重新 CAS 校验。 */
    private Integer financeStateVersion;
    /** 本次进入补偿候选的稳定原因分类。 */
    private String reason;
}
