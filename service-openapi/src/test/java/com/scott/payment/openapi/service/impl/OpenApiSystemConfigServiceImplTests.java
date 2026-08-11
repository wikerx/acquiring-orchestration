package com.scott.payment.openapi.service.impl;

import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.db.systemconfig.service.SystemConfigReadService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** OpenAPI 统一系统参数读取契约测试。 */
class OpenApiSystemConfigServiceImplTests {

    /** 必需参数存在时必须返回公共读取器提供的启用值。 */
    @Test
    void shouldReturnRequiredValueFromSharedReader() {
        SystemConfigReadService readService = mock(SystemConfigReadService.class);
        when(readService.findEnabledValue("platform.checkout.frontend-base-url"))
                .thenReturn(Optional.of("https://checkout.example.com"));
        OpenApiSystemConfigServiceImpl service = new OpenApiSystemConfigServiceImpl(readService);

        String result = service.requiredEnabledValue("platform.checkout.frontend-base-url");

        assertThat(result).isEqualTo("https://checkout.example.com");
        verify(readService).findEnabledValue("platform.checkout.frontend-base-url");
    }

    /** 停用、删除、缺失或空值配置必须继续按必需参数缺失处理。 */
    @Test
    void shouldRejectUnavailableRequiredValue() {
        SystemConfigReadService readService = mock(SystemConfigReadService.class);
        when(readService.findEnabledValue("platform.checkout.frontend-base-url"))
                .thenReturn(Optional.empty());
        OpenApiSystemConfigServiceImpl service = new OpenApiSystemConfigServiceImpl(readService);

        assertThatThrownBy(() -> service.requiredEnabledValue(
                "platform.checkout.frontend-base-url"
        )).isInstanceOf(ApiException.class)
                .hasMessageContaining("system config is not enabled or empty");
    }
}
