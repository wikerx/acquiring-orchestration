package com.scott.payment.openapi;

import cn.hutool.jwt.JWTHeader;
import cn.hutool.jwt.JWTUtil;
import cn.hutool.jwt.RegisteredPayload;
import com.alibaba.fastjson2.TypeReference;
import com.scott.payment.component.core.enums.ApiCoResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.util.SensitiveDataMaskUtils;
import com.scott.payment.component.security.crypto.OpenApiPayloadCrypto;
import com.scott.payment.component.security.jwt.JwtMerchantClaims;
import com.scott.payment.component.security.jwt.MerchantJwtVerifier;
import com.scott.payment.component.security.key.OpenApiKeyMaterialFactory;
import com.scott.payment.openapi.dto.body.ApiMerchantPaymentRequestDTO;
import com.scott.payment.openapi.dto.security.OpenApiMerchantSecurityMaterialDTO;
import com.scott.payment.openapi.dto.security.OpenApiMerchantSecuritySeedDTO;
import com.scott.payment.openapi.service.OpenApiMerchantSecurityService;
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

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiMerchantSecurityDatabaseFlowTests
 * @date : 2026-05-30 00:00
 * @email : scott_x@163.com
 * @description : OpenAPI 商户密钥 MySQL 存储、MyBatisPlus 查询和加密接口调用集成测试
 * @status : create
 */
@Slf4j
@AutoConfigureMockMvc
@ActiveProfiles("mysql-test")
@SpringBootTest(classes = OpenApiApplication.class)
@Sql(scripts = "/sql/openapi-merchant-security-schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class OpenApiMerchantSecurityDatabaseFlowTests {

    /**
     * 主测试商户号，模拟真实外卡收单商户。
     */
    private static final String MERCHANT_ID = "200045";

    /**
     * 第二个测试商户号，用于证明数据库可以承载多个商户的独立密钥材料。
     */
    private static final String SECOND_MERCHANT_ID = "200046";

    /**
     * OpenAPI 授权接口路径。
     */
    private static final String AUTHORIZATION_PATH = "/api/rest/payment/v1/authorization";

    /**
     * OpenAPI 授权请求头名称。
     */
    private static final String AUTHORIZATION_HEADER = "authorization";

    /**
     * 平台请求体 RSA 密钥编号，商户加密请求体时写入 data 的 kid。
     */
    private static final String PLATFORM_KEY_ID = "payment-platform-payload-test-v1";

    /**
     * 商户响应 RSA 公钥编号，平台响应加密增强模式写入 data 的 kid。
     */
    private static final String RESPONSE_KEY_ID = "merchant-200045-response-test-v1";

    /**
     * 第二个商户的平台请求体 RSA 密钥编号。
     */
    private static final String SECOND_PLATFORM_KEY_ID = "payment-platform-payload-test-v2";

    /**
     * 第二个商户的响应 RSA 公钥编号。
     */
    private static final String SECOND_RESPONSE_KEY_ID = "merchant-200046-response-test-v1";

    /**
     * 固定过期 JWT 测试时间，避免异常用例依赖真实时间。
     */
    private static final long FIXED_EXPIRED_ISSUED_AT = 1_704_960_018L;

    /**
     * MockMvc 用于模拟商户 HTTP 请求进入真实 Spring MVC 链路。
     */
    private final MockMvc mockMvc;

    /**
     * JDBC 模板仅用于测试前清理本用例数据，业务检索统一走 MyBatisPlus Service。
     */
    private final JdbcTemplate jdbcTemplate;

    /**
     * OpenAPI 商户安全服务，内部通过 MyBatisPlus Mapper 查询商户和密钥表。
     */
    private final OpenApiMerchantSecurityService merchantSecurityService;

    /**
     * OpenAPI 报文加解密工具，用于模拟商户加密请求和服务端响应加密。
     */
    private final OpenApiPayloadCrypto payloadCrypto;

    /**
     * 商户 JWT 验签器，用于测试服务端鉴权成功和异常分支。
     */
    private final MerchantJwtVerifier merchantJwtVerifier;

    /**
     * 密钥材料生成工具，用于日志指纹计算，避免输出完整密钥。
     */
    private final OpenApiKeyMaterialFactory keyMaterialFactory;

    @Autowired
    OpenApiMerchantSecurityDatabaseFlowTests(MockMvc mockMvc,
                                             JdbcTemplate jdbcTemplate,
                                             OpenApiMerchantSecurityService merchantSecurityService,
                                             OpenApiPayloadCrypto payloadCrypto,
                                             MerchantJwtVerifier merchantJwtVerifier,
                                             OpenApiKeyMaterialFactory keyMaterialFactory) {
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
        this.merchantSecurityService = merchantSecurityService;
        this.payloadCrypto = payloadCrypto;
        this.merchantJwtVerifier = merchantJwtVerifier;
        this.keyMaterialFactory = keyMaterialFactory;
    }

    /**
     * 清理当前测试商户数据，避免重复执行测试时历史密钥影响断言。
     */
    @BeforeEach
    void cleanOpenApiMerchantSecurityData() {
        jdbcTemplate.update("DELETE FROM openapi_merchant_response_key WHERE merchant_id IN (?, ?)", MERCHANT_ID, SECOND_MERCHANT_ID);
        jdbcTemplate.update("DELETE FROM openapi_merchant_jwt_key WHERE merchant_id IN (?, ?)", MERCHANT_ID, SECOND_MERCHANT_ID);
        jdbcTemplate.update("DELETE FROM openapi_platform_payload_key WHERE platform_key_id IN (?, ?)", PLATFORM_KEY_ID, SECOND_PLATFORM_KEY_ID);
        jdbcTemplate.update("DELETE FROM openapi_merchant_info WHERE merchant_id IN (?, ?)", MERCHANT_ID, SECOND_MERCHANT_ID);
    }

    /**
     * 验证商户开户密钥落库、MyBatisPlus 查询、JWT 鉴权、请求解密、HTTP 调用、响应加密和商户响应解密完整链路。
     *
     * @throws Exception MockMvc 调用异常
     */
    @Test
    void shouldCompleteMerchantOpenApiRoundTripWithMysqlAndMyBatisPlus() throws Exception {
        OpenApiMerchantSecurityMaterialDTO merchantMaterial = provisionMerchant(MERCHANT_ID, PLATFORM_KEY_ID, RESPONSE_KEY_ID);
        OpenApiMerchantSecurityMaterialDTO secondMerchantMaterial = provisionMerchant(
                SECOND_MERCHANT_ID,
                SECOND_PLATFORM_KEY_ID,
                SECOND_RESPONSE_KEY_ID
        );

        logProvisionedSecurityMaterials(merchantMaterial, secondMerchantMaterial);
        assertDatabaseLookupByMyBatisPlus(merchantMaterial, secondMerchantMaterial);

        String plainRequestJson = authorizationPlainText(merchantMaterial.getMerchantId());
        String encryptedRequestData = encryptMerchantRequestData(merchantMaterial, plainRequestJson);
        String authorization = createMerchantJwt(
                merchantMaterial.getMerchantId(),
                merchantMaterial.getMerchantKey(),
                System.currentTimeMillis() / 1000L
        );
        String httpRequestBody = JsonUtils.toJsonString(Map.of("data", encryptedRequestData));
        logMerchantHttpCall(authorization, httpRequestBody);

        verifyServerReceiveAndSecurityBranches(authorization, encryptedRequestData, merchantMaterial);

        MvcResult mvcResult = mockMvc.perform(post(AUTHORIZATION_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(AUTHORIZATION_HEADER, authorization)
                        .content(httpRequestBody))
                .andDo(result -> log.info("服务端HTTP调用完成，HTTP状态：{}，明文响应摘要：{}",
                        result.getResponse().getStatus(),
                        result.getResponse().getContentAsString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ApiCoResultEnum.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data.merchantOrderNo").value("20250116140182865587"))
                .andExpect(jsonPath("$.data.currency").value("USD"))
                .andExpect(jsonPath("$.data.amount").value(1_238_945L))
                .andReturn();

        String encryptedResponseJson = encryptServerResponseData(
                merchantMaterial.getMerchantId(),
                mvcResult.getResponse().getContentAsString()
        );
        PaymentCreateVO merchantResponse = decryptMerchantResponseData(merchantMaterial, encryptedResponseJson);

        assertThat(merchantResponse.getMerchantOrderNo()).isEqualTo("20250116140182865587");
        assertThat(merchantResponse.getCurrency()).isEqualTo("USD");
        assertThat(merchantResponse.getAmount()).isEqualTo(1_238_945L);
    }

    private OpenApiMerchantSecurityMaterialDTO provisionMerchant(String merchantId, String platformKeyId, String responseKeyId) {
        OpenApiMerchantSecuritySeedDTO seedDTO = new OpenApiMerchantSecuritySeedDTO();
        seedDTO.setMerchantId(merchantId);
        seedDTO.setMerchantName("Scott Test Merchant " + merchantId);
        seedDTO.setMerchantShortName("ScottMerchant" + merchantId);
        seedDTO.setMerchantCategoryCode("5311");
        seedDTO.setCountryCode("USA");
        seedDTO.setRegionCode("CA");
        seedDTO.setCity("San Jose");
        seedDTO.setAddressLine("1 Payment Framework Road");
        seedDTO.setContactEmail("merchant" + merchantId + "@example.com");
        seedDTO.setContactPhone("+1-408-555-0100");
        seedDTO.setSettlementCurrency("USD");
        seedDTO.setTimezone("Asia/Shanghai");
        seedDTO.setRiskLevel("NORMAL");
        seedDTO.setPlatformPayloadKeyId(platformKeyId);
        seedDTO.setMerchantResponseKeyId(responseKeyId);
        return merchantSecurityService.provisionMerchantSecurityMaterial(seedDTO);
    }

    private void logProvisionedSecurityMaterials(OpenApiMerchantSecurityMaterialDTO merchantMaterial,
                                                 OpenApiMerchantSecurityMaterialDTO secondMerchantMaterial) {
        log.info("系统生成商户密钥材料，商户数量：2，主商户号：{}，第二商户号：{}",
                merchantMaterial.getMerchantId(),
                secondMerchantMaterial.getMerchantId());
        log.info("给商户使用的材料：merchantKey指纹：{}，平台公钥kid：{}，平台公钥指纹：{}，商户响应私钥kid：{}，商户响应私钥指纹：{}",
                keyMaterialFactory.fingerprint(merchantMaterial.getMerchantKey()),
                merchantMaterial.getPlatformPayloadKeyId(),
                keyMaterialFactory.fingerprint(merchantMaterial.getPlatformPublicKeyX509Base64()),
                merchantMaterial.getMerchantResponseKeyId(),
                keyMaterialFactory.fingerprint(merchantMaterial.getMerchantResponsePrivateKeyPkcs8Base64()));
        log.info("平台保留的材料：平台私钥只在openapi_platform_payload_key表内保存，平台只保存商户响应公钥，响应私钥不属于平台");
        log.info("密钥关联关系：merchant_id关联merchantKey；platform_key_id关联平台RSA公私钥；response_key_id关联商户响应公钥和商户侧响应私钥");
    }

    private void assertDatabaseLookupByMyBatisPlus(OpenApiMerchantSecurityMaterialDTO merchantMaterial,
                                                   OpenApiMerchantSecurityMaterialDTO secondMerchantMaterial) {
        assertThat(merchantSecurityService.getActiveMerchant(merchantMaterial.getMerchantId()).getMerchantName())
                .isEqualTo(merchantMaterial.getMerchantName());
        assertThat(merchantSecurityService.getMerchantKey(merchantMaterial.getMerchantId()))
                .isEqualTo(merchantMaterial.getMerchantKey());
        assertThat(merchantSecurityService.getPlatformPublicKey(merchantMaterial.getPlatformPayloadKeyId()))
                .isNotNull();
        assertThat(merchantSecurityService.getMerchantResponsePublicKey(
                merchantMaterial.getMerchantId(),
                merchantMaterial.getMerchantResponseKeyId()
        )).isNotNull();
        assertThat(merchantSecurityService.getMerchantKey(secondMerchantMaterial.getMerchantId()))
                .isEqualTo(secondMerchantMaterial.getMerchantKey());
        log.info("MyBatisPlus数据库检索成功，主商户：{}，第二商户：{}，主从数据源当前都指向同一个MySQL库",
                merchantMaterial.getMerchantId(),
                secondMerchantMaterial.getMerchantId());
    }

    private String encryptMerchantRequestData(OpenApiMerchantSecurityMaterialDTO merchantMaterial, String plainRequestJson) {
        String encryptedRequestData = payloadCrypto.encrypt(
                plainRequestJson,
                merchantSecurityService.getPlatformPublicKey(merchantMaterial.getPlatformPayloadKeyId()),
                merchantMaterial.getPlatformPayloadKeyId()
        );
        log.info("商户请求JSON明文已生成，脱敏内容：{}", SensitiveDataMaskUtils.maskJson(plainRequestJson));
        log.info("商户完成请求体data加密，data段数：{}，data长度：{}，data指纹：{}",
                encryptedRequestData.split("\\.").length,
                encryptedRequestData.length(),
                keyMaterialFactory.fingerprint(encryptedRequestData));
        return encryptedRequestData;
    }

    private void logMerchantHttpCall(String authorization, String httpRequestBody) {
        Map<String, Object> safeHeaders = new LinkedHashMap<>();
        safeHeaders.put("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        safeHeaders.put("authorizationParts", authorization.split("\\.").length);
        safeHeaders.put("authorizationFingerprint", keyMaterialFactory.fingerprint(authorization));
        Map<String, Object> safeBody = new LinkedHashMap<>();
        safeBody.put("data", "<compact密文省略>");
        safeBody.put("bodyLength", httpRequestBody.length());
        safeBody.put("bodyFingerprint", keyMaterialFactory.fingerprint(httpRequestBody));
        log.info("商户发起HTTP调用，method：POST，path：{}，headers安全摘要：{}，body安全摘要：{}",
                AUTHORIZATION_PATH,
                JsonUtils.toJsonString(safeHeaders),
                JsonUtils.toJsonString(safeBody));
    }

    private void verifyServerReceiveAndSecurityBranches(String authorization,
                                                        String encryptedRequestData,
                                                        OpenApiMerchantSecurityMaterialDTO merchantMaterial) {
        String merchantId = merchantJwtVerifier.peekMerchantId(authorization);
        String merchantKey = merchantSecurityService.getMerchantKey(merchantId);
        JwtMerchantClaims claims = merchantJwtVerifier.verify(authorization, merchantKey);
        String decryptedJson = payloadCrypto.decrypt(encryptedRequestData, merchantSecurityService::getPlatformPrivateKey);
        ApiMerchantPaymentRequestDTO requestDTO = JsonUtils.parseObject(decryptedJson, ApiMerchantPaymentRequestDTO.class);

        log.info("服务端收到商户参数，JWT验签成功，商户号：{}，jti：{}，请求体解密脱敏：{}",
                claims.getMerchantId(),
                claims.getJwtId(),
                SensitiveDataMaskUtils.maskJson(decryptedJson));
        log.info("服务端解析DTO成功，订单号：{}，金额：{} {}，脱敏卡号：{}",
                requestDTO.getOrderInfo().getTradeNo(),
                requestDTO.getOrderInfo().getAmount(),
                requestDTO.getOrderInfo().getCurrency(),
                SensitiveDataMaskUtils.maskPan(requestDTO.getCardInfo().getCardNo()));

        assertThatThrownBy(() -> merchantJwtVerifier.verify(authorization, "wrong-merchant-key"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining(ApiCoResultEnum.CO_UNAUTHORIZED_JWT_SIGN.getMessage());
        log.info("异常分支-错误merchantKey验签失败，预期错误码：{}", ApiCoResultEnum.CO_UNAUTHORIZED_JWT_SIGN.getCode());

        String expiredJwt = createMerchantJwt(
                merchantMaterial.getMerchantId(),
                merchantMaterial.getMerchantKey(),
                FIXED_EXPIRED_ISSUED_AT
        );
        assertThatThrownBy(() -> merchantJwtVerifier.verify(
                expiredJwt,
                merchantMaterial.getMerchantKey(),
                FIXED_EXPIRED_ISSUED_AT + 181L
        )).isInstanceOf(ApiException.class)
                .hasMessageContaining(ApiCoResultEnum.CO_UNAUTHORIZED_JWT_EXP.getMessage());
        log.info("异常分支-JWT过期被拒绝，预期错误码：{}", ApiCoResultEnum.CO_UNAUTHORIZED_JWT_EXP.getCode());

        String tamperedData = encryptedRequestData.substring(0, encryptedRequestData.length() - 1)
                + (encryptedRequestData.endsWith("A") ? "B" : "A");
        assertThatThrownBy(() -> payloadCrypto.decrypt(tamperedData, merchantSecurityService::getPlatformPrivateKey))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining(ApiCoResultEnum.CO_REQUIRED_PARAMETER_ILLEGAL.getMessage());
        log.info("异常分支-data密文被篡改，AES-GCM认证失败，服务端拒绝解析");
    }

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
        log.info("服务端响应加密完成，使用商户响应公钥kid：{}，响应data长度：{}，响应data指纹：{}",
                responseKeyId,
                encryptedResponseData.length(),
                keyMaterialFactory.fingerprint(encryptedResponseData));
        return JsonUtils.toJsonString(encryptedResponse);
    }

    private PaymentCreateVO decryptMerchantResponseData(OpenApiMerchantSecurityMaterialDTO merchantMaterial,
                                                        String encryptedResponseJson) {
        Map<String, Object> responseMap = JsonUtils.parseObject(encryptedResponseJson, new TypeReference<Map<String, Object>>() {
        });
        String encryptedResponseData = String.valueOf(responseMap.get("data"));
        String plainResponseData = payloadCrypto.decrypt(
                encryptedResponseData,
                keyId -> resolveMerchantResponsePrivateKey(merchantMaterial, keyId)
        );
        PaymentCreateVO responseVO = JsonUtils.parseObject(plainResponseData, PaymentCreateVO.class);
        log.info("商户收到响应并解密成功，响应码：{}，响应消息：{}，订单号：{}，金额：{}，币种：{}",
                responseMap.get("code"),
                responseMap.get("message"),
                responseVO.getMerchantOrderNo(),
                responseVO.getAmount(),
                responseVO.getCurrency());
        return responseVO;
    }

    private PrivateKey resolveMerchantResponsePrivateKey(OpenApiMerchantSecurityMaterialDTO merchantMaterial, String keyId) {
        assertThat(keyId).isEqualTo(merchantMaterial.getMerchantResponseKeyId());
        return payloadCrypto.readPrivateKey(merchantMaterial.getMerchantResponsePrivateKeyPkcs8Base64());
    }

    private String createMerchantJwt(String merchantId, String merchantKey, long issuedAt) {
        Map<String, Object> header = new LinkedHashMap<>();
        header.put(JWTHeader.TYPE, "JWT");
        header.put(JWTHeader.ALGORITHM, "HS256");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(RegisteredPayload.AUDIENCE, List.of("gateway"));
        payload.put(RegisteredPayload.ISSUER, "merchant");
        payload.put(RegisteredPayload.JWT_ID, "trade-" + issuedAt);
        payload.put(RegisteredPayload.ISSUED_AT, issuedAt);
        payload.put(RegisteredPayload.EXPIRES_AT, issuedAt + 180L);
        payload.put("merchantId", merchantId);
        return JWTUtil.createToken(header, payload, merchantKey.getBytes(StandardCharsets.UTF_8));
    }

    private String authorizationPlainText(String merchantId) {
        return """
                {
                  "merchantInfo": {
                    "merchantId": "%s",
                    "subMerchantInfo": {
                      "subName": "John",
                      "subCompanyName": "JohnCompany",
                      "subId": "123456789111111",
                      "subPostal": "SW1 1AA",
                      "subStreet": "Regent Street",
                      "subCity": "London",
                      "subState": "AL",
                      "subCountryCode": "USA",
                      "subTaxId": "ABC-123456789",
                      "subEmail": "John@email.com",
                      "subPhone": "+55-5058149876",
                      "merchantCategory": "5311",
                      "intesCode": "1009",
                      "chargeType": "310"
                    }
                  },
                  "orderInfo": {
                    "amount": 12389.45,
                    "currency": "USD",
                    "tradeNo": "20250116140182865587"
                  },
                  "billingCardHolderInfo": {
                    "firstName": "John",
                    "lastName": "tom",
                    "phone": "+55-5058149876",
                    "email": "username@liquido.com",
                    "country": "USA",
                    "state": "AL",
                    "city": "city name",
                    "street": "street name",
                    "postal": "03400"
                  },
                  "cardInfo": {
                    "cardNo": "5387380678556554",
                    "expirationMonth": "03",
                    "expirationYear": "2028",
                    "securityCode": "123"
                  },
                  "threeDSInfo": {
                    "eci": "212",
                    "cavv": "kANiJlhEqL/yaEfVxr/BUoQBicnh",
                    "dsTransactionId": "b96c957d-daa1-4b7f-b8b4-373fb9dec47b",
                    "threeDsVersion": "2.2.0"
                  },
                  "transactionInfo": {
                    "transactionId": "20250116140182887083",
                    "description": "authorize request"
                  }
                }""".formatted(merchantId);
    }
}
