package com.scott.payment.job.handler.exchange;

import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.job.executor.JobExecuteContext;
import com.scott.payment.component.job.model.JobExecuteResult;
import com.scott.payment.job.client.admin.AdminInternalClient;
import com.scott.payment.job.client.admin.dto.ExchangeRateAlertEvaluationClientDTOs.Request;
import com.scott.payment.job.client.admin.dto.ExchangeRateAlertEvaluationClientDTOs.Response;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 汇率波动监控任务的幂等键、时间和结果透传测试。
 */
class ExchangeRateVolatilityMonitorJobTest {

    /**
     * 存在生产构造器和测试时钟构造器时，Spring 仍必须明确选择生产构造器完成 Bean 创建。
     */
    @Test
    void shouldResolveProductionConstructorInSpringContext() {
        AdminInternalClient client = mock(AdminInternalClient.class);

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(AdminInternalClient.class, () -> client);
            context.register(ExchangeRateVolatilityMonitorJob.class);
            context.refresh();

            assertThat(context.getBean(ExchangeRateVolatilityMonitorJob.class)).isNotNull();
        }
    }

    /**
     * 同一次 runId 必须生成稳定请求键，并返回管理服务的真实统计结果。
     */
    @Test
    void shouldUseRunIdAsStableRequestKeyAndReturnEvaluationCounts() {
        AdminInternalClient client = mock(AdminInternalClient.class);
        Clock clock = Clock.fixed(
                Instant.parse("2026-11-02T02:00:00Z"), ZoneId.of("Asia/Shanghai"));
        Response response = new Response();
        response.setEvaluatedRuleCount(2);
        response.setEvaluatedPairCount(16);
        response.setTriggeredEventCount(3);
        response.setResolvedEventCount(1);
        when(client.evaluateExchangeRateAlerts(org.mockito.ArgumentMatchers.any(Request.class)))
                .thenReturn(response);
        ExchangeRateVolatilityMonitorJob job = new ExchangeRateVolatilityMonitorJob(client, clock);
        JobExecuteContext context = new JobExecuteContext();
        context.setRunId("run-20261102-001");

        JobExecuteResult result = job.execute(context);

        ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        verify(client).evaluateExchangeRateAlerts(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getRequestKey())
                .isEqualTo("EXCHANGE_RATE_VOLATILITY_MONITOR:run-20261102-001");
        assertThat(requestCaptor.getValue().getEvaluationTime())
                .isEqualTo(LocalDateTime.of(2026, 11, 2, 10, 0));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isSameAs(response);
        assertThat(result.getMessage())
                .contains("rules=2", "pairs=16", "triggered=3", "resolved=1");
    }

    /**
     * 缺失 runId 时禁止生成不可重试的随机业务幂等键。
     */
    @Test
    void shouldRejectMissingRunId() {
        ExchangeRateVolatilityMonitorJob job = new ExchangeRateVolatilityMonitorJob(
                mock(AdminInternalClient.class));

        assertThatThrownBy(() -> job.execute(new JobExecuteContext()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("runId");
    }
}
