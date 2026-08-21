package com.scott.payment.job.handler;

import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.job.executor.JobExecuteContext;
import com.scott.payment.component.job.executor.JobHandler;
import com.scott.payment.component.job.executor.JobHandlerDescriptor;
import com.scott.payment.component.job.model.JobExecuteResult;
import com.scott.payment.job.client.payment.PaymentInternalClient;
import com.scott.payment.job.dto.transaction.PaymentTimeoutCloseRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentTimeoutCloseJob
 * @date : 2026-08-20 20:40
 * @email : scott_x@163.com
 * @description : 收银台超时关单任务处理器，调用 service-payment 关闭超过截止时间且从未提交支付的订单
 * @status : update
 */
@Slf4j
@Component
public class PaymentTimeoutCloseJob implements JobHandler {

    /** 任务编码，必须与 sys_job_task.job_code 保持一致。 */
    public static final String JOB_CODE = "PAY_TIMEOUT_CLOSE";

    /** 处理器编码，必须与 sys_job_task.handler_code 保持一致。 */
    public static final String HANDLER_CODE = "paymentTimeoutClose";

    /** 默认单批扫描数量。 */
    private static final int DEFAULT_LIMIT = 200;

    /** 支付核心允许的最大单批扫描数量。 */
    private static final int MAX_LIMIT = 1000;

    /** 支付核心内部客户端。 */
    private final PaymentInternalClient paymentInternalClient;

    /**
     * 创建支付超时关单任务处理器。
     *
     * @param paymentInternalClient service-payment 内部客户端
     */
    public PaymentTimeoutCloseJob(PaymentInternalClient paymentInternalClient) {
        this.paymentInternalClient = paymentInternalClient;
    }

    /**
     * 返回支付超时关单任务处理器描述。
     *
     * @return 处理器描述
     */
    @Override
    public JobHandlerDescriptor descriptor() {
        return JobHandlerDescriptor.sync(
                HANDLER_CODE,
                "支付超时关单",
                "payment",
                "关闭超过 24 小时且从未提交支付的收银台订单"
        );
    }

    /**
     * 执行支付超时关单任务。
     *
     * <p>任务层不查询订单表、不判断交易状态，只把受保护的批量上限传给支付核心；
     * 状态 CAS、商户通知幂等和主库事务均由 service-payment 完成。</p>
     *
     * @param context 调度执行上下文
     * @return 实际超时关闭数量
     */
    @Override
    public JobExecuteResult execute(JobExecuteContext context) {
        PaymentTimeoutCloseRequest request = context == null
                ? null : context.parseParams(PaymentTimeoutCloseRequest.class);
        int limit = normalizeLimit(request == null ? null : request.getLimit());
        long startNanos = System.nanoTime();
        String traceId = context == null ? TraceContext.getTraceId() : context.getTraceId();
        log.info("event: JOB_HANDLER_SCAN_START traceId: {} jobId: {} handler: {} runId: {} limit: {}",
                traceId,
                context == null ? null : context.getJobId(),
                HANDLER_CODE,
                context == null ? null : context.getRunId(),
                limit);
        int expiredCount = paymentInternalClient.expireDueCheckoutSessions(limit);
        log.info("event: JOB_HANDLER_SCAN_END traceId: {} jobId: {} handler: {} runId: {} expiredCount: {} durationMs: {}",
                traceId,
                context == null ? null : context.getJobId(),
                HANDLER_CODE,
                context == null ? null : context.getRunId(),
                expiredCount,
                (System.nanoTime() - startNanos) / 1_000_000L);
        return JobExecuteResult.success(
                "payment timeout close finished, expiredCount=" + expiredCount,
                Map.of("expiredCount", expiredCount));
    }

    /** 将任务参数限制到支付核心允许的单批范围。 */
    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
