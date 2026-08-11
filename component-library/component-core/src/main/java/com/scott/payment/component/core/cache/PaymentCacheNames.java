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
     * 跨 Admin、Merchant Portal、OpenAPI 与支付服务共享的完整商户资料缓存，物理 Key 示例：
     * {@code acquiring:dev:merchant:info:200045}。
     */
    public static final String MERCHANT_RUNTIME_PROFILE = "merchant:info";

    /**
     * 商户 OpenAPI IP 访问策略缓存，物理 Key 示例：
     * {@code acquiring:dev:merchant:openapi:200045}。
     */
    public static final String MERCHANT_OPENAPI_ACCESS = "merchant:openapi";

    /**
     * 商户 OpenAPI 密钥版本元数据缓存，物理 Key 示例：
     * {@code acquiring:dev:merchant:keyMeta:200045}。
     *
     * <p>Value 只允许保存密钥 ID、版本、算法、更新时间和组合 revision，禁止保存 JWT Secret、
     * RSA 私钥、公钥正文或其他可直接参与加解密的材料。</p>
     */
    public static final String MERCHANT_KEY_METADATA = "merchant:keyMeta";

    /**
     * 商户收单路由非敏感聚合快照，物理 Key 示例：
     * {@code acquiring:dev:merchant:route:200045}。
     */
    public static final String MERCHANT_ROUTE = "merchant:route";

    /**
     * 跨服务共享的系统参数配置缓存，物理 Key 示例：
     * {@code acquiring:dev:system:config:platform.gateway.base-url}。
     *
     * <p>Value 保存数据库配置快照，业务服务只能读取启用且非空的配置值；数据库始终是事实源。</p>
     */
    public static final String SYSTEM_CONFIG = "system:config";

    /**
     * 运营后台用户维护资料缓存，物理 Key 示例：
     * {@code acquiring:dev:admin:user:profile:10001}。
     *
     * <p>Value 只允许保存用户维护页面需要的资料和关联主键，禁止保存密码哈希、Salt、
     * TOTP Secret、登录失败次数、Session Token 或其他鉴权事实。</p>
     */
    public static final String ADMIN_USER_PROFILE = "admin:user:profile";

    /**
     * 按卡号前 11 位保存的卡 BIN 匹配结果，物理 Key 示例：
     * {@code acquiring:dev:cardBin:51234500000}。
     */
    public static final String CARD_BIN = "cardBin";

    private PaymentCacheNames() {
    }
}
