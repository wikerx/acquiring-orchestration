package com.scott.payment.risk.schedule;

import com.scott.payment.risk.domain.MerchantLimitReservationReconciliationSummary;
import com.scott.payment.risk.service.MerchantLimitReservationReconciliationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantLimitReservationReconciliationScheduler
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 商户累计限额预占超时扫描调度器。
 * @status : create
 */
@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "risk.evaluation",
        name = "reservation-reconcile-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class MerchantLimitReservationReconciliationScheduler {

    /**
     * 预占状态与支付交易事实对账服务。
     */
    private final MerchantLimitReservationReconciliationService reconciliationService;

    /**
     * 单轮最大对账记录数，最小为 1。
     */
    private final int batchSize;

    /**
     * 创建累计限额预占对账调度器。
     *
     * @param reconciliationService 对账服务
     * @param batchSize             单轮扫描上限
     */
    public MerchantLimitReservationReconciliationScheduler(
            MerchantLimitReservationReconciliationService reconciliationService,
            @Value("${risk.evaluation.reservation-reconcile-batch-size:100}") int batchSize) {
        this.reconciliationService = reconciliationService;
        this.batchSize = Math.max(1, batchSize);
    }

    /**
     * 扫描到期非终态预占，并按支付数据库事实执行确认、取消或保留。
     * <p>
     * Redis 不是交易状态事实源；无法确定支付终态的记录保持非终态，等待下一轮或人工处理。
     * </p>
     */
    @Scheduled(
            initialDelayString = "${risk.evaluation.reservation-reconcile-initial-delay-ms:30000}",
            fixedDelayString = "${risk.evaluation.reservation-reconcile-fixed-delay-ms:60000}")
    public void reconcile() {
        MerchantLimitReservationReconciliationSummary summary =
                reconciliationService.reconcile(LocalDateTime.now(), batchSize);
        if (summary.reserved() + summary.confirmed() + summary.cancelled() + summary.retained() > 0) {
            log.info("event: RISK_MERCHANT_LIMIT_RECONCILE_BATCH reserved: {} confirmed: {} cancelled: {} retained: {}",
                    summary.reserved(),
                    summary.confirmed(),
                    summary.cancelled(),
                    summary.retained());
        }
    }
}
