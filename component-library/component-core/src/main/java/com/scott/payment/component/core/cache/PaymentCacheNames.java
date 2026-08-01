package com.scott.payment.component.core.cache;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentCacheNames
 * @date : 2026-07-30 00:00
 * @email : scott_x@163.com
 * @description : 公共组件层 Spring Cache 名称注册常量，限定可跨服务共享的永久 Redis 业务读模型
 * @status : update
 *
 * <p>Cache Name 会直接参与 Redis 物理 Key，统一保持
 * {@code acquiring:{environment}:{domain}:{dataset}:{businessKey}} 的短命名格式。
 * 这里登记的缓存均为数据库数据的常驻读模型，不能替代数据库事实源。</p>
 */
public final class PaymentCacheNames {

    /**
     * 商户最小运行时资料缓存，物理 Key 示例：
     * {@code acquiring:dev:merchant:info:200045}。
     */
    public static final String MERCHANT_RUNTIME_PROFILE = "merchant:info";

    /**
     * 商户 OpenAPI IP 访问策略缓存，物理 Key 示例：
     * {@code acquiring:dev:merchant:openapi:200045}。
     */
    public static final String MERCHANT_OPENAPI_ACCESS = "merchant:openapi";

    /**
     * 商户端读取的非敏感平台公开参数缓存，物理 Key 示例：
     * {@code acquiring:dev:config:public:platform.gateway.base-url}。
     */
    public static final String PLATFORM_CONFIG = "config:public";

    private PaymentCacheNames() {
    }
}
