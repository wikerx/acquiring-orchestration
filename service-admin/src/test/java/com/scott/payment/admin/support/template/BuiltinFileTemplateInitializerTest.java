package com.scott.payment.admin.support.template;

import com.scott.payment.admin.application.risk.AdminRiskManagementApplicationService;
import com.scott.payment.admin.application.risk.AdminTradeBlackWorkbookApplicationService;
import com.scott.payment.admin.application.system.AdminFileTemplateApplicationService;
import com.scott.payment.admin.dto.system.FileTemplateDTOs.Detail;
import com.scott.payment.admin.dto.system.FileTemplateDTOs.Version;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 系统内置模板初始化的幂等和发布回归测试。 */
class BuiltinFileTemplateInitializerTest {

    @AfterEach
    void clearContext() {
        InternalAuthContextHolder.clear();
    }

    @Test
    void shouldSkipDefinitionsThatAlreadyHavePublishedVersion() {
        AdminFileTemplateApplicationService templateService = mock(AdminFileTemplateApplicationService.class);
        Detail published = detail(9L, 10L, 1L);
        when(templateService.findDetailByTemplateCode(anyString())).thenReturn(Optional.of(published));
        BuiltinFileTemplateInitializer initializer = new BuiltinFileTemplateInitializer(
                templateService,
                mock(AdminRiskManagementApplicationService.class),
                mock(AdminTradeBlackWorkbookApplicationService.class));

        initializer.run(new DefaultApplicationArguments());

        verify(templateService, never()).create(any());
        verify(templateService, never()).uploadVersion(any(), any(), any(), any(), any(), any(), any());
        verify(templateService, never()).publish(any(), any(), any());
        assertThat(InternalAuthContextHolder.get()).isNull();
    }

    @Test
    void shouldCreateUploadAndPublishMissingBuiltinTemplate() {
        AdminFileTemplateApplicationService templateService = mock(AdminFileTemplateApplicationService.class);
        AdminRiskManagementApplicationService riskService = mock(AdminRiskManagementApplicationService.class);
        AdminTradeBlackWorkbookApplicationService tradeBlackService =
                mock(AdminTradeBlackWorkbookApplicationService.class);
        String targetCode = BuiltinFileTemplateCatalog.all().get(0).templateCode();
        Detail published = detail(99L, 100L, 1L);
        when(templateService.findDetailByTemplateCode(anyString())).thenReturn(Optional.of(published));
        when(templateService.findDetailByTemplateCode(targetCode)).thenReturn(Optional.empty());
        when(templateService.create(any())).thenReturn(detail(1L, null, 0L));
        Version version = new Version();
        version.setId(2L);
        when(templateService.uploadVersion(eq(1L), eq(1), eq("1.0"), anyString(), anyString(), any(), any()))
                .thenReturn(version);
        when(riskService.builtinTemplateContent(anyString(), anyString(), any()))
                .thenReturn(new byte[]{1, 2, 3});
        BuiltinFileTemplateInitializer initializer = new BuiltinFileTemplateInitializer(
                templateService, riskService, tradeBlackService);

        initializer.run(new DefaultApplicationArguments());

        verify(templateService).create(any());
        verify(templateService).uploadVersion(eq(1L), eq(1), eq("1.0"),
                eq("BUILTIN_" + targetCode + "_V1"), anyString(), any(), any());
        ArgumentCaptor<com.scott.payment.admin.dto.system.FileTemplateDTOs.PublishRequest> publish =
                ArgumentCaptor.forClass(com.scott.payment.admin.dto.system.FileTemplateDTOs.PublishRequest.class);
        verify(templateService).publish(eq(1L), eq(2L), publish.capture());
        assertThat(publish.getValue().getExpectedTemplateVersion()).isZero();
        assertThat(publish.getValue().getRequestId()).isEqualTo("PUBLISH_" + targetCode + "_V1");
        assertThat(InternalAuthContextHolder.get()).isNull();
    }

    private Detail detail(Long id, Long currentVersionId, Long version) {
        Detail detail = new Detail();
        detail.setId(id);
        detail.setCurrentVersionId(currentVersionId);
        detail.setVersion(version);
        return detail;
    }
}
