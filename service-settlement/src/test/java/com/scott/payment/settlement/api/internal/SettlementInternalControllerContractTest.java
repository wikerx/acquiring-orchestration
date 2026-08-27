package com.scott.payment.settlement.api.internal;

import com.scott.payment.settlement.api.internal.dto.SettlementManagementDTOs.BatchCommandRequest;
import com.scott.payment.settlement.api.internal.dto.SettlementManagementDTOs.BatchSearchRequest;
import com.scott.payment.settlement.application.SettlementBatchCommandApplicationService;
import com.scott.payment.settlement.service.SettlementManagementQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** 验证结算内部接口路径、HTTP 方法以及命令参数校验契约。 */
class SettlementInternalControllerContractTest {

    @Test
    void routesShouldRemainUnderVersionedInternalSettlementBoundary() {
        RequestMapping root = SettlementInternalController.class.getAnnotation(RequestMapping.class);
        assertThat(root.value()).containsExactly("/internal/settlement/v1/batches");
        Map<String, String> posts = Map.of(
                "search", "/search",
                "cancel", "/{settlementBatchNo}/cancel",
                "reverse", "/{settlementBatchNo}/reverse");
        posts.forEach((methodName, path) -> assertThat(java.util.Arrays.stream(
                        SettlementInternalController.class.getDeclaredMethods())
                .filter(method -> method.getName().equals(methodName)).findFirst().orElseThrow()
                .getAnnotation(PostMapping.class).value()).containsExactly(path));
        GetMapping detail = java.util.Arrays.stream(SettlementInternalController.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("detail")).findFirst().orElseThrow()
                .getAnnotation(GetMapping.class);
        assertThat(detail.value()).containsExactly("/{settlementBatchNo}");
    }

    @Test
    void cancelShouldValidateAuditFieldsAndDelegateExpectedVersion() {
        SettlementManagementQueryService queryService = mock(SettlementManagementQueryService.class);
        SettlementBatchCommandApplicationService commandService =
                mock(SettlementBatchCommandApplicationService.class);
        SettlementInternalController controller = new SettlementInternalController(queryService, commandService);
        BatchCommandRequest valid = new BatchCommandRequest();
        valid.setRequestKey("REQ-1");
        valid.setExpectedVersion(3L);
        valid.setReason("approved pre-post cancellation");
        valid.setOperator("admin-account:88/Settlement Operator");

        controller.cancel("SB20260826-00000001", valid);

        verify(commandService).cancelBeforePosting(eq("SB20260826-00000001"), eq(3L), any());
        BatchCommandRequest invalid = new BatchCommandRequest();
        invalid.setExpectedVersion(3L);
        assertThatThrownBy(() -> controller.cancel("SB20260826-00000001", invalid))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
