package com.scott.payment.admin.application.transaction;

import com.scott.payment.admin.client.clearing.ClearingInternalClient;
import com.scott.payment.admin.service.AdminClearingQueryService;
import com.scott.payment.admin.service.AdminClearingFeeVersionQueryService;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.ActionRequest;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.CommandResponse;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.InternalActionRequest;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.InternalRecalculateRequest;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.RecalculateRequest;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.RecalculateBatchItem;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.RecalculateBatchRequest;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.RecalculateBatchResponse;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.Summary;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.ReserveAdjustmentSubmitRequest;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.InternalReserveAdjustmentSubmitRequest;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.InternalReserveAdjustmentReviewRequest;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.ReserveAdjustmentReviewRequest;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.InternalTierPeriodReplayReviewRequest;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.InternalTierPeriodReplaySubmitRequest;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.TierPeriodReplayReviewRequest;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.TierPeriodReplaySubmitRequest;
import com.scott.payment.admin.dto.transaction.AdminClearingDTOs.SearchRequest;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.exception.ServiceException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class AdminClearingApplicationServiceTest {

    @AfterEach
    void clearContext() {
        InternalAuthContextHolder.clear();
    }

    @Test
    void searchAndDetailShouldUseAdminLocalQueryServiceOnly() {
        ClearingInternalClient client = mock(ClearingInternalClient.class);
        AdminClearingQueryService queryService = mock(AdminClearingQueryService.class);
        AdminClearingApplicationService service = new AdminClearingApplicationService(
                client, queryService, mock(AdminClearingFeeVersionQueryService.class));
        InternalAuthAccount account = new InternalAuthAccount();
        account.setAccountId(88L);
        account.setLoginAccount("ops@example.com");
        InternalAuthContextHolder.set(account);
        SearchRequest request = new SearchRequest();
        LocalDateTime transactionTime = LocalDateTime.of(2026, 8, 25, 9, 0);

        service.search(request);
        service.detail(" TX-1 ", transactionTime);

        verify(queryService).search(request);
        verify(queryService).detail("TX-1", transactionTime);
        verifyNoInteractions(client);
    }

    @Test
    void retryAndRecalculateShouldDeriveTrustedOperatorFromLoginContext() {
        ClearingInternalClient client = mock(ClearingInternalClient.class);
        AdminClearingApplicationService service = new AdminClearingApplicationService(
                client, mock(AdminClearingQueryService.class),
                mock(AdminClearingFeeVersionQueryService.class));
        InternalAuthAccount account = new InternalAuthAccount();
        account.setAccountId(88L);
        account.setLoginAccount("ops@example.com");
        account.setRealName("Clearing Operator");
        InternalAuthContextHolder.set(account);
        LocalDateTime transactionTime = LocalDateTime.of(2026, 8, 25, 9, 0);
        ActionRequest retry = new ActionRequest();
        retry.setTransactionDateTime(transactionTime);
        retry.setExpectedVersion(3);
        retry.setReason(" retry after review ");

        service.retry("TX-1", retry);

        ArgumentCaptor<InternalActionRequest> retryCaptor = ArgumentCaptor.forClass(InternalActionRequest.class);
        verify(client).retry(org.mockito.ArgumentMatchers.eq("TX-1"), retryCaptor.capture());
        assertThat(retryCaptor.getValue().getOperator())
                .isEqualTo("admin-account:88/Clearing Operator");
        assertThat(retryCaptor.getValue().getReason()).isEqualTo("retry after review");

        RecalculateRequest recalculate = new RecalculateRequest();
        recalculate.setTransactionDateTime(transactionTime);
        recalculate.setExpectedVersion(3);
        recalculate.setExpectedClearingRevision(1);
        recalculate.setTargetFeePlanId(20L);
        recalculate.setTargetFeePlanVersionId(21L);
        recalculate.setReason(" approved fee correction ");

        service.recalculate("TX-1", recalculate);

        ArgumentCaptor<InternalRecalculateRequest> recalculateCaptor =
                ArgumentCaptor.forClass(InternalRecalculateRequest.class);
        verify(client).recalculate(org.mockito.ArgumentMatchers.eq("TX-1"), recalculateCaptor.capture());
        assertThat(recalculateCaptor.getValue().getOperator())
                .isEqualTo("admin-account:88/Clearing Operator");
        assertThat(recalculateCaptor.getValue().getReason()).isEqualTo("approved fee correction");
        assertThat(recalculateCaptor.getValue().getTargetFeePlanVersionId()).isEqualTo(21L);
    }

    @Test
    void batchRecalculateShouldKeepPerRecordCasAndReportPartialFailures() {
        ClearingInternalClient client = mock(ClearingInternalClient.class);
        AdminClearingQueryService queryService = mock(AdminClearingQueryService.class);
        AdminClearingApplicationService service = new AdminClearingApplicationService(
                client, queryService, mock(AdminClearingFeeVersionQueryService.class));
        InternalAuthAccount account = new InternalAuthAccount();
        account.setAccountId(88L);
        account.setLoginAccount("ops@example.com");
        account.setRealName("Clearing Operator");
        InternalAuthContextHolder.set(account);
        LocalDateTime time1 = LocalDateTime.of(2026, 8, 25, 9, 0);
        LocalDateTime time2 = LocalDateTime.of(2026, 8, 25, 9, 1);
        RecalculateBatchRequest request = new RecalculateBatchRequest();
        request.setTargetFeePlanId(20L);
        request.setTargetFeePlanVersionId(21L);
        request.setReason(" approved batch correction ");
        request.setRecords(List.of(batchItem("TX-1", time1, 3, 1),
                batchItem("TX-2", time2, 4, 2)));
        when(queryService.findByReferences(any())).thenReturn(List.of(
                summary("TX-1", time1, 3, 1), summary("TX-2", time2, 4, 2)));
        CommandResponse completed = new CommandResponse();
        completed.setResult("COMPLETED");
        when(client.recalculate(eq("TX-1"), any())).thenReturn(completed);
        when(client.recalculate(eq("TX-2"), any()))
                .thenThrow(new ServiceException("409", "stale clearing state"));

        RecalculateBatchResponse response = service.batchRecalculate(request);

        assertThat(response.getRequestedCount()).isEqualTo(2);
        assertThat(response.getSuccessCount()).isEqualTo(1);
        assertThat(response.getFailureCount()).isEqualTo(1);
        assertThat(response.getResults()).extracting("transactionId", "success", "result")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("TX-1", true, "COMPLETED"),
                        org.assertj.core.groups.Tuple.tuple("TX-2", false, "FAILED"));
        ArgumentCaptor<InternalRecalculateRequest> commandCaptor =
                ArgumentCaptor.forClass(InternalRecalculateRequest.class);
        verify(client).recalculate(eq("TX-1"), commandCaptor.capture());
        assertThat(commandCaptor.getValue().getExpectedVersion()).isEqualTo(3);
        assertThat(commandCaptor.getValue().getExpectedClearingRevision()).isEqualTo(1);
        assertThat(commandCaptor.getValue().getOperator())
                .isEqualTo("admin-account:88/Clearing Operator");
    }

    @Test
    void batchRecalculateShouldRejectSingleRecordRequests() {
        ClearingInternalClient client = mock(ClearingInternalClient.class);
        AdminClearingQueryService queryService = mock(AdminClearingQueryService.class);
        AdminClearingApplicationService service = new AdminClearingApplicationService(
                client, queryService, mock(AdminClearingFeeVersionQueryService.class));
        RecalculateBatchRequest request = new RecalculateBatchRequest();
        request.setTargetFeePlanId(20L);
        request.setTargetFeePlanVersionId(21L);
        request.setReason("not a batch");
        request.setRecords(List.of(batchItem("TX-1",
                LocalDateTime.of(2026, 8, 25, 9, 0), 3, 1)));

        assertThatThrownBy(() -> service.batchRecalculate(request))
                .isInstanceOf(ServiceException.class);
        verifyNoInteractions(client, queryService);
    }

    private RecalculateBatchItem batchItem(String transactionId, LocalDateTime time,
                                           int version, int revision) {
        RecalculateBatchItem item = new RecalculateBatchItem();
        item.setTransactionId(transactionId);
        item.setTransactionDateTime(time);
        item.setExpectedVersion(version);
        item.setExpectedClearingRevision(revision);
        return item;
    }

    private Summary summary(String transactionId, LocalDateTime time, int version, int revision) {
        Summary summary = new Summary();
        summary.setTransactionId(transactionId);
        summary.setTransactionDateTime(time);
        summary.setMerchantId("M-1");
        summary.setFeePlanId(20L);
        summary.setClearingStatus("CLEARED");
        summary.setSettlementStatus("NOT_SETTLED");
        summary.setClearingRevision(revision);
        summary.setVersion(version);
        return summary;
    }

    @Test
    void reserveAdjustmentSubmitShouldDeriveTrustedOperatorFromLoginContext() {
        ClearingInternalClient client = mock(ClearingInternalClient.class);
        AdminClearingApplicationService service = new AdminClearingApplicationService(
                client, mock(AdminClearingQueryService.class),
                mock(AdminClearingFeeVersionQueryService.class));
        InternalAuthAccount account = new InternalAuthAccount();
        account.setAccountId(88L);
        account.setLoginAccount("ops@example.com");
        account.setRealName("Clearing Operator");
        InternalAuthContextHolder.set(account);
        ReserveAdjustmentSubmitRequest request = new ReserveAdjustmentSubmitRequest();
        request.setRequestKey("REQ-1");
        request.setReserveStateId("RS-1");
        request.setOriginalTransactionId("PAY-1");
        request.setOriginalTransactionDateTime(LocalDateTime.of(2026, 1, 1, 10, 0));
        request.setExpectedReserveStateVersion(3L);
        request.setDirection("DEBIT");
        request.setAdjustmentAmount(new BigDecimal("2.00"));
        request.setRequestedReleaseDate(LocalDate.of(2026, 12, 1));
        request.setReason(" reserve shortfall correction ");

        service.submitReserveAdjustment(request);

        ArgumentCaptor<InternalReserveAdjustmentSubmitRequest> captor =
                ArgumentCaptor.forClass(InternalReserveAdjustmentSubmitRequest.class);
        verify(client).submitReserveAdjustment(captor.capture());
        assertThat(captor.getValue().getSubmitOperator())
                .isEqualTo("admin-account:88/Clearing Operator");
        assertThat(captor.getValue().getReason()).isEqualTo("reserve shortfall correction");
        assertThat(captor.getValue().getAdjustmentAmount()).isEqualByComparingTo("2.00");
    }

    @Test
    void reserveAdjustmentReviewShouldDeriveTrustedReviewerAndNormalizeDecision() {
        ClearingInternalClient client = mock(ClearingInternalClient.class);
        AdminClearingApplicationService service = new AdminClearingApplicationService(
                client, mock(AdminClearingQueryService.class),
                mock(AdminClearingFeeVersionQueryService.class));
        InternalAuthAccount account = new InternalAuthAccount();
        account.setAccountId(99L);
        account.setLoginAccount("reviewer@example.com");
        account.setRealName("Reserve Reviewer");
        InternalAuthContextHolder.set(account);
        ReserveAdjustmentReviewRequest request = new ReserveAdjustmentReviewRequest();
        request.setExpectedRequestVersion(0L);
        request.setDecision(" approve ");
        request.setReviewComment(" approved after evidence review ");

        service.reviewReserveAdjustment(" RA-1 ", request);

        ArgumentCaptor<InternalReserveAdjustmentReviewRequest> captor =
                ArgumentCaptor.forClass(InternalReserveAdjustmentReviewRequest.class);
        verify(client).reviewReserveAdjustment(org.mockito.ArgumentMatchers.eq("RA-1"), captor.capture());
        assertThat(captor.getValue().getReviewOperator())
                .isEqualTo("admin-account:99/Reserve Reviewer");
        assertThat(captor.getValue().getDecision()).isEqualTo("APPROVE");
        assertThat(captor.getValue().getReviewComment()).isEqualTo("approved after evidence review");
    }

    @Test
    void tierPeriodReplaySubmitShouldDeriveTrustedOperatorAndNormalizeScope() {
        ClearingInternalClient client = mock(ClearingInternalClient.class);
        AdminClearingApplicationService service = new AdminClearingApplicationService(
                client, mock(AdminClearingQueryService.class),
                mock(AdminClearingFeeVersionQueryService.class));
        InternalAuthAccount account = new InternalAuthAccount();
        account.setAccountId(88L);
        account.setLoginAccount("ops@example.com");
        account.setRealName("Clearing Operator");
        InternalAuthContextHolder.set(account);
        TierPeriodReplaySubmitRequest request = new TierPeriodReplaySubmitRequest();
        request.setRequestKey(" REQ-TIER-1 ");
        request.setMerchantId(" M-1 ");
        request.setFeePlanId(10L);
        request.setFeePlanVersionId(11L);
        request.setTriggerFeeRuleId(101L);
        request.setPeriodKey("202608");
        request.setReason(" immutable tier correction ");

        service.submitTierPeriodReplay(request);

        ArgumentCaptor<InternalTierPeriodReplaySubmitRequest> captor =
                ArgumentCaptor.forClass(InternalTierPeriodReplaySubmitRequest.class);
        verify(client).submitTierPeriodReplay(captor.capture());
        assertThat(captor.getValue().getSubmitOperator())
                .isEqualTo("admin-account:88/Clearing Operator");
        assertThat(captor.getValue().getRequestKey()).isEqualTo("REQ-TIER-1");
        assertThat(captor.getValue().getMerchantId()).isEqualTo("M-1");
        assertThat(captor.getValue().getReason()).isEqualTo("immutable tier correction");
    }

    @Test
    void tierPeriodReplayReviewShouldDeriveTrustedReviewerAndNormalizeDecision() {
        ClearingInternalClient client = mock(ClearingInternalClient.class);
        AdminClearingApplicationService service = new AdminClearingApplicationService(
                client, mock(AdminClearingQueryService.class),
                mock(AdminClearingFeeVersionQueryService.class));
        InternalAuthAccount account = new InternalAuthAccount();
        account.setAccountId(99L);
        account.setLoginAccount("reviewer@example.com");
        account.setRealName("Tier Reviewer");
        InternalAuthContextHolder.set(account);
        TierPeriodReplayReviewRequest request = new TierPeriodReplayReviewRequest();
        request.setExpectedRequestVersion(0L);
        request.setDecision(" approve ");
        request.setReviewComment(" approved immutable version replay ");

        service.reviewTierPeriodReplay(" TR-1 ", request);

        ArgumentCaptor<InternalTierPeriodReplayReviewRequest> captor =
                ArgumentCaptor.forClass(InternalTierPeriodReplayReviewRequest.class);
        verify(client).reviewTierPeriodReplay(org.mockito.ArgumentMatchers.eq("TR-1"), captor.capture());
        assertThat(captor.getValue().getReviewOperator())
                .isEqualTo("admin-account:99/Tier Reviewer");
        assertThat(captor.getValue().getDecision()).isEqualTo("APPROVE");
        assertThat(captor.getValue().getReviewComment())
                .isEqualTo("approved immutable version replay");
    }
}
