package com.scott.payment.job.handler.transaction;

import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.job.executor.JobExecuteContext;
import com.scott.payment.component.job.model.JobExecuteResult;
import com.scott.payment.job.client.data.DataInternalClient;
import com.scott.payment.job.client.data.dto.DataMerchantNotificationNotifyDueClientRequestDTO;
import com.scott.payment.job.dto.transaction.MerchantNotificationRetryRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
    void executeShouldNotifyDueForEachTransactionDateTime() {
        DataInternalClient dataInternalClient = mock(DataInternalClient.class);
        when(dataInternalClient.notifyDueMerchantNotifications(org.mockito.ArgumentMatchers.any()))
                .thenReturn(2)
                .thenReturn(3);
        MerchantNotificationRetryJob job = new MerchantNotificationRetryJob(dataInternalClient);
        LocalDateTime firstTime = LocalDateTime.of(2026, 7, 15, 0, 0, 0);
        LocalDateTime secondTime = LocalDateTime.of(2026, 4, 1, 0, 0, 0);
        MerchantNotificationRetryRequest request = new MerchantNotificationRetryRequest();
        request.setTransactionDateTimes(List.of(firstTime, secondTime));
        request.setLimit(20);

        JobExecuteResult result = job.execute(context(request));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).contains("successCount=5");
        ArgumentCaptor<DataMerchantNotificationNotifyDueClientRequestDTO> captor =
                ArgumentCaptor.forClass(DataMerchantNotificationNotifyDueClientRequestDTO.class);
        verify(dataInternalClient, times(2)).notifyDueMerchantNotifications(captor.capture());
        assertThat(captor.getAllValues()).extracting(DataMerchantNotificationNotifyDueClientRequestDTO::getTransactionDateTime)
                .containsExactly(firstTime, secondTime);
        assertThat(captor.getAllValues()).extracting(DataMerchantNotificationNotifyDueClientRequestDTO::getLimit)
                .containsExactly(20, 20);
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

    private JobExecuteContext context(MerchantNotificationRetryRequest request) {
        JobExecuteContext context = new JobExecuteContext();
        context.setParamsJson(JsonUtils.toJsonString(request));
        context.setActualTriggerTime(LocalDateTime.of(2026, 7, 15, 0, 0, 0));
        return context;
    }
}
