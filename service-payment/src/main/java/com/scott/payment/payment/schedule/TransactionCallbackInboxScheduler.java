package com.scott.payment.payment.schedule;

import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import com.scott.payment.payment.service.TransactionCallbackInboxWorker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionCallbackInboxScheduler
 * @date : 2026-10-17 12:00
 * @email : scott_x@163.com
 * @description : 渠道回调 Inbox 调度器，位于 service-payment 调度层，按配置批量触发数据库可靠消费；部署迁移表后方可启用。
 * @status : create
 */
@Component
@Slf4j
@ConditionalOnProperty(
        prefix = "payment.transaction.callback-inbox",
        name = "worker-enabled",
        havingValue = "true")
public class TransactionCallbackInboxScheduler {

    private final TransactionCallbackInboxWorker worker;
    private final int batchSize;
    private final Clock clock;

    /** 创建 Inbox 调度器。 */
    @Autowired
    public TransactionCallbackInboxScheduler(
            TransactionCallbackInboxWorker worker,
            @Value("${payment.transaction.callback-inbox.batch-size:100}") int batchSize) {
        this(worker, batchSize,
                Clock.system(ZoneId.of(TransactionShardingProperties.REQUIRED_ZONE_ID)));
    }

    /** 测试可注入固定时钟，确保数据库到期时间比较使用统一业务时区。 */
    TransactionCallbackInboxScheduler(TransactionCallbackInboxWorker worker,
                                      int batchSize,
                                      Clock clock) {
        this.worker = worker;
        this.batchSize = Math.max(1, Math.min(batchSize, 500));
        this.clock = clock;
    }

    /** 扫描并处理一批到期回调。 */
    @Scheduled(
            initialDelayString = "${payment.transaction.callback-inbox.initial-delay-ms:10000}",
            fixedDelayString = "${payment.transaction.callback-inbox.fixed-delay-ms:1000}")
    public void processDueCallbacks() {
        int processed = worker.processDue(LocalDateTime.now(clock), batchSize);
        if (processed > 0) {
            log.info("event: PAYMENT_CALLBACK_INBOX_BATCH_PROCESSED processedCount: {} batchSize: {}",
                    processed, batchSize);
        }
    }
}
