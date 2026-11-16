package com.scott.payment.admin.application.monitor;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 一次性补偿启动入口的显式授权边界测试。
 */
class AuditOutboxCompensationRunnerTest {

    private final AuditOutboxCompensationService service = mock(AuditOutboxCompensationService.class);
    private final ApplicationArguments arguments = mock(ApplicationArguments.class);

    /** 默认预演只能走只读候选查询。 */
    @Test
    void shouldUsePreviewWhenDryRunIsEnabled() {
        AuditOutboxCompensationRunner runner = new AuditOutboxCompensationRunner(
                service, true, 5, "", "");
        when(service.preview(5)).thenReturn(result(true));

        runner.run(arguments);

        verify(service).preview(5);
        verify(service, never()).recoverAndRelay(
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyString());
    }

    /** 执行态缺少精确二次确认令牌时必须拒绝。 */
    @Test
    void shouldRejectExecutionWithoutExactApprovalToken() {
        AuditOutboxCompensationRunner runner = new AuditOutboxCompensationRunner(
                service, false, 5, "wrong-token", "approved change");

        assertThatThrownBy(() -> runner.run(arguments))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("approval token");

        verify(service, never()).recoverAndRelay(
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyString());
    }

    /** 精确令牌存在时才允许进入受控补偿服务。 */
    @Test
    void shouldExecuteOnlyWithExactApprovalToken() {
        AuditOutboxCompensationRunner runner = new AuditOutboxCompensationRunner(
                service, false, 5, AuditOutboxCompensationRunner.APPROVAL_TOKEN, "approved change");
        when(service.recoverAndRelay(5, "approved change")).thenReturn(result(false));

        runner.run(arguments);

        verify(service).recoverAndRelay(5, "approved change");
        verify(service, never()).preview(org.mockito.ArgumentMatchers.anyInt());
    }

    private AuditOutboxCompensationService.AuditOutboxCompensationResult result(boolean dryRun) {
        return new AuditOutboxCompensationService.AuditOutboxCompensationResult(
                dryRun, 0, 0, 0, List.of(), null);
    }
}
