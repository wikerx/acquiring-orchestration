package com.scott.payment.admin.constant;

import com.scott.payment.component.core.cache.PlatformConfigCachePolicy;

import java.util.Map;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SystemConfigKeys
 * @date : 2026-07-04 00:00
 * @email : scott_x@163.com
 * @description : 系统参数键名常量，位于 service-admin 常量层，统一约束平台访问地址等跨功能配置，避免邮件模板和配置服务散落硬编码。
 * @status : create
 */
public final class SystemConfigKeys {

    /**
     * 网关服务对外 base 地址。
     */
    public static final String GATEWAY_BASE_URL = "platform.gateway.base-url";

    /**
     * 收银台前端访问 base 地址。
     */
    public static final String CHECKOUT_FRONTEND_BASE_URL = "platform.checkout.frontend-base-url";

    /**
     * 商户系统前端访问 base 地址。
     */
    public static final String MERCHANT_FRONTEND_BASE_URL = "platform.merchant.frontend-base-url";

    /**
     * 管理系统前端访问 base 地址。
     */
    public static final String ADMIN_FRONTEND_BASE_URL = "platform.admin.frontend-base-url";

    /**
     * 需要校验为 HTTP(S) URL 的系统参数。
     */
    public static final Set<String> HTTP_BASE_URL_KEYS = PlatformConfigCachePolicy.cacheableKeys();

    /**
     * 邮件模板中自动注入的访问地址变量与系统参数键映射。
     */
    public static final Map<String, String> EMAIL_BASE_URL_VARIABLE_KEYS = Map.of(
            "gatewayBaseUrl", GATEWAY_BASE_URL,
            "checkoutBaseUrl", CHECKOUT_FRONTEND_BASE_URL,
            "merchantSystemBaseUrl", MERCHANT_FRONTEND_BASE_URL,
            "adminSystemBaseUrl", ADMIN_FRONTEND_BASE_URL
    );

    private SystemConfigKeys() {
    }
}
