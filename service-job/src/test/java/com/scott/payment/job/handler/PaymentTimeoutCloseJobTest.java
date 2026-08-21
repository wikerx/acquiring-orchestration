package com.scott.payment.job.handler;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.job.executor.JobExecuteContext;
import com.scott.payment.component.job.model.JobExecuteResult;
import com.scott.payment.job.client.payment.PaymentInternalClient;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentTimeoutCloseJobTest
 * @date : 2026-08-20 20:10
 * @email : scott_x@163.com
 * @description : 校验支付超时关单任务会调用 service-payment 的未提交收银台扫描接口并返回实际处理数量
 * @status : create
 */
@Slf4j
class PaymentTimeoutCloseJobTest {

    /** 任务参数中的批量上限必须传递给支付核心，不能继续执行占位逻辑。 */
    @Test
    void shouldDelegateTimeoutCloseToPaymentService() {
        log.info("用例开始：校验任务参数中的单批数量会传递给支付核心");
        PaymentInternalClient paymentInternalClient = mock(PaymentInternalClient.class);
        when(paymentInternalClient.expireDueCheckoutSessions(120)).thenReturn(7);
        PaymentTimeoutCloseJob job = new PaymentTimeoutCloseJob(paymentInternalClient);
        JobExecuteContext context = new JobExecuteContext();
        context.setParamsJson(JsonUtils.toJsonString(Map.of("limit", 120)));

        JobExecuteResult result = job.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).contains("expiredCount=7");
        assertThat(result.getData()).isEqualTo(Map.of("expiredCount", 7));
        verify(paymentInternalClient).expireDueCheckoutSessions(120);
        log.info("用例结果：任务使用 limit=120 调用支付核心并返回 expiredCount=7");
    }

    /** 未配置单批数量时必须使用任务默认值，避免空参数导致扫描失败。 */
    @Test
    void shouldUseDefaultLimitWhenTaskParamsAreMissing() {
        log.info("用例开始：校验任务参数缺失时使用默认单批数量");
        PaymentInternalClient paymentInternalClient = mock(PaymentInternalClient.class);
        when(paymentInternalClient.expireDueCheckoutSessions(200)).thenReturn(0);
        PaymentTimeoutCloseJob job = new PaymentTimeoutCloseJob(paymentInternalClient);

        JobExecuteResult result = job.execute(null);

        assertThat(result.isSuccess()).isTrue();
        verify(paymentInternalClient).expireDueCheckoutSessions(200);
        log.info("用例结果：空任务上下文已归一化为默认 limit=200");
    }

    /** 负数单批数量必须回退默认值，不能直接透传到支付核心。 */
    @Test
    void shouldUseDefaultLimitWhenConfiguredLimitIsNegative() {
        log.info("用例开始：校验负数单批数量回退为默认值");
        PaymentInternalClient paymentInternalClient = mock(PaymentInternalClient.class);
        when(paymentInternalClient.expireDueCheckoutSessions(200)).thenReturn(0);
        PaymentTimeoutCloseJob job = new PaymentTimeoutCloseJob(paymentInternalClient);
        JobExecuteContext context = contextWithLimit(-1);

        JobExecuteResult result = job.execute(context);

        assertThat(result.isSuccess()).isTrue();
        verify(paymentInternalClient).expireDueCheckoutSessions(200);
        log.info("用例结果：limit=-1 已归一化为默认 limit=200");
    }

    /** 超过支付核心保护上限的单批数量必须截断为 1000。 */
    @Test
    void shouldCapConfiguredLimitAtPaymentServiceMaximum() {
        log.info("用例开始：校验超大单批数量不会突破支付核心保护上限");
        PaymentInternalClient paymentInternalClient = mock(PaymentInternalClient.class);
        when(paymentInternalClient.expireDueCheckoutSessions(1000)).thenReturn(0);
        PaymentTimeoutCloseJob job = new PaymentTimeoutCloseJob(paymentInternalClient);
        JobExecuteContext context = contextWithLimit(5000);

        JobExecuteResult result = job.execute(context);

        assertThat(result.isSuccess()).isTrue();
        verify(paymentInternalClient).expireDueCheckoutSessions(1000);
        log.info("用例结果：limit=5000 已截断为最大 limit=1000");
    }

    /** 构造只包含单批数量的任务上下文。 */
    private JobExecuteContext contextWithLimit(int limit) {
        JobExecuteContext context = new JobExecuteContext();
        context.setParamsJson(JsonUtils.toJsonString(Map.of("limit", limit)));
        return context;
    }
}
