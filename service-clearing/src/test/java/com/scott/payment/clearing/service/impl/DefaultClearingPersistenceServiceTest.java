package com.scott.payment.clearing.service.impl;

import com.scott.payment.clearing.config.ClearingProperties;
import com.scott.payment.clearing.dto.ClearingClaimResult;
import com.scott.payment.clearing.entity.ClearingTransactionFinanceStateDO;
import com.scott.payment.clearing.entity.ClearingTransactionOperationDO;
import com.scott.payment.clearing.mapper.ClearingTransactionFinanceStateMapper;
import com.scott.payment.clearing.mapper.ClearingTransactionIdempotencyMapper;
import com.scott.payment.clearing.mapper.ClearingTransactionOperationMapper;
import com.scott.payment.clearing.support.ClearingTimeSupport;
import com.scott.payment.component.mq.constant.MqTag;
import com.scott.payment.component.mq.message.PaymentTransactionEventMessage;
import com.scott.payment.component.mq.message.ClearingRetryDueMessage;
import com.scott.payment.component.core.id.GlobalIdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证清分阶段A只按动作分片时间和数据库权威身份领取处理租约，并安全处理重复消费。
 */
class DefaultClearingPersistenceServiceTest {

    private static final LocalDateTime TRANSACTION_TIME = LocalDateTime.of(2026, 8, 26, 8, 30, 0, 123_000_000);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 26, 9, 0);

    private ClearingTransactionOperationMapper operationMapper;
    private ClearingTransactionFinanceStateMapper financeStateMapper;
    private ClearingTransactionIdempotencyMapper idempotencyMapper;
    private GlobalIdGenerator globalIdGenerator;
    private DefaultClearingPersistenceService service;

    @BeforeEach
    void setUp() {
        operationMapper = mock(ClearingTransactionOperationMapper.class);
        financeStateMapper = mock(ClearingTransactionFinanceStateMapper.class);
        idempotencyMapper = mock(ClearingTransactionIdempotencyMapper.class);
        globalIdGenerator = mock(GlobalIdGenerator.class);
        ClearingProperties properties = new ClearingProperties();
        properties.setProcessingTimeoutSeconds(120);
        service = new DefaultClearingPersistenceService(
                operationMapper, financeStateMapper, idempotencyMapper, globalIdGenerator, properties);
    }

    @Test
    void claimShouldAcquireLeaseUsingAuthoritativeOperationAndFinanceState() {
        PaymentTransactionEventMessage message = message();
        ClearingTransactionOperationDO operation = operation("SUCCESS");
        ClearingTransactionFinanceStateDO financeState = financeState("PENDING", 4, 0);
        when(idempotencyMapper.existsSuccessfulConsumption("service-clearing-transaction-status:MSG-1"))
                .thenReturn(false);
        when(operationMapper.selectByTransaction("TX-1", TRANSACTION_TIME)).thenReturn(operation);
        when(financeStateMapper.selectByTransaction("TX-1", TRANSACTION_TIME)).thenReturn(financeState);
        when(financeStateMapper.claimProcessing(
                101L, "TX-1", TRANSACTION_TIME, 4, "worker-1", NOW, NOW.plusSeconds(120), "MSG-1"))
                .thenReturn(1);
        when(operationMapper.updateClearingProjection(
                "TX-1", TRANSACTION_TIME, 3, "PENDING", null, null, NOW)).thenReturn(1);

        ClearingClaimResult result = service.claim(message, "worker-1", NOW);

        assertThat(result.outcome()).isEqualTo(ClearingClaimResult.Outcome.ACQUIRED);
        assertThat(result.financeStateId()).isEqualTo("FS-1");
        assertThat(result.financeStateVersion()).isEqualTo(5);
        assertThat(result.operation().operationVersion()).isEqualTo(4);
        assertThat(result.operation().transactionStatus()).isEqualTo("SUCCESS");
        verify(operationMapper).updateClearingProjection(
                "TX-1", TRANSACTION_TIME, 3, "PENDING", null, null, NOW);
    }

    @Test
    void claimShouldAcknowledgeAlreadyCompletedActionWithoutTakingAnotherLease() {
        PaymentTransactionEventMessage message = message();
        when(idempotencyMapper.existsSuccessfulConsumption("service-clearing-transaction-status:MSG-1"))
                .thenReturn(false);
        when(operationMapper.selectByTransaction("TX-1", TRANSACTION_TIME)).thenReturn(operation("SUCCESS"));
        when(financeStateMapper.selectByTransaction("TX-1", TRANSACTION_TIME))
                .thenReturn(financeState("CLEARED", 7, 2));

        ClearingClaimResult result = service.claim(message, "worker-1", NOW);

        assertThat(result.outcome()).isEqualTo(ClearingClaimResult.Outcome.ALREADY_COMPLETED);
        assertThat(result.clearingRevision()).isEqualTo(2);
        verify(financeStateMapper, never()).claimProcessing(
                101L, "TX-1", TRANSACTION_TIME, 7, "worker-1", NOW, NOW.plusSeconds(120), "MSG-1");
    }

    @Test
    void claimShouldRejectMessageIdentityThatDoesNotMatchDatabase() {
        PaymentTransactionEventMessage message = message();
        ClearingTransactionOperationDO operation = operation("SUCCESS");
        operation.setMerchantId("M-OTHER");
        when(idempotencyMapper.existsSuccessfulConsumption("service-clearing-transaction-status:MSG-1"))
                .thenReturn(false);
        when(operationMapper.selectByTransaction("TX-1", TRANSACTION_TIME)).thenReturn(operation);

        assertThatThrownBy(() -> service.claim(message, "worker-1", NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("identity");

        verify(financeStateMapper, never()).selectByTransaction("TX-1", TRANSACTION_TIME);
    }

    @Test
    void claimShouldCreateMissingFinanceStateBeforeTakingLease() {
        PaymentTransactionEventMessage message = message();
        ClearingTransactionOperationDO operation = operation("SUCCESS");
        ClearingTransactionFinanceStateDO created = financeState("PENDING", 0, 0);
        when(idempotencyMapper.existsSuccessfulConsumption("service-clearing-transaction-status:MSG-1"))
                .thenReturn(false);
        when(operationMapper.selectByTransaction("TX-1", TRANSACTION_TIME)).thenReturn(operation);
        when(financeStateMapper.selectByTransaction("TX-1", TRANSACTION_TIME))
                .thenReturn(null, created);
        when(globalIdGenerator.nextId()).thenReturn("2026082609000000000001");
        when(financeStateMapper.claimProcessing(
                101L, "TX-1", TRANSACTION_TIME, 0, "worker-1", NOW, NOW.plusSeconds(120), "MSG-1"))
                .thenReturn(1);
        when(operationMapper.updateClearingProjection(
                "TX-1", TRANSACTION_TIME, 3, "PENDING", null, null, NOW)).thenReturn(1);

        ClearingClaimResult result = service.claim(message, "worker-1", NOW);

        assertThat(result.outcome()).isEqualTo(ClearingClaimResult.Outcome.ACQUIRED);
        verify(financeStateMapper).insertIfAbsent(
                "FS2026082609000000000001", operation, NOW);
    }

    @Test
    void claimShouldRejectAcquisitionWhenOperationProjectionCasFails() {
        PaymentTransactionEventMessage message = message();
        ClearingTransactionOperationDO operation = operation("SUCCESS");
        when(idempotencyMapper.existsSuccessfulConsumption("service-clearing-transaction-status:MSG-1"))
                .thenReturn(false);
        when(operationMapper.selectByTransaction("TX-1", TRANSACTION_TIME)).thenReturn(operation);
        when(financeStateMapper.selectByTransaction("TX-1", TRANSACTION_TIME))
                .thenReturn(financeState("PENDING", 4, 0));
        when(financeStateMapper.claimProcessing(
                101L, "TX-1", TRANSACTION_TIME, 4, "worker-1", NOW, NOW.plusSeconds(120), "MSG-1"))
                .thenReturn(1);
        when(operationMapper.updateClearingProjection(
                "TX-1", TRANSACTION_TIME, 3, "PENDING", null, null, NOW)).thenReturn(0);

        assertThatThrownBy(() -> service.claim(message, "worker-1", NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("projection CAS");
    }

    @Test
    void claimShouldRejectFinanceStateWithMismatchedImmutableFacts() {
        PaymentTransactionEventMessage message = message();
        ClearingTransactionOperationDO operation = operation("SUCCESS");
        ClearingTransactionFinanceStateDO state = financeState("PENDING", 4, 0);
        state.setLabelCurrency("EUR");
        when(idempotencyMapper.existsSuccessfulConsumption("service-clearing-transaction-status:MSG-1"))
                .thenReturn(false);
        when(operationMapper.selectByTransaction("TX-1", TRANSACTION_TIME)).thenReturn(operation);
        when(financeStateMapper.selectByTransaction("TX-1", TRANSACTION_TIME)).thenReturn(state);

        assertThatThrownBy(() -> service.claim(message, "worker-1", NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("identity");
        verify(financeStateMapper, never()).claimProcessing(
                101L, "TX-1", TRANSACTION_TIME, 4, "worker-1", NOW, NOW.plusSeconds(120), "MSG-1");
    }

    @Test
    void duplicateTerminalMessageShouldAcknowledgeExistingBusinessRetryWithoutReclaiming() {
        ClearingTransactionFinanceStateDO state = financeState("WAITING_SOURCE", 4, 0);
        state.setClearingRetryCount(1);
        state.setNextRetryTime(NOW.plusMinutes(1));
        state.setLastFailureCode("SOURCE_CLEARING_PENDING");
        when(idempotencyMapper.existsSuccessfulConsumption("service-clearing-transaction-status:MSG-1"))
                .thenReturn(false);
        when(operationMapper.selectByTransaction("TX-1", TRANSACTION_TIME)).thenReturn(operation("SUCCESS"));
        when(financeStateMapper.selectByTransaction("TX-1", TRANSACTION_TIME)).thenReturn(state);

        ClearingClaimResult result = service.claim(message(), "worker-1", NOW);

        assertThat(result.outcome()).isEqualTo(ClearingClaimResult.Outcome.RETRY_ALREADY_SCHEDULED);
        verify(financeStateMapper, never()).claimProcessing(
                101L, "TX-1", TRANSACTION_TIME, 4, "worker-1", NOW, NOW.plusSeconds(120), "MSG-1");
    }

    @Test
    void automaticMessageShouldAcknowledgeManualReviewWithoutReclaiming() {
        when(idempotencyMapper.existsSuccessfulConsumption("service-clearing-transaction-status:MSG-1"))
                .thenReturn(false);
        when(operationMapper.selectByTransaction("TX-1", TRANSACTION_TIME)).thenReturn(operation("SUCCESS"));
        when(financeStateMapper.selectByTransaction("TX-1", TRANSACTION_TIME))
                .thenReturn(financeState("MANUAL_REVIEW", 4, 0));

        ClearingClaimResult result = service.claim(message(), "worker-1", NOW);

        assertThat(result.outcome()).isEqualTo(ClearingClaimResult.Outcome.MANUAL_REVIEW_REQUIRED);
        verify(financeStateMapper, never()).claimProcessing(
                101L, "TX-1", TRANSACTION_TIME, 4, "worker-1", NOW, NOW.plusSeconds(120), "MSG-1");
    }

    /**
     * 到期重试的数据库本地时间与消息 Instant 一致时允许重新领取处理租约。
     */
    @Test
    void dueRetryShouldMatchPlatformTimeAndAcquireLease() {
        LocalDateTime dueTime = NOW.minusMinutes(1);
        ClearingRetryDueMessage message = retryMessage("RETRY-DUE", 1, dueTime);
        ClearingTransactionFinanceStateDO state = financeState("WAITING_SOURCE", 4, 0);
        state.setClearingRetryCount(1);
        state.setNextRetryTime(dueTime);
        state.setLastFailureCode("SOURCE_CLEARING_PENDING");
        when(idempotencyMapper.existsSuccessfulConsumption(
                "service-clearing-transaction-status:RETRY-DUE")).thenReturn(false);
        when(operationMapper.selectByTransaction("TX-1", TRANSACTION_TIME)).thenReturn(operation("SUCCESS"));
        when(financeStateMapper.selectByTransaction("TX-1", TRANSACTION_TIME)).thenReturn(state);
        when(financeStateMapper.claimProcessing(
                101L, "TX-1", TRANSACTION_TIME, 4, "worker-1",
                NOW, NOW.plusSeconds(120), "RETRY-DUE")).thenReturn(1);
        when(operationMapper.updateClearingProjection(
                "TX-1", TRANSACTION_TIME, 3, "PENDING", null, null, NOW)).thenReturn(1);

        ClearingClaimResult result = service.claim(message, "worker-1", NOW);

        assertThat(result.outcome()).isEqualTo(ClearingClaimResult.Outcome.ACQUIRED);
    }

    /**
     * 即使消息提前到达，只要数据库本地到期时间仍在未来就不得抢占租约。
     */
    @Test
    void futureRetryShouldRemainBusyWithoutTakingLease() {
        LocalDateTime dueTime = NOW.plusMinutes(1);
        ClearingRetryDueMessage message = retryMessage("RETRY-FUTURE", 1, dueTime);
        ClearingTransactionFinanceStateDO state = financeState("FAILED", 4, 0);
        state.setClearingRetryCount(1);
        state.setNextRetryTime(dueTime);
        state.setLastFailureCode("SOURCE_CLEARING_PENDING");
        when(idempotencyMapper.existsSuccessfulConsumption(
                "service-clearing-transaction-status:RETRY-FUTURE")).thenReturn(false);
        when(operationMapper.selectByTransaction("TX-1", TRANSACTION_TIME)).thenReturn(operation("SUCCESS"));
        when(financeStateMapper.selectByTransaction("TX-1", TRANSACTION_TIME)).thenReturn(state);

        ClearingClaimResult result = service.claim(message, "worker-1", NOW);

        assertThat(result.outcome()).isEqualTo(ClearingClaimResult.Outcome.BUSY);
        verify(financeStateMapper, never()).claimProcessing(
                101L, "TX-1", TRANSACTION_TIME, 4, "worker-1",
                NOW, NOW.plusSeconds(120), "RETRY-FUTURE");
    }

    @Test
    void claimShouldAcknowledgeStaleRetryWithoutTakingLease() {
        ClearingRetryDueMessage message = new ClearingRetryDueMessage();
        PaymentTransactionEventMessage source = message();
        message.setMessageId("RETRY-2");
        message.setEventType(MqTag.TRANSACTION_CLEARING_RETRY_DUE);
        message.setTransactionId(source.getTransactionId());
        message.setOperationId(source.getOperationId());
        message.setMerchantId(source.getMerchantId());
        message.setMerchantOrderNo(source.getMerchantOrderNo());
        message.setTransactionType(source.getTransactionType());
        message.setTransactionDateTime(source.getTransactionDateTime());
        message.setSourceEventNo("MSG-1");
        message.setExpectedClearingRevision(0);
        message.setClearingRetryCount(1);
        message.setRetryReasonCode("SOURCE_CLEARING_PENDING");
        message.setDeliverAt(ClearingTimeSupport.toInstant(NOW.minusMinutes(1)));
        ClearingTransactionFinanceStateDO state = financeState("WAITING_SOURCE", 4, 0);
        state.setClearingRetryCount(2);
        state.setNextRetryTime(NOW.minusMinutes(1));
        state.setLastFailureCode("SOURCE_CLEARING_PENDING");
        when(idempotencyMapper.existsSuccessfulConsumption("service-clearing-transaction-status:RETRY-2"))
                .thenReturn(false);
        when(operationMapper.selectByTransaction("TX-1", TRANSACTION_TIME)).thenReturn(operation("SUCCESS"));
        when(financeStateMapper.selectByTransaction("TX-1", TRANSACTION_TIME)).thenReturn(state);

        ClearingClaimResult result = service.claim(message, "worker-1", NOW);

        assertThat(result.outcome()).isEqualTo(ClearingClaimResult.Outcome.STALE_RETRY);
        verify(financeStateMapper, never()).claimProcessing(
                101L, "TX-1", TRANSACTION_TIME, 4, "worker-1", NOW, NOW.plusSeconds(120), "RETRY-2");
    }

    /**
     * 构造与数据库重试控制字段完全一致的到期消息。
     *
     * @param messageId 消息唯一号
     * @param retryCount 清分业务重试次数
     * @param deliverAt 平台本地到期时间
     * @return 携带绝对投递时刻的重试消息
     */
    private ClearingRetryDueMessage retryMessage(String messageId,
                                                 int retryCount,
                                                 LocalDateTime deliverAt) {
        PaymentTransactionEventMessage source = message();
        ClearingRetryDueMessage message = new ClearingRetryDueMessage();
        message.setMessageId(messageId);
        message.setEventType(MqTag.TRANSACTION_CLEARING_RETRY_DUE);
        message.setTransactionId(source.getTransactionId());
        message.setOperationId(source.getOperationId());
        message.setMerchantId(source.getMerchantId());
        message.setMerchantOrderNo(source.getMerchantOrderNo());
        message.setTransactionType(source.getTransactionType());
        message.setTransactionDateTime(source.getTransactionDateTime());
        message.setSourceEventNo("MSG-1");
        message.setExpectedClearingRevision(0);
        message.setClearingRetryCount(retryCount);
        message.setRetryReasonCode("SOURCE_CLEARING_PENDING");
        message.setDeliverAt(ClearingTimeSupport.toInstant(deliverAt));
        return message;
    }

    private PaymentTransactionEventMessage message() {
        PaymentTransactionEventMessage message = new PaymentTransactionEventMessage();
        message.setMessageId("MSG-1");
        message.setEventType(MqTag.TRANSACTION_STATUS_CHANGED);
        message.setTransactionId("TX-1");
        message.setOperationId("OP-1");
        message.setMerchantId("M-1");
        message.setMerchantOrderNo("ORDER-1");
        message.setTransactionType("PAYMENT");
        message.setTransactionDateTime(TRANSACTION_TIME);
        return message;
    }

    private ClearingTransactionOperationDO operation(String status) {
        ClearingTransactionOperationDO operation = new ClearingTransactionOperationDO();
        operation.setTransactionId("TX-1");
        operation.setOperationId("OP-1");
        operation.setMerchantId("M-1");
        operation.setMerchantOrderNo("ORDER-1");
        operation.setTransactionType("PAYMENT");
        operation.setTransactionStatus(status);
        operation.setLabelCurrency("USD");
        operation.setCurrencyExponent(2);
        operation.setTransactionDateTime(TRANSACTION_TIME);
        operation.setTransactionUtcTime(TRANSACTION_TIME.minusHours(8));
        operation.setTransactionTimeZone("Asia/Shanghai");
        operation.setVersion(3);
        return operation;
    }

    private ClearingTransactionFinanceStateDO financeState(String status, int version, int revision) {
        ClearingTransactionFinanceStateDO state = new ClearingTransactionFinanceStateDO();
        state.setId(101L);
        state.setFinanceStateId("FS-1");
        state.setTransactionId("TX-1");
        state.setOperationId("OP-1");
        state.setMerchantId("M-1");
        state.setLabelCurrency("USD");
        state.setTransactionType("PAYMENT");
        state.setClearingStatus(status);
        state.setClearingRevision(revision);
        state.setVersion(version);
        state.setTransactionDateTime(TRANSACTION_TIME);
        state.setTransactionUtcTime(TRANSACTION_TIME.minusHours(8));
        state.setTransactionTimeZone("Asia/Shanghai");
        return state;
    }
}
