package com.scott.payment.settlement.api.internal;

import com.scott.payment.settlement.api.internal.dto.SettlementManagementDTOs.BatchCommandRequest;
import com.scott.payment.settlement.application.SettlementBatchCommandApplicationService;
import com.scott.payment.settlement.dto.SettlementCommandAudit;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementInternalControllerContractTest
 * @date : 2026-09-01 23:20
 * @email : scott_x@163.com
 * @description : 验证结算内部命令接口路径、HTTP 方法、操作审计和参数校验契约
 * @status : create
 */
class SettlementInternalControllerContractTest {

    @Test
    void routeShouldRemainUnderVersionedInternalSettlementCommandBoundary() {
        RequestMapping root = SettlementInternalController.class.getAnnotation(RequestMapping.class);
        assertThat(root.value()).containsExactly("/internal/settlement/v1/batches");
        Map<String, String> posts = Map.of("cancel", "/{settlementBatchNo}/cancel");
        posts.forEach((methodName, path) -> assertThat(java.util.Arrays.stream(
                        SettlementInternalController.class.getDeclaredMethods())
                .filter(method -> method.getName().equals(methodName)).findFirst().orElseThrow()
                .getAnnotation(PostMapping.class).value()).containsExactly(path));
    }

    @Test
    void cancelShouldValidateAuditFieldsAndDelegateExpectedVersion() {
        SettlementBatchCommandApplicationService commandService =
                mock(SettlementBatchCommandApplicationService.class);
        SettlementInternalController controller = new SettlementInternalController(commandService);
        BatchCommandRequest valid = new BatchCommandRequest();
        valid.setRequestKey("REQ-1");
        valid.setExpectedVersion(3L);
        valid.setReason("approved pre-post cancellation");
        valid.setOperatorId(88L);
        valid.setOperatorName("Settlement Operator");
        valid.setRoleSnapshot("SETTLEMENT_OPERATOR");
        valid.setClientIp("10.0.0.8");
        valid.setUserAgent("JUnit Admin");
        valid.setOperationTime(java.time.LocalDateTime.of(2026, 8, 31, 18, 0));

        controller.cancel("SB20260826-00000001", valid);

        ArgumentCaptor<SettlementCommandAudit> audit =
                ArgumentCaptor.forClass(SettlementCommandAudit.class);
        verify(commandService).cancelBeforePosting(
                eq("SB20260826-00000001"), eq(3L), audit.capture(), any());
        assertThat(audit.getValue().requestKey()).isEqualTo("REQ-1");
        assertThat(audit.getValue().reason()).isEqualTo("approved pre-post cancellation");
        assertThat(audit.getValue().operator().accountId()).isEqualTo(88L);
        assertThat(audit.getValue().operator().roleSnapshot()).isEqualTo("SETTLEMENT_OPERATOR");
        assertThat(audit.getValue().operator().clientIp()).isEqualTo("10.0.0.8");
        BatchCommandRequest invalid = new BatchCommandRequest();
        invalid.setExpectedVersion(3L);
        assertThatThrownBy(() -> controller.cancel("SB20260826-00000001", invalid))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void browserQueryAndDirectReversalRoutesMustNotBeExposed() {
        assertThat(java.util.Arrays.stream(SettlementInternalController.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName)).doesNotContain("search", "detail", "reverse");
    }
}
