package com.scott.payment.settlement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 汇率锁定重试耗尽批次的人工恢复不可变审计快照。 */
@Data
@TableName("settlement_batch_recovery_audit")
public class SettlementBatchRecoveryAuditDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String settlementBatchNo;
    private String requestKey;
    private Long expectedVersion;
    private String merchantId;
    private String recoveryAction;
    private String batchStatusBefore;
    private String failureStageBefore;
    private String failureCodeBefore;
    private Integer retryCountBefore;
    private Integer restoredCandidateCount;
    private Long operatorAccountId;
    private String operatorAccountName;
    private String operatorRoleSnapshot;
    private String clientIp;
    private String userAgent;
    private String reason;
    private LocalDateTime operationTime;
    private LocalDateTime recoveredTime;
    private LocalDateTime createTime;
}
