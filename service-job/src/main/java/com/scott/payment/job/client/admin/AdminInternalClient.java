package com.scott.payment.job.client.admin;

import com.scott.payment.job.client.admin.dto.ExchangeRateAlertEvaluationClientDTOs.Request;
import com.scott.payment.job.client.admin.dto.ExchangeRateAlertEvaluationClientDTOs.Response;
import com.scott.payment.job.client.admin.dto.OperationalWorkItemSyncClientDTOs;
import com.scott.payment.job.client.admin.dto.PaymentCapabilityOperationsClientDTOs;

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

    /**
     * 触发一轮幂等的统一运营待办同步。
     *
     * @param request 任务运行标识和同步基准时间
     * @return 扫描、创建、重开、关闭和刷新统计
     */
    OperationalWorkItemSyncClientDTOs.Response synchronizeOperationalWorkItems(
            OperationalWorkItemSyncClientDTOs.Request request);

    /**
     * 调用管理服务扫描到达生效或失效边界的支付能力事实。
     */
    PaymentCapabilityOperationsClientDTOs.BoundaryResponse scanPaymentCapabilityBoundaries(
            PaymentCapabilityOperationsClientDTOs.BoundaryRequest request);

    /**
     * 调用管理服务复核超时的支付能力通知投递。
     */
    PaymentCapabilityOperationsClientDTOs.RecoveryResponse recoverPaymentCapabilityDelivery(
            PaymentCapabilityOperationsClientDTOs.RecoveryRequest request);
}
