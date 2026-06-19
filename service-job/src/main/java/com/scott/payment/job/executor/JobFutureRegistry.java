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

@Component
public class JobFutureRegistry {

    private final Map<String, CompletableFuture<?>> futureMap = new ConcurrentHashMap<>();

    /**
     * 注册运行中的 Future。
     *
     * @param runId  执行批次号
     * @param future 执行 Future
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
    public boolean cancel(String runId) {
        CompletableFuture<?> future = futureMap.remove(runId);
        if (future == null) {
            return false;
        }
        return future.cancel(true);
    }
}
