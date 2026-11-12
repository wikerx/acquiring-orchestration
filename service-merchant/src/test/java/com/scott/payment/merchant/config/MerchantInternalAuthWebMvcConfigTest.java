package com.scott.payment.merchant.config;

import com.scott.payment.component.core.security.InternalRequestReplayGuard;
import com.scott.payment.component.web.internal.InternalServiceAuthInterceptor;
import com.scott.payment.component.web.internal.InternalServiceAuthProperties;
import org.junit.jupiter.api.Test;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.handler.MappedInterceptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Merchant 内部来源接口 HMAC 启动门禁和路径边界测试。
 */
class MerchantInternalAuthWebMvcConfigTest {

    /**
     * 未配置调用方凭据时服务必须失败关闭，完整 Data 调用边允许启动。
     */
    @Test
    void shouldFailClosedUntilDataCallerCredentialIsProvided() {
        InternalRequestReplayGuard replayGuard = mock(InternalRequestReplayGuard.class);
        assertThatThrownBy(() -> new MerchantInternalAuthWebMvcConfig(
                new InternalServiceAuthProperties(), replayGuard))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("caller credentials");

        assertThatCode(() -> new MerchantInternalAuthWebMvcConfig(validProperties(), replayGuard))
                .doesNotThrowAnyException();
    }

    /**
     * Merchant 内部来源路径必须命中 HMAC 拦截器，浏览器 API 路径不得误入该拦截器。
     */
    @Test
    void shouldProtectOnlyInternalMerchantPaths() {
        MerchantInternalAuthWebMvcConfig config = new MerchantInternalAuthWebMvcConfig(
                validProperties(), mock(InternalRequestReplayGuard.class));
        InspectableInterceptorRegistry registry = new InspectableInterceptorRegistry();
        config.addInterceptors(registry);

        MappedInterceptor hmac = registry.interceptors().stream()
                .map(MappedInterceptor.class::cast)
                .filter(item -> item.getInterceptor() instanceof InternalServiceAuthInterceptor)
                .findFirst()
                .orElseThrow();
        AntPathMatcher pathMatcher = new AntPathMatcher();
        assertThat(hmac.matches("/internal/merchant/export-sources/refunds", pathMatcher)).isTrue();
        assertThat(hmac.matches("/api/merchant/export-tasks/search", pathMatcher)).isFalse();
    }

    /**
     * 构造只允许 service-data 读取 Merchant 导出安全投影的服务端凭据。
     */
    private InternalServiceAuthProperties validProperties() {
        InternalServiceAuthProperties.CallerCredential credential =
                new InternalServiceAuthProperties.CallerCredential();
        credential.setActiveSecret("unit-test-merchant-server-secret-at-least-32-bytes");
        credential.setAllowedPaths(List.of("/internal/merchant/export-sources/**"));
        InternalServiceAuthProperties properties = new InternalServiceAuthProperties();
        properties.setCallers(Map.of("service-data", credential));
        return properties;
    }

    /**
     * 暴露 Spring 生成的拦截器映射，仅用于断言路径边界。
     */
    private static final class InspectableInterceptorRegistry extends InterceptorRegistry {
        private List<Object> interceptors() {
            return getInterceptors();
        }
    }
}
