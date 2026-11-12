package com.scott.payment.job.config;

import com.scott.payment.component.db.auth.service.SystemAuthService;
import com.scott.payment.component.core.security.InternalRequestReplayGuard;
import com.scott.payment.component.web.auth.InternalAuthInterceptor;
import com.scott.payment.component.web.internal.InternalServiceAuthInterceptor;
import com.scott.payment.component.web.internal.InternalServiceAuthProperties;
import org.junit.jupiter.api.Test;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.handler.MappedInterceptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Job 内部接口鉴权边界测试，确保后台导出只跳过管理员会话而不跳过服务签名。
 */
class JobAuthWebMvcConfigTest {

    /**
     * 导出专用入口必须命中 HMAC 拦截器，同时排除管理员浏览器会话拦截器。
     */
    @Test
    void shouldRequireHmacButNotBrowserSessionForRunLogExport() {
        InternalServiceAuthProperties properties = new InternalServiceAuthProperties();
        properties.setEnabled(false);
        JobAuthWebMvcConfig config = new JobAuthWebMvcConfig(
                mock(SystemAuthService.class), properties, mock(InternalRequestReplayGuard.class));
        InspectableInterceptorRegistry registry = new InspectableInterceptorRegistry();
        config.addInterceptors(registry);

        MappedInterceptor hmac = interceptor(registry.interceptors(), InternalServiceAuthInterceptor.class);
        MappedInterceptor browserSession = interceptor(registry.interceptors(), InternalAuthInterceptor.class);
        AntPathMatcher pathMatcher = new AntPathMatcher();
        assertThat(hmac.matches("/internal/job/logs/export/search", pathMatcher)).isTrue();
        assertThat(browserSession.matches("/internal/job/logs/export/search", pathMatcher)).isFalse();
        assertThat(hmac.matches("/internal/job/logs/search", pathMatcher)).isTrue();
        assertThat(browserSession.matches("/internal/job/logs/search", pathMatcher)).isTrue();
    }

    /**
     * 按实际拦截器类型取得 Spring 生成的路径映射包装器。
     */
    private MappedInterceptor interceptor(List<Object> interceptors, Class<?> type) {
        return interceptors.stream()
                .map(MappedInterceptor.class::cast)
                .filter(item -> type.isInstance(item.getInterceptor()))
                .findFirst()
                .orElseThrow();
    }

    /**
     * 暴露 Spring 受保护的拦截器注册结果，仅用于验证路径包含和排除规则。
     */
    private static final class InspectableInterceptorRegistry extends InterceptorRegistry {
        private List<Object> interceptors() {
            return getInterceptors();
        }
    }
}
