package com.scott.payment.component.core.cache;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PlatformConfigCachePolicyTests
 * @date : 2026-07-30 21:10
 * @email : scott_x@163.com
 * @description : 验证平台配置缓存只接收明确登记的公开地址，避免密钥或任意配置进入 Redis
 * @status : create
 */
@Slf4j
class PlatformConfigCachePolicyTests {

    /**
     * 只有四个跨服务公开访问地址允许进入平台配置缓存，空白和敏感配置必须绕过缓存。
     */
    @Test
    void shouldAllowOnlyRegisteredNonSensitivePlatformConfigKeys() {
        log.info("测试平台配置缓存白名单，输入包含公开地址、空白值和模拟敏感配置键");

        assertThat(PlatformConfigCachePolicy.isCacheable("platform.gateway.base-url")).isTrue();
        assertThat(PlatformConfigCachePolicy.isCacheable(" platform.checkout.frontend-base-url ")).isTrue();
        assertThat(PlatformConfigCachePolicy.isCacheable("platform.merchant.frontend-base-url")).isTrue();
        assertThat(PlatformConfigCachePolicy.isCacheable("platform.admin.frontend-base-url")).isTrue();
        assertThat(PlatformConfigCachePolicy.isCacheable("platform.gateway.api-secret")).isFalse();
        assertThat(PlatformConfigCachePolicy.isCacheable(" ")).isFalse();

        log.info("平台配置缓存白名单验证完成，未登记配置均被拒绝进入缓存");
    }
}
