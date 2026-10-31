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
 * 渠道回调 Inbox 的可靠消费 Worker。
 *
 * <p>Worker 通过租约恢复与版本 CAS 在多实例间竞争处理权，并以至少一次语义调用幂等回调服务。
 * 只有业务处理完成后才将 Inbox 标记为 PROCESSED；任何解析或业务异常都持久化为可重试失败，
 * 单条失败不会中断同一批次的其他回调。</p>
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
        // 先释放超过处理租约的记录，避免实例宕机后回调永久停留在 PROCESSING。
        int recovered = inboxService.recoverStaleProcessing(
                actualNow.minusSeconds(processingTimeoutSeconds), actualNow, limit);
        if (recovered > 0) {
            log.warn("渠道回调Inbox超时任务已恢复, event: PAYMENT_CALLBACK_INBOX_PROCESSING_RECOVERED, recoveredCount: {}", recovered);
        }
        List<TransactionCallbackInboxDO> due = inboxService.listDue(actualNow, limit);
        int processed = 0;
        for (TransactionCallbackInboxDO inbox : due) {
            try {
                if (processOne(inbox, actualNow)) {
                    processed++;
                }
            } catch (Exception exception) {
                // 兜底隔离 Inbox 状态服务自身的异常，确保一条记录不能阻断整批扫描。
                log.error("渠道回调Inbox单条处理异常中止, event: PAYMENT_CALLBACK_INBOX_ITEM_ABORTED, inboxId: {}, channelCode: {}, errorType: {}",
                        inbox == null ? null : inbox.getInboxId(),
                        inbox == null ? null : inbox.getChannelCode(),
                        exception.getClass().getSimpleName());
            }
        }
        return processed;
    }

    private boolean processOne(TransactionCallbackInboxDO inbox, LocalDateTime now) {
        // claim 的状态与版本条件是多实例并发下唯一处理权的持久化边界。
        if (!inboxService.claim(inbox, now)) {
            return false;
        }
        // Worker 线程会复用，必须为当前记录恢复 traceId，并在 finally 中清理，防止串链。
        TraceContext.setTraceId(TraceContext.resolveOrCreate(inbox.getTraceId()));
        try {
            TransactionChannelCallbackCommandDTO command = JsonUtils.parseObject(
                    inbox.getPayloadJson(), TransactionChannelCallbackCommandDTO.class);
            if (command == null) {
                throw new IllegalStateException("callback inbox payload is empty");
            }
            // 原始请求头不进入异步可信上下文；验签/IP 结论和报文摘要已在入箱时冻结。
            command.setRequestHeaders(null);
            command.setRequestBodyLength(inbox.getRequestBodyLength());
            command.setRequestBodySha256(inbox.getRequestBodySha256());
            // 回调服务自身必须按渠道事件号幂等，Worker 重试不能重复推进交易状态或重复发事件。
            callbackService.recordChannelCallback(command);
            boolean updated = inboxService.markProcessed(inbox, currentBusinessTime(now));
            if (!updated) {
                log.warn("渠道回调Inbox完成状态更新竞争失败, event: PAYMENT_CALLBACK_INBOX_MARK_PROCESSED_CAS_FAILED, traceId: {}, inboxId: {}, channelCode: {}, expectedVersion: {}",
                        TraceContext.getTraceId(), inbox.getInboxId(), inbox.getChannelCode(), inbox.getVersion());
            }
            return updated;
        } catch (Exception exception) {
            // 失败状态和下次重试时间必须持久化；只打印日志不足以支撑可靠重试。
            boolean updated = inboxService.markFailed(inbox, exception, currentBusinessTime(now));
            log.warn("渠道回调Inbox处理失败, event: PAYMENT_CALLBACK_INBOX_PROCESS_FAILED, traceId: {}, inboxId: {}, channelCode: {}, expectedVersion: {}, stateRecorded: {}, errorType: {}",
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
