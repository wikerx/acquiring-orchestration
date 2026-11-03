package com.scott.payment.job.client.admin;

import com.scott.payment.job.client.admin.dto.ExchangeRateAlertEvaluationClientDTOs.Request;
import com.scott.payment.job.client.admin.dto.ExchangeRateAlertEvaluationClientDTOs.Response;

/**
 * service-job 调用 service-admin 内部管理命令的客户端契约。
 */
public interface AdminInternalClient {

    /**
     * 触发一轮幂等的汇率波动评估。
     *
     * @param request 任务运行标识和评估基准时间
     * @return 本轮规则、币种对和事件处理统计
     */
    Response evaluateExchangeRateAlerts(Request request);
}
