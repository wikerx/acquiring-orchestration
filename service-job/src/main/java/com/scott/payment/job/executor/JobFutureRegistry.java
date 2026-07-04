package com.scott.payment.job.executor;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobFutureRegistry
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 任务Future注册表注册中心
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobFutureRegistry
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Job Future Registry，位于 service-job 的任务调度层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Component
public class JobFutureRegistry {

    private final Map<String, CompletableFuture<?>> futureMap = new ConcurrentHashMap<>();

    /**
     * 注册运行中的 Future。
     *
     * @param runId  执行批次号
     * @param future 执行 Future
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param runId 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param future 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */
    public void register(String runId, CompletableFuture<?> future) {
        if (runId != null && future != null) {
            futureMap.put(runId, future);
        }
    }

    /**
     * 注销运行中的 Future。
     *
     * @param runId 执行批次号
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param runId 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */
    public void unregister(String runId) {
        if (runId != null) {
            futureMap.remove(runId);
        }
    }

    /**
     * 尝试取消运行中的任务。
     *
     * @param runId 执行批次号
     * @return true 表示找到了对应 Future 并发起取消
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param runId 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public boolean cancel(String runId) {
        CompletableFuture<?> future = futureMap.remove(runId);
        if (future == null) {
            return false;
        }
        return future.cancel(true);
    }
}
