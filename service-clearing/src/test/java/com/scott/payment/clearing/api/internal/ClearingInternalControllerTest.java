package com.scott.payment.clearing.api.internal;

import com.scott.payment.clearing.api.internal.dto.ClearingManagementDTOs.ReserveAdjustmentSubmitRequest;
import com.scott.payment.clearing.api.internal.dto.ClearingManagementDTOs.TierPeriodReplaySubmitRequest;
import com.scott.payment.clearing.service.ClearingCompensationService;
import com.scott.payment.clearing.service.ClearingManagementCommandService;
import com.scott.payment.clearing.service.ClearingQueryService;
import com.scott.payment.clearing.service.ReserveAdjustmentService;
import com.scott.payment.clearing.service.TierPeriodReplayService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证内部协议不会允许调用方另传保证金币种，且完整绑定状态版本。 */
class ClearingInternalControllerTest {

    @Test
    void reserveAdjustmentSubmitShouldBindFrozenReserveIdentity() {
        ReserveAdjustmentService adjustmentService = mock(ReserveAdjustmentService.class);
        when(adjustmentService.submit(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ReserveAdjustmentService.ReserveAdjustmentResult(
                        "RA-1", "PENDING_REVIEW", null, 0, 0L));
        ClearingInternalController controller = new ClearingInternalController(
                mock(ClearingQueryService.class), mock(ClearingManagementCommandService.class),
                mock(ClearingCompensationService.class), adjustmentService,
                mock(TierPeriodReplayService.class));
        ReserveAdjustmentSubmitRequest request = new ReserveAdjustmentSubmitRequest();
        request.setRequestKey("REQ-1");
        request.setReserveStateId("RS-1");
        request.setOriginalTransactionId("PAY-1");
        request.setOriginalTransactionDateTime(LocalDateTime.of(2026, 1, 1, 10, 0));
        request.setExpectedReserveStateVersion(3L);
        request.setDirection("DEBIT");
        request.setAdjustmentAmount(new BigDecimal("2.00"));
        request.setRequestedReleaseDate(LocalDate.of(2026, 12, 1));
        request.setReason("reserve shortfall correction");
        request.setSubmitOperator("admin-account:88/Clearing Operator");

        var response = controller.submitReserveAdjustment(request);

        ArgumentCaptor<ReserveAdjustmentService.SubmitCommand> captor =
                ArgumentCaptor.forClass(ReserveAdjustmentService.SubmitCommand.class);
        verify(adjustmentService).submit(captor.capture());
        assertThat(captor.getValue().expectedReserveStateVersion()).isEqualTo(3L);
        assertThat(captor.getValue().adjustmentAmount()).isEqualByComparingTo("2.00");
        assertThat(captor.getValue().direction().name()).isEqualTo("DEBIT");
        assertThat(response.getData().getStatus()).isEqualTo("PENDING_REVIEW");
    }

    @Test
    void tierPeriodReplaySubmitShouldBindImmutableFeeVersionAndMonthScope() {
        TierPeriodReplayService replayService = mock(TierPeriodReplayService.class);
        when(replayService.submit(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new TierPeriodReplayService.ReplayResult(
                        "TR-1", "PENDING_REVIEW", 0, 0, 0L));
        ClearingInternalController controller = new ClearingInternalController(
                mock(ClearingQueryService.class), mock(ClearingManagementCommandService.class),
                mock(ClearingCompensationService.class), mock(ReserveAdjustmentService.class), replayService);
        TierPeriodReplaySubmitRequest request = new TierPeriodReplaySubmitRequest();
        request.setRequestKey("REQ-TIER-1");
        request.setMerchantId("M-1");
        request.setFeePlanId(10L);
        request.setFeePlanVersionId(11L);
        request.setTriggerFeeRuleId(101L);
        request.setPeriodKey("202608");
        request.setReason("immutable tier correction");
        request.setSubmitOperator("admin-account:88/Clearing Operator");

        var response = controller.submitTierPeriodReplay(request);

        ArgumentCaptor<TierPeriodReplayService.SubmitCommand> captor =
                ArgumentCaptor.forClass(TierPeriodReplayService.SubmitCommand.class);
        verify(replayService).submit(captor.capture());
        assertThat(captor.getValue().feePlanId()).isEqualTo(10L);
        assertThat(captor.getValue().feePlanVersionId()).isEqualTo(11L);
        assertThat(captor.getValue().triggerFeeRuleId()).isEqualTo(101L);
        assertThat(captor.getValue().periodKey()).isEqualTo("202608");
        assertThat(response.getData().getStatus()).isEqualTo("PENDING_REVIEW");
    }
}
