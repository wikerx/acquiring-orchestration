package com.scott.payment.admin.api.risk;

import com.scott.payment.admin.application.disputealert.DisputeAlertApplicationService;
import com.scott.payment.admin.dto.risk.DisputeAlertDtos.*;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class DisputeAlertHttpContractTest {

    @Test
    void credentialsRequireDedicatedPermissionAndExcludeBodiesFromAudit() throws NoSuchMethodException {
        var endpoint = AdminDisputeAlertConfigurationController.class.getMethod("replaceCredentials", long.class, CredentialUpdate.class);
        assertThat(endpoint.getAnnotation(RequiresPermission.class).value()).isEqualTo("channel:alert-platform:credential");
        var audit = endpoint.getAnnotation(OperationLog.class);
        assertThat(audit.recordRequest()).isFalse();
        assertThat(audit.recordResponse()).isFalse();
        assertThat(new CredentialUpdate(4L, "public-fixture", "private-fixture").toString()).doesNotContain("fixture");
    }

    @Test
    void providerCredentialWriteIsAuditedWithoutBodiesAndResponseHasOnlyRegistration() throws Exception {
        for (var endpoint : AdminDisputeAlertConfigurationController.class.getDeclaredMethods()) {
            if (!List.of("createProviderAccount", "updateProviderAccount").contains(endpoint.getName())) continue;
            assertThat(endpoint.getAnnotation(RequiresPermission.class).value()).isEqualTo("channel:alert-platform:account");
            var audit = endpoint.getAnnotation(OperationLog.class);
            assertThat(audit.recordRequest()).isFalse();
            assertThat(audit.recordResponse()).isFalse();
        }
        var service = mock(DisputeAlertApplicationService.class);
        var input = new ProviderAccountWrite(11L, null, "DEMO-NUVEI", Status.DRAFT,
                List.of(new ProviderProductInput(Product.ETHOCA, Status.DRAFT)), Map.of("usage", List.of("dmn")), Map.of("merchantSecretKey", "sample-value"));
        assertThat(input.toString()).doesNotContain("sample-value");
        when(service.createProviderAccount(input)).thenReturn(new AccountView(21L, 11L, "NUVEI", "Nuvei",
                "DEMO-NUVEI", Status.DRAFT, 1L, null, null, List.of(),
                new ProviderConfigView(Map.of("usage", List.of("dmn")), List.of("merchantSecretKey"), false)));
        var mvc = standaloneSetup(new AdminDisputeAlertConfigurationController(service)).build();

        mvc.perform(post("/admin/risk/dispute-alert/accounts/provider").contentType(MediaType.APPLICATION_JSON).content("""
                {"platformId":11,"merchantNumber":"DEMO-NUVEI","status":"DRAFT",
                 "products":[{"product":"ETHOCA","status":"DRAFT"}],"settings":{"usage":["dmn"]},
                 "credentials":{"merchantSecretKey":"sample-value"}}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.providerConfig.registered[0]").value("merchantSecretKey"))
                .andExpect(jsonPath("$.data.providerConfig.values.merchantSecretKey").doesNotExist())
                .andExpect(jsonPath("$.data.providerConfig.credentialCipherJson").doesNotExist());
        verify(service).createProviderAccount(input);
    }

    @Test
    void cardBrandOptionsReuseAlertPlatformPermission() throws NoSuchMethodException {
        var endpoint = AdminDisputeAlertConfigurationController.class.getMethod("cardBrandOptions", String.class);
        assertThat(endpoint.getAnnotation(RequiresPermission.class).value()).isEqualTo("channel:alert-platform:list");
    }

    @Test
    void requestParametersCanBeBoundWithoutJavaParameterMetadata() {
        assertThat(Arrays.stream(AdminDisputeAlertConfigurationController.class.getDeclaredMethods())
                .flatMap(method -> Arrays.stream(method.getParameters()))
                .map(parameter -> parameter.getAnnotation(RequestParam.class))
                .filter(annotation -> annotation != null).toList())
                .isNotEmpty()
                .allMatch(annotation -> !annotation.name().isBlank() || !annotation.value().isBlank());
    }

    @Test
    void midPickerPassesBothExactChannelAndSearchKeyword() throws Exception {
        var service = mock(DisputeAlertApplicationService.class);
        when(service.mids("SCOTT_CHANNEL_A", "123")).thenReturn(List.of());
        var mvc = standaloneSetup(new AdminDisputeAlertConfigurationController(service)).build();

        mvc.perform(get("/admin/risk/dispute-alert/mids")
                .param("channelCode", "SCOTT_CHANNEL_A").param("keyword", "123"))
                .andExpect(status().isOk());

        verify(service).mids("SCOTT_CHANNEL_A", "123");
    }

    @Test
    void createAccountDoesNotRequireOrExposeAnEnvironmentField() throws Exception {
        var service = mock(DisputeAlertApplicationService.class);
        var input = new AccountCreate(11L, "MER-FIXTURE", Status.DRAFT,
                List.of(new AccountProductInput(Product.ETHOCA, Status.DRAFT, null)));
        when(service.createAccount(input)).thenReturn(new AccountView(21L, 11L, "VERXA_SHIELD", "Verxa Shield",
                "MER-FIXTURE", Status.DRAFT, 1L, null, null, List.of(), null));
        var mvc = standaloneSetup(new AdminDisputeAlertConfigurationController(service)).build();

        mvc.perform(post("/admin/risk/dispute-alert/accounts").contentType(MediaType.APPLICATION_JSON).content("""
                {"platformId":11,"merchantNumber":"MER-FIXTURE","status":"DRAFT",
                 "products":[{"product":"ETHOCA","status":"DRAFT"}]}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.merchantNumber").value("MER-FIXTURE"))
                .andExpect(jsonPath("$.data.environment").doesNotExist());
        verify(service).createAccount(input);
    }
}
