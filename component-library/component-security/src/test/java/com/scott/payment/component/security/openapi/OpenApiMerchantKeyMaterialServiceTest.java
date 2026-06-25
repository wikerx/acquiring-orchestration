package com.scott.payment.component.security.openapi;

import com.scott.payment.component.db.auth.entity.BaseMerchantInfoDO;
import com.scott.payment.component.db.auth.entity.BaseMerchantJwtKeyDO;
import com.scott.payment.component.db.auth.entity.BaseMerchantResponseKeyDO;
import com.scott.payment.component.db.auth.entity.BasePlatformPayloadKeyDO;
import com.scott.payment.component.db.auth.mapper.BaseMerchantInfoMapper;
import com.scott.payment.component.db.auth.mapper.BaseMerchantJwtKeyMapper;
import com.scott.payment.component.db.auth.mapper.BaseMerchantResponseKeyMapper;
import com.scott.payment.component.db.auth.mapper.BasePlatformPayloadKeyMapper;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.security.key.OpenApiKeyMaterialFactory;
import com.scott.payment.component.security.key.OpenApiKeyMaterialFactory.OpenApiMerchantOnboardingMaterial;
import com.scott.payment.component.security.key.OpenApiKeyMaterialFactory.RsaKeyMaterial;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * OpenAPI 密钥材料导出服务回归测试，覆盖管理端查看/复制各类密钥时的材料分支。
 */
class OpenApiMerchantKeyMaterialServiceTest {

    private static final String MERCHANT_ID = "200046";
    private static final String MERCHANT_NAME = "Scott Test Merchant 200046";

    private BaseMerchantResponseKeyMapper responseKeyMapper;
    private OpenApiMerchantKeyMaterialService materialService;
    private OpenApiMerchantOnboardingMaterial material;

    @BeforeEach
    void setUp() {
        OpenApiKeyMaterialFactory keyMaterialFactory = new OpenApiKeyMaterialFactory();
        material = keyMaterialFactory.generateDemoOnboardingMaterial(MERCHANT_ID);

        BaseMerchantInfoMapper merchantInfoMapper = mock(BaseMerchantInfoMapper.class);
        BaseMerchantJwtKeyMapper jwtKeyMapper = mock(BaseMerchantJwtKeyMapper.class);
        BasePlatformPayloadKeyMapper platformPayloadKeyMapper = mock(BasePlatformPayloadKeyMapper.class);
        responseKeyMapper = mock(BaseMerchantResponseKeyMapper.class);

        when(merchantInfoMapper.selectOne(any())).thenReturn(merchant());
        when(jwtKeyMapper.selectOne(any())).thenReturn(jwtKey());
        when(platformPayloadKeyMapper.selectOne(any())).thenReturn(platformKey());
        when(responseKeyMapper.selectOne(any())).thenReturn(responseKey(true));

        OpenApiMerchantKeyExportProperties exportProperties = new OpenApiMerchantKeyExportProperties();
        exportProperties.setOpenApiBaseUrl("http://127.0.0.1:8004");
        exportProperties.setSdkVersion("0.1.0-SNAPSHOT");
        exportProperties.setCryptoMode("RSA-OAEP-256+A256GCM");

        OpenApiKeyExportService exportService = new OpenApiKeyExportService(exportProperties);
        materialService = new OpenApiMerchantKeyMaterialService(
                merchantInfoMapper,
                jwtKeyMapper,
                platformPayloadKeyMapper,
                responseKeyMapper,
                keyMaterialFactory,
                exportService,
                exportProperties
        );
    }

    /**
     * 管理端查看平台公钥、平台私钥、响应公钥和响应私钥时必须分别读取自己的原始字段。
     */
    @Test
    void shouldCopyEachSingleKeyMaterialFromItsOwnSource() {
        assertThat(copy(OpenApiKeyType.PLATFORM_PUBLIC_KEY))
                .isEqualTo(material.platformPayloadKey().publicKeyX509Base64());
        assertThat(copy(OpenApiKeyType.PLATFORM_PRIVATE_KEY))
                .isEqualTo(material.platformPayloadKey().privateKeyPkcs8Base64());
        assertThat(copy(OpenApiKeyType.MERCHANT_RESPONSE_PUBLIC_KEY))
                .isEqualTo(material.merchantResponseKey().publicKeyX509Base64());
        assertThat(copy(OpenApiKeyType.MERCHANT_RESPONSE_PRIVATE_KEY))
                .isEqualTo(material.merchantResponseKey().privateKeyPkcs8Base64());
    }

    /**
     * 响应公钥是平台响应加密使用的公开材料，历史数据缺少响应私钥时也必须允许单独查看公钥。
     */
    @Test
    void shouldCopyMerchantResponsePublicKeyWithoutRequiringPrivateKey() {
        when(responseKeyMapper.selectOne(any())).thenReturn(responseKey(false));

        assertThat(copy(OpenApiKeyType.MERCHANT_RESPONSE_PUBLIC_KEY))
                .isEqualTo(material.merchantResponseKey().publicKeyX509Base64());
    }

    /**
     * JWT 单独下载只依赖商户号和 JWT 密钥，不能因为历史响应私钥缺失而失败。
     */
    @Test
    void shouldDownloadJwtKeyWithoutRequiringResponsePrivateKey() {
        when(responseKeyMapper.selectOne(any())).thenReturn(responseKey(false));

        OpenApiKeyDownloadFile file = materialService.download(MERCHANT_ID, OpenApiKeyType.JWT_KEY, OpenApiKeyExportFormat.TXT);
        String content = new String(file.getContent());

        assertThat(file.getFileName()).isEqualTo(MERCHANT_ID + "-merchant-jwt-key.txt");
        assertThat(content).contains("merchant.jwt.secret=" + material.merchantCredential().merchantKey());
    }

    /**
     * 完整接入配置必须包含商户响应私钥；历史私钥缺失时不能生成不完整包。
     */
    @Test
    void shouldRejectFullIntegrationMaterialWhenResponsePrivateKeyIsMissing() {
        when(responseKeyMapper.selectOne(any())).thenReturn(responseKey(false));

        assertThatThrownBy(() -> materialService.download(MERCHANT_ID, OpenApiKeyType.SDK_KIT, OpenApiKeyExportFormat.ZIP))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("商户响应私钥未配置");
        assertThatThrownBy(() -> copy(OpenApiKeyType.MERCHANT_CONFIG))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("商户响应私钥未配置");
    }

    private String copy(OpenApiKeyType keyType) {
        OpenApiKeyExportRequest request = new OpenApiKeyExportRequest();
        request.setKeyType(keyType);
        request.setExportFormat(OpenApiKeyExportFormat.TEXT);
        return materialService.copy(MERCHANT_ID, request).getContent();
    }

    private BaseMerchantInfoDO merchant() {
        BaseMerchantInfoDO row = new BaseMerchantInfoDO();
        row.setMerchantId(MERCHANT_ID);
        row.setMerchantName(MERCHANT_NAME);
        row.setDeleted(0);
        return row;
    }

    private BaseMerchantJwtKeyDO jwtKey() {
        BaseMerchantJwtKeyDO row = new BaseMerchantJwtKeyDO();
        row.setMerchantId(MERCHANT_ID);
        row.setMerchantKey(material.merchantCredential().merchantKey());
        row.setAlgorithm(material.merchantCredential().jwtAlgorithm());
        row.setExpiresSeconds(material.merchantCredential().jwtExpiresSeconds());
        row.setEnabled(1);
        row.setDeleted(0);
        return row;
    }

    private BasePlatformPayloadKeyDO platformKey() {
        RsaKeyMaterial platformKey = material.platformPayloadKey();
        BasePlatformPayloadKeyDO row = new BasePlatformPayloadKeyDO();
        row.setMerchantId(MERCHANT_ID);
        row.setPublicKeyX509Base64(platformKey.publicKeyX509Base64());
        row.setPrivateKeyPkcs8Base64(platformKey.privateKeyPkcs8Base64());
        row.setAlgorithm("RSA-OAEP-256+A256GCM");
        row.setKeySize(platformKey.keySize());
        row.setEnabled(1);
        row.setDeleted(0);
        return row;
    }

    private BaseMerchantResponseKeyDO responseKey(boolean includePrivateKey) {
        RsaKeyMaterial responseKey = material.merchantResponseKey();
        BaseMerchantResponseKeyDO row = new BaseMerchantResponseKeyDO();
        row.setMerchantId(MERCHANT_ID);
        row.setPublicKeyX509Base64(responseKey.publicKeyX509Base64());
        row.setPrivateKeyPkcs8Base64(includePrivateKey ? responseKey.privateKeyPkcs8Base64() : null);
        row.setAlgorithm("RSA-OAEP-256+A256GCM");
        row.setKeySize(responseKey.keySize());
        row.setEnabled(1);
        row.setDeleted(0);
        return row;
    }
}
