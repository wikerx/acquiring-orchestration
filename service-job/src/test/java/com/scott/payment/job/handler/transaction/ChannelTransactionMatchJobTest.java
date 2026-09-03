package com.scott.payment.job.handler.transaction;

import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import com.scott.payment.component.job.executor.JobExecuteContext;
import com.scott.payment.job.client.payment.PaymentInternalClient;
import com.scott.payment.job.client.payment.dto.PaymentChannelMatchClientRequestDTO;
import com.scott.payment.job.client.payment.dto.PaymentChannelMatchClientResultDTO;
import com.scott.payment.job.dto.transaction.ChannelTransactionMatchRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelTransactionMatchJobTest
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 渠道交易勾兑任务测试，验证默认跨季度回看和任务参数保护。
 * @status : create
 */
class ChannelTransactionMatchJobTest {

    @Test
    void executeShouldScanConfiguredLookbackQuarters() {
        PaymentInternalClient paymentInternalClient = mock(PaymentInternalClient.class);
        when(paymentInternalClient.matchDueChannelTransactions(any())).thenReturn(new PaymentChannelMatchClientResultDTO());
        ChannelTransactionMatchJob job = new ChannelTransactionMatchJob(
                paymentInternalClient,
                shardingProperties("202601", "202602", "202603", "202604"));
        ChannelTransactionMatchRequest request = new ChannelTransactionMatchRequest();
        request.setLookbackQuarters(3);
        request.setLimit(20);

        job.execute(context(request, LocalDateTime.of(2026, 7, 29, 12, 0)));

        ArgumentCaptor<PaymentChannelMatchClientRequestDTO> captor =
                ArgumentCaptor.forClass(PaymentChannelMatchClientRequestDTO.class);
        verify(paymentInternalClient, times(3)).matchDueChannelTransactions(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(PaymentChannelMatchClientRequestDTO::getTransactionDateTime)
                .containsExactly(
                        LocalDateTime.of(2026, 7, 1, 0, 0),
                        LocalDateTime.of(2026, 4, 1, 0, 0),
                        LocalDateTime.of(2026, 1, 1, 0, 0));
        assertThat(captor.getAllValues())
                .extracting(PaymentChannelMatchClientRequestDTO::getLimit)
                .containsOnly(20);
    }

    @Test
    void executeShouldRejectInvalidLookbackQuarters() {
        ChannelTransactionMatchJob job = new ChannelTransactionMatchJob(
                mock(PaymentInternalClient.class),
                shardingProperties("202603"));
        ChannelTransactionMatchRequest request = new ChannelTransactionMatchRequest();
        request.setLookbackQuarters(0);

        assertThatThrownBy(() -> job.execute(context(request, LocalDateTime.of(2026, 7, 29, 12, 0))))
                .isInstanceOf(ServiceException.class);
    }

    /**
     * 自动回看只能扫描已配置物理表，避免开发环境访问起始季度之前的分表。
     */
    @Test
    void executeShouldSkipAutomaticLookbackOutsideConfiguredRange() {
        PaymentInternalClient paymentInternalClient = mock(PaymentInternalClient.class);
        when(paymentInternalClient.matchDueChannelTransactions(any()))
                .thenReturn(new PaymentChannelMatchClientResultDTO());
        ChannelTransactionMatchJob job = new ChannelTransactionMatchJob(
                paymentInternalClient,
                shardingProperties("202603", "202604"));
        ChannelTransactionMatchRequest request = new ChannelTransactionMatchRequest();
        request.setLookbackQuarters(4);

        job.execute(context(request, LocalDateTime.of(2026, 7, 29, 12, 0)));

        ArgumentCaptor<PaymentChannelMatchClientRequestDTO> captor =
                ArgumentCaptor.forClass(PaymentChannelMatchClientRequestDTO.class);
        verify(paymentInternalClient).matchDueChannelTransactions(captor.capture());
        assertThat(captor.getValue().getTransactionDateTime())
                .isEqualTo(LocalDateTime.of(2026, 7, 1, 0, 0));
    }

    @Test
    void executeShouldNotApplyLegacyFixedQuarterCap() {
        PaymentInternalClient paymentInternalClient = mock(PaymentInternalClient.class);
        when(paymentInternalClient.matchDueChannelTransactions(any()))
                .thenReturn(new PaymentChannelMatchClientResultDTO());
        ChannelTransactionMatchJob job = new ChannelTransactionMatchJob(
                paymentInternalClient,
                shardingProperties("202501", "202502", "202503", "202504",
                        "202601", "202602", "202603"));
        ChannelTransactionMatchRequest request = new ChannelTransactionMatchRequest();
        request.setLookbackQuarters(20);

        job.execute(context(request, LocalDateTime.of(2026, 7, 29, 12, 0)));

        verify(paymentInternalClient, times(7)).matchDueChannelTransactions(any());
    }

    private TransactionShardingProperties shardingProperties(String... physicalNodes) {
        TransactionShardingProperties properties = new TransactionShardingProperties();
        properties.setPhysicalNodes(java.util.List.of(physicalNodes));
        return properties;
    }

    private JobExecuteContext context(ChannelTransactionMatchRequest request, LocalDateTime triggerTime) {
        JobExecuteContext context = new JobExecuteContext();
        context.setParamsJson(JsonUtils.toJsonString(request));
        context.setActualTriggerTime(triggerTime);
        return context;
    }
}
