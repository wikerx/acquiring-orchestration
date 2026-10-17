package com.scott.payment.payment.service.impl;

import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.payment.api.internal.dto.TransactionCallbackInboxAcceptResultDTO;
import com.scott.payment.payment.api.internal.dto.TransactionChannelCallbackCommandDTO;
import com.scott.payment.payment.entity.TransactionCallbackInboxDO;
import com.scott.payment.payment.mapper.TransactionCallbackInboxMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultTransactionCallbackInboxServiceTests
 * @date : 2026-10-17 12:00
 * @email : scott_x@163.com
 * @description : 渠道回调 Inbox 服务测试，验证原子受理、数据库幂等、版本 CAS、重试封顶和超时恢复。
 * @status : create
 */
class DefaultTransactionCallbackInboxServiceTests {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 17, 12, 0);
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-10-17T04:00:00Z"), ZoneId.of("Asia/Shanghai"));

    @AfterEach
    void clearTraceContext() {
        TraceContext.clear();
    }

    /** 首次回调必须保存标准事件和摘要，不得把请求头或可逆正文写入 payload。 */
    @Test
    void shouldAcceptFirstCallbackWithoutPersistingRawPayload() {
        TransactionCallbackInboxMapper mapper = mock(TransactionCallbackInboxMapper.class);
        AtomicReference<TransactionCallbackInboxDO> inserted = new AtomicReference<>();
        doAnswer(invocation -> {
            TransactionCallbackInboxDO value = invocation.getArgument(0);
            value.setId(1L);
            inserted.set(value);
            return 1;
        }).when(mapper).upsertInbox(any());
        when(mapper.selectByIdempotency(eq("MPGS"), anyString()))
                .thenAnswer(invocation -> inserted.get());
        DefaultTransactionCallbackInboxService service =
                new DefaultTransactionCallbackInboxService(mapper, CLOCK);

        TransactionCallbackInboxAcceptResultDTO result = service.accept(command());

        assertThat(result.getAcceptResult()).isEqualTo("ACCEPTED");
        assertThat(result.getInboxStatus()).isEqualTo("RECEIVED");
        assertThat(inserted.get().getPayloadJson())
                .contains("event-001")
                .doesNotContain("Authorization")
                .doesNotContain("provider-secret");
        assertThat(inserted.get().getRequestHeaderJsonMasked()).doesNotContain("provider-secret");
        assertThat(inserted.get().getRequestBodyLength()).isEqualTo(128);
        assertThat(inserted.get().getRequestBodySha256()).hasSize(64);
        assertThat(inserted.get().getMaxRetryCount()).isEqualTo(12);
    }

    /** 唯一键命中必须返回首次记录，不能生成第二个可消费事件。 */
    @Test
    void shouldReturnExistingRecordWhenDatabaseIdempotencyMatches() {
        TransactionCallbackInboxMapper mapper = mock(TransactionCallbackInboxMapper.class);
        TransactionCallbackInboxDO existing = persistedInbox();
        when(mapper.selectByIdempotency(eq("MPGS"), anyString())).thenReturn(existing);
        DefaultTransactionCallbackInboxService service =
                new DefaultTransactionCallbackInboxService(mapper, CLOCK);

        TransactionCallbackInboxAcceptResultDTO result = service.accept(command());

        assertThat(result.getAcceptResult()).isEqualTo("DUPLICATE");
        assertThat(result.getInboxId()).isEqualTo("CBI-FIRST");
        verify(mapper).upsertInbox(any());
    }

    /** 安全结论或原始字节摘要不完整时必须失败关闭且不得访问数据库。 */
    @Test
    void shouldRejectUnverifiedCallbackBeforeDatabaseAccess() {
        TransactionCallbackInboxMapper mapper = mock(TransactionCallbackInboxMapper.class);
        DefaultTransactionCallbackInboxService service =
                new DefaultTransactionCallbackInboxService(mapper, CLOCK);
        TransactionChannelCallbackCommandDTO command = command();
        command.setSignatureValid(false);

        assertThatThrownBy(() -> service.accept(command)).isInstanceOf(ServiceException.class);
        verify(mapper, never()).upsertInbox(any());
    }

    /** 超长稳定事件号必须在数据库写入前失败关闭，避免字段截断或持续重试。 */
    @Test
    void shouldRejectOversizedCallbackEventIdBeforeDatabaseAccess() {
        TransactionCallbackInboxMapper mapper = mock(TransactionCallbackInboxMapper.class);
        DefaultTransactionCallbackInboxService service =
                new DefaultTransactionCallbackInboxService(mapper, CLOCK);
        TransactionChannelCallbackCommandDTO command = command();
        command.setCallbackEventId("E".repeat(257));

        assertThatThrownBy(() -> service.accept(command)).isInstanceOf(ServiceException.class);
        verify(mapper, never()).upsertInbox(any());
    }

    /** CAS 抢占成功后必须同步增加内存版本，供后续完成或失败更新校验所有权。 */
    @Test
    void shouldClaimWithVersionAndAdvanceInMemoryVersion() {
        TransactionCallbackInboxMapper mapper = mock(TransactionCallbackInboxMapper.class);
        when(mapper.claim(eq(1L), eq(3), eq(NOW), anyString(), anyString(), anyString()))
                .thenReturn(1);
        DefaultTransactionCallbackInboxService service =
                new DefaultTransactionCallbackInboxService(mapper, CLOCK);
        TransactionCallbackInboxDO inbox = persistedInbox();
        inbox.setVersion(3);

        assertThat(service.claim(inbox, NOW)).isTrue();
        assertThat(inbox.getVersion()).isEqualTo(4);
        assertThat(inbox.getInboxStatus()).isEqualTo("PROCESSING");
    }

    /** 普通失败按指数退避进入 RETRY，达到单条最大次数时必须进入 CLOSED 且不再安排时间。 */
    @Test
    void shouldRetryThenCloseAtConfiguredMaximum() {
        TransactionCallbackInboxMapper mapper = mock(TransactionCallbackInboxMapper.class);
        when(mapper.markFailed(any(), any(), anyString(), any(), anyString(), any(), anyString()))
                .thenReturn(1);
        DefaultTransactionCallbackInboxService service =
                new DefaultTransactionCallbackInboxService(mapper, CLOCK);
        TransactionCallbackInboxDO retry = persistedInbox();
        retry.setVersion(4);
        retry.setRetryCount(0);
        retry.setMaxRetryCount(12);

        assertThat(service.markFailed(retry, new IllegalStateException(), NOW)).isTrue();
        verify(mapper).markFailed(
                1L, 4, "RETRY", NOW.plusSeconds(5), "IllegalStateException", NOW, "PROCESSING");

        TransactionCallbackInboxDO closed = persistedInbox();
        closed.setVersion(9);
        closed.setRetryCount(11);
        closed.setMaxRetryCount(12);
        assertThat(service.markFailed(closed, new IllegalArgumentException(), NOW)).isTrue();
        verify(mapper).markFailed(
                1L, 9, "CLOSED", null, "IllegalArgumentException", NOW, "PROCESSING");
    }

    /** Worker 崩溃遗留的 PROCESSING 记录必须由租约扫描恢复。 */
    @Test
    void shouldRecoverStaleProcessingRecords() {
        TransactionCallbackInboxMapper mapper = mock(TransactionCallbackInboxMapper.class);
        when(mapper.recoverStaleProcessing(any(), any(), anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(2);
        DefaultTransactionCallbackInboxService service =
                new DefaultTransactionCallbackInboxService(mapper, CLOCK);

        assertThat(service.recoverStaleProcessing(NOW.minusMinutes(2), NOW, 800)).isEqualTo(2);
        verify(mapper).recoverStaleProcessing(
                NOW.minusMinutes(2), NOW, "PROCESSING", "RETRY", "CLOSED", 500);
    }

    private TransactionChannelCallbackCommandDTO command() {
        TransactionChannelCallbackCommandDTO command = new TransactionChannelCallbackCommandDTO();
        command.setChannelCode("mpgs");
        command.setChannelCredentialHint("mid-001");
        command.setCallbackEventId("event-001");
        command.setCallbackKind("FINANCIAL_TRANSACTION");
        command.setChannelOrderNo("order-001");
        command.setChannelTransactionId("transaction-001");
        command.setRequestHeaders(Map.of("Authorization", "provider-secret"));
        command.setRequestBodyLength(128);
        command.setRequestBodySha256(
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        command.setSignatureValid(true);
        command.setIpAllowed(true);
        command.setReceivedTime(NOW);
        return command;
    }

    private TransactionCallbackInboxDO persistedInbox() {
        TransactionCallbackInboxDO inbox = new TransactionCallbackInboxDO();
        inbox.setId(1L);
        inbox.setInboxId("CBI-FIRST");
        inbox.setChannelCode("MPGS");
        inbox.setInboxStatus("RECEIVED");
        inbox.setRetryCount(0);
        inbox.setMaxRetryCount(12);
        inbox.setVersion(0);
        return inbox;
    }
}
