package com.scott.payment.component.core.cache;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentCacheNames
 * @date : 2026-07-30 00:00
 * @email : scott_x@163.com
 * @description : 公共组件层 Spring Cache 名称注册常量，限定可跨服务共享的 Redis 业务读模型
 * @status : update
 *
 * <p>Cache Name 会直接参与 Redis 物理 Key，统一保持
 * {@code acquiring:{environment}:{domain}:{dataset}:{businessKey}} 的短命名格式。
 * 这里登记的缓存包含常驻快照和有限期读模型；具体生命周期由 component-redis 统一注册，
 * 所有缓存都不能替代数据库事实源。</p>
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
     * 按卡号前 11 位保存的卡 BIN 正向匹配结果，物理 Key 示例：
     * {@code acquiring:dev:cardBin:51234500000}。
     */
    public static final String CARD_BIN = "cardBin";

    /**
     * 按卡号前 11 位保存的 Card BIN 未匹配短期标记；TTL 必须短于正向匹配缓存。
     */
    public static final String CARD_BIN_MISS = "cardBin:miss";

    /**
     * 全局中国大陆结算日历月视图缓存，业务键为 {@code yyyy-MM}。
     * 数据库是事实源，日历初始化、批量维护和年度确认后统一失效。
     */
    public static final String SETTLEMENT_HOLIDAY_MONTH = "settlement:calendar:month";

    /**
     * 商户当前已生效费率只读快照，业务键为商户号。
     * 管理端审核新版本成功后统一失效，模板启停不影响已经复制到商户的版本。
     */
    public static final String MERCHANT_ACTIVE_FEE = "merchant:activeFee";

    /**
     * 全局启用国家地区快照，固定业务键为 {@code all}。
     */
    public static final String ISO_COUNTRY = "iso:country";

    /**
     * 全局启用币种快照，固定业务键为 {@code all}。
     */
    public static final String ISO_CURRENCY = "iso:currency";

    /**
     * 跨系统共享的启用 MCC 三级选项快照，固定业务键为 {@code all}。
     */
    public static final String MCC_OPTIONS = "mcc:options";

    /**
     * 跨 Admin 与 Merchant Portal 共享的启用数据字典下拉快照，
     * 业务键为 {@code dictType:locale}。
     */
    public static final String SYSTEM_DICT_OPTIONS = "system:dict:options";

    /**
     * 跨 Admin 与 Merchant Portal 共享的已启用邮件模板快照，
     * 业务键为 {@code templateCode:locale}，不得包含 SMTP 账号密钥。
     */
    public static final String EMAIL_TEMPLATE_ENABLED = "email:template:enabled";

    private PaymentCacheNames() {
    }
}
