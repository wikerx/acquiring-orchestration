package com.scott.payment.settlement.api.internal;

import com.scott.payment.settlement.api.internal.dto.SettlementManagementDTOs.ReviewCandidateReference;
import com.scott.payment.settlement.api.internal.dto.SettlementManagementDTOs.ReviewDecisionRequest;
import com.scott.payment.settlement.api.internal.dto.SettlementManagementDTOs.ReviewDecisionTaskResumeRequest;
import com.scott.payment.settlement.api.internal.dto.SettlementManagementDTOs.ReviewSubmitRequest;
import com.scott.payment.settlement.api.internal.dto.SettlementManagementDTOs.ManualReviewPreviewRequest;
import com.scott.payment.settlement.api.internal.dto.SettlementManagementDTOs.ManualReviewStartRequest;
import com.scott.payment.settlement.application.SettlementManualReviewApplicationService;
import com.scott.payment.settlement.application.SettlementReviewDecisionApplicationService;
import com.scott.payment.settlement.application.SettlementReviewOrderApplicationService;
import com.scott.payment.settlement.dto.SettlementManualReviewModels.PreviewCommand;
import com.scott.payment.settlement.dto.SettlementManualReviewModels.StartCommand;
import com.scott.payment.settlement.dto.SettlementManualReviewModels.TaskResult;
import com.scott.payment.settlement.dto.SettlementReviewCommandResult;
import com.scott.payment.settlement.dto.SettlementReviewCreateCommand;
import com.scott.payment.settlement.dto.SettlementReviewDecisionCommand;
import com.scott.payment.settlement.dto.SettlementCommandAudit;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementReviewInternalControllerContractTest
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 验证预审命令只暴露在版本化内部边界并完整接收可信操作人快照。
 * @status : create
 */
class SettlementReviewInternalControllerContractTest {

    @Test
    void routesShouldRemainUnderSignedInternalReviewBoundary() {
        RequestMapping root = SettlementReviewInternalController.class.getAnnotation(RequestMapping.class);
        assertThat(root.value()).containsExactly("/internal/settlement/v1/reviews");
        PostMapping submit = method("submit").getAnnotation(PostMapping.class);
        PostMapping decide = method("decide").getAnnotation(PostMapping.class);
        assertThat(submit.value()).isEmpty();
        assertThat(decide.value()).containsExactly("/{reviewOrderNo}/decisions");
    }

    @Test
    void manualReserveRoutesShouldUseDedicatedVersionedResources() {
        assertThat(method("previewManualReserve").getAnnotation(PostMapping.class).value())
                .containsExactly("/manual-reserve-tasks/preview");
        assertThat(method("startManualReserve").getAnnotation(PostMapping.class).value())
                .containsExactly("/manual-reserve-tasks/{taskNo}/start");
        assertThat(method("manualReserveTask").getAnnotation(GetMapping.class).value())
                .containsExactly("/manual-reserve-tasks/{taskNo}");
        assertThat(method("previewManualTransaction").getAnnotation(PostMapping.class).value())
                .containsExactly("/manual-transaction-tasks/preview");
    }

    @Test
    void reserveManualRoutesShouldDelegateOnlyReserveReleaseTasks() {
        SettlementManualReviewApplicationService manualService =
                mock(SettlementManualReviewApplicationService.class);
        SettlementReviewInternalController controller = new SettlementReviewInternalController(
                mock(SettlementReviewOrderApplicationService.class), manualService);
        ManualReviewPreviewRequest previewRequest = new ManualReviewPreviewRequest();
        previewRequest.setRequestKey("RESERVE-PREVIEW-1");
        previewRequest.setMerchantId("M1001");
        previewRequest.setSettlementProfileId(11L);
        previewRequest.setPaymentType(null);
        previewRequest.setPaymentMethod("VISA");
        previewRequest.setReason("settle all matured reserve releases");
        previewRequest.setOperatorId(88L);
        previewRequest.setOperatorName("Maker");
        previewRequest.setRoleSnapshot("SETTLEMENT_MAKER");
        previewRequest.setClientIp("10.0.0.1");
        previewRequest.setUserAgent("JUnit");
        previewRequest.setOperationTime(LocalDateTime.of(2026, 9, 8, 8, 30));
        when(manualService.preview(org.mockito.ArgumentMatchers.any())).thenReturn(manualTask());

        controller.previewManualReserve(previewRequest);

        ArgumentCaptor<PreviewCommand> previewCaptor = ArgumentCaptor.forClass(PreviewCommand.class);
        verify(manualService).preview(previewCaptor.capture());
        assertThat(previewCaptor.getValue().reviewType()).isEqualTo("RESERVE_RELEASE");
        assertThat(previewCaptor.getValue().paymentType()).isNull();
        assertThat(previewCaptor.getValue().paymentMethod()).isEqualTo("VISA");
        assertThat(previewCaptor.getValue().operator().accountId()).isEqualTo(88L);

        ManualReviewStartRequest startRequest = new ManualReviewStartRequest();
        startRequest.setRequestKey("RESERVE-START-1");
        startRequest.setExpectedVersion(0L);
        when(manualService.start(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(manualTask());
        controller.startManualReserve(manualTaskNo(), startRequest);
        ArgumentCaptor<StartCommand> startCaptor = ArgumentCaptor.forClass(StartCommand.class);
        verify(manualService).start(org.mockito.ArgumentMatchers.eq(manualTaskNo()),
                startCaptor.capture(), org.mockito.ArgumentMatchers.eq("RESERVE_RELEASE"));
        assertThat(startCaptor.getValue().requestKey()).isEqualTo("RESERVE-START-1");

        when(manualService.get(manualTaskNo(), "RESERVE_RELEASE")).thenReturn(manualTask());
        controller.manualReserveTask(manualTaskNo());
        verify(manualService).get(manualTaskNo(), "RESERVE_RELEASE");
    }

    @Test
    void submitShouldMapCandidateVersionsAndTrustedOperatorSnapshot() {
        SettlementReviewOrderApplicationService applicationService =
                mock(SettlementReviewOrderApplicationService.class);
        SettlementReviewInternalController controller =
                new SettlementReviewInternalController(applicationService);
        ReviewSubmitRequest request = submitRequest();
        when(applicationService.submit(org.mockito.ArgumentMatchers.any()))
                .thenReturn(result("PENDING_APPROVAL", null, 0L));

        controller.submit(request);

        ArgumentCaptor<SettlementReviewCreateCommand> captor =
                ArgumentCaptor.forClass(SettlementReviewCreateCommand.class);
        verify(applicationService).submit(captor.capture());
        SettlementReviewCreateCommand command = captor.getValue();
        assertThat(command.requestKey()).isEqualTo("CREATE-1");
        assertThat(command.reviewType().name()).isEqualTo("REGULAR");
        assertThat(command.candidates()).containsExactly(
                new SettlementReviewCreateCommand.CandidateReference(1L, 7L));
        assertThat(command.submitter().accountId()).isEqualTo(88L);
        assertThat(command.submitter().roleSnapshot()).isEqualTo("FINANCE,SETTLEMENT_MAKER");
        assertThat(command.submitter().operationTime()).isEqualTo(request.getOperationTime());
    }

    @Test
    void decisionShouldMapExpectedVersionAndCheckerSnapshot() {
        SettlementReviewOrderApplicationService applicationService =
                mock(SettlementReviewOrderApplicationService.class);
        SettlementReviewInternalController controller =
                new SettlementReviewInternalController(applicationService);
        ReviewDecisionRequest request = new ReviewDecisionRequest();
        request.setRequestKey("DECIDE-1");
        request.setExpectedVersion(3L);
        request.setDecision("APPROVE");
        request.setComment("checked against clearing facts");
        request.setOperatorId(99L);
        request.setOperatorName("Checker");
        request.setRoleSnapshot("SETTLEMENT_CHECKER");
        request.setClientIp("10.0.0.2");
        request.setUserAgent("JUnit");
        request.setOperationTime(LocalDateTime.of(2026, 8, 31, 9, 50));
        when(applicationService.decide(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any())).thenReturn(result("APPROVED", "SB20260831-00000002", 4L));

        controller.decide("SO20260831-00000001", request);

        ArgumentCaptor<SettlementReviewDecisionCommand> captor =
                ArgumentCaptor.forClass(SettlementReviewDecisionCommand.class);
        verify(applicationService).decide(org.mockito.ArgumentMatchers.eq("SO20260831-00000001"),
                captor.capture());
        assertThat(captor.getValue().expectedVersion()).isEqualTo(3L);
        assertThat(captor.getValue().decision()).isEqualTo("APPROVE");
        assertThat(captor.getValue().operator().accountId()).isEqualTo(99L);
        assertThat(captor.getValue().operator().operationTime()).isEqualTo(request.getOperationTime());
    }

    @Test
    void decisionTaskResumeShouldUseDedicatedRouteAndTrustedRecoveryAudit() {
        SettlementReviewDecisionApplicationService decisionService =
                mock(SettlementReviewDecisionApplicationService.class);
        SettlementReviewInternalController controller = new SettlementReviewInternalController(
                mock(SettlementReviewOrderApplicationService.class),
                mock(SettlementManualReviewApplicationService.class), decisionService);
        ReviewDecisionTaskResumeRequest request = new ReviewDecisionTaskResumeRequest();
        request.setRequestKey("RESUME-1");
        request.setExpectedVersion(7L);
        request.setReason("database connectivity restored");
        request.setOperatorId(88L);
        request.setOperatorName("Settlement Operator");
        request.setRoleSnapshot("SETTLEMENT_RECOVERY");
        request.setClientIp("10.0.0.8");
        request.setUserAgent("JUnit Admin");
        request.setOperationTime(LocalDateTime.of(2026, 9, 8, 10, 0));
        when(decisionService.resume(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(mock(com.scott.payment.settlement.dto.SettlementReviewDecisionModels.TaskResult.class));

        controller.resumeDecisionTask("DT" + "a".repeat(32), request);

        assertThat(method("resumeDecisionTask").getAnnotation(PostMapping.class).value())
                .containsExactly("/decision-tasks/{taskNo}/resume");
        ArgumentCaptor<SettlementCommandAudit> captor = ArgumentCaptor.forClass(SettlementCommandAudit.class);
        verify(decisionService).resume(org.mockito.ArgumentMatchers.eq("DT" + "a".repeat(32)),
                org.mockito.ArgumentMatchers.eq(7L), captor.capture());
        assertThat(captor.getValue().requestKey()).isEqualTo("RESUME-1");
        assertThat(captor.getValue().reason()).isEqualTo("database connectivity restored");
        assertThat(captor.getValue().operator().accountId()).isEqualTo(88L);
    }

    @Test
    void submitShouldRejectCandidateWithoutExpectedVersion() {
        SettlementReviewInternalController controller = new SettlementReviewInternalController(
                mock(SettlementReviewOrderApplicationService.class));
        ReviewSubmitRequest request = submitRequest();
        request.getCandidates().get(0).setExpectedVersion(null);

        assertThatThrownBy(() -> controller.submit(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("candidate reference");
    }

    private ReviewSubmitRequest submitRequest() {
        ReviewCandidateReference candidate = new ReviewCandidateReference();
        candidate.setCandidateId(1L);
        candidate.setExpectedVersion(7L);
        ReviewSubmitRequest request = new ReviewSubmitRequest();
        request.setRequestKey("CREATE-1");
        request.setReviewType("REGULAR");
        request.setBusinessDate(LocalDate.of(2026, 8, 31));
        request.setCutoffBeginTime(LocalDateTime.of(2026, 8, 30, 0, 0));
        request.setCutoffEndTime(LocalDateTime.of(2026, 8, 31, 0, 0));
        request.setCandidates(List.of(candidate));
        request.setReason("manual settlement requested");
        request.setOperatorId(88L);
        request.setOperatorName("Maker");
        request.setRoleSnapshot("FINANCE,SETTLEMENT_MAKER");
        request.setClientIp("10.0.0.1");
        request.setUserAgent("JUnit");
        request.setOperationTime(LocalDateTime.of(2026, 8, 31, 9, 15));
        return request;
    }

    private SettlementReviewCommandResult result(String status, String batchNo, long version) {
        return new SettlementReviewCommandResult("SO20260831-00000001", status, batchNo,
                1, "USD", 2, "CREDIT", new BigDecimal("10.00"), version);
    }

    private TaskResult manualTask() {
        return new TaskResult(manualTaskNo(), "SO20260908-00000001", "PREVIEWED",
                "RESERVE_RELEASE", "M1001", 11L, 21L, "USD", 2,
                null, "VISA", "settle all matured reserve releases",
                LocalDate.of(2026, 9, 8), LocalDateTime.of(2026, 9, 8, 0, 0),
                101L, 3, 0, 0, 0, null, 0, 0, null, null,
                List.of(), 0, null, null, null, null, 0L);
    }

    private String manualTaskNo() {
        return "MT" + "a".repeat(32);
    }

    private java.lang.reflect.Method method(String name) {
        return Arrays.stream(SettlementReviewInternalController.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(name)).findFirst().orElseThrow();
    }
}
