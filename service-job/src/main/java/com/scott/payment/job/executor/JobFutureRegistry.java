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
     * future Map，用于保存 Job Future Registry 中与 futuremap 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
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
