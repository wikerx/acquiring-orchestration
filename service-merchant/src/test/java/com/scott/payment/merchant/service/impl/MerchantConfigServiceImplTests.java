package com.scott.payment.merchant.service.impl;

import com.scott.payment.component.db.systemconfig.service.SystemConfigReadService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantConfigServiceImplTests
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 商户服务统一系统参数读取契约测试。
 * @status : create
 */
class MerchantConfigServiceImplTests {

    /** 商户服务必须原样委托公共读取服务，不能维护私有缓存命名空间。 */
    @Test
    void shouldDelegateEnabledValueLookupToSharedReader() {
        SystemConfigReadService readService = mock(SystemConfigReadService.class);
        when(readService.findEnabledValue("platform.gateway.base-url"))
                .thenReturn(Optional.of("https://gateway.example.com"));
        MerchantConfigServiceImpl service = new MerchantConfigServiceImpl(readService);

        Optional<String> result = service.enabledConfigValue("platform.gateway.base-url");

        assertThat(result).contains("https://gateway.example.com");
        verify(readService).findEnabledValue("platform.gateway.base-url");
    }
}
