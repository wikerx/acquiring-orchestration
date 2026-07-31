package com.scott.payment.component.core.cache;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentRedisKeyResolver
 * @date : 2026-07-30 21:10
 * @email : scott_x@163.com
 * @description : 跨组件 Redis 业务 Key 解析契约，使数据组件复用统一环境前缀而不依赖 Redis 实现模块
 * @status : create
 */
public interface PaymentRedisKeyResolver {

    /**
     * 按系统、环境、业务域和业务用途构造受校验的 Redis Key。
     *
     * @param domain           业务数据所属领域，不允许包含冒号、空白或调用方 Hash Tag
     * @param business         领域内的业务用途
     * @param businessSegments 可选业务唯一性片段
     * @return 格式为 acquiring:{environment}:{domain}:{business}[:{businessKey}] 的物理 Key
     * @throws IllegalArgumentException 任一 Key 片段不满足安全约束时抛出
     */
    String businessKey(String domain, String business, String... businessSegments);
}
