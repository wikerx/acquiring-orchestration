package com.scott.payment.job.handler.exchange;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.job.executor.JobExecuteContext;
import com.scott.payment.job.dto.exchange.ExchangeRateFetchDTOs.ExchangeRateFetchRequest;
import com.scott.payment.job.dto.exchange.ExchangeRateFetchDTOs.ExchangeRateFetchResult;
import com.scott.payment.job.exchange.service.ExchangeRateFetchService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : NbpExchangeRateFetchJobTest
 * @date : 2026-08-08 00:00
 * @email : scott_x@163.com
 * @description : 验证 NBP 定时任务固定调用 NBP Provider，同时保留 dryRun 等通用任务参数。
 * @status : create
 */
class NbpExchangeRateFetchJobTest {

    /**
     * 验证来源专属任务不会被错误任务参数切换到其他汇率源。
     */
    @Test
    void shouldForceNbpSourceCodeAndPreserveDryRun() {
        ExchangeRateFetchService fetchService = mock(ExchangeRateFetchService.class);
        JobExecuteContext context = new JobExecuteContext();
        ExchangeRateFetchRequest params = new ExchangeRateFetchRequest();
        params.setSourceCode("BOC");
        params.setDryRun(true);
        context.setParamsJson(JsonUtils.toJsonString(params));
        ExchangeRateFetchResult fetchResult = new ExchangeRateFetchResult();
        fetchResult.setFetchStatus("SUCCESS");
        when(fetchService.fetch(org.mockito.ArgumentMatchers.any(), same(context))).thenReturn(fetchResult);
        NbpExchangeRateFetchJob job = new NbpExchangeRateFetchJob(fetchService);

        job.execute(context);

        ArgumentCaptor<ExchangeRateFetchRequest> requestCaptor = ArgumentCaptor.forClass(ExchangeRateFetchRequest.class);
        verify(fetchService).fetch(requestCaptor.capture(), same(context));
        assertThat(requestCaptor.getValue().getSourceCode()).isEqualTo("NBP");
        assertThat(requestCaptor.getValue().getDryRun()).isTrue();
        assertThat(job.descriptor().getHandlerCode()).isEqualTo("nbpExchangeRateFetchJob");
    }
}
