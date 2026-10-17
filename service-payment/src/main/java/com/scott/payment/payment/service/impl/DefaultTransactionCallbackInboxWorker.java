package com.scott.payment.payment.service.impl;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import com.scott.payment.payment.api.internal.dto.TransactionChannelCallbackCommandDTO;
import com.scott.payment.payment.entity.TransactionCallbackInboxDO;
import com.scott.payment.payment.service.TransactionCallbackInboxService;
import com.scott.payment.payment.service.TransactionCallbackInboxWorker;
import com.scott.payment.payment.service.TransactionCallbackService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultTransactionCallbackInboxWorker
 * @date : 2026-10-17 12:00
 * @email : scott_x@163.com
 * @description : 渠道回调 Inbox Worker 默认实现，位于 service-payment 服务实现层，以至少一次语义调用现有幂等回调服务并用版本 CAS 记录结果。
 * @status : create
 */
@Service
@Slf4j
public class DefaultTransactionCallbackInboxWorker implements TransactionCallbackInboxWorker {

    private final TransactionCallbackInboxService inboxService;
    private final TransactionCallbackService callbackService;
    private final long processingTimeoutSeconds;

    /** 创建生产环境使用的 Inbox Worker。 */
    @Autowired
    public DefaultTransactionCallbackInboxWorker(
            TransactionCallbackInboxService inboxService,
            TransactionCallbackService callbackService,
            @Value("${payment.transaction.callback-inbox.processing-timeout-seconds:120}")
            long processingTimeoutSeconds) {
        this.inboxService = inboxService;
        this.callbackService = callbackService;
        this.processingTimeoutSeconds = Math.max(processingTimeoutSeconds, 1L);
    }

    /** 测试使用默认两分钟 PROCESSING 租约。 */
    DefaultTransactionCallbackInboxWorker(TransactionCallbackInboxService inboxService,
                                           TransactionCallbackService callbackService) {
        this(inboxService, callbackService, 120L);
    }

    /** 先恢复超时抢占，再逐条用 CAS 获取处理权，单条失败不会中断整批。 */
    @Override
    public int processDue(LocalDateTime now, int limit) {
        LocalDateTime actualNow = now == null
                ? LocalDateTime.now(ZoneId.of(TransactionShardingProperties.REQUIRED_ZONE_ID))
                : now;
        int recovered = inboxService.recoverStaleProcessing(
                actualNow.minusSeconds(processingTimeoutSeconds), actualNow, limit);
        if (recovered > 0) {
            log.warn("event: PAYMENT_CALLBACK_INBOX_PROCESSING_RECOVERED recoveredCount: {}", recovered);
        }
        List<TransactionCallbackInboxDO> due = inboxService.listDue(actualNow, limit);
        int processed = 0;
        for (TransactionCallbackInboxDO inbox : due) {
            try {
                if (processOne(inbox, actualNow)) {
                    processed++;
                }
            } catch (Exception exception) {
                log.error("event: PAYMENT_CALLBACK_INBOX_ITEM_ABORTED inboxId: {} channelCode: {} errorType: {}",
                        inbox == null ? null : inbox.getInboxId(),
                        inbox == null ? null : inbox.getChannelCode(),
                        exception.getClass().getSimpleName());
            }
        }
        return processed;
    }

    private boolean processOne(TransactionCallbackInboxDO inbox, LocalDateTime now) {
        if (!inboxService.claim(inbox, now)) {
            return false;
        }
        TraceContext.setTraceId(TraceContext.resolveOrCreate(inbox.getTraceId()));
        try {
            TransactionChannelCallbackCommandDTO command = JsonUtils.parseObject(
                    inbox.getPayloadJson(), TransactionChannelCallbackCommandDTO.class);
            if (command == null) {
                throw new IllegalStateException("callback inbox payload is empty");
            }
            command.setRequestHeaders(null);
            command.setRequestBodyLength(inbox.getRequestBodyLength());
            command.setRequestBodySha256(inbox.getRequestBodySha256());
            callbackService.recordChannelCallback(command);
            boolean updated = inboxService.markProcessed(inbox, currentBusinessTime(now));
            if (!updated) {
                log.warn("event: PAYMENT_CALLBACK_INBOX_MARK_PROCESSED_CAS_FAILED traceId: {} inboxId: {} channelCode: {} expectedVersion: {}",
                        TraceContext.getTraceId(), inbox.getInboxId(), inbox.getChannelCode(), inbox.getVersion());
            }
            return updated;
        } catch (Exception exception) {
            boolean updated = inboxService.markFailed(inbox, exception, currentBusinessTime(now));
            log.warn("event: PAYMENT_CALLBACK_INBOX_PROCESS_FAILED traceId: {} inboxId: {} channelCode: {} expectedVersion: {} stateRecorded: {} errorType: {}",
                    TraceContext.getTraceId(), inbox.getInboxId(), inbox.getChannelCode(), inbox.getVersion(),
                    updated, exception.getClass().getSimpleName());
            return false;
        } finally {
            TraceContext.clear();
        }
    }

    /** 返回不早于抢占时间的当前交易业务时间，避免长任务把完成时间记录为批次开始时间。 */
    private LocalDateTime currentBusinessTime(LocalDateTime claimedAt) {
        LocalDateTime current = LocalDateTime.now(ZoneId.of(TransactionShardingProperties.REQUIRED_ZONE_ID));
        return current.isBefore(claimedAt) ? claimedAt : current;
    }
}
