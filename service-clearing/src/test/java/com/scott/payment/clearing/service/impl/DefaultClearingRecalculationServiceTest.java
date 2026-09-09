package com.scott.payment.clearing.service.impl;

import com.scott.payment.clearing.api.internal.dto.ClearingManagementDTOs.ClearingCommandResponse;
import com.scott.payment.clearing.api.internal.dto.ClearingManagementDTOs.ClearingRecalculateRequest;
import com.scott.payment.clearing.domain.model.ClearingCompletionModels.CompletionCommand;
import com.scott.payment.clearing.domain.model.ClearingCompletionModels.CompletionResult;
import com.scott.payment.clearing.entity.ClearingTransactionFinanceStateDO;
import com.scott.payment.clearing.entity.ClearingTransactionMerchantSnapshotDO;
import com.scott.payment.clearing.entity.ClearingTransactionOperationDO;
import com.scott.payment.clearing.mapper.ClearingTransactionFinanceStateMapper;
import com.scott.payment.clearing.mapper.ClearingTransactionMerchantSnapshotMapper;
import com.scott.payment.clearing.mapper.ClearingTransactionOperationMapper;
import com.scott.payment.clearing.service.ClearingCompletionService;
import com.scott.payment.clearing.service.ClearingPreparationService;
import com.scott.payment.clearing.service.FeeConfigurationSnapshotService;
import com.scott.payment.component.mq.message.PaymentTransactionEventMessage;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.FeeVersionSnapshot;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultClearingRecalculationServiceTest
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 验证清分重算显式锁定费用版本、传递期望 CAS 状态并拒绝已结算或过期事实
 * @status : create
 */
class DefaultClearingRecalculationServiceTest {

    private static final LocalDateTime TRANSACTION_TIME = LocalDateTime.of(2026, 8, 25, 9, 0);
    private static final LocalDateTime SNAPSHOT_TIME = LocalDateTime.of(2026, 8, 25, 8, 59);

    @Test
    void recalculateShouldLoadExplicitVersionAndForwardExpectedCasState() {
        Dependencies dependencies = new Dependencies();
        DefaultClearingRecalculationService service = dependencies.service();
        ClearingTransactionFinanceStateDO state = state("CLEARED", "NOT_SETTLED");
        ClearingTransactionOperationDO operation = operation();
        ClearingTransactionMerchantSnapshotDO originalSnapshot = snapshot();
        FeeVersionSnapshot targetSnapshot = mock(FeeVersionSnapshot.class);
        CompletionCommand command = mock(CompletionCommand.class);
        when(dependencies.financeMapper.selectByTransaction("TX-1", TRANSACTION_TIME)).thenReturn(state);
        when(dependencies.operationMapper.selectByTransaction("TX-1", TRANSACTION_TIME)).thenReturn(operation);
        when(dependencies.snapshotMapper.selectByTransaction("TX-1", TRANSACTION_TIME)).thenReturn(originalSnapshot);
        when(dependencies.snapshotService.loadForRecalculation("M-1", 20L, 21L, SNAPSHOT_TIME))
                .thenReturn(targetSnapshot);
        when(dependencies.preparationService.prepareForRecalculation(
                any(PaymentTransactionEventMessage.class), any(),
                org.mockito.ArgumentMatchers.eq("manual-recalc:admin-1"),
                org.mockito.ArgumentMatchers.same(targetSnapshot))).thenReturn(command);
        when(dependencies.completionService.recalculate(command, 3, 1,
                LocalDateTime.of(2026, 8, 26, 6, 0)))
                .thenReturn(new CompletionResult("CLEARED", 2, 3, 0));

        ClearingCommandResponse response = service.recalculate("TX-1", request());

        ArgumentCaptor<PaymentTransactionEventMessage> messageCaptor =
                ArgumentCaptor.forClass(PaymentTransactionEventMessage.class);
        verify(dependencies.preparationService).prepareForRecalculation(
                messageCaptor.capture(), any(), anyString(),
                org.mockito.ArgumentMatchers.same(targetSnapshot));
        assertThat(messageCaptor.getValue().getMessageId()).isEqualTo("RECALC:FS-1:1:21");
        assertThat(messageCaptor.getValue().getTransactionDateTime()).isEqualTo(TRANSACTION_TIME);
        assertThat(response.getClearingRevision()).isEqualTo(2);
        assertThat(response.getVersion()).isEqualTo(4);
        assertThat(response.getResult()).isEqualTo("COMPLETED");
    }

    @Test
    void recalculateShouldRejectSettledOrStaleFactsBeforeLoadingTargetVersion() {
        Dependencies dependencies = new Dependencies();
        DefaultClearingRecalculationService service = dependencies.service();
        when(dependencies.financeMapper.selectByTransaction("TX-1", TRANSACTION_TIME))
                .thenReturn(state("CLEARED", "SETTLED"));
        when(dependencies.operationMapper.selectByTransaction("TX-1", TRANSACTION_TIME)).thenReturn(operation());
        when(dependencies.snapshotMapper.selectByTransaction("TX-1", TRANSACTION_TIME)).thenReturn(snapshot());

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> service.recalculate("TX-1", request()))
                .withMessageContaining("stale or already settled");
        verify(dependencies.snapshotService, never()).loadForRecalculation(anyString(), any(), any(), any());
        verify(dependencies.completionService, never()).recalculate(any(), any(Integer.class), any(Integer.class), any());
    }

    private ClearingRecalculateRequest request() {
        ClearingRecalculateRequest request = new ClearingRecalculateRequest();
        request.setTransactionDateTime(TRANSACTION_TIME);
        request.setExpectedVersion(3);
        request.setExpectedClearingRevision(1);
        request.setTargetFeePlanId(20L);
        request.setTargetFeePlanVersionId(21L);
        request.setOperator("admin-1");
        request.setReason("approved fee correction");
        return request;
    }

    private ClearingTransactionFinanceStateDO state(String clearingStatus, String settlementStatus) {
        ClearingTransactionFinanceStateDO row = new ClearingTransactionFinanceStateDO();
        row.setFinanceStateId("FS-1");
        row.setTransactionId("TX-1");
        row.setOperationId("OP-1");
        row.setMerchantId("M-1");
        row.setClearingStatus(clearingStatus);
        row.setClearingRevision(1);
        row.setSettlementStatus(settlementStatus);
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

    private ClearingTransactionMerchantSnapshotDO snapshot() {
        ClearingTransactionMerchantSnapshotDO row = new ClearingTransactionMerchantSnapshotDO();
        row.setTransactionId("TX-1");
        row.setOperationId("OP-1");
        row.setMerchantId("M-1");
        row.setFeeSnapshotTime(SNAPSHOT_TIME);
        row.setTransactionDateTime(TRANSACTION_TIME);
        return row;
    }

    private static final class Dependencies {
        private final ClearingTransactionFinanceStateMapper financeMapper =
                mock(ClearingTransactionFinanceStateMapper.class);
        private final ClearingTransactionOperationMapper operationMapper =
                mock(ClearingTransactionOperationMapper.class);
        private final ClearingTransactionMerchantSnapshotMapper snapshotMapper =
                mock(ClearingTransactionMerchantSnapshotMapper.class);
        private final FeeConfigurationSnapshotService snapshotService = mock(FeeConfigurationSnapshotService.class);
        private final ClearingPreparationService preparationService = mock(ClearingPreparationService.class);
        private final ClearingCompletionService completionService = mock(ClearingCompletionService.class);

        private DefaultClearingRecalculationService service() {
            Clock clock = Clock.fixed(Instant.parse("2026-08-26T06:00:00Z"), ZoneOffset.UTC);
            return new DefaultClearingRecalculationService(
                    financeMapper, operationMapper, snapshotMapper, snapshotService,
                    preparationService, completionService, clock);
        }
    }
}
