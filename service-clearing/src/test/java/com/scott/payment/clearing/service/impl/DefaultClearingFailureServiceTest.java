package com.scott.payment.clearing.service.impl;

import com.scott.payment.clearing.config.ClearingProperties;
import com.scott.payment.clearing.domain.model.ClearingOperationFacts;
import com.scott.payment.clearing.domain.state.ClearingFailureCodeEnum;
import com.scott.payment.clearing.domain.state.ClearingStateEnum;
import com.scott.payment.clearing.dto.ClearingClaimResult;
import com.scott.payment.clearing.dto.ClearingFailureResult;
import com.scott.payment.clearing.entity.ClearingTransactionEventOutboxDO;
import com.scott.payment.clearing.entity.ClearingTransactionFinanceStateDO;
import com.scott.payment.clearing.exception.ClearingProcessingException;
import com.scott.payment.clearing.mapper.ClearingTransactionEventOutboxMapper;
import com.scott.payment.clearing.mapper.ClearingTransactionFinanceStateMapper;
import com.scott.payment.clearing.service.ClearingProjectionService;
import com.scott.payment.clearing.service.ClearingAnomalyService;
import com.scott.payment.component.core.id.GlobalIdGenerator;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.mq.constant.MqTag;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.component.mq.message.ClearingRetryDueMessage;
import com.scott.payment.component.mq.message.PaymentTransactionEventMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultClearingFailureServiceTest
 * @date : 2026-08-26 16:00
 * @email : scott_x@163.com
 * @description : 验证受控清分失败在独立事务中进入等待、失败或人工复核，并原子写入绝对定时重试 Outbox。
 * @status : create
 */
class DefaultClearingFailureServiceTest {

    private static final LocalDateTime TRANSACTION_TIME =
            LocalDateTime.of(2026, 8, 26, 8, 30, 0, 123_000_000);
    private static final LocalDateTime NOW_UTC =
            LocalDateTime.of(2026, 8, 26, 8, 40, 0, 123_000_000);

    private ClearingTransactionFinanceStateMapper financeStateMapper;
    private ClearingTransactionEventOutboxMapper outboxMapper;
    private ClearingProjectionService projectionService;
    private GlobalIdGenerator idGenerator;
    private DefaultClearingFailureService service;

    @BeforeEach
    void setUp() {
        financeStateMapper = mock(ClearingTransactionFinanceStateMapper.class);
        outboxMapper = mock(ClearingTransactionEventOutboxMapper.class);
        projectionService = mock(ClearingProjectionService.class);
        idGenerator = mock(GlobalIdGenerator.class);
        ClearingProperties properties = new ClearingProperties();
        properties.setMaxRetryCount(8);
        service = new DefaultClearingFailureService(
                financeStateMapper, outboxMapper, projectionService, idGenerator, properties,
                mock(ClearingAnomalyService.class));
    }

    @Test
    void sourcePendingShouldEnterWaitingSourceAndWriteMatchingScheduledOutbox() {
        PaymentTransactionEventMessage message = message();
        ClearingClaimResult claim = claim();
        when(financeStateMapper.selectForUpdate("TX-1", TRANSACTION_TIME))
                .thenReturn(processingState(0));
        when(financeStateMapper.recordFailure(
                "TX-1", TRANSACTION_TIME, "worker-1", 5, "WAITING_SOURCE", 1,
                NOW_UTC.plusMinutes(1), "SOURCE_CLEARING_PENDING",
                "source transaction clearing is pending", NOW_UTC)).thenReturn(1);
        when(idGenerator.nextId()).thenReturn("9001");
        when(outboxMapper.insertLogical(any())).thenReturn(1);

        ClearingFailureResult result = service.recordFailure(
                message, claim, "worker-1",
                new ClearingProcessingException(ClearingFailureCodeEnum.SOURCE_CLEARING_PENDING,
                        "source transaction clearing is pending"), NOW_UTC);

        assertThat(result.targetStatus()).isEqualTo("WAITING_SOURCE");
        assertThat(result.recordedFailureCode()).isEqualTo("SOURCE_CLEARING_PENDING");
        assertThat(result.clearingRetryCount()).isEqualTo(1);
        assertThat(result.nextRetryTime()).isEqualTo(NOW_UTC.plusMinutes(1));
        assertThat(result.retryScheduled()).isTrue();
        verify(projectionService).updateResolvingLocator(
                claim.operation(), ClearingStateEnum.WAITING_SOURCE,
                "SOURCE_CLEARING_PENDING", NOW_UTC);

        ArgumentCaptor<ClearingTransactionEventOutboxDO> outboxCaptor =
                ArgumentCaptor.forClass(ClearingTransactionEventOutboxDO.class);
        verify(outboxMapper).insertLogical(outboxCaptor.capture());
        ClearingTransactionEventOutboxDO outbox = outboxCaptor.getValue();
        assertThat(outbox.getTopic()).isEqualTo(MqTopic.PAYMENT_CLEARING_DELAY);
        assertThat(outbox.getTag()).isEqualTo(MqTag.TRANSACTION_CLEARING_RETRY_DUE);
        assertThat(outbox.getDeliveryMode()).isEqualTo("SCHEDULED");
        assertThat(outbox.getDeliverAt()).isEqualTo(NOW_UTC.plusMinutes(1));
        assertThat(outbox.getNextRetryTime()).isEqualTo(NOW_UTC);
        assertThat(outbox.getMessageGroup()).isNull();

        ClearingRetryDueMessage retryMessage = JsonUtils.parseObject(
                outbox.getPayloadJson(), ClearingRetryDueMessage.class);
        assertThat(retryMessage.getSourceEventNo()).isEqualTo("MSG-1");
        assertThat(retryMessage.getExpectedClearingRevision()).isZero();
        assertThat(retryMessage.getClearingRetryCount()).isEqualTo(1);
        assertThat(retryMessage.getRetryReasonCode()).isEqualTo("SOURCE_CLEARING_PENDING");
        assertThat(retryMessage.getDeliverAt())
                .isEqualTo(NOW_UTC.plusMinutes(1).toInstant(java.time.ZoneOffset.UTC));
    }

    @Test
    void retryScheduleShouldBeNormalizedToDatabaseMillisecondPrecision() {
        LocalDateTime nowWithNanos = LocalDateTime.of(2026, 8, 26, 8, 40, 0, 123_456_789);
        LocalDateTime expectedDeliverAt = nowWithNanos.plusMinutes(1).truncatedTo(ChronoUnit.MILLIS);
        when(financeStateMapper.selectForUpdate("TX-1", TRANSACTION_TIME))
                .thenReturn(processingState(0));
        when(financeStateMapper.recordFailure(
                "TX-1", TRANSACTION_TIME, "worker-1", 5, "WAITING_SOURCE", 1,
                expectedDeliverAt, "SOURCE_CLEARING_PENDING",
                "source transaction clearing is pending", nowWithNanos)).thenReturn(1);
        when(idGenerator.nextId()).thenReturn("9003");
        when(outboxMapper.insertLogical(any())).thenReturn(1);

        service.recordFailure(message(), claim(), "worker-1",
                new ClearingProcessingException(ClearingFailureCodeEnum.SOURCE_CLEARING_PENDING,
                        "source transaction clearing is pending"), nowWithNanos);

        ArgumentCaptor<ClearingTransactionEventOutboxDO> outboxCaptor =
                ArgumentCaptor.forClass(ClearingTransactionEventOutboxDO.class);
        verify(outboxMapper).insertLogical(outboxCaptor.capture());
        ClearingTransactionEventOutboxDO outbox = outboxCaptor.getValue();
        ClearingRetryDueMessage retryMessage = JsonUtils.parseObject(
                outbox.getPayloadJson(), ClearingRetryDueMessage.class);
        assertThat(outbox.getDeliverAt()).isEqualTo(expectedDeliverAt);
        assertThat(retryMessage.getDeliverAt())
                .isEqualTo(expectedDeliverAt.toInstant(java.time.ZoneOffset.UTC));
    }

    @Test
    void retryableFailureShouldUseDelayLadderAndPreserveOriginalSourceEvent() {
        ClearingRetryDueMessage message = retryMessage();
        when(financeStateMapper.selectForUpdate("TX-1", TRANSACTION_TIME))
                .thenReturn(processingState(2));
        when(financeStateMapper.recordFailure(
                "TX-1", TRANSACTION_TIME, "worker-1", 5, "FAILED", 3,
                NOW_UTC.plusMinutes(15), "FEE_VERSION_NOT_FOUND",
                "fee version is not visible", NOW_UTC)).thenReturn(1);
        when(idGenerator.nextId()).thenReturn("9002");
        when(outboxMapper.insertLogical(any())).thenReturn(1);

        ClearingFailureResult result = service.recordFailure(
                message, claim(), "worker-1",
                new ClearingProcessingException(ClearingFailureCodeEnum.FEE_VERSION_NOT_FOUND,
                        "fee version is not visible"), NOW_UTC);

        assertThat(result.targetStatus()).isEqualTo("FAILED");
        assertThat(result.nextRetryTime()).isEqualTo(NOW_UTC.plusMinutes(15));
        verify(projectionService).updateResolvingLocator(
                claim().operation(), ClearingStateEnum.FAILED,
                "FEE_VERSION_NOT_FOUND", NOW_UTC);
        ArgumentCaptor<ClearingTransactionEventOutboxDO> outboxCaptor =
                ArgumentCaptor.forClass(ClearingTransactionEventOutboxDO.class);
        verify(outboxMapper).insertLogical(outboxCaptor.capture());
        ClearingRetryDueMessage persisted = JsonUtils.parseObject(
                outboxCaptor.getValue().getPayloadJson(), ClearingRetryDueMessage.class);
        assertThat(persisted.getSourceEventNo()).isEqualTo("ORIGINAL-EVENT-1");
    }

    @Test
    void nonRetryableFailureShouldEnterManualReviewWithoutOutboxAndTruncateSummary() {
        String longSummary = "x".repeat(700);
        when(financeStateMapper.selectForUpdate("TX-1", TRANSACTION_TIME))
                .thenReturn(processingState(2));
        when(financeStateMapper.recordFailure(
                "TX-1", TRANSACTION_TIME, "worker-1", 5, "MANUAL_REVIEW", 2,
                null, "FEE_SNAPSHOT_HASH_MISMATCH", "x".repeat(512), NOW_UTC)).thenReturn(1);

        ClearingFailureResult result = service.recordFailure(
                message(), claim(), "worker-1",
                new ClearingProcessingException(ClearingFailureCodeEnum.FEE_SNAPSHOT_HASH_MISMATCH,
                        longSummary), NOW_UTC);

        assertThat(result.targetStatus()).isEqualTo("MANUAL_REVIEW");
        assertThat(result.clearingRetryCount()).isEqualTo(2);
        assertThat(result.nextRetryTime()).isNull();
        assertThat(result.retryScheduled()).isFalse();
        verify(projectionService).updateResolvingLocator(
                claim().operation(), ClearingStateEnum.MANUAL_REVIEW,
                "FEE_SNAPSHOT_HASH_MISMATCH", NOW_UTC);
        verify(outboxMapper, never()).insertLogical(any());
    }

    @Test
    void exhaustedRetryShouldEnterManualReviewWithStableExhaustedCode() {
        when(financeStateMapper.selectForUpdate("TX-1", TRANSACTION_TIME))
                .thenReturn(processingState(8));
        when(financeStateMapper.recordFailure(
                "TX-1", TRANSACTION_TIME, "worker-1", 5, "MANUAL_REVIEW", 8,
                null, "CLEARING_RETRY_EXHAUSTED",
                "clearing retry exhausted after 8 attempts; last failure=FEE_SNAPSHOT_MISSING", NOW_UTC))
                .thenReturn(1);

        ClearingFailureResult result = service.recordFailure(
                retryMessage(), claim(), "worker-1",
                new ClearingProcessingException(ClearingFailureCodeEnum.FEE_SNAPSHOT_MISSING,
                        "fee snapshot is still unavailable"), NOW_UTC);

        assertThat(result.targetStatus()).isEqualTo("MANUAL_REVIEW");
        assertThat(result.recordedFailureCode()).isEqualTo("CLEARING_RETRY_EXHAUSTED");
        assertThat(result.clearingRetryCount()).isEqualTo(8);
        verify(projectionService).updateResolvingLocator(
                claim().operation(), ClearingStateEnum.MANUAL_REVIEW,
                "CLEARING_RETRY_EXHAUSTED", NOW_UTC);
        verify(outboxMapper, never()).insertLogical(any());
    }

    @Test
    void controlledProjectionConflictShouldNotDiscardAuthoritativeFailureState() {
        when(financeStateMapper.selectForUpdate("TX-1", TRANSACTION_TIME))
                .thenReturn(processingState(2));
        when(financeStateMapper.recordFailure(
                "TX-1", TRANSACTION_TIME, "worker-1", 5, "MANUAL_REVIEW", 2,
                null, "FEE_SNAPSHOT_HASH_MISMATCH", "snapshot hash mismatch", NOW_UTC)).thenReturn(1);
        org.mockito.Mockito.doThrow(new ClearingProcessingException(
                        ClearingFailureCodeEnum.TRANSACTION_VERSION_CONFLICT, "locator is unavailable"))
                .when(projectionService).updateResolvingLocator(
                        claim().operation(), ClearingStateEnum.MANUAL_REVIEW,
                        "FEE_SNAPSHOT_HASH_MISMATCH", NOW_UTC);

        ClearingFailureResult result = service.recordFailure(
                message(), claim(), "worker-1",
                new ClearingProcessingException(
                        ClearingFailureCodeEnum.FEE_SNAPSHOT_HASH_MISMATCH, "snapshot hash mismatch"),
                NOW_UTC);

        assertThat(result.targetStatus()).isEqualTo("MANUAL_REVIEW");
        assertThat(result.retryScheduled()).isFalse();
        verify(outboxMapper, never()).insertLogical(any());
    }

    @Test
    void staleClaimShouldFailWithoutWritingStateOrOutbox() {
        ClearingTransactionFinanceStateDO state = processingState(0);
        state.setVersion(6);
        when(financeStateMapper.selectForUpdate("TX-1", TRANSACTION_TIME)).thenReturn(state);

        assertThatThrownBy(() -> service.recordFailure(
                message(), claim(), "worker-1",
                new ClearingProcessingException(ClearingFailureCodeEnum.FEE_VERSION_NOT_FOUND,
                        "fee version is missing"), NOW_UTC))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("lease");

        verify(financeStateMapper, never()).recordFailure(
                any(), any(), any(), any(Integer.class), any(), any(Integer.class),
                any(), any(), any(), any());
        verify(outboxMapper, never()).insertLogical(any());
        verify(projectionService, never()).updateResolvingLocator(any(), any(), any(), any());
    }

    private PaymentTransactionEventMessage message() {
        PaymentTransactionEventMessage message = new PaymentTransactionEventMessage();
        message.setMessageId("MSG-1");
        message.setCreatedAt(NOW_UTC.minusMinutes(1));
        message.setTraceId("TRACE-1");
        message.setRetryCount(0);
        message.setTransactionId("TX-1");
        message.setOperationId("OP-1");
        message.setMerchantId("M-1");
        message.setMerchantOrderNo("ORDER-1");
        message.setTransactionType("REFUND");
        message.setTransactionStatus("SUCCESS");
        message.setEventType(MqTag.TRANSACTION_STATUS_CHANGED);
        message.setTransactionDateTime(TRANSACTION_TIME);
        return message;
    }

    private ClearingRetryDueMessage retryMessage() {
        ClearingRetryDueMessage message = new ClearingRetryDueMessage();
        PaymentTransactionEventMessage source = message();
        message.setMessageId("RETRY-MSG-2");
        message.setCreatedAt(source.getCreatedAt());
        message.setTraceId(source.getTraceId());
        message.setRetryCount(0);
        message.setTransactionId(source.getTransactionId());
        message.setOperationId(source.getOperationId());
        message.setMerchantId(source.getMerchantId());
        message.setMerchantOrderNo(source.getMerchantOrderNo());
        message.setTransactionType(source.getTransactionType());
        message.setTransactionStatus(source.getTransactionStatus());
        message.setEventType(MqTag.TRANSACTION_CLEARING_RETRY_DUE);
        message.setTransactionDateTime(source.getTransactionDateTime());
        message.setSourceEventNo("ORIGINAL-EVENT-1");
        message.setExpectedClearingRevision(0);
        message.setClearingRetryCount(2);
        message.setRetryReasonCode("FEE_VERSION_NOT_FOUND");
        message.setDeliverAt(NOW_UTC.minusMinutes(1).toInstant(java.time.ZoneOffset.UTC));
        return message;
    }

    private ClearingClaimResult claim() {
        ClearingOperationFacts operation = new ClearingOperationFacts(
                "TX-1", "OP-1", "PAY-1", "M-1", "ORDER-1", "REFUND", "SUCCESS",
                "USD", new BigDecimal("20.00"), "USD", new BigDecimal("20.00"),
                "USD", new BigDecimal("20.00"), 2, TRANSACTION_TIME,
                TRANSACTION_TIME.minusHours(8), "Asia/Shanghai", 3);
        return new ClearingClaimResult(ClearingClaimResult.Outcome.ACQUIRED,
                "FS-1", 0, 5, operation);
    }

    private ClearingTransactionFinanceStateDO processingState(int retryCount) {
        ClearingTransactionFinanceStateDO state = new ClearingTransactionFinanceStateDO();
        state.setFinanceStateId("FS-1");
        state.setTransactionId("TX-1");
        state.setOperationId("OP-1");
        state.setMerchantId("M-1");
        state.setClearingStatus("PROCESSING");
        state.setClearingRevision(0);
        state.setProcessingOwner("worker-1");
        state.setProcessingDeadline(NOW_UTC.plusMinutes(2));
        state.setClearingRetryCount(retryCount);
        state.setTransactionDateTime(TRANSACTION_TIME);
        state.setVersion(5);
        return state;
    }
}
