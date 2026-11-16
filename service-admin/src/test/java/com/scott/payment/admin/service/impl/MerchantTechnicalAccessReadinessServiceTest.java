package com.scott.payment.admin.service.impl;

import com.scott.payment.admin.mapper.MerchantActivationReadinessMapper;
import com.scott.payment.component.security.openapi.OpenApiMerchantKeyMaterialService;
import com.scott.payment.component.security.openapi.OpenApiMerchantKeyMaterialVO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 商户技术接入门禁测试，覆盖凭证、IP 白名单和必需店铺网址语义。
 */
class MerchantTechnicalAccessReadinessServiceTest {

    @Test
    void shouldPassWhenCredentialsAndRequiredAccessRulesAreReady() {
        OpenApiMerchantKeyMaterialService keyService = mock(OpenApiMerchantKeyMaterialService.class);
        MerchantActivationReadinessMapper mapper = mock(MerchantActivationReadinessMapper.class);
        when(keyService.queryMaterial("MTEST0001")).thenReturn(enabledMaterial());
        when(mapper.countActiveIpWhitelists("MTEST0001")).thenReturn(1L);
        when(mapper.countActiveSourceUrlRules("MTEST0001", LocalDateTime.of(2026, 11, 4, 10, 0)))
                .thenReturn(1L);
        MerchantTechnicalAccessReadinessService service =
                new MerchantTechnicalAccessReadinessService(keyService, mapper);

        assertThat(service.evaluate(" MTEST0001 ", LocalDateTime.of(2026, 11, 4, 10, 0)))
                .isEmpty();
    }

    @Test
    void shouldReportEveryMissingTechnicalRequirement() {
        OpenApiMerchantKeyMaterialService keyService = mock(OpenApiMerchantKeyMaterialService.class);
        MerchantActivationReadinessMapper mapper = mock(MerchantActivationReadinessMapper.class);
        MerchantTechnicalAccessReadinessService service =
                new MerchantTechnicalAccessReadinessService(keyService, mapper);

        assertThat(service.evaluate("MTEST0001", LocalDateTime.of(2026, 11, 4, 10, 0)))
                .containsExactly(
                        "OPENAPI_CREDENTIALS_NOT_READY",
                        "OPENAPI_IP_WHITELIST_NOT_READY",
                        "SOURCE_URL_NOT_READY");
    }

    @Test
    void shouldRequireStoreWebsiteWithoutOptionalRiskFunctionConfiguration() {
        OpenApiMerchantKeyMaterialService keyService = mock(OpenApiMerchantKeyMaterialService.class);
        MerchantActivationReadinessMapper mapper = mock(MerchantActivationReadinessMapper.class);
        when(keyService.queryMaterial("MTEST0001")).thenReturn(enabledMaterial());
        when(mapper.countActiveIpWhitelists("MTEST0001")).thenReturn(1L);
        MerchantTechnicalAccessReadinessService service =
                new MerchantTechnicalAccessReadinessService(keyService, mapper);

        assertThat(service.evaluate("MTEST0001", LocalDateTime.of(2026, 11, 4, 10, 0)))
                .containsExactly("SOURCE_URL_NOT_READY");
    }

    private OpenApiMerchantKeyMaterialVO enabledMaterial() {
        OpenApiMerchantKeyMaterialVO material = new OpenApiMerchantKeyMaterialVO();
        material.setJwtKeyStatus("ENABLED");
        material.setPlatformPayloadKeyStatus("ENABLED");
        material.setMerchantResponseKeyStatus("ENABLED");
        material.setMerchantResponsePrivateKeyAvailable(true);
        return material;
    }
}
