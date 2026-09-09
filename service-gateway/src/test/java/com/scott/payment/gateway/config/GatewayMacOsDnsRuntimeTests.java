package com.scott.payment.gateway.config;

import io.netty.resolver.dns.macos.MacOSDnsServerAddressStreamProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : GatewayMacOsDnsRuntimeTests
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 验证 Gateway 在当前 macOS JVM 架构上能够加载 Netty 原生 DNS 解析器。
 * @status : create
 */
class GatewayMacOsDnsRuntimeTests {

    /** macOS 运行时必须提供与 JVM 架构匹配的 native resolver，不能静默依赖系统 DNS 降级。 */
    @Test
    @EnabledOnOs(OS.MAC)
    void shouldLoadNativeDnsResolverForCurrentJvmArchitecture() {
        assertDoesNotThrow(MacOSDnsServerAddressStreamProvider::ensureAvailability,
                () -> "Netty macOS DNS resolver is unavailable for JVM architecture "
                        + System.getProperty("os.arch"));
    }
}
