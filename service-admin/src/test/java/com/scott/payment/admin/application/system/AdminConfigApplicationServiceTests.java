package com.scott.payment.admin.application.system;

import com.scott.payment.admin.dto.SysConfigDTO;
import com.scott.payment.admin.dto.SysConfigSaveRequest;
import com.scott.payment.admin.service.AdminConfigService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 管理端系统参数保存后的缓存预热行为测试。
 */
class AdminConfigApplicationServiceTests {

    @Test
    void shouldReturnFreshConfigFromSharedReaderAfterSaving() {
        AdminConfigService configService = mock(AdminConfigService.class);
        SysConfigSaveRequest request = new SysConfigSaveRequest();
        request.setConfigKey("platform.gateway.base-url");

        SysConfigDTO saved = new SysConfigDTO();
        saved.setConfigKey("platform.gateway.base-url");
        saved.setConfigValue("https://old.example.com");
        SysConfigDTO refreshed = new SysConfigDTO();
        refreshed.setConfigKey("platform.gateway.base-url");
        refreshed.setConfigValue("https://gateway.example.com");

        when(configService.saveConfig(request)).thenReturn(saved);
        when(configService.getConfigByKey("platform.gateway.base-url")).thenReturn(refreshed);
        AdminConfigApplicationService applicationService = new AdminConfigApplicationService(
                configService,
                null,
                null,
                null,
                null
        );

        SysConfigDTO result = applicationService.saveConfig(request);

        assertThat(result).isSameAs(refreshed);
    }
}
