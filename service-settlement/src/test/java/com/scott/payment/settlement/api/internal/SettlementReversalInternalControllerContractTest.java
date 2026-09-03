package com.scott.payment.settlement.api.internal;

import com.scott.payment.settlement.api.internal.dto.SettlementManagementDTOs.ReversalDecisionRequest;
import com.scott.payment.settlement.api.internal.dto.SettlementManagementDTOs.ReversalSubmitRequest;
import com.scott.payment.settlement.application.SettlementReversalOrderApplicationService;
import com.scott.payment.settlement.dto.SettlementReversalCommandResult;
import com.scott.payment.settlement.dto.SettlementReversalCreateCommand;
import com.scott.payment.settlement.dto.SettlementReversalDecisionCommand;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementReversalInternalControllerContractTest
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 验证冲正命令只暴露在版本化内部边界并完整接收可信操作人快照。
 * @status : create
 */
class SettlementReversalInternalControllerContractTest {

    @Test
    void routesShouldRemainUnderSignedInternalReversalBoundary() {
        RequestMapping root = SettlementReversalInternalController.class.getAnnotation(RequestMapping.class);
        assertThat(root.value()).containsExactly("/internal/settlement/v1/reversal-orders");
        assertThat(method("submit").getAnnotation(PostMapping.class).value()).isEmpty();
        assertThat(method("decide").getAnnotation(PostMapping.class).value())
                .containsExactly("/{reversalOrderNo}/decisions");
    }

    @Test
    void submitAndDecisionShouldMapTrustedOperatorSnapshots() {
        SettlementReversalOrderApplicationService applicationService =
                mock(SettlementReversalOrderApplicationService.class);
        SettlementReversalInternalController controller =
                new SettlementReversalInternalController(applicationService);
        ReversalSubmitRequest submit = submitRequest();
        when(applicationService.submit(org.mockito.ArgumentMatchers.any())).thenReturn(result("PENDING_APPROVAL"));

        controller.submit(submit);

        ArgumentCaptor<SettlementReversalCreateCommand> createCaptor =
                ArgumentCaptor.forClass(SettlementReversalCreateCommand.class);
        verify(applicationService).submit(createCaptor.capture());
        assertThat(createCaptor.getValue().originalBatchNo()).isEqualTo("SB20260830-00000001");
        assertThat(createCaptor.getValue().operator().accountId()).isEqualTo(88L);
        assertThat(createCaptor.getValue().operator().operationTime()).isEqualTo(submit.getOperationTime());

        ReversalDecisionRequest decision = new ReversalDecisionRequest();
        decision.setRequestKey("DECIDE-1");
        decision.setExpectedVersion(0L);
        decision.setDecision("APPROVE");
        decision.setComment("checked");
        decision.setOperatorId(99L);
        decision.setOperatorName("Checker");
        decision.setRoleSnapshot("SETTLEMENT_CHECKER");
        decision.setClientIp("10.0.0.2");
        decision.setUserAgent("JUnit");
        decision.setOperationTime(LocalDateTime.of(2026, 8, 31, 9, 50));
        when(applicationService.decide(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any())).thenReturn(result("APPROVED"));

        controller.decide("SRO20260831-00000001", decision);

        ArgumentCaptor<SettlementReversalDecisionCommand> decisionCaptor =
                ArgumentCaptor.forClass(SettlementReversalDecisionCommand.class);
        verify(applicationService).decide(org.mockito.ArgumentMatchers.eq("SRO20260831-00000001"),
                decisionCaptor.capture());
        assertThat(decisionCaptor.getValue().decision()).isEqualTo("APPROVE");
        assertThat(decisionCaptor.getValue().operator().accountId()).isEqualTo(99L);
    }

    private ReversalSubmitRequest submitRequest() {
        ReversalSubmitRequest request = new ReversalSubmitRequest();
        request.setRequestKey("CREATE-1");
        request.setOriginalBatchNo("SB20260830-00000001");
        request.setExpectedBatchVersion(7L);
        request.setReason("duplicate posting confirmed");
        request.setOperatorId(88L);
        request.setOperatorName("Maker");
        request.setRoleSnapshot("FINANCE,SETTLEMENT_MAKER");
        request.setClientIp("10.0.0.1");
        request.setUserAgent("JUnit");
        request.setOperationTime(LocalDateTime.of(2026, 8, 31, 9, 15));
        return request;
    }

    private SettlementReversalCommandResult result(String status) {
        return new SettlementReversalCommandResult("SRO20260831-00000001", status,
                "SB20260830-00000001", "APPROVED".equals(status) ? "SB20260831-00000002" : null,
                "M1001", "USD", "CREDIT", new BigDecimal("10.00"),
                "APPROVED".equals(status) ? 1L : 0L);
    }

    private java.lang.reflect.Method method(String name) {
        return Arrays.stream(SettlementReversalInternalController.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(name)).findFirst().orElseThrow();
    }
}
