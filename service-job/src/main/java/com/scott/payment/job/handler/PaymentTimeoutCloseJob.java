package com.scott.payment.job.handler;

import com.scott.payment.component.job.executor.JobHandlerDescriptor;
import com.scott.payment.component.job.handler.AbstractJobHandler;
import com.scott.payment.component.job.model.JobExecuteResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentTimeoutCloseJob
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : Payment超时Close任务
 * @status : create
 */
@Slf4j
@Component
public class PaymentTimeoutCloseJob extends AbstractJobHandler {

    /**
     * 返回支付超时关单任务处理器描述。
     *
     * @return 处理器描述
     */
    @Override
    public JobHandlerDescriptor descriptor() {
        return JobHandlerDescriptor.sync(
                "paymentTimeoutClose",
                "支付超时关单",
                "payment",
                "扫描超时未支付订单并触发关单处理"
        );
    }

    /**
     * 执行支付超时关单任务。
     *
     * <p>第一版调度中心先保留占位逻辑，后续再接入真实支付订单扫描与幂等关单编排。</p>
     *
     * @param parameter 调度平台传入的任务参数 JSON
     * @return 任务执行结果
     */
    @Override
    public JobExecuteResult execute(String parameter) {
        long startNanos = System.nanoTime();
        log.info("event=JOB_HANDLER_SCAN_START handler=paymentTimeoutClose paramsSummary: {} scanRange: {} limit: {}",
                parameter,
                null,
                0);
        log.info("event=JOB_HANDLER_SCAN_END handler=paymentTimeoutClose scannedCount: {} successCount: {} failureCount: {} skipCount: {} failureReasons: {} durationMs: {}",
                0,
                0,
                0,
                1,
                "placeholderHandler",
                (System.nanoTime() - startNanos) / 1_000_000L);
        return JobExecuteResult.success("payment timeout close job finished");
    }
}
