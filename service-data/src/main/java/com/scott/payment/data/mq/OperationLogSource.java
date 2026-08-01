package com.scott.payment.data.mq;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OperationLogSource
 * @date : 2026-08-01 14:40
 * @email : scott_x@163.com
 * @description : 操作日志消息来源枚举，为不同生产系统隔离 Redis 辅助幂等命名空间
 * @status : create
 */
public enum OperationLogSource {

    /** 管理后台操作日志。 */
    ADMIN("admin-operation-log"),

    /** 商户后台操作日志。 */
    MERCHANT("merchant-operation-log");

    /** Redis MQ 辅助幂等命名空间。 */
    private final String idempotentNamespace;

    OperationLogSource(String idempotentNamespace) {
        this.idempotentNamespace = idempotentNamespace;
    }

    /**
     * 获取 Redis MQ 辅助幂等命名空间。
     *
     * @return 与来源系统隔离的命名空间
     */
    public String getIdempotentNamespace() {
        return idempotentNamespace;
    }
}
