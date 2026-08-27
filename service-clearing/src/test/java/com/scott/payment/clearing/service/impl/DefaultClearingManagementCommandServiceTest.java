package com.scott.payment.clearing.service.impl;

import com.scott.payment.clearing.api.internal.dto.ClearingManagementDTOs.ClearingCommandResponse;
import com.scott.payment.clearing.api.internal.dto.ClearingManagementDTOs.ClearingRetryRequest;
import com.scott.payment.clearing.api.internal.dto.ClearingManagementDTOs.ClearingReviewRequest;
import com.scott.payment.clearing.domain.state.ClearingAnomalyTypeEnum;
import com.scott.payment.clearing.domain.state.ClearingStateEnum;
import com.scott.payment.clearing.entity.ClearingTransactionEventOutboxDO;
import com.scott.payment.clearing.entity.ClearingTransactionFinanceStateDO;
import com.scott.payment.clearing.entity.ClearingTransactionOperationDO;
import com.scott.payment.clearing.mapper.ClearingTransactionEventOutboxMapper;
import com.scott.payment.clearing.mapper.ClearingTransactionFinanceStateMapper;
import com.scott.payment.clearing.mapper.ClearingTransactionOperationMapper;
import com.scott.payment.clearing.service.ClearingAnomalyService;
import com.scott.payment.clearing.service.ClearingProjectionService;
import com.scott.payment.clearing.service.ClearingRecalculationService;
import com.scott.payment.clearing.support.ClearingOperationalMetrics;
import com.scott.payment.component.mq.constant.MqTag;
import com.scott.payment.component.mq.constant.MqTopic;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultClearingManagementCommandServiceTest {

    private static final LocalDateTime TRANSACTION_TIME = LocalDateTime.of(2026, 8, 25, 9, 0);
    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 8, 26, 6, 0, 0, 123_456_789);
    private static final LocalDateTime DELIVER_AT =
            NOW.plusMinutes(1).truncatedTo(ChronoUnit.MILLIS);

    @Test
    void retryShouldUseShardVersionCasAndPersistScheduledOutbox() {
        Dependencies dependencies = new Dependencies();
        DefaultClearingManagementCommandService service = dependencies.service();
        when(dependencies.financeMapper.selectForUpdate("TX-1", TRANSACTION_TIME))
                .thenReturn(state("FAILED"));
        when(dependencies.operationMapper.selectByTransaction("TX-1", TRANSACTION_TIME))
                .thenReturn(operation());
        when(dependencies.financeMapper.scheduleManualRetry(
                "TX-1", TRANSACTION_TIME, 3, 2, DELIVER_AT, "admin-1: retry after config repair", NOW))
                .thenReturn(1);
        when(dependencies.outboxMapper.insertLogical(any())).thenReturn(1);

        ClearingRetryRequest request = new ClearingRetryRequest();
        request.setTransactionDateTime(TRANSACTION_TIME);
        request.setExpectedVersion(3);
        request.setOperator("admin-1");
        request.setReason("retry after config repair");

        ClearingCommandResponse response = service.retry("TX-1", request);

        ArgumentCaptor<ClearingTransactionEventOutboxDO> captor =
                ArgumentCaptor.forClass(ClearingTransactionEventOutboxDO.class);
        verify(dependencies.outboxMapper).insertLogical(captor.capture());
        ClearingTransactionEventOutboxDO outbox = captor.getValue();
        assertThat(outbox.getTopic()).isEqualTo(MqTopic.PAYMENT_CLEARING_DELAY);
        assertThat(outbox.getTag()).isEqualTo(MqTag.TRANSACTION_CLEARING_RETRY_DUE);
        assertThat(outbox.getDeliveryMode()).isEqualTo("SCHEDULED");
        assertThat(outbox.getDeliverAt()).isEqualTo(DELIVER_AT);
        assertThat(outbox.getTransactionDateTime()).isEqualTo(TRANSACTION_TIME);
        assertThat(response.getResult()).isEqualTo("SCHEDULED");
        assertThat(response.getVersion()).isEqualTo(4);
        verify(dependencies.projectionService).updateResolvingLocator(
                any(), org.mockito.ArgumentMatchers.eq(ClearingStateEnum.FAILED),
                org.mockito.ArgumentMatchers.eq("CLEARING_MANUAL_RETRY"),
                org.mockito.ArgumentMatchers.eq(NOW));
        verify(dependencies.metrics).recordCommand("RETRY", "SCHEDULED");
    }

    @Test
    void retryShouldFailWhenDuplicateOutboxHasNoMatchingPersistedEvent() {
        Dependencies dependencies = new Dependencies();
        DefaultClearingManagementCommandService service = dependencies.service();
        when(dependencies.financeMapper.selectForUpdate("TX-1", TRANSACTION_TIME))
                .thenReturn(state("FAILED"));
        when(dependencies.operationMapper.selectByTransaction("TX-1", TRANSACTION_TIME))
                .thenReturn(operation());
        when(dependencies.financeMapper.scheduleManualRetry(
                "TX-1", TRANSACTION_TIME, 3, 2, DELIVER_AT,
                "admin-1: retry after config repair", NOW)).thenReturn(1);
        when(dependencies.outboxMapper.insertLogical(any())).thenReturn(0);

        ClearingRetryRequest request = new ClearingRetryRequest();
        request.setTransactionDateTime(TRANSACTION_TIME);
        request.setExpectedVersion(3);
        request.setOperator("admin-1");
        request.setReason("retry after config repair");

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> service.retry("TX-1", request))
                .withMessageContaining("outbox");
        verify(dependencies.projectionService, never()).updateResolvingLocator(any(), any(), any(), any());
        verify(dependencies.metrics, never()).recordCommand(anyString(), anyString());
    }

    @Test
    void retryShouldAcceptOnlyAnExactExistingOutboxIdentity() {
        Dependencies dependencies = new Dependencies();
        DefaultClearingManagementCommandService service = dependencies.service();
        AtomicReference<ClearingTransactionEventOutboxDO> persisted = new AtomicReference<>();
        when(dependencies.financeMapper.selectForUpdate("TX-1", TRANSACTION_TIME))
                .thenReturn(state("FAILED"));
        when(dependencies.operationMapper.selectByTransaction("TX-1", TRANSACTION_TIME))
                .thenReturn(operation());
        when(dependencies.financeMapper.scheduleManualRetry(
                "TX-1", TRANSACTION_TIME, 3, 2, DELIVER_AT,
                "admin-1: retry after config repair", NOW)).thenReturn(1);
        when(dependencies.outboxMapper.selectByEventNoForUpdate(any(), any()))
                .thenAnswer(invocation -> persisted.get());
        when(dependencies.outboxMapper.insertLogical(any())).thenAnswer(invocation -> {
            persisted.set(invocation.getArgument(0));
            return 1;
        });

        ClearingRetryRequest request = new ClearingRetryRequest();
        request.setTransactionDateTime(TRANSACTION_TIME);
        request.setExpectedVersion(3);
        request.setOperator("admin-1");
        request.setReason("retry after config repair");

        assertThat(service.retry("TX-1", request).getResult()).isEqualTo("SCHEDULED");
        assertThat(service.retry("TX-1", request).getResult()).isEqualTo("ALREADY_SCHEDULED");
        persisted.get().setPayloadJson("{\"messageId\":\"other\"}");
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> service.retry("TX-1", request))
                .withMessageContaining("identity");

        verify(dependencies.outboxMapper, times(1)).insertLogical(any());
    }

    @Test
    void reviewShouldEscalateWithAnomalyAndRejectCompletedState() {
        Dependencies dependencies = new Dependencies();
        DefaultClearingManagementCommandService service = dependencies.service();
        when(dependencies.financeMapper.selectForUpdate("TX-1", TRANSACTION_TIME))
                .thenReturn(state("PENDING"));
        when(dependencies.operationMapper.selectByTransaction("TX-1", TRANSACTION_TIME))
                .thenReturn(operation());
        when(dependencies.financeMapper.markManualReview(
                "TX-1", TRANSACTION_TIME, 3, "MANUAL_REVIEW_REQUESTED",
                "admin-2: verify merchant fee version", NOW)).thenReturn(1);

        ClearingReviewRequest request = new ClearingReviewRequest();
        request.setTransactionDateTime(TRANSACTION_TIME);
        request.setExpectedVersion(3);
        request.setOperator("admin-2");
        request.setReason("verify merchant fee version");

        ClearingCommandResponse response = service.review("TX-1", request);

        verify(dependencies.anomalyService).record(
                any(), org.mockito.ArgumentMatchers.eq("FS-1"), org.mockito.ArgumentMatchers.eq(1),
                org.mockito.ArgumentMatchers.eq(ClearingAnomalyTypeEnum.MANUAL_REVIEW),
                org.mockito.ArgumentMatchers.eq("MANUAL_REVIEW_REQUESTED"),
                org.mockito.ArgumentMatchers.eq("admin-2: verify merchant fee version"),
                org.mockito.ArgumentMatchers.eq(NOW));
        verify(dependencies.projectionService).updateResolvingLocator(
                any(), org.mockito.ArgumentMatchers.eq(ClearingStateEnum.MANUAL_REVIEW),
                org.mockito.ArgumentMatchers.eq("MANUAL_REVIEW_REQUESTED"),
                org.mockito.ArgumentMatchers.eq(NOW));
        assertThat(response.getClearingStatus()).isEqualTo("MANUAL_REVIEW");

        Dependencies completedDependencies = new Dependencies();
        when(completedDependencies.financeMapper.selectForUpdate("TX-1", TRANSACTION_TIME))
                .thenReturn(state("CLEARED"));
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> completedDependencies.service().review("TX-1", request))
                .withMessageContaining("does not allow");
        verify(completedDependencies.financeMapper, never()).markManualReview(
                anyString(), any(), anyInt(), anyString(), anyString(), any());
    }

    private ClearingTransactionFinanceStateDO state(String status) {
        ClearingTransactionFinanceStateDO row = new ClearingTransactionFinanceStateDO();
        row.setFinanceStateId("FS-1");
        row.setTransactionId("TX-1");
        row.setOperationId("OP-1");
        row.setMerchantId("M-1");
        row.setClearingStatus(status);
        row.setClearingRevision(1);
        row.setClearingRetryCount(1);
        row.setSettlementStatus("NOT_SETTLED");
        row.setTransactionDateTime(TRANSACTION_TIME);
        row.setVersion(3);
        return row;
    }

    private ClearingTransactionOperationDO operation() {
        ClearingTransactionOperationDO row = new ClearingTransactionOperationDO();
        row.setTransactionId("TX-1");
        row.setOperationId("OP-1");
        row.setMerchantId("M-1");
        row.setMerchantOrderNo("ORDER-1");
        row.setTransactionType("PAYMENT");
        row.setTransactionStatus("SUCCESS");
        row.setLabelCurrency("USD");
        row.setLabelAmount(new BigDecimal("100.00"));
        row.setApprovedCurrency("USD");
        row.setApprovedAmount(new BigDecimal("100.00"));
        row.setTransactionCurrency("USD");
        row.setTransactionAmount(new BigDecimal("100.00"));
        row.setCurrencyExponent(2);
        row.setTransactionDateTime(TRANSACTION_TIME);
        row.setTransactionUtcTime(TRANSACTION_TIME.minusHours(8));
        row.setTransactionTimeZone("Asia/Shanghai");
        row.setVersion(7);
        return row;
    }

    private static final class Dependencies {
        private final ClearingTransactionFinanceStateMapper financeMapper =
                mock(ClearingTransactionFinanceStateMapper.class);
        private final ClearingTransactionOperationMapper operationMapper =
                mock(ClearingTransactionOperationMapper.class);
        private final ClearingTransactionEventOutboxMapper outboxMapper =
                mock(ClearingTransactionEventOutboxMapper.class);
        private final ClearingProjectionService projectionService = mock(ClearingProjectionService.class);
        private final ClearingRecalculationService recalculationService = mock(ClearingRecalculationService.class);
        private final ClearingOperationalMetrics metrics = mock(ClearingOperationalMetrics.class);
        private final ClearingAnomalyService anomalyService = mock(ClearingAnomalyService.class);

        private DefaultClearingManagementCommandService service() {
            Clock clock = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
            return new DefaultClearingManagementCommandService(
                    financeMapper, operationMapper, outboxMapper, projectionService,
                    recalculationService, metrics, anomalyService, clock);
        }
    }
}
