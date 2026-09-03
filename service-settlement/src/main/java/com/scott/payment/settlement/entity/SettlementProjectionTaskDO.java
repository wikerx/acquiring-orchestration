package com.scott.payment.settlement.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementProjectionTaskDO
 * @date : 2026-09-01 00:00
 * @email : scott_x@163.com
 * @description : 结算资金提交后异步更新交易主单/动作单并创建 Outbox 的可靠任务；仅真实 CLEARING_REVISION 候选可生成，纯保证金候选不得伪造交易投影。
 * @status : create
 */
@Data
public class SettlementProjectionTaskDO {
    /** 投影任务数据库主键，插入前允许为空。 */
    private Long id;
    /** 稳定任务号，数据库必须唯一。 */
    private String taskNo;
    /** 当前正式结算或冲正批次号。 */
    private String settlementBatchNo;
    /** SETTLE 或 REVERSE。 */
    private String projectionAction;
    /** REVERSE 任务引用的原正式批次号；SETTLE 时允许为空。 */
    private String originalBatchNo;
    /** 真实 CLEARING_REVISION 候选主键。 */
    private Long candidateId;
    /** 待投影平台交易主单号。 */
    private String transactionId;
    /** 交易分片时间，定位主单和动作单物理表时不允许为空。 */
    private LocalDateTime transactionDateTime;
    /** 本次清分修订号，用于阻止旧修订覆盖新状态。 */
    private Integer clearingRevision;
    /** 待投影交易动作单号，也是顺序消息分组键。 */
    private String operationId;
    /** 交易所属平台商户号。 */
    private String merchantId;
    /** 交易动作冻结的结算 ISO 币种。 */
    private String settlementCurrency;
    /** 原交易动作的有符号结算金额；冲正任务保持原值，不表达反向资金发生额。 */
    private BigDecimal settlementAmount;
    /** 原交易动作的结算业务日期；冲正任务保持原值。 */
    private LocalDate settlementDate;
    /** PENDING、PROCESSING、COMPLETED 或待重试状态。 */
    private String taskStatus;
    /** 已失败处理次数，不包含成功处理。 */
    private Integer retryCount;
    /** 下次允许处理时间；首次处理允许为空。 */
    private LocalDateTime nextRetryTime;
    /** 最近一次脱敏失败码；成功时允许为空。 */
    private String lastFailureCode;
    /** 交易状态和 Outbox 同事务提交的完成时间。 */
    private LocalDateTime completedTime;
    /** 任务状态 CAS 版本，不允许为空。 */
    private Long version;
    /** 任务创建时间，数据库精度为毫秒。 */
    private LocalDateTime createTime;
    /** 任务最近更新时间，数据库精度为毫秒。 */
    private LocalDateTime updateTime;
}
