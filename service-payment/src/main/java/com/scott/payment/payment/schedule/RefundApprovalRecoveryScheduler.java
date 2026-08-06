package com.scott.payment.payment.schedule;

import com.scott.payment.payment.config.RefundManagementProperties;
import com.scott.payment.payment.service.RefundApprovalRecoveryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RefundApprovalRecoveryScheduler
 * @date : 2026-08-06 15:20
 * @email : scott_x@163.com
 * @description : 退款审批恢复调度器，驱动审批过期和稳定执行事件补偿，不直接执行渠道退款。
 * @status : create
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "payment.refund.management", name = "recovery-enabled", havingValue = "true")
public class RefundApprovalRecoveryScheduler {

    private final RefundApprovalRecoveryService recoveryService;
    private final RefundManagementProperties properties;

    /**
     * 创建退款审批恢复调度器。
     *
     * @param recoveryService 审批恢复编排服务
     * @param properties 退款管理配置
     */
    public RefundApprovalRecoveryScheduler(RefundApprovalRecoveryService recoveryService,
                                           RefundManagementProperties properties) {
        this.recoveryService = recoveryService;
        this.properties = properties;
    }

    /**
     * 按固定间隔处理过期审批和已批准未执行任务。
     */
    @Scheduled(
            initialDelayString = "${payment.refund.management.recovery-initial-delay-ms:30000}",
            fixedDelayString = "${payment.refund.management.recovery-fixed-delay-ms:30000}")
    public void recover() {
        LocalDateTime now = LocalDateTime.now();
        int expired = recoveryService.expireDue(now, properties.getExpirationBatchSize());
        int recovered = recoveryService.recoverApproved(
                now,
                properties.getExecutionRecoveryStaleSeconds(),
                properties.getExecutionRecoveryBatchSize());
        if (expired > 0 || recovered > 0) {
            log.info("event: REFUND_APPROVAL_RECOVERY expired: {} executionRecovered: {}", expired, recovered);
        }
    }
}
