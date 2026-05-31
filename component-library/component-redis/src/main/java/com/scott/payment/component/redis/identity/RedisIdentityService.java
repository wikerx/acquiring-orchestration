package com.scott.payment.component.redis.identity;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RedisIdentityService
 * @date : 2026-05-31 22:08
 * @email : scott_x@163.com
 * @description : Redis 分布式业务标识生成服务
 * @status : create
 */
public interface RedisIdentityService {

    /**
     * 生成平台业务标识。
     *
     * @param businessPrefix 业务前缀，例如 PA 表示收单支付
     * @return 平台业务标识
     */
    String nextIdentityId(String businessPrefix);

    /**
     * 生成每日递增的 6 位系统追踪号。
     *
     * @param institutionCode 机构号或通道机构编码
     * @return 6 位系统追踪号
     */
    String nextDailyStan(String institutionCode);

    /**
     * 生成按卡品牌和机构号隔离的每日 6 位系统追踪号。
     *
     * @param cardBrand       卡品牌
     * @param institutionCode 机构号或通道机构编码
     * @return 6 位系统追踪号
     */
    String nextDailyStan(String cardBrand, String institutionCode);

    /**
     * 生成每日递减的 6 位系统追踪号。
     * <p>
     * 兼容部分渠道希望从 999999 向下取号的场景。
     *
     * @param businessKey 业务隔离键
     * @return 6 位系统追踪号
     */
    String nextDailyDecrementStan(String businessKey);
}
