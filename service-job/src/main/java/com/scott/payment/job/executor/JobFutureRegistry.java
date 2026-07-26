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

    /**
     * future Map 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
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
