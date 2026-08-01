package com.scott.payment.openapi.service.impl;

import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.db.auth.model.MerchantRuntimeProfile;
import com.scott.payment.component.db.auth.service.MerchantRuntimeProfileCacheService;
import com.scott.payment.component.security.crypto.OpenApiPayloadCrypto;
import com.scott.payment.component.security.key.OpenApiKeyMaterialFactory;
import com.scott.payment.openapi.entity.MerchantInfoDO;
import com.scott.payment.openapi.mapper.MerchantInfoMapper;
import com.scott.payment.openapi.mapper.MerchantJwtKeyMapper;
import com.scott.payment.openapi.mapper.MerchantResponseKeyMapper;
import com.scott.payment.openapi.mapper.PlatformPayloadKeyMapper;
import com.scott.payment.openapi.security.OpenApiMerchantSecretCache;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantSecurityServiceSharedCacheTests
 * @date : 2026-08-01 13:00
 * @email : scott_x@163.com
 * @description : 验证 OpenAPI 商户状态校验复用共享非敏感 merchant:info 画像且不回退到私有商户 Mapper
 * @status : create
 */
class MerchantSecurityServiceSharedCacheTests {

    /**
     * 验证启用商户从共享画像适配为既有 OpenAPI 契约，敏感联系字段保持为空。
     */
    @Test
    void shouldReadActiveMerchantFromSharedRuntimeProfile() {
        TestFixture fixture = fixture();
        MerchantRuntimeProfile profile = runtimeProfile(1);
        when(fixture.runtimeProfileCacheService().findRuntimeProfile("200045")).thenReturn(profile);

        MerchantInfoDO actual = fixture.service().getActiveMerchant("200045");

        assertThat(actual.getMerchantId()).isEqualTo("200045");
        assertThat(actual.getMerchantName()).isEqualTo("Acquiring Merchant");
        assertThat(actual.getSettlementCurrency()).isEqualTo("USD");
        assertThat(actual.getContactEmail()).isNull();
        assertThat(actual.getAddressLine()).isNull();
        verifyNoInteractions(fixture.merchantInfoMapper());
    }

    /**
     * 验证冻结商户即使存在永久缓存也不能通过 OpenAPI 商户状态校验。
     */
    @Test
    void shouldRejectInactiveMerchantFromSharedRuntimeProfile() {
        TestFixture fixture = fixture();
        when(fixture.runtimeProfileCacheService().findRuntimeProfile("200045"))
                .thenReturn(runtimeProfile(2));

        assertThatThrownBy(() -> fixture.service().getActiveMerchant("200045"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Merchant config not found");
        verifyNoInteractions(fixture.merchantInfoMapper());
    }

    /**
     * 验证 JWT 鉴权先校验共享商户状态，再从不落 Redis 的短时密钥缓存读取 Secret。
     */
    @Test
    void shouldReadMerchantKeyFromLocalSecretCacheAfterMerchantValidation() {
        TestFixture fixture = fixture();
        when(fixture.runtimeProfileCacheService().findRuntimeProfile("200045"))
                .thenReturn(runtimeProfile(1));
        when(fixture.merchantSecretCache().getMerchantKey("200045"))
                .thenReturn("short-lived-local-secret");

        String actual = fixture.service().getMerchantKey("200045");

        assertThat(actual).isEqualTo("short-lived-local-secret");
        verify(fixture.runtimeProfileCacheService()).findRuntimeProfile("200045");
        verify(fixture.merchantSecretCache()).getMerchantKey("200045");
        verifyNoInteractions(fixture.merchantInfoMapper());
    }

    private TestFixture fixture() {
        MerchantInfoMapper merchantInfoMapper = mock(MerchantInfoMapper.class);
        MerchantRuntimeProfileCacheService runtimeProfileCacheService =
                mock(MerchantRuntimeProfileCacheService.class);
        OpenApiMerchantSecretCache merchantSecretCache = mock(OpenApiMerchantSecretCache.class);
        MerchantSecurityServiceImpl service = new MerchantSecurityServiceImpl(
                merchantInfoMapper,
                mock(MerchantJwtKeyMapper.class),
                mock(PlatformPayloadKeyMapper.class),
                mock(MerchantResponseKeyMapper.class),
                mock(OpenApiPayloadCrypto.class),
                mock(OpenApiKeyMaterialFactory.class),
                runtimeProfileCacheService,
                merchantSecretCache
        );
        return new TestFixture(service, merchantInfoMapper, runtimeProfileCacheService, merchantSecretCache);
    }

    private MerchantRuntimeProfile runtimeProfile(int status) {
        MerchantRuntimeProfile profile = new MerchantRuntimeProfile();
        profile.setId(1L);
        profile.setMerchantId("200045");
        profile.setMerchantName("Acquiring Merchant");
        profile.setMerchantShortName("Acquiring");
        profile.setMerchantStatus(status);
        profile.setCountryCode("USA");
        profile.setSettlementCurrency("USD");
        profile.setTimezone("Asia/Shanghai");
        profile.setRiskLevel(2);
        return profile;
    }

    private record TestFixture(
            MerchantSecurityServiceImpl service,
            MerchantInfoMapper merchantInfoMapper,
            MerchantRuntimeProfileCacheService runtimeProfileCacheService,
            OpenApiMerchantSecretCache merchantSecretCache) {
    }
}
