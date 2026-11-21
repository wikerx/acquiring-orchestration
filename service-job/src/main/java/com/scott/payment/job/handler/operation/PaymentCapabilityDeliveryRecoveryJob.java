package com.scott.payment.job.handler.operation;

import com.scott.payment.component.job.executor.JobExecuteContext;
import com.scott.payment.component.job.executor.JobHandler;
import com.scott.payment.component.job.executor.JobHandlerDescriptor;
import com.scott.payment.component.job.model.JobExecuteResult;
import com.scott.payment.job.client.admin.AdminInternalClient;
import com.scott.payment.job.client.admin.dto.PaymentCapabilityOperationsClientDTOs.RecoveryRequest;
import com.scott.payment.job.client.admin.dto.PaymentCapabilityOperationsClientDTOs.RecoveryResponse;
import com.scott.payment.job.support.PaymentCapabilityJobCodes;
import org.springframework.stereotype.Component;

/** 触发支付方式范围的可靠出站和通知恢复。 */
@Component
public class PaymentCapabilityDeliveryRecoveryJob implements JobHandler {

    private final AdminInternalClient admin;

    public PaymentCapabilityDeliveryRecoveryJob(AdminInternalClient admin) {
        this.admin = admin;
    }

    /**
     * 声明支付能力通知恢复任务的调度编码和执行约束。
     */
    @Override
    public JobHandlerDescriptor descriptor() {
        return JobHandlerDescriptor.sync(PaymentCapabilityJobCodes.DELIVERY_RECOVERY, "支付方式通知恢复",
                "operations", "补发已提交的支付方式事件和通知投递意图");
    }

    /**
     * 要求管理服务恢复超时通知投递并返回本次处理结果。
     */
    @Override
    public JobExecuteResult execute(JobExecuteContext context) {
        RecoveryResponse result = admin.recoverPaymentCapabilityDelivery(new RecoveryRequest(100));
        if (result == null) {
            throw new IllegalStateException("payment capability delivery recovery response is empty");
        }
        return JobExecuteResult.success("payment capability delivery recovery: requeued=" + result.requeued()
                + ", relayed=" + result.relayed(), result);
    }
}
