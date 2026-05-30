package com.scott.payment.openapi;

import com.alibaba.fastjson2.TypeReference;
import com.scott.payment.component.core.enums.ApiCoResultEnum;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.security.crypto.OpenApiPayloadCrypto;
import com.scott.payment.component.security.key.OpenApiKeyMaterialFactory;
import com.scott.payment.openapi.dto.security.MerchantSecurityMaterialDTO;
import com.scott.payment.openapi.service.MerchantSecurityService;
import com.scott.payment.openapi.support.MerchantOpenApiTestSupport;
import com.scott.payment.openapi.vo.payment.PaymentCreateVO;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantOpenApiEndToEndTests
 * @date : 2026-05-30 09:40
 * @email : scott_x@163.com
 * @description : 商户使用自身密钥调用 OpenAPI 服务端的成功、响应解密和异常失败集成测试
 * @status : create
 */
@Slf4j
@AutoConfigureMockMvc
@ActiveProfiles("mysql-test")
@SpringBootTest(classes = OpenApiApplication.class)
@Sql(scripts = "/sql/openapi-merchant-security-schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class MerchantOpenApiEndToEndTests {

    /**
     * 测试商户号。
     */
    private static final String MERCHANT_ID = "230001";

    /**
     * 测试平台请求体 RSA kid。
     */
    private static final String PLATFORM_KEY_ID = "payment-platform-openapi-e2e-v1";

    /**
     * 测试商户响应加密 RSA kid。
     */
    private static final String RESPONSE_KEY_ID = "merchant-230001-response-v1";

    /**
     * 成功分支商户订单号。
     */
    private static final String SUCCESS_TRADE_NO = "202605300003";

    /**
     * 过期 JWT 用例的固定签发时间，单位秒。
     */
    private static final long EXPIRED_ISSUED_AT = 1_704_960_018L;

    /**
     * MockMvc 用于走完整 Spring MVC 请求链路。
     */
    private final MockMvc mockMvc;

    /**
     * 商户密钥服务，测试中用于开户、查询平台公钥和服务端响应公钥。
     */
    private final MerchantSecurityService merchantSecurityService;

    /**
     * OpenAPI 报文加解密工具。
     */
    private final OpenApiPayloadCrypto payloadCrypto;

    /**
     * JDBC 模板只用于测试数据清理。
     */
    private final JdbcTemplate jdbcTemplate;

    /**
     * 密钥材料工厂用于日志指纹计算。
     */
    private final OpenApiKeyMaterialFactory keyMaterialFactory;

    @Autowired
    MerchantOpenApiEndToEndTests(MockMvc mockMvc,
                                 MerchantSecurityService merchantSecurityService,
                                 OpenApiPayloadCrypto payloadCrypto,
                                 JdbcTemplate jdbcTemplate,
                                 OpenApiKeyMaterialFactory keyMaterialFactory) {
        this.mockMvc = mockMvc;
        this.merchantSecurityService = merchantSecurityService;
        this.payloadCrypto = payloadCrypto;
        this.jdbcTemplate = jdbcTemplate;
        this.keyMaterialFactory = keyMaterialFactory;
    }

    /**
     * 每个用例执行前清理本类测试商户和测试平台 kid。
     */
    @BeforeEach
    void cleanMerchantData() {
        MerchantOpenApiTestSupport.cleanMerchantSecurityData(
                jdbcTemplate,
                List.of(MERCHANT_ID),
                List.of(PLATFORM_KEY_ID)
        );
    }

    /**
     * 模拟商户使用自己的商户号、merchantKey 和平台公钥加密请求体，成功调用 OpenAPI 并解密响应增强 data。
     *
     * @throws Exception MockMvc 调用异常
     */
    @Test
    void shouldCallOpenApiSuccessfullyAndDecryptResponseData() throws Exception {
        MerchantSecurityMaterialDTO onboardingMaterial = provisionMerchant();
        MerchantSecurityMaterialDTO clientMaterial = merchantSecurityService.getMerchantClientSecurityMaterial(MERCHANT_ID);
        String plainRequestJson = MerchantOpenApiTestSupport.authorizationPlainText(MERCHANT_ID, SUCCESS_TRADE_NO);
        log.info("plainRequestJson={}", plainRequestJson);
        String encryptedData = payloadCrypto.encrypt(
                plainRequestJson,
                payloadCrypto.readPublicKey(clientMaterial.getPlatformPublicKeyX509Base64()),
                clientMaterial.getPlatformPayloadKeyId()
        );
        log.info("encryptedData={}", encryptedData);
        String authorization = MerchantOpenApiTestSupport.createMerchantJwt(
                MERCHANT_ID,
                clientMaterial.getMerchantKey(),
                System.currentTimeMillis() / 1000L,
                SUCCESS_TRADE_NO
        );
        log.info("authorization: {}", authorization);
        String httpRequestBody = MerchantOpenApiTestSupport.wrapEncryptedData(encryptedData);
        log.info("httpRequestBody={}", httpRequestBody);

        log.info("商户准备调用OpenAPI-密钥摘要：merchantKey指纹={}，平台公钥kid={}，平台公钥指纹={}",
                keyMaterialFactory.fingerprint(clientMaterial.getMerchantKey()),
                clientMaterial.getPlatformPayloadKeyId(),
                keyMaterialFactory.fingerprint(clientMaterial.getPlatformPublicKeyX509Base64()));
        log.info("商户准备调用OpenAPI-HTTP安全摘要：{}",
                JsonUtils.toJsonString(MerchantOpenApiTestSupport.safeHttpCallSummary(authorization, httpRequestBody, keyMaterialFactory)));

        MvcResult mvcResult = mockMvc.perform(post(MerchantOpenApiTestSupport.AUTHORIZATION_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(MerchantOpenApiTestSupport.AUTHORIZATION_HEADER, authorization)
                        .content(httpRequestBody))
                .andDo(result -> log.info("服务端返回明文响应，HTTP状态：{}，响应体：{}",
                        result.getResponse().getStatus(),
                        result.getResponse().getContentAsString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ApiCoResultEnum.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.merchantOrderNo").value(SUCCESS_TRADE_NO))
                .andExpect(jsonPath("$.data.currency").value("USD"))
                .andReturn();

        String encryptedResponseJson = encryptServerResponseData(MERCHANT_ID, mvcResult.getResponse().getContentAsString());
        log.info("encryptedResponseJson={}", encryptedResponseJson);
        PaymentCreateVO decryptedResponse = decryptMerchantResponseData(onboardingMaterial, encryptedResponseJson);
        log.info("decryptedResponse={}", decryptedResponse);

        assertThat(decryptedResponse.getMerchantOrderNo()).isEqualTo(SUCCESS_TRADE_NO);
        assertThat(decryptedResponse.getCurrency()).isEqualTo("USD");
        log.info("商户完整调用成功-响应解密后订单号：{}，金额：{} {}",
                decryptedResponse.getMerchantOrderNo(),
                decryptedResponse.getAmount(),
                decryptedResponse.getCurrency());
    }

    /**
     * 模拟商户调用 OpenAPI 时常见异常：缺少请求头、错误 merchantKey、JWT 过期、密文篡改。
     *
     * @throws Exception MockMvc 调用异常
     */
    @Test
    void shouldRejectOpenApiCallsWhenSignatureOrPayloadIsInvalid() throws Exception {
        MerchantSecurityMaterialDTO clientMaterial = merchantSecurityService.getMerchantClientSecurityMaterial(provisionMerchant().getMerchantId());
        String plainRequestJson = MerchantOpenApiTestSupport.authorizationPlainText(MERCHANT_ID, "202605300004");
        String encryptedData = payloadCrypto.encrypt(
                plainRequestJson,
                payloadCrypto.readPublicKey(clientMaterial.getPlatformPublicKeyX509Base64()),
                clientMaterial.getPlatformPayloadKeyId()
        );
        String validAuthorization = MerchantOpenApiTestSupport.createMerchantJwt(
                MERCHANT_ID,
                clientMaterial.getMerchantKey(),
                System.currentTimeMillis() / 1000L,
                "202605300004"
        );
        String validBody = MerchantOpenApiTestSupport.wrapEncryptedData(encryptedData);

        assertOpenApiError("缺少authorization请求头",
                null,
                validBody,
                ApiCoResultEnum.CO_UNAUTHORIZED_NULL);
        assertOpenApiError("错误merchantKey导致JWT签名失败",
                MerchantOpenApiTestSupport.createMerchantJwt(MERCHANT_ID, "wrong-merchant-key", System.currentTimeMillis() / 1000L, "bad-key"),
                validBody,
                ApiCoResultEnum.CO_UNAUTHORIZED_JWT_SIGN);
        assertOpenApiError("JWT已过期",
                MerchantOpenApiTestSupport.createMerchantJwt(MERCHANT_ID, clientMaterial.getMerchantKey(), EXPIRED_ISSUED_AT, "expired-jwt"),
                validBody,
                ApiCoResultEnum.CO_UNAUTHORIZED_JWT_EXP);
        assertOpenApiError("请求体data被篡改",
                validAuthorization,
                MerchantOpenApiTestSupport.wrapEncryptedData(MerchantOpenApiTestSupport.tamperCiphertextSegment(encryptedData)),
                ApiCoResultEnum.CO_REQUIRED_PARAMETER_ILLEGAL);
    }

    /**
     * 创建当前测试商户并返回开户交付材料。
     *
     * @return 商户开户交付材料
     */
    private MerchantSecurityMaterialDTO provisionMerchant() {
        return merchantSecurityService.provisionMerchantSecurityMaterial(
                MerchantOpenApiTestSupport.buildMerchantSeed(MERCHANT_ID, PLATFORM_KEY_ID, RESPONSE_KEY_ID)
        );
    }

    /**
     * 按统一错误响应断言 OpenAPI 异常分支。
     *
     * @param caseName      当前异常分支名称
     * @param authorization authorization 请求头；为空表示不传
     * @param body          HTTP 请求体
     * @param expectedEnum  预期错误枚举
     * @throws Exception MockMvc 调用异常
     */
    private void assertOpenApiError(String caseName,
                                    String authorization,
                                    String body,
                                    ApiCoResultEnum expectedEnum) throws Exception {
        var requestBuilder = post(MerchantOpenApiTestSupport.AUTHORIZATION_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
        if (authorization != null) {
            requestBuilder.header(MerchantOpenApiTestSupport.AUTHORIZATION_HEADER, authorization);
        }
        mockMvc.perform(requestBuilder)
                .andDo(result -> log.info("异常分支-{}，HTTP状态：{}，响应体：{}",
                        caseName,
                        result.getResponse().getStatus(),
                        result.getResponse().getContentAsString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(expectedEnum.getCode()));
    }

    /**
     * 模拟服务端响应加密增强模式：使用商户响应公钥加密响应 data。
     *
     * @param merchantId         支付框架颁发的商户号
     * @param plainResponseBody  OpenAPI 控制器返回的明文响应 JSON
     * @return data 已加密的响应 JSON
     */
    private String encryptServerResponseData(String merchantId, String plainResponseBody) {
        Map<String, Object> responseMap = JsonUtils.parseObject(plainResponseBody, new TypeReference<Map<String, Object>>() {
        });
        String responseKeyId = merchantSecurityService.getEnabledMerchantResponseKeyId(merchantId);
        String plainResponseData = JsonUtils.toJsonString(responseMap.get("data"));
        String encryptedResponseData = payloadCrypto.encrypt(
                plainResponseData,
                merchantSecurityService.getMerchantResponsePublicKey(merchantId, responseKeyId),
                responseKeyId
        );
        Map<String, Object> encryptedResponse = new LinkedHashMap<>();
        encryptedResponse.put("code", responseMap.get("code"));
        encryptedResponse.put("message", responseMap.get("message"));
        encryptedResponse.put("data", encryptedResponseData);
        log.info("服务端响应加密增强-使用商户响应公钥kid={}，响应data摘要={}",
                responseKeyId,
                MerchantOpenApiTestSupport.safeSecretSummary(encryptedResponseData, keyMaterialFactory));
        return JsonUtils.toJsonString(encryptedResponse);
    }

    /**
     * 模拟商户收到响应后，使用自己保存的响应私钥解密响应 data。
     *
     * @param merchantMaterial    商户开户时拿到的响应私钥材料
     * @param encryptedResponseJson data 已加密的响应 JSON
     * @return 解密后的响应业务对象
     */
    private PaymentCreateVO decryptMerchantResponseData(MerchantSecurityMaterialDTO merchantMaterial,
                                                        String encryptedResponseJson) {
        Map<String, Object> responseMap = JsonUtils.parseObject(encryptedResponseJson, new TypeReference<Map<String, Object>>() {
        });
        String encryptedResponseData = String.valueOf(responseMap.get("data"));
        String plainResponseData = payloadCrypto.decrypt(
                encryptedResponseData,
                keyId -> MerchantOpenApiTestSupport.resolveMerchantResponsePrivateKey(merchantMaterial, payloadCrypto, keyId)
        );
        log.info("商户响应解密成功-响应码={}，响应消息={}，响应明文={}",
                responseMap.get("code"),
                responseMap.get("message"),
                plainResponseData);
        return JsonUtils.parseObject(plainResponseData, PaymentCreateVO.class);
    }
}
