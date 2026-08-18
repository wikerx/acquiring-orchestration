package com.scott.payment.job.handler.transaction;

import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.job.executor.JobExecuteContext;
import com.scott.payment.component.job.model.JobExecuteResult;
import com.scott.payment.job.client.data.DataInternalClient;
import com.scott.payment.job.client.data.dto.DataMerchantNotificationNotifyClientRequestDTO;
import com.scott.payment.job.client.data.dto.DataMerchantNotificationNotifyDueClientRequestDTO;
import com.scott.payment.job.client.data.dto.DataMerchantNotificationReconcileClientRequestDTO;
import com.scott.payment.job.dto.transaction.MerchantNotificationRetryRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantNotificationRetryJobTest
 * @date : 2026-07-15 00:00
 * @email : scott_x@163.com
 * @description : 商户通知补偿任务单元测试，验证任务参数能按 transaction_date_time 转换为 service-data 内部补偿请求。
 * @status : create
 */
class MerchantNotificationRetryJobTest {

    /**
     * 多个交易时间点应分别触发补偿请求，并汇总成功通知数量。
     */
    @Test
    void executeShouldRequeueDueRetriesThroughMqForAllRequestedQuarters() {
        DataInternalClient dataInternalClient = mock(DataInternalClient.class);
        when(dataInternalClient.reconcileDueMerchantNotifications(org.mockito.ArgumentMatchers.any()))
                .thenReturn(5);
        MerchantNotificationRetryJob job = new MerchantNotificationRetryJob(dataInternalClient);
        LocalDateTime firstTime = LocalDateTime.of(2026, 7, 15, 0, 0, 0);
        LocalDateTime secondTime = LocalDateTime.of(2026, 4, 1, 0, 0, 0);
        MerchantNotificationRetryRequest request = new MerchantNotificationRetryRequest();
        request.setTransactionDateTimes(List.of(firstTime, secondTime));
        request.setLimit(20);

        JobExecuteResult result = job.execute(context(request));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).contains("successCount=5");
        ArgumentCaptor<DataMerchantNotificationReconcileClientRequestDTO> captor =
                ArgumentCaptor.forClass(DataMerchantNotificationReconcileClientRequestDTO.class);
        verify(dataInternalClient).reconcileDueMerchantNotifications(captor.capture());
        assertThat(captor.getValue().getTransactionDateTimes()).containsExactly(firstTime, secondTime);
        assertThat(captor.getValue().getLimit()).isEqualTo(5);
        verify(dataInternalClient, never()).notifyDueMerchantNotifications(org.mockito.ArgumentMatchers.any());
    }

    /** 单笔人工补偿必须同时透传交易号和明确分片时间。 */
    @Test
    void executeShouldNotifySingleTransactionWithExplicitTransactionTime() {
        DataInternalClient dataInternalClient = mock(DataInternalClient.class);
        when(dataInternalClient.notifyMerchantNotification(org.mockito.ArgumentMatchers.any())).thenReturn(false);
        MerchantNotificationRetryJob job = new MerchantNotificationRetryJob(dataInternalClient);
        LocalDateTime transactionDateTime = LocalDateTime.of(2026, 8, 3, 3, 17, 58);
        MerchantNotificationRetryRequest request = new MerchantNotificationRetryRequest();
        request.setTransactionId("202608030317582640931");
        request.setTransactionDateTime(transactionDateTime);

        JobExecuteResult result = job.execute(context(request));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).contains("successCount=0");
        ArgumentCaptor<DataMerchantNotificationNotifyClientRequestDTO> captor =
                ArgumentCaptor.forClass(DataMerchantNotificationNotifyClientRequestDTO.class);
        verify(dataInternalClient).notifyMerchantNotification(captor.capture());
        assertThat(captor.getValue().getTransactionId()).isEqualTo("202608030317582640931");
        assertThat(captor.getValue().getTransactionDateTime()).isEqualTo(transactionDateTime);
        verify(dataInternalClient, never()).notifyDueMerchantNotifications(org.mockito.ArgumentMatchers.any());
    }

    /** 单笔人工补偿禁止从交易号推算时间。 */
    @Test
    void executeShouldRequireExplicitTransactionTimeForSingleRetry() {
        MerchantNotificationRetryJob job = new MerchantNotificationRetryJob(mock(DataInternalClient.class));
        MerchantNotificationRetryRequest request = new MerchantNotificationRetryRequest();
        request.setTransactionId("202608030317582640931");

        assertThatThrownBy(() -> job.execute(context(request)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("transactionDateTime is required");
    }

    /**
     * 非法 limit 应直接拒绝，避免任务误触发无效补偿请求。
     */
    @Test
    void executeShouldRejectInvalidLimit() {
        MerchantNotificationRetryJob job = new MerchantNotificationRetryJob(mock(DataInternalClient.class));
        MerchantNotificationRetryRequest request = new MerchantNotificationRetryRequest();
        request.setLimit(0);

        assertThatThrownBy(() -> job.execute(context(request)))
                .isInstanceOf(ServiceException.class);
    }

    /** JOB 模式保留旧的直接 HTTP 扫描，作为 MQ 改造的紧急回退开关。 */
    @Test
    void executeShouldUseDirectDeliveryOnlyInJobMode() {
        DataInternalClient dataInternalClient = mock(DataInternalClient.class);
        when(dataInternalClient.notifyDueMerchantNotifications(org.mockito.ArgumentMatchers.any())).thenReturn(1);
        MerchantNotificationRetryJob job = new MerchantNotificationRetryJob(dataInternalClient);
        MerchantNotificationRetryRequest request = new MerchantNotificationRetryRequest();
        request.setMode("JOB");
        request.setTransactionDateTime(LocalDateTime.of(2026, 8, 1, 0, 0));

        JobExecuteResult result = job.execute(context(request));

        assertThat(result.isSuccess()).isTrue();
        verify(dataInternalClient).notifyDueMerchantNotifications(org.mockito.ArgumentMatchers.any());
        verify(dataInternalClient, never()).reconcileDueMerchantNotifications(org.mockito.ArgumentMatchers.any());
    }

    private JobExecuteContext context(MerchantNotificationRetryRequest request) {
        JobExecuteContext context = new JobExecuteContext();
        context.setParamsJson(JsonUtils.toJsonString(request));
        context.setActualTriggerTime(LocalDateTime.of(2026, 7, 15, 0, 0, 0));
        return context;
    }
}
