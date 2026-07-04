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
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentTimeoutCloseJob
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Payment Timeout Close Job，位于 service-job 的任务调度层，用于承载该模块对应的业务职责和数据流转边界。
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
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @return 处理后的业务结果或页面展示数据。
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
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param parameter 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    public JobExecuteResult execute(String parameter) {
        log.info("Execute payment timeout close job, parameter: {}", parameter);
        return JobExecuteResult.success("payment timeout close job finished");
    }
}
