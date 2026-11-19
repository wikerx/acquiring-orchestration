package com.scott.payment.merchant.config;

import com.scott.payment.link.config.PaymentLinkSettings;
import com.scott.payment.link.mapper.*;
import com.scott.payment.link.service.*;
import com.scott.payment.component.core.id.GlobalIdGenerator;
import com.scott.payment.component.db.auth.service.MerchantRuntimeProfileCacheService;
import com.scott.payment.component.db.iso.service.IsoDictionaryService;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 三端分别验证新管理模块的真实 bean 组合和缺省关闭行为，避免加入强制启动依赖。 */
class PaymentLinkManagementRuntimeTest {
    @Configuration(proxyBeanMethods = false)
    @Import({MerchantPaymentLinkProperties.class, PaymentLinkManagementService.class, PaymentLinkManagementPolicy.class,
            PaymentLinkDatabaseCapabilities.class, PaymentLinkTokenService.class})
    static class LinkSlice {
        @Bean PaymentLinkManagementMapper links() { return mock(PaymentLinkManagementMapper.class); }
        @Bean PaymentLinkManagementAccessMapper access() { return mock(PaymentLinkManagementAccessMapper.class); }
        @Bean PaymentLinkManagementEventMapper events() { return mock(PaymentLinkManagementEventMapper.class); }
        @Bean PaymentLinkCapabilityMapper capabilities() { return mock(PaymentLinkCapabilityMapper.class); }
        @Bean MerchantRuntimeProfileCacheService merchants() { return mock(MerchantRuntimeProfileCacheService.class); }
        @Bean IsoDictionaryService currencies() { return mock(IsoDictionaryService.class); }
        @Bean Validator validator() { return mock(Validator.class); }
        @Bean GlobalIdGenerator ids() { return () -> "isolated"; }
    }
    final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class)).withUserConfiguration(LinkSlice.class);
    @Test void absentLinkConfigurationKeepsServiceAvailableAndWritesClosed() {
        runner.run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(PaymentLinkManagementService.class);
            assertThat(context.getBean(PaymentLinkSettings.class).isEnabled()).isFalse();
        });
    }
    @Test void existingDedicatedKeysBindWithoutChangingServiceImportsOrProtocol() {
        runner.withPropertyValues("payment.link.enabled=true", "payment.link.storage-ready=true",
                "payment.link.frontend-base-url=https://pay.example.invalid", "payment.link.active-key-version=v1",
                "payment.link.signing-keys.v1=isolated-runtime-test-key-material-0001")
                .run(context -> { assertThat(context).hasNotFailed().hasSingleBean(PaymentLinkManagementService.class);
                    assertThat(context.getBean(PaymentLinkSettings.class).getActiveKeyVersion()).isEqualTo("v1"); });
    }
    @Test void enabledIncompleteSettingsFailClosedWithSanitizedDiagnostics() {
        runner.withPropertyValues("payment.link.enabled=true", "payment.link.storage-ready=true",
                "payment.link.frontend-base-url=https://pay.example.invalid", "payment.link.active-key-version=v1",
                "payment.link.signing-keys.v1=short-test-key")
                .run(context -> { assertThat(context).hasFailed(); assertThat(context.getStartupFailure())
                    .hasStackTraceContaining("keys of at least 32 bytes");
                    assertThat(context.getStartupFailure().getMessage()).doesNotContain("short-test-key"); });
    }
}
