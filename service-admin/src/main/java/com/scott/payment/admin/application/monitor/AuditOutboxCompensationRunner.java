package com.scott.payment.admin.application.monitor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 显式开关控制的一次性非资金审计补偿入口，默认不存在且默认只读预演。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(prefix = "acquiring.audit-outbox-compensation", name = "enabled", havingValue = "true")
public class AuditOutboxCompensationRunner implements ApplicationRunner {

    /** 执行态必须提供的二次确认令牌。 */
    static final String APPROVAL_TOKEN = "ACK_NON_FINANCIAL_AUDIT_REPLAY";

    private final AuditOutboxCompensationService compensationService;
    private final boolean dryRun;
    private final int batchSize;
    private final String approvalToken;
    private final String recoveryReason;

    /**
     * 创建一次性补偿入口。
     */
    public AuditOutboxCompensationRunner(
            AuditOutboxCompensationService compensationService,
            @Value("${acquiring.audit-outbox-compensation.dry-run:true}") boolean dryRun,
            @Value("${acquiring.audit-outbox-compensation.batch-size:1}") int batchSize,
            @Value("${acquiring.audit-outbox-compensation.approval-token:}") String approvalToken,
            @Value("${acquiring.audit-outbox-compensation.recovery-reason:}") String recoveryReason) {
        this.compensationService = compensationService;
        this.dryRun = dryRun;
        this.batchSize = batchSize;
        this.approvalToken = approvalToken;
        this.recoveryReason = recoveryReason;
    }

    /**
     * 启动时执行一次预演或受控重放，不注册 HTTP 接口，也不接受任意 Topic。
     */
    @Override
    public void run(ApplicationArguments args) {
        AuditOutboxCompensationService.AuditOutboxCompensationResult result;
        if (dryRun) {
            result = compensationService.preview(batchSize);
        } else {
            if (!APPROVAL_TOKEN.equals(approvalToken)) {
                throw new IllegalStateException("explicit audit replay approval token is required");
            }
            result = compensationService.recoverAndRelay(batchSize, recoveryReason);
        }
        log.info("非资金审计 Outbox 补偿批次完成, event: AUDIT_OUTBOX_COMPENSATION_BATCH, dryRun: {}, candidates: {}, recovered: {}, relayed: {}, eventIds: {}, stopReason: {}",
                result.dryRun(), result.candidateCount(), result.recoveredCount(), result.relayedCount(),
                result.eventIds(), result.stopReason());
    }
}
