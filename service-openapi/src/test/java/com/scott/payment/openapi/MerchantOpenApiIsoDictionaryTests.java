package com.scott.payment.openapi;

import com.alibaba.fastjson2.TypeReference;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.security.crypto.OpenApiPayloadCrypto;
import com.scott.payment.component.security.key.OpenApiKeyMaterialFactory;
import com.scott.payment.openapi.dto.security.MerchantSecurityMaterialDTO;
import com.scott.payment.openapi.service.MerchantSecurityService;
import com.scott.payment.openapi.support.MerchantOpenApiTestSupport;
import com.scott.payment.openapi.vo.iso.IsoCountryVO;
import com.scott.payment.openapi.vo.iso.IsoCurrencyVO;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.security.PrivateKey;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantOpenApiIsoDictionaryTests
 * @date : 2026-06-03 15:50
 * @email : scott_x@163.com
 * @description : 商户 200045 加密调用 ISO 国家地区与币种 OpenAPI 接口测试
 * @status : create
 */
@Slf4j
@AutoConfigureMockMvc
@ActiveProfiles("mysql-test")
@SpringBootTest(classes = OpenApiApplication.class)
@Sql(scripts = "/sql/openapi-merchant-security-schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class MerchantOpenApiIsoDictionaryTests {

    /**
     * 测试商户号，模拟商户 200045 调用开放接口。
     */
    private static final String MERCHANT_ID = "200045";

    /**
     * 国家地区查询接口地址。
     */
    private static final String COUNTRY_PATH = "/api/rest/iso/v1/countries/query";

    /**
     * 币种查询接口地址。
     */
    private static final String CURRENCY_PATH = "/api/rest/iso/v1/currencies/query";

    /**
     * MockMvc 用于走完整 Spring MVC、拦截器、请求解密和响应加密链路。
     */
    private final MockMvc mockMvc;

    /**
     * 商户安全材料服务，用于准备 200045 商户的 JWT 密钥、平台公钥和商户响应私钥。
     */
    private final MerchantSecurityService merchantSecurityService;

    /**
     * OpenAPI 报文加解密工具，测试中模拟商户加密请求和解密响应。
     */
    private final OpenApiPayloadCrypto payloadCrypto;

    /**
     * 密钥材料工具，用于计算安全指纹，避免日志打印完整密钥和密文。
     */
    private final OpenApiKeyMaterialFactory keyMaterialFactory;

    /**
     * 创建商户 ISO 字典 OpenAPI 测试。
     *
     * @param mockMvc                 MockMvc
     * @param merchantSecurityService 商户安全材料服务
     * @param payloadCrypto           OpenAPI 报文加解密工具
     * @param keyMaterialFactory      OpenAPI 密钥材料工具
     */
    MerchantOpenApiIsoDictionaryTests(MockMvc mockMvc,
                                      MerchantSecurityService merchantSecurityService,
                                      OpenApiPayloadCrypto payloadCrypto,
                                      OpenApiKeyMaterialFactory keyMaterialFactory) {
        this.mockMvc = mockMvc;
        this.merchantSecurityService = merchantSecurityService;
        this.payloadCrypto = payloadCrypto;
        this.keyMaterialFactory = keyMaterialFactory;
    }

    /**
     * 模拟商户 200045 加密查询国家地区列表，平台解密处理后加密响应，商户再解密响应 data。
     *
     * @throws Exception MockMvc 调用异常
     */
    @Test
    void shouldQueryCountriesThroughEncryptedOpenApi() throws Exception {
        MerchantSecurityMaterialDTO merchantMaterial = provisionMerchantMaterial();
        String plainRequestJson = JsonUtils.toJsonString(Map.of("alpha3", "USA"));
        MvcResult mvcResult = performEncryptedQuery(
                COUNTRY_PATH,
                plainRequestJson,
                merchantMaterial,
                MerchantOpenApiTestSupport.uniqueJwtId("iso-country-200045")
        );
        List<IsoCountryVO> countryList = decryptDataList(
                mvcResult.getResponse().getContentAsString(),
                merchantMaterial,
                new TypeReference<List<IsoCountryVO>>() {
                }
        );

        log.info("商户解密国家地区响应成功，响应数量：{}，第一条：{}", countryList.size(), countryList.isEmpty() ? null : countryList.get(0));
        assertThat(countryList).extracting(IsoCountryVO::getAlpha3).contains("USA");
    }

    /**
     * 模拟商户 200045 加密查询币种列表，平台解密处理后加密响应，商户再解密响应 data。
     *
     * @throws Exception MockMvc 调用异常
     */
    @Test
    void shouldQueryCurrenciesThroughEncryptedOpenApi() throws Exception {
        MerchantSecurityMaterialDTO merchantMaterial = provisionMerchantMaterial();
        String plainRequestJson = JsonUtils.toJsonString(Map.of("alphabeticCode", "USD"));
        MvcResult mvcResult = performEncryptedQuery(
                CURRENCY_PATH,
                plainRequestJson,
                merchantMaterial,
                MerchantOpenApiTestSupport.uniqueJwtId("iso-currency-200045")
        );
        List<IsoCurrencyVO> currencyList = decryptDataList(
                mvcResult.getResponse().getContentAsString(),
                merchantMaterial,
                new TypeReference<List<IsoCurrencyVO>>() {
                }
        );

        log.info("商户解密币种响应成功，响应数量：{}，第一条：{}", currencyList.size(), currencyList.isEmpty() ? null : currencyList.get(0));
        assertThat(currencyList).extracting(IsoCurrencyVO::getAlphabeticCode).contains("USD");
        assertThat(currencyList).filteredOn(currency -> "USD".equals(currency.getAlphabeticCode()))
                .first()
                .extracting(IsoCurrencyVO::getDefaultFractionDigits)
                .isEqualTo(2);
    }

    /**
     * 模拟商户国家地区查询参数格式错误，验证平台能返回稳定参数错误码。
     *
     * @throws Exception MockMvc 调用异常
     */
    @Test
    void shouldRejectInvalidCountryQueryParameter() throws Exception {
        MerchantSecurityMaterialDTO merchantMaterial = provisionMerchantMaterial();
        String plainRequestJson = JsonUtils.toJsonString(Map.of("alpha2", "USA"));

        MvcResult mvcResult = performEncryptedQueryExpectError(
                COUNTRY_PATH,
                plainRequestJson,
                merchantMaterial,
                MerchantOpenApiTestSupport.uniqueJwtId("iso-country-invalid-alpha2"),
                ApiResultEnum.PARAM_INVALID
        );

        log.info("商户国家地区查询异常响应验证完成，错误响应：{}",
                mvcResult.getResponse().getContentAsString());
    }

    /**
     * 模拟商户币种查询参数格式错误，验证平台能返回稳定参数错误码。
     *
     * @throws Exception MockMvc 调用异常
     */
    @Test
    void shouldRejectInvalidCurrencyQueryParameter() throws Exception {
        MerchantSecurityMaterialDTO merchantMaterial = provisionMerchantMaterial();
        String plainRequestJson = JsonUtils.toJsonString(Map.of("alphabeticCode", "US"));

        MvcResult mvcResult = performEncryptedQueryExpectError(
                CURRENCY_PATH,
                plainRequestJson,
                merchantMaterial,
                MerchantOpenApiTestSupport.uniqueJwtId("iso-currency-invalid-code"),
                ApiResultEnum.PARAM_INVALID
        );

        log.info("商户币种查询异常响应验证完成，错误响应：{}",
                mvcResult.getResponse().getContentAsString());
    }

    /**
     * 准备商户 200045 的安全材料。
     * <p>
     * 当前测试通过服务端开户流程幂等写入数据库，确保测试库存在 JWT、平台请求体 RSA 密钥和商户响应公钥。
     *
     * @return 商户侧用于联调的安全材料
     */
    private MerchantSecurityMaterialDTO provisionMerchantMaterial() {
        MerchantSecurityMaterialDTO merchantMaterial = merchantSecurityService.provisionMerchantSecurityMaterial(
                MerchantOpenApiTestSupport.buildMerchantSeed(MERCHANT_ID)
        );
        log.info("商户200045安全材料准备完成，merchantKey指纹={}，平台公钥指纹={}，商户响应私钥指纹={}",
                keyMaterialFactory.fingerprint(merchantMaterial.getMerchantKey()),
                keyMaterialFactory.fingerprint(merchantMaterial.getPlatformPublicKeyX509Base64()),
                keyMaterialFactory.fingerprint(merchantMaterial.getMerchantResponsePrivateKeyPkcs8Base64()));
        return merchantMaterial;
    }

    /**
     * 模拟商户完成请求体加密、JWT 请求头封装并发起 OpenAPI POST 查询调用。
     *
     * @param path                OpenAPI 请求路径
     * @param plainRequestJson    明文业务 JSON
     * @param merchantMaterial    商户侧安全材料
     * @param jwtId               JWT 唯一标识
     * @return MockMvc 调用结果
     * @throws Exception MockMvc 调用异常
     */
    private MvcResult performEncryptedQuery(String path,
                                            String plainRequestJson,
                                            MerchantSecurityMaterialDTO merchantMaterial,
                                            String jwtId) throws Exception {
        String encryptedData = payloadCrypto.encrypt(
                plainRequestJson,
                payloadCrypto.readPublicKey(merchantMaterial.getPlatformPublicKeyX509Base64())
        );
        String authorization = MerchantOpenApiTestSupport.createMerchantJwt(
                MERCHANT_ID,
                merchantMaterial.getMerchantKey(),
                System.currentTimeMillis() / 1000L,
                jwtId
        );
        String httpRequestBody = MerchantOpenApiTestSupport.wrapEncryptedData(encryptedData);
        log.info("商户发起ISO查询，path={}，请求明文={}，authorization摘要={}，data摘要={}",
                path,
                plainRequestJson,
                MerchantOpenApiTestSupport.safeSecretSummary(authorization, keyMaterialFactory),
                MerchantOpenApiTestSupport.safeSecretSummary(encryptedData, keyMaterialFactory));

        return mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(MerchantOpenApiTestSupport.AUTHORIZATION_HEADER, authorization)
                        .content(httpRequestBody))
                .andDo(result -> log.info("平台返回ISO查询加密响应，path={}，HTTP状态={}，响应摘要={}",
                        path,
                        result.getResponse().getStatus(),
                        MerchantOpenApiTestSupport.safeSecretSummary(result.getResponse().getContentAsString(), keyMaterialFactory)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ApiResultEnum.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data").isString())
                .andReturn();
    }

    /**
     * 模拟商户发起预期失败的加密 OpenAPI 请求，并校验平台返回指定错误码。
     *
     * @param path             OpenAPI 请求路径
     * @param plainRequestJson 明文业务 JSON
     * @param merchantMaterial 商户侧安全材料
     * @param jwtId            JWT 唯一标识
     * @param expectedError    预期错误枚举
     * @return MockMvc 调用结果
     * @throws Exception MockMvc 调用异常
     */
    private MvcResult performEncryptedQueryExpectError(String path,
                                                       String plainRequestJson,
                                                       MerchantSecurityMaterialDTO merchantMaterial,
                                                       String jwtId,
                                                       ApiResultEnum expectedError) throws Exception {
        String encryptedData = payloadCrypto.encrypt(
                plainRequestJson,
                payloadCrypto.readPublicKey(merchantMaterial.getPlatformPublicKeyX509Base64())
        );
        String authorization = MerchantOpenApiTestSupport.createMerchantJwt(
                MERCHANT_ID,
                merchantMaterial.getMerchantKey(),
                System.currentTimeMillis() / 1000L,
                jwtId
        );
        String httpRequestBody = MerchantOpenApiTestSupport.wrapEncryptedData(encryptedData);
        log.info("商户发起ISO异常查询，path={}，请求明文={}，authorization摘要={}，data摘要={}，预期错误码={}",
                path,
                plainRequestJson,
                MerchantOpenApiTestSupport.safeSecretSummary(authorization, keyMaterialFactory),
                MerchantOpenApiTestSupport.safeSecretSummary(encryptedData, keyMaterialFactory),
                expectedError.getCode());

        return mockMvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(MerchantOpenApiTestSupport.AUTHORIZATION_HEADER, authorization)
                        .content(httpRequestBody))
                .andDo(result -> log.info("平台返回ISO异常响应，path={}，HTTP状态={}，响应={}",
                        path,
                        result.getResponse().getStatus(),
                        result.getResponse().getContentAsString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(expectedError.getCode()))
                .andReturn();
    }

    /**
     * 模拟商户使用响应私钥解密 CommonResult.data 并转换成目标列表。
     *
     * @param encryptedResponseJson 平台返回的加密响应 JSON
     * @param merchantMaterial      商户侧安全材料
     * @param typeReference         目标列表类型
     * @param <T>                   响应数据元素类型
     * @return 解密后的响应列表
     */
    private <T> List<T> decryptDataList(String encryptedResponseJson,
                                        MerchantSecurityMaterialDTO merchantMaterial,
                                        TypeReference<List<T>> typeReference) {
        CommonResult<String> encryptedResult = JsonUtils.parseObject(
                encryptedResponseJson,
                new TypeReference<CommonResult<String>>() {
                }
        );
        assertThat(encryptedResult).isNotNull();
        PrivateKey responsePrivateKey = payloadCrypto.readPrivateKey(merchantMaterial.getMerchantResponsePrivateKeyPkcs8Base64());
        String plainDataJson = payloadCrypto.decrypt(encryptedResult.getData(), responsePrivateKey);
        log.info("商户响应data解密完成，明文data={}", plainDataJson);
        return JsonUtils.parseObject(plainDataJson, typeReference);
    }
}
