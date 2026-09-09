package com.scott.payment.openapi;

import com.alibaba.fastjson2.TypeReference;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.util.SensitiveDataMaskUtils;
import com.scott.payment.component.security.crypto.OpenApiPayloadCrypto;
import com.scott.payment.component.security.key.OpenApiKeyMaterialFactory;
import com.scott.payment.openapi.dto.security.MerchantSecurityMaterialDTO;
import com.scott.payment.openapi.service.MerchantSecurityService;
import com.scott.payment.openapi.support.MerchantOpenApiTestSupport;
import com.scott.payment.openapi.vo.payment.PaymentCreateVO;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

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
 * @date : 2026-05-30 09:37
 * @email : scott_x@163.com
 * @description : Merchant Open API End To End Tests 自动化测试类，位于 商户开放接口服务，验证当前模块的正常路径、异常边界和回归场景。
 * @status : create
 */
@Slf4j
@AutoConfigureMockMvc
@SpringBootTest(
        classes = OpenApiApplication.class,
        properties = {
                "spring.cloud.nacos.discovery.enabled=false",
                "openapi.payment-client.remote-enabled=false"
        }
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Sql(scripts = "/sql/openapi-merchant-security-schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class MerchantOpenApiEndToEndTests {

    /**
     * 测试商户号。
     */
    private static final String MERCHANT_ID = "230001";

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
     * 每个用例执行前清理本类测试商户数据，确保密钥材料按 merchantId 重新生成。
     */
    @BeforeEach
    void cleanMerchantData() {
        MerchantOpenApiTestSupport.cleanMerchantSecurityData(
                jdbcTemplate,
                List.of(MERCHANT_ID)
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
        log.info("商户生成请求明文JSON-脱敏内容：{}", SensitiveDataMaskUtils.maskJson(plainRequestJson));
        String encryptedData = payloadCrypto.encrypt(
                plainRequestJson,
                payloadCrypto.readPublicKey(clientMaterial.getPlatformPublicKeyX509Base64())
        );
        log.info("商户完成请求体data加密-data段数：{}，data摘要：{}",
                MerchantOpenApiTestSupport.compactPartCount(encryptedData),
                MerchantOpenApiTestSupport.safeSecretSummary(encryptedData, keyMaterialFactory));
        String authorization = MerchantOpenApiTestSupport.createMerchantJwt(
                MERCHANT_ID,
                clientMaterial.getMerchantKey(),
                System.currentTimeMillis() / 1000L,
                MerchantOpenApiTestSupport.uniqueJwtId(SUCCESS_TRADE_NO)
        );
        log.info("商户完成JWT请求头封装-authorization摘要：{}",
                MerchantOpenApiTestSupport.safeSecretSummary(authorization, keyMaterialFactory));
        String httpRequestBody = MerchantOpenApiTestSupport.wrapEncryptedData(encryptedData);
        log.info("商户完成HTTP请求体封装-body摘要：{}",
                MerchantOpenApiTestSupport.safeSecretSummary(httpRequestBody, keyMaterialFactory));

        log.info("商户准备调用OpenAPI-密钥摘要：merchantKey指纹: {}，平台公钥指纹: {}，商户响应私钥指纹: {}",
                keyMaterialFactory.fingerprint(clientMaterial.getMerchantKey()),
                keyMaterialFactory.fingerprint(clientMaterial.getPlatformPublicKeyX509Base64()),
                keyMaterialFactory.fingerprint(onboardingMaterial.getMerchantResponsePrivateKeyPkcs8Base64()));
        log.info("商户准备调用OpenAPI-HTTP安全摘要：{}",
                JsonUtils.toJsonString(MerchantOpenApiTestSupport.safeHttpCallSummary(authorization, httpRequestBody, keyMaterialFactory)));

        MvcResult mvcResult = mockMvc.perform(post(MerchantOpenApiTestSupport.AUTHORIZATION_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(MerchantOpenApiTestSupport.AUTHORIZATION_HEADER, authorization)
                        .content(httpRequestBody))
                .andDo(result -> log.info("服务端返回加密响应，HTTP状态：{}，响应体摘要：{}",
                        result.getResponse().getStatus(),
                        MerchantOpenApiTestSupport.safeSecretSummary(result.getResponse().getContentAsString(), keyMaterialFactory)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ApiResultEnum.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data").isString())
                .andReturn();

        PaymentCreateVO decryptedResponse = decryptMerchantResponseData(onboardingMaterial, mvcResult.getResponse().getContentAsString());
        log.info("decryptedResponse: {}", decryptedResponse);

        assertThat(decryptedResponse.getOrderInfo().getOrderNo()).isEqualTo(SUCCESS_TRADE_NO);
        assertThat(decryptedResponse.getBillingInfo().getTransactionCurrency()).isEqualTo("USD");
        assertThat(decryptedResponse.getBillingInfo().getTransactionAmount()).isEqualByComparingTo("12389.45");
        assertThat(decryptedResponse.getTransactionInfo().getCode()).isEqualTo(ApiResultEnum.PROCESSING.getCode());
        log.info("商户完整调用成功-响应解密后订单号：{}，金额：{} {}",
                decryptedResponse.getOrderInfo().getOrderNo(),
                decryptedResponse.getBillingInfo().getTransactionAmount(),
                decryptedResponse.getBillingInfo().getTransactionCurrency());
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
                payloadCrypto.readPublicKey(clientMaterial.getPlatformPublicKeyX509Base64())
        );
        String validAuthorization = MerchantOpenApiTestSupport.createMerchantJwt(
                MERCHANT_ID,
                clientMaterial.getMerchantKey(),
                System.currentTimeMillis() / 1000L,
                MerchantOpenApiTestSupport.uniqueJwtId("202605300004")
        );
        String validBody = MerchantOpenApiTestSupport.wrapEncryptedData(encryptedData);

        assertOpenApiError("缺少authorization请求头",
                null,
                validBody,
                ApiResultEnum.AUTHORIZATION_HEADER_MISSING);
        assertOpenApiError("错误merchantKey导致JWT签名失败",
                MerchantOpenApiTestSupport.createMerchantJwt(MERCHANT_ID, "wrong-merchant-key", System.currentTimeMillis() / 1000L,
                        MerchantOpenApiTestSupport.uniqueJwtId("bad-key")),
                validBody,
                ApiResultEnum.AUTHORIZATION_JWT_SIGNATURE_INVALID);
        assertOpenApiError("JWT已过期",
                MerchantOpenApiTestSupport.createMerchantJwt(MERCHANT_ID, clientMaterial.getMerchantKey(), EXPIRED_ISSUED_AT,
                        MerchantOpenApiTestSupport.uniqueJwtId("expired-jwt")),
                validBody,
                ApiResultEnum.AUTHORIZATION_JWT_EXPIRED);
        assertOpenApiError("请求体data被篡改",
                validAuthorization,
                MerchantOpenApiTestSupport.wrapEncryptedData(MerchantOpenApiTestSupport.tamperCiphertextSegment(encryptedData)),
                ApiResultEnum.ENCRYPTED_DATA_INVALID);
    }

    /**
     * 创建当前测试商户并返回开户交付材料。
     *
     * @return 商户开户交付材料
     */
    private MerchantSecurityMaterialDTO provisionMerchant() {
        return merchantSecurityService.provisionMerchantSecurityMaterial(
                MerchantOpenApiTestSupport.buildMerchantSeed(MERCHANT_ID)
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
                                    ApiResultEnum expectedEnum) throws Exception {
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
     * 模拟商户收到响应后，使用自己保存的响应私钥解密响应 data。
     *
     * @param merchantMaterial    商户开户时拿到的响应私钥材料
     * @param encryptedResponseJson data 已加密的响应 JSON
     * @return 解密后的响应业务对象
     */
    private PaymentCreateVO decryptMerchantResponseData(MerchantSecurityMaterialDTO merchantMaterial,
                                                        String encryptedResponseJson) {
        Map<String, Object> responseMap = JsonUtils.parseObject(encryptedResponseJson, new TypeReference<>() {
        });
        String encryptedResponseData = String.valueOf(responseMap.get("data"));
        String plainResponseData = payloadCrypto.decrypt(
                encryptedResponseData,
                MerchantOpenApiTestSupport.resolveMerchantResponsePrivateKey(merchantMaterial, payloadCrypto)
        );
        log.info("商户响应解密成功-响应码: {}，响应消息: {}，响应明文: {}",
                responseMap.get("code"),
                responseMap.get("message"),
                plainResponseData);
        return JsonUtils.parseObject(plainResponseData, PaymentCreateVO.class);
    }
}
