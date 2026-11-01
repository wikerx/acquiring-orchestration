package com.scott.payment.settlement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 汇率锁定重试耗尽批次的人工恢复不可变审计快照。
 */
@Data
@TableName("settlement_batch_recovery_audit")
public class SettlementBatchRecoveryAuditDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 持久化的结算批次编号，用于还原当前记录的业务事实。
     */
    private String settlementBatchNo;
    /**
     * 持久化的请求密钥，用于还原当前记录的业务事实。
     */
    private String requestKey;

    private Long expectedVersion;
    /**
     * 商户号，用于限定商户配置、交易数据、风控规则和权限归属。
     */
    private String merchantId;
    /**
     * 持久化的{@code recoveryAction}，用于还原当前记录的业务事实。
     */
    private String recoveryAction;

    private String batchStatusBefore;
    /**
     * 持久化的{@code failureStageBefore}，用于还原当前记录的业务事实。
     */
    private String failureStageBefore;

    private String failureCodeBefore;
    /**
     * 重试次数，用于记录 MQ、任务或商户通知当前已执行的重试轮次。
     */
    private Integer retryCountBefore;

    private Integer restoredCandidateCount;

    private Long operatorAccountId;

    private String operatorAccountName;
    /**
     * 持久化的{@code operatorRoleSnapshot}，用于还原当前记录的业务事实。
     */
    private String operatorRoleSnapshot;
    /**
     * 持久化的{@code clientIp}，用于还原当前记录的业务事实。
     */
    private String clientIp;
    /**
     * 持久化的{@code userAgent}，用于还原当前记录的业务事实。
     */
    private String userAgent;
    /**
     * 持久化的原因，用于还原当前记录的业务事实。
     */
    private String reason;
    /**
     * 持久化的动作时间，用于还原当前记录的业务事实。
     */
    private LocalDateTime operationTime;
    /**
     * 持久化的{@code recoveredTime}，用于还原当前记录的业务事实。
     */
    private LocalDateTime recoveredTime;
    /**
     * 记录创建时刻，持久化精度为毫秒。
     */
    private LocalDateTime createTime;
}
