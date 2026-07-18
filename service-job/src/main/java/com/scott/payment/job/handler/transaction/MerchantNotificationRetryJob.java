package com.scott.payment.job.handler.transaction;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.job.executor.JobExecuteContext;
import com.scott.payment.component.job.executor.JobHandler;
import com.scott.payment.component.job.executor.JobHandlerDescriptor;
import com.scott.payment.component.job.model.JobExecuteResult;
import com.scott.payment.job.client.payment.PaymentInternalClient;
import com.scott.payment.job.client.payment.dto.PaymentMerchantNotificationNotifyDueClientRequestDTO;
import com.scott.payment.job.dto.transaction.MerchantNotificationRetryRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantNotificationRetryJob
 * @date : 2026-07-15 00:00
 * @email : scott_x@163.com
 * @description : 商户通知补偿任务，位于 service-job 任务处理层，按 transaction_date_time 定位分表并触发 service-payment 重试到期商户通知。
 * @status : create
 */
@Component
public class MerchantNotificationRetryJob implements JobHandler {

    /**
     * 任务编码，和 sys_job_task.job_code 保持一致。
     */
    public static final String JOB_CODE = "MERCHANT_NOTIFICATION_RETRY";

    /**
     * 处理器编码。
     */
    public static final String HANDLER_CODE = "merchantNotificationRetry";

    private static final int DEFAULT_LIMIT = 100;

    private static final int MAX_LIMIT = 500;

    private final PaymentInternalClient paymentInternalClient;

    /**
     * 创建商户通知补偿任务处理器。
     *
     * @param paymentInternalClient service-payment 内部补偿客户端
     */
    public MerchantNotificationRetryJob(PaymentInternalClient paymentInternalClient) {
        this.paymentInternalClient = paymentInternalClient;
    }

    /**
     * 返回处理器注册描述。
     *
     * @return 处理器描述
     */
    @Override
    public JobHandlerDescriptor descriptor() {
        return JobHandlerDescriptor.sync(
                HANDLER_CODE,
                "商户通知补偿重试",
                "transaction",
                "扫描到期商户通知任务并调用支付核心执行补偿重试"
        );
    }

    /**
     * 执行商户通知补偿。
     *
     * @param context 任务执行上下文
     * @return 执行结果，包含每个分表时间点的成功通知数量
     */
    @Override
    public JobExecuteResult execute(JobExecuteContext context) {
        MerchantNotificationRetryRequest request = context == null ? null : context.parseParams(MerchantNotificationRetryRequest.class);
        if (request == null) {
            request = new MerchantNotificationRetryRequest();
        }
        int limit = normalizeLimit(request.getLimit());
        List<LocalDateTime> transactionDateTimes = resolveTransactionDateTimes(request);
        Map<String, Integer> result = new LinkedHashMap<>();
        int totalSuccessCount = 0;
        for (LocalDateTime transactionDateTime : transactionDateTimes) {
            PaymentMerchantNotificationNotifyDueClientRequestDTO clientRequestDTO =
                    new PaymentMerchantNotificationNotifyDueClientRequestDTO();
            clientRequestDTO.setTransactionDateTime(transactionDateTime);
            clientRequestDTO.setLimit(limit);
            Integer successCount = paymentInternalClient.notifyDueMerchantNotifications(clientRequestDTO);
            int safeSuccessCount = successCount == null ? 0 : successCount;
            totalSuccessCount += safeSuccessCount;
            result.put(transactionDateTime.toString(), safeSuccessCount);
        }
        return JobExecuteResult.success("merchant notification retry finished, successCount=" + totalSuccessCount, result);
    }

    private List<LocalDateTime> resolveTransactionDateTimes(MerchantNotificationRetryRequest request) {
        if (request.getTransactionDateTimes() != null && !request.getTransactionDateTimes().isEmpty()) {
            return request.getTransactionDateTimes();
        }
        if (request.getTransactionDateTime() != null) {
            return List.of(request.getTransactionDateTime());
        }
        return List.of(LocalDateTime.now());
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit <= 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "limit must be greater than zero");
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
