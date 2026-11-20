package com.scott.payment.link.service;

import com.baomidou.dynamic.datasource.aop.DynamicDataSourceAnnotationInterceptor;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.link.mapper.PaymentLinkCapabilityMapper;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** 主库能力读取通过真实路由拦截器验证，快照复用不访问调用方的副本路由。 */
class PaymentLinkDatabaseCapabilitiesDataSourceTest {
    @Test
    void allDatabaseEntriesReadMasterAndRestoreCallerRoute() {
        var mapper = mock(PaymentLinkCapabilityMapper.class, invocation -> {
            assertThat(DynamicDataSourceContextHolder.peek()).isEqualTo(DataSourceName.MASTER);
            return RETURNS_DEFAULTS.answer(invocation);
        });
        var factory = new ProxyFactory(new PaymentLinkDatabaseCapabilities(mapper));
        factory.setProxyTargetClass(true);
        factory.addAdvice(new DynamicDataSourceAnnotationInterceptor(true, null));
        var capabilities = (PaymentLinkDatabaseCapabilities) factory.getProxy();
        DynamicDataSourceContextHolder.push("caller-replica");
        try {
            capabilities.resolve("merchant-1", List.of(), "USD");
            assertThat(DynamicDataSourceContextHolder.peek()).isEqualTo("caller-replica");
            capabilities.profile("merchant-1");
            assertThat(DynamicDataSourceContextHolder.peek()).isEqualTo("caller-replica");
            var snapshot = capabilities.snapshot("merchant-1");
            assertThat(DynamicDataSourceContextHolder.peek()).isEqualTo("caller-replica");
            clearInvocations(mapper);
            assertThat(snapshot.resolve("merchant-1", List.of(), "USD")).isEmpty();
            verifyNoInteractions(mapper);
        } finally {
            DynamicDataSourceContextHolder.clear();
        }
    }
}
