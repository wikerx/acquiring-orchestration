package com.scott.payment.settlement.api.internal;

import com.scott.payment.settlement.api.internal.dto.SettlementManagementDTOs.ReviewCandidateReference;
import com.scott.payment.settlement.api.internal.dto.SettlementManagementDTOs.ReviewDecisionRequest;
import com.scott.payment.settlement.api.internal.dto.SettlementManagementDTOs.ReviewSubmitRequest;
import com.scott.payment.settlement.application.SettlementReviewOrderApplicationService;
import com.scott.payment.settlement.dto.SettlementReviewCommandResult;
import com.scott.payment.settlement.dto.SettlementReviewCreateCommand;
import com.scott.payment.settlement.dto.SettlementReviewDecisionCommand;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

    private java.lang.reflect.Method method(String name) {
        return Arrays.stream(SettlementReviewInternalController.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(name)).findFirst().orElseThrow();
    }
}
