package com.scott.payment.component.core.cache;

import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PlatformConfigCachePolicy
 * @date : 2026-07-30 21:10
 * @email : scott_x@163.com
 * @description : 平台配置缓存准入策略，只允许跨服务公开访问地址进入 Redis，敏感或未登记配置始终直查数据库
 * @status : create
 */
public final class PlatformConfigCachePolicy {

    /**
     * 允许缓存的非敏感平台配置键；集合内容是跨服务契约，不允许业务代码运行时扩展。
     */
    private static final Set<String> CACHEABLE_KEYS = Set.of(
            "platform.gateway.base-url",
            "platform.checkout.frontend-base-url",
            "platform.merchant.frontend-base-url",
            "platform.admin.frontend-base-url"
    );

    private PlatformConfigCachePolicy() {
    }

    /**
     * 判断配置键是否允许进入平台配置缓存。
     *
     * @param configKey 平台配置键，允许包含首尾空白但不允许为空
     * @return 已登记为非敏感公开地址时返回 true，其他配置返回 false 并绕过缓存
     */
    public static boolean isCacheable(String configKey) {
        if (configKey == null) {
            return false;
        }
        String normalizedConfigKey = configKey.trim();
        return !normalizedConfigKey.isEmpty() && CACHEABLE_KEYS.contains(normalizedConfigKey);
    }

    /**
     * 返回不可修改的平台配置缓存白名单，供管理端参数校验复用。
     *
     * @return 四个公开访问地址配置键
     */
    public static Set<String> cacheableKeys() {
        return CACHEABLE_KEYS;
    }
}
