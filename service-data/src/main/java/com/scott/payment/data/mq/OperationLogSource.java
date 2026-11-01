package com.scott.payment.data.mq;

/**
 * 操作日志消息来源，为管理端和商户端隔离 Redis 辅助幂等命名空间。
 */
public enum OperationLogSource {

    /**
     * 管理后台操作日志。
     */
    ADMIN("admin-operation-log"),

    /**
     * 商户后台操作日志。
     */
    MERCHANT("merchant-operation-log");

    /**
     * Redis MQ 辅助幂等命名空间。
     */
    private final String idempotentNamespace;

    /**
     * 创建带独立 Redis 命名空间的消息来源。
     *
     * @param idempotentNamespace Redis 辅助幂等命名空间
     */
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
