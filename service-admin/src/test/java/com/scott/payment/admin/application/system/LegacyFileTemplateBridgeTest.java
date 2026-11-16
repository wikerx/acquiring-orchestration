package com.scott.payment.admin.application.system;

import com.scott.payment.admin.dto.system.FileTemplateDTOs.Download;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 旧下载地址迁移桥必须优先命中模板中心，并在未登记时安全回退。 */
@ExtendWith(MockitoExtension.class)
class LegacyFileTemplateBridgeTest {

    @Mock
    private AdminFileTemplateApplicationService fileTemplateApplicationService;

    @AfterEach
    void clearLocaleContext() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void shouldReturnFalseWhenTemplateCenterHasNoPublishedVersion() {
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
        when(fileTemplateApplicationService.downloadCurrentIfPublished("RISK_BLACK_IMPORT", "zh-CN"))
                .thenReturn(Optional.empty());
        LegacyFileTemplateBridge bridge = new LegacyFileTemplateBridge(fileTemplateApplicationService);

        boolean handled = bridge.writeIfPublished("RISK_BLACK_IMPORT", new MockHttpServletResponse());

        assertThat(handled).isFalse();
        verify(fileTemplateApplicationService)
                .downloadCurrentIfPublished("RISK_BLACK_IMPORT", "zh-CN");
    }

    @Test
    void shouldWriteControlledDownloadWhenTemplateCenterHasPublishedVersion() throws Exception {
        LocaleContextHolder.setLocale(Locale.SIMPLIFIED_CHINESE);
        byte[] content = "template-content".getBytes(StandardCharsets.UTF_8);
        when(fileTemplateApplicationService.downloadCurrentIfPublished("RISK_BLACK_IMPORT", "zh-CN"))
                .thenReturn(Optional.of(new Download("风险黑名单导入模板.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", content)));
        LegacyFileTemplateBridge bridge = new LegacyFileTemplateBridge(fileTemplateApplicationService);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean handled = bridge.writeIfPublished("RISK_BLACK_IMPORT", response);

        assertThat(handled).isTrue();
        assertThat(response.getContentAsByteArray()).containsExactly(content);
        assertThat(response.getContentType())
                .isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(response.getHeader("Content-Disposition"))
                .isEqualTo("attachment;filename*=utf-8''%E9%A3%8E%E9%99%A9%E9%BB%91%E5%90%8D%E5%8D%95%E5%AF%BC%E5%85%A5%E6%A8%A1%E6%9D%BF.xlsx");
        assertThat(response.getHeader("Cache-Control"))
                .isEqualTo("no-store, no-cache, must-revalidate, max-age=0");
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
    }
}
