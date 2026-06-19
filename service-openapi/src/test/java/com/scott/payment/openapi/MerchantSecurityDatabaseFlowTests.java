package com.scott.payment.openapi;

import cn.hutool.jwt.JWTHeader;
import cn.hutool.jwt.JWTUtil;
import cn.hutool.jwt.RegisteredPayload;
import com.alibaba.fastjson2.TypeReference;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.util.SensitiveDataMaskUtils;
import com.scott.payment.component.security.crypto.OpenApiPayloadCrypto;
import com.scott.payment.component.security.jwt.JwtMerchantClaims;
import com.scott.payment.component.security.jwt.MerchantJwtVerifier;
import com.scott.payment.component.security.key.OpenApiKeyMaterialFactory;
import com.scott.payment.openapi.dto.body.ApiMerchantPaymentRequestDTO;
import com.scott.payment.openapi.dto.security.MerchantSecurityMaterialDTO;
import com.scott.payment.openapi.dto.security.MerchantSecuritySeedDTO;
import com.scott.payment.openapi.enums.MerchantRiskLevelEnum;
import com.scott.payment.openapi.service.MerchantSecurityService;
import com.scott.payment.openapi.vo.payment.PaymentCreateVO;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
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
 * @classname : MerchantSecurityDatabaseFlowTests
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
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class MerchantSecurityDatabaseFlowTests {

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
     * 固定过期 JWT 测试时间，避免反向用例依赖真实时间。
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
    private final MerchantSecurityService merchantSecurityService;

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

    MerchantSecurityDatabaseFlowTests(MockMvc mockMvc,
                                      JdbcTemplate jdbcTemplate,
                                      MerchantSecurityService merchantSecurityService,
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
    void cleanMerchantSecurityData() {
        jdbcTemplate.update("DELETE FROM base_merchant_response_key WHERE merchant_id IN (?, ?)", MERCHANT_ID, SECOND_MERCHANT_ID);
        jdbcTemplate.update("DELETE FROM base_merchant_jwt_key WHERE merchant_id IN (?, ?)", MERCHANT_ID, SECOND_MERCHANT_ID);
        jdbcTemplate.update("DELETE FROM base_platform_payload_key WHERE merchant_id IN (?, ?)", MERCHANT_ID, SECOND_MERCHANT_ID);
        jdbcTemplate.update("DELETE FROM base_merchant_info WHERE merchant_id IN (?, ?)", MERCHANT_ID, SECOND_MERCHANT_ID);
    }

    /**
     * 验证商户开户密钥落库、MyBatisPlus 查询、JWT 鉴权、请求解密、HTTP 调用、响应加密和商户响应解密完整链路。
     *
     * @throws Exception MockMvc 调用异常
     */
    @Test
    void shouldCompleteMerchantOpenApiRoundTripWithMysqlAndMyBatisPlus() throws Exception {
        MerchantSecurityMaterialDTO merchantMaterial = provisionMerchant(MERCHANT_ID);
        MerchantSecurityMaterialDTO secondMerchantMaterial = provisionMerchant(SECOND_MERCHANT_ID);

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
                .andDo(result -> log.info("服务端HTTP调用完成，HTTP状态：{}，加密响应摘要：{}",
                        result.getResponse().getStatus(),
                        keyMaterialFactory.fingerprint(result.getResponse().getContentAsString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ApiResultEnum.SUCCESS.getCode()))
                .andExpect(jsonPath("$.data").isString())
                .andReturn();

        PaymentCreateVO merchantResponse = decryptMerchantResponseData(merchantMaterial, mvcResult.getResponse().getContentAsString());

        assertThat(merchantResponse.getMerchantOrderNo()).isEqualTo("20250116140182865587");
        assertThat(merchantResponse.getCurrency()).isEqualTo("USD");
        assertThat(merchantResponse.getAmount()).isEqualTo(1_238_945L);
    }

    private MerchantSecurityMaterialDTO provisionMerchant(String merchantId) {
        MerchantSecuritySeedDTO seedDTO = new MerchantSecuritySeedDTO();
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
        seedDTO.setRiskLevel(MerchantRiskLevelEnum.NORMAL.getCode());
        return merchantSecurityService.provisionMerchantSecurityMaterial(seedDTO);
    }

    private void logProvisionedSecurityMaterials(MerchantSecurityMaterialDTO merchantMaterial,
                                                 MerchantSecurityMaterialDTO secondMerchantMaterial) {
        log.info("系统生成商户密钥材料，商户数量：2，主商户号：{}，第二商户号：{}",
                merchantMaterial.getMerchantId(),
                secondMerchantMaterial.getMerchantId());
        log.info("商户默认必需材料摘要：merchantKey指纹：{}，平台公钥指纹：{}，商户响应私钥指纹：{}",
                keyMaterialFactory.fingerprint(merchantMaterial.getMerchantKey()),
                keyMaterialFactory.fingerprint(merchantMaterial.getPlatformPublicKeyX509Base64()),
                keyMaterialFactory.fingerprint(merchantMaterial.getMerchantResponsePrivateKeyPkcs8Base64()));
        log.info("平台保留的材料：每个merchant_id独立关联平台请求体RSA私钥、商户响应公钥和merchantKey；平台不保存商户响应私钥");
        log.info("密钥关联关系：merchant_id关联merchantKey、平台请求体RSA密钥、商户响应公钥；请求体和响应体都不再携带密钥编号");
    }

    private void assertDatabaseLookupByMyBatisPlus(MerchantSecurityMaterialDTO merchantMaterial,
                                                   MerchantSecurityMaterialDTO secondMerchantMaterial) {
        assertThat(merchantSecurityService.getActiveMerchant(merchantMaterial.getMerchantId()).getMerchantName())
                .isEqualTo(merchantMaterial.getMerchantName());
        assertThat(merchantSecurityService.getMerchantKey(merchantMaterial.getMerchantId()))
                .isEqualTo(merchantMaterial.getMerchantKey());
        assertThat(merchantSecurityService.getPlatformPublicKey(merchantMaterial.getMerchantId()))
                .isNotNull();
        assertThat(merchantSecurityService.getMerchantResponsePublicKey(merchantMaterial.getMerchantId())).isNotNull();
        assertThat(merchantSecurityService.getMerchantKey(secondMerchantMaterial.getMerchantId()))
                .isEqualTo(secondMerchantMaterial.getMerchantKey());
        log.info("MyBatisPlus数据库检索成功，主商户：{}，第二商户：{}，主从数据源当前都指向同一个MySQL库",
                merchantMaterial.getMerchantId(),
                secondMerchantMaterial.getMerchantId());
    }

    private String encryptMerchantRequestData(MerchantSecurityMaterialDTO merchantMaterial, String plainRequestJson) {
        String encryptedRequestData = payloadCrypto.encrypt(
                plainRequestJson,
                merchantSecurityService.getPlatformPublicKey(merchantMaterial.getMerchantId())
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
                                                        MerchantSecurityMaterialDTO merchantMaterial) {
        String merchantId = merchantJwtVerifier.peekMerchantId(authorization);
        String merchantKey = merchantSecurityService.getMerchantKey(merchantId);
        JwtMerchantClaims claims = merchantJwtVerifier.verify(authorization, merchantKey);
        String decryptedJson = payloadCrypto.decrypt(encryptedRequestData, merchantSecurityService.getPlatformPrivateKey(merchantMaterial.getMerchantId()));
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
                .hasMessageContaining(ApiResultEnum.AUTHORIZATION_JWT_SIGNATURE_INVALID.getMessage());
        log.info("反向用例校验通过-错误merchantKey会被拒绝，预期错误码：{}", ApiResultEnum.AUTHORIZATION_JWT_SIGNATURE_INVALID.getCode());

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
                .hasMessageContaining(ApiResultEnum.AUTHORIZATION_JWT_EXPIRED.getMessage());
        log.info("反向用例校验通过-JWT过期会被拒绝，预期错误码：{}", ApiResultEnum.AUTHORIZATION_JWT_EXPIRED.getCode());

        String tamperedData = tamperCiphertextSegment(encryptedRequestData);
        assertThatThrownBy(() -> payloadCrypto.decrypt(tamperedData, merchantSecurityService.getPlatformPrivateKey(merchantMaterial.getMerchantId())))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining(ApiResultEnum.ENCRYPTED_DATA_INVALID.getMessage());
        log.info("反向用例校验通过-data密文被篡改时，AES-GCM认证失败，服务端拒绝解析");
    }

    private PaymentCreateVO decryptMerchantResponseData(MerchantSecurityMaterialDTO merchantMaterial,
                                                        String encryptedResponseJson) {
        Map<String, Object> responseMap = JsonUtils.parseObject(encryptedResponseJson, new TypeReference<>() {
        });
        String encryptedResponseData = String.valueOf(responseMap.get("data"));
        String plainResponseData = payloadCrypto.decrypt(
                encryptedResponseData,
                payloadCrypto.readPrivateKey(merchantMaterial.getMerchantResponsePrivateKeyPkcs8Base64())
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

    /**
     * 稳定篡改 compact 密文中的 ciphertext 段，确保 AES-GCM 认证一定失败。
     *
     * @param encryptedData OpenAPI compact 密文
     * @return 已篡改 ciphertext 段的密文
     */
    private String tamperCiphertextSegment(String encryptedData) {
        String[] segments = encryptedData.split("\\.");
        assertThat(segments).hasSize(5);
        StringBuilder ciphertextBuilder = new StringBuilder(segments[3]);
        int tamperIndex = ciphertextBuilder.length() / 2;
        char currentChar = ciphertextBuilder.charAt(tamperIndex);
        ciphertextBuilder.setCharAt(tamperIndex, currentChar == 'A' ? 'B' : 'A');
        segments[3] = ciphertextBuilder.toString();
        return String.join(".", segments);
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
