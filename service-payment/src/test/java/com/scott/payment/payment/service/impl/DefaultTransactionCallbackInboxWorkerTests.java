package com.scott.payment.payment.service.impl;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.payment.api.internal.dto.TransactionChannelCallbackCommandDTO;
import com.scott.payment.payment.entity.TransactionCallbackInboxDO;
import com.scott.payment.payment.service.TransactionCallbackInboxService;
import com.scott.payment.payment.service.TransactionCallbackService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultTransactionCallbackInboxWorkerTests
 * @date : 2026-10-17 12:00
 * @email : scott_x@163.com
 * @description : 渠道回调 Inbox Worker 测试，验证成功完成、业务失败重试和线程 TraceContext 清理。
 * @status : create
 */
class DefaultTransactionCallbackInboxWorkerTests {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 17, 12, 0);

    @AfterEach
    void clearTraceContext() {
        TraceContext.clear();
    }

    /** 成功抢占后调用现有幂等回调服务，并仅在业务处理返回后标记 PROCESSED。 */
    @Test
    void shouldProcessClaimedCallbackAndMarkProcessed() {
        TransactionCallbackInboxService inboxService = mock(TransactionCallbackInboxService.class);
        TransactionCallbackService callbackService = mock(TransactionCallbackService.class);
        TransactionCallbackInboxDO inbox = inbox();
        when(inboxService.listDue(NOW, 100)).thenReturn(List.of(inbox));
        when(inboxService.claim(inbox, NOW)).thenAnswer(invocation -> {
            inbox.setVersion(1);
            return true;
        });
        when(inboxService.markProcessed(eq(inbox), any())).thenReturn(true);
        DefaultTransactionCallbackInboxWorker worker =
                new DefaultTransactionCallbackInboxWorker(inboxService, callbackService);

        assertThat(worker.processDue(NOW, 100)).isEqualTo(1);

        verify(callbackService).recordChannelCallback(any(TransactionChannelCallbackCommandDTO.class));
        verify(inboxService).markProcessed(eq(inbox), any());
        verify(inboxService, never()).markFailed(eq(inbox), any(), any());
        assertThat(TraceContext.getTraceId()).isNull();
    }

    /** 业务处理异常时不得标记完成，必须记录失败供后续重试。 */
    @Test
    void shouldRecordFailureWhenCallbackProcessingThrows() {
        TransactionCallbackInboxService inboxService = mock(TransactionCallbackInboxService.class);
        TransactionCallbackService callbackService = mock(TransactionCallbackService.class);
        TransactionCallbackInboxDO inbox = inbox();
        when(inboxService.listDue(NOW, 50)).thenReturn(List.of(inbox));
        when(inboxService.claim(inbox, NOW)).thenReturn(true);
        when(callbackService.recordChannelCallback(any())).thenThrow(new IllegalStateException("database unavailable"));
        when(inboxService.markFailed(eq(inbox), any(), any())).thenReturn(true);
        DefaultTransactionCallbackInboxWorker worker =
                new DefaultTransactionCallbackInboxWorker(inboxService, callbackService);

        assertThat(worker.processDue(NOW, 50)).isZero();

        verify(inboxService, never()).markProcessed(eq(inbox), any());
        verify(inboxService).markFailed(eq(inbox), any(IllegalStateException.class), any());
        assertThat(TraceContext.getTraceId()).isNull();
    }

    private TransactionCallbackInboxDO inbox() {
        TransactionChannelCallbackCommandDTO command = new TransactionChannelCallbackCommandDTO();
        command.setChannelCode("MPGS");
        command.setCallbackEventId("event-001");
        command.setCallbackKind("FINANCIAL_TRANSACTION");
        command.setRequestBodyLength(16);
        command.setRequestBodySha256(
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        command.setSignatureValid(true);
        command.setIpAllowed(true);

        TransactionCallbackInboxDO inbox = new TransactionCallbackInboxDO();
        inbox.setId(1L);
        inbox.setInboxId("CBI-001");
        inbox.setTraceId("trace-callback-001");
        inbox.setChannelCode("MPGS");
        inbox.setPayloadJson(JsonUtils.toJsonString(command));
        inbox.setRequestBodyLength(command.getRequestBodyLength());
        inbox.setRequestBodySha256(command.getRequestBodySha256());
        inbox.setRetryCount(0);
        inbox.setMaxRetryCount(12);
        inbox.setVersion(0);
        return inbox;
    }
}
