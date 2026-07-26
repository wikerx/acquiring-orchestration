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
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiMerchantKeyMaterialServiceTest
 * @date : 2026-06-25 19:11
 * @email : scott_x@163.com
 * @description : OpenApiMerchantKeyMaterialServiceTest 自动化测试类，用于验证对应模块的业务规则、异常边界和回归场景，位于 公共组件层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
class OpenApiMerchantKeyMaterialServiceTest {

    /**
     * MERCHANT ID 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String MERCHANT_ID = "200046";
    /**
     * MERCHANT NAME 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；敏感或可识别字段，日志输出必须脱敏。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String MERCHANT_NAME = "Scott Test Merchant 200046";

    /**
     * 商户 OpenAPI敏感或密钥相关字段，日志和接口展示必须脱敏，必要时仅保存密文。
     */
    private BaseMerchantResponseKeyMapper responseKeyMapper;
    /**
     * material Service 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private OpenApiMerchantKeyMaterialService materialService;
    /**
     * material 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
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
     * Admin 侧导出地址来自系统参数解析器时，查询展示和接入配置必须使用解析后的 gateway 地址。
     */
    @Test
    void shouldUseResolvedOpenApiBaseUrlForMaterialViewAndExportConfig() {
        String gatewayBaseUrl = "https://gateway.local.test";
        OpenApiMerchantKeyExportProperties exportProperties = new OpenApiMerchantKeyExportProperties();
        exportProperties.setOpenApiBaseUrl("https://ignored.example.com");
        exportProperties.setSdkVersion("0.1.0-SNAPSHOT");
        exportProperties.setCryptoMode("RSA-OAEP-256+A256GCM");
        OpenApiKeyExportService exportService = new OpenApiKeyExportService(() -> gatewayBaseUrl);
        OpenApiMerchantKeyMaterialService service = new OpenApiMerchantKeyMaterialService(
                mockMerchantInfoMapper(),
                mockJwtKeyMapper(),
                mockPlatformPayloadKeyMapper(),
                responseKeyMapper,
                new OpenApiKeyMaterialFactory(),
                exportService,
                exportProperties,
                () -> gatewayBaseUrl
        );

        OpenApiMerchantKeyMaterialVO vo = service.queryMaterial(MERCHANT_ID);
        String config = service.copy(MERCHANT_ID, configTextRequest()).getContent();

        assertThat(vo.getOpenApiBaseUrl()).isEqualTo(gatewayBaseUrl);
        assertThat(config).contains("merchant.openapi.base-url=" + gatewayBaseUrl);
        assertThat(config).doesNotContain("https://ignored.example.com");
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

    private OpenApiKeyExportRequest configTextRequest() {
        OpenApiKeyExportRequest request = new OpenApiKeyExportRequest();
        request.setKeyType(OpenApiKeyType.MERCHANT_CONFIG_TEXT);
        request.setExportFormat(OpenApiKeyExportFormat.TEXT);
        return request;
    }

    private BaseMerchantInfoMapper mockMerchantInfoMapper() {
        BaseMerchantInfoMapper mapper = mock(BaseMerchantInfoMapper.class);
        when(mapper.selectOne(any())).thenReturn(merchant());
        return mapper;
    }

    private BaseMerchantJwtKeyMapper mockJwtKeyMapper() {
        BaseMerchantJwtKeyMapper mapper = mock(BaseMerchantJwtKeyMapper.class);
        when(mapper.selectOne(any())).thenReturn(jwtKey());
        return mapper;
    }

    private BasePlatformPayloadKeyMapper mockPlatformPayloadKeyMapper() {
        BasePlatformPayloadKeyMapper mapper = mock(BasePlatformPayloadKeyMapper.class);
        when(mapper.selectOne(any())).thenReturn(platformKey());
        return mapper;
    }
}
