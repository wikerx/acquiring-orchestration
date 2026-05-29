package com.scott.payment.openapi;

import cn.hutool.jwt.JWTHeader;
import cn.hutool.jwt.JWTUtil;
import cn.hutool.jwt.RegisteredPayload;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.util.SensitiveDataMaskUtils;
import com.scott.payment.component.security.crypto.OpenApiPayloadCrypto;
import com.scott.payment.component.security.jwt.JwtMerchantClaims;
import com.scott.payment.component.security.jwt.MerchantJwtVerifier;
import com.scott.payment.component.security.key.OpenApiKeyMaterialFactory;
import com.scott.payment.component.security.key.OpenApiKeyMaterialFactory.OpenApiMerchantOnboardingMaterial;
import com.scott.payment.component.web.handler.GlobalExceptionHandler;
import com.scott.payment.openapi.api.rest.payment.v1.OpenApiPaymentController;
import com.scott.payment.openapi.dto.body.ApiMerchantPaymentRequestDTO;
import com.scott.payment.openapi.security.OpenApiPayloadKeyProvider;
import com.scott.payment.openapi.service.OpenApiPaymentService;
import com.scott.payment.openapi.support.OpenApiHeaderInterceptor;
import com.scott.payment.openapi.support.OpenApiPayloadDecoder;
import com.scott.payment.openapi.support.OpenApiRequestArgumentResolver;
import com.scott.payment.openapi.support.OpenApiRequestBodyAdvice;
import com.scott.payment.openapi.support.OpenApiRequestHeaderExtractor;
import com.scott.payment.openapi.support.OpenApiValidator;
import com.scott.payment.openapi.vo.payment.PaymentCreateVO;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiSecurityFlowTests
 * @date : 2026-05-29 21:55
 * @email : scott_x@163.com
 * @description : OpenAPI JWT 鉴权、报文加解密和授权接口调用流程测试
 * @status : create
 */
@Slf4j
class OpenApiSecurityFlowTests {

    /**
     * 测试商户号，当前用于替代数据库中的商户基础资料。
     */
    private static final String MERCHANT_ID = "200045";

    /**
     * 测试 RSA 密钥编号，模拟后续生产密钥轮换场景。
     */
    private static final String KEY_ID = "payment-test-rsa-001";

    /**
     * 固定测试时间，避免 JWT iat/exp 因真实时钟变化导致测试不稳定。
     */
    private static final long NOW_EPOCH_SECONDS = 1_704_960_018L;

    /**
     * 密钥材料生成入口，测试中展示商户可见材料和平台内部 RSA 材料的生成效果。
     */
    private final OpenApiKeyMaterialFactory keyMaterialFactory = new OpenApiKeyMaterialFactory();

    /**
     * 验证商户接入密钥材料可以统一生成，并通过安全日志展示关键摘要。
     */
    @Test
    void shouldGenerateMerchantSecurityMaterial() {
        OpenApiMerchantOnboardingMaterial material = keyMaterialFactory.generateDemoOnboardingMaterial(
                MERCHANT_ID,
                KEY_ID
        );

        log.info("商户对接材料已生成，商户号：{}，JWT算法：{}，JWT有效期秒数：{}，商户签名密钥长度：{}，商户签名密钥指纹：{}",
                material.merchantCredential().merchantId(),
                material.merchantCredential().jwtAlgorithm(),
                material.merchantCredential().jwtExpiresSeconds(),
                material.merchantCredential().merchantKey().length(),
                keyMaterialFactory.fingerprint(material.merchantCredential().merchantKey()));
        log.info("下发给商户的平台公钥摘要，keyId：{}，公钥长度：{}，公钥指纹：{}",
                material.merchantCredential().platformPayloadKeyId(),
                material.merchantCredential().platformPublicKeyX509Base64().length(),
                keyMaterialFactory.fingerprint(material.merchantCredential().platformPublicKeyX509Base64()));
        log.info("平台服务端内部私钥材料摘要，keyId：{}，密钥位数：{}，公钥指纹：{}，服务端私钥长度：{}",
                material.platformPayloadKey().keyId(),
                material.platformPayloadKey().keySize(),
                keyMaterialFactory.fingerprint(material.platformPayloadKey().publicKeyX509Base64()),
                material.platformPayloadKey().privateKeyPkcs8Base64().length());

        assertThat(material.merchantCredential().merchantKey()).isNotBlank();
        assertThat(material.merchantCredential().platformPublicKeyPem()).startsWith("-----BEGIN PUBLIC KEY-----");
        assertThat(material.platformPayloadKey().publicKeyPem()).startsWith("-----BEGIN PUBLIC KEY-----");
        assertThat(material.platformPayloadKey().privateKeyPem()).startsWith("-----BEGIN PRIVATE KEY-----");
    }

    /**
     * 验证 JWT 使用标准 HS256 生成后，可以被当前 OpenAPI 验签器解析和验证。
     */
    @Test
    void shouldCreateAndVerifyMerchantJwtByHs256() {
        String merchantKey = keyMaterialFactory.generateMerchantJwtKey(MERCHANT_ID).merchantKey();
        String token = createMerchantJwt(MERCHANT_ID, merchantKey, NOW_EPOCH_SECONDS);

        JwtMerchantClaims claims = new MerchantJwtVerifier().verify(token, merchantKey, NOW_EPOCH_SECONDS + 10L);

        log.info("商户JWT验签通过，商户号：{}，请求唯一号：{}，签发时间：{}，过期时间：{}，JWT段数：{}",
                claims.getMerchantId(),
                claims.getJwtId(),
                claims.getIssuedAt(),
                claims.getExpiresAt(),
                token.split("\\.").length);

        assertThat(token.split("\\.")).hasSize(3);
        assertThat(claims.getMerchantId()).isEqualTo(MERCHANT_ID);
        assertThat(claims.getIssuedAt()).isEqualTo(NOW_EPOCH_SECONDS);
        assertThat(claims.getExpiresAt()).isEqualTo(NOW_EPOCH_SECONDS + 180L);
    }

    /**
     * 验证 OpenAPI 的 data 字段使用 RSA-OAEP-SHA256 + AES-256-GCM 混合加密后可以正常解密。
     */
    @Test
    void shouldEncryptAndDecryptOpenApiPayload() {
        OpenApiPayloadCrypto payloadCrypto = new OpenApiPayloadCrypto();
        KeyPair platformKeyPair = payloadCrypto.generateRsaKeyPair(2048);
        String plainText = authorizationPlainText();

        String encryptedData = payloadCrypto.encrypt(plainText, platformKeyPair.getPublic(), KEY_ID);
        String decryptedText = payloadCrypto.decrypt(encryptedData, keyId -> platformKeyPair.getPrivate());

        log.info("请求密文已生成，受保护头：{}，密文段数：{}，密文长度：{}，密文指纹：{}",
                decodeProtectedHeader(encryptedData),
                encryptedData.split("\\.").length,
                encryptedData.length(),
                keyMaterialFactory.fingerprint(encryptedData));
        log.info("请求密文已解密，脱敏后的明文参数：{}", SensitiveDataMaskUtils.maskJson(decryptedText));

        assertThat(encryptedData.split("\\.")).hasSize(5);
        assertThat(encryptedData).doesNotContain("5387380678556554");
        assertThat(decryptedText).isEqualTo(plainText);
    }

    /**
     * 验证授权接口完整链路：请求头 JWT 验签、data 解密、DTO 校验、控制器参数注入、业务响应。
     */
    @Test
    void shouldCallAuthorizationApiWithJwtAndEncryptedPayload() throws Exception {
        OpenApiPayloadCrypto payloadCrypto = new OpenApiPayloadCrypto();
        KeyPair platformKeyPair = payloadCrypto.generateRsaKeyPair(2048);
        String encryptedData = payloadCrypto.encrypt(authorizationPlainText(), platformKeyPair.getPublic(), KEY_ID);
        String merchantKey = keyMaterialFactory.generateMerchantJwtKey(MERCHANT_ID).merchantKey();
        String authorization = createMerchantJwt(MERCHANT_ID, merchantKey, System.currentTimeMillis() / 1000L);
        AtomicReference<ApiMerchantPaymentRequestDTO> capturedRequest = new AtomicReference<>();
        MockMvc mockMvc = buildMockMvc(payloadCrypto, platformKeyPair, merchantKey, capturedRequest);

        mockMvc.perform(post("/api/rest/payment/v1/authorization")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("authorization", authorization)
                        .content(JsonUtils.toJsonString(Map.of("data", encryptedData))))
                .andDo(result -> log.info("授权接口调用完成，HTTP状态：{}，响应体：{}",
                        result.getResponse().getStatus(),
                        result.getResponse().getContentAsString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("T200"))
                .andExpect(jsonPath("$.data.merchantOrderNo").value("20250116140182865587"))
                .andExpect(jsonPath("$.data.currency").value("USD"))
                .andExpect(jsonPath("$.data.amount").value(1238945));

        log.info("控制器已收到解密DTO，商户号：{}，商户订单号：{}，脱敏卡号：{}，订单金额：{}，币种：{}",
                capturedRequest.get().getMerchantInfo().getMerchantId(),
                capturedRequest.get().getOrderInfo().getTradeNo(),
                SensitiveDataMaskUtils.maskPan(capturedRequest.get().getCardInfo().getCardNo()),
                capturedRequest.get().getOrderInfo().getAmount(),
                capturedRequest.get().getOrderInfo().getCurrency());

        assertThat(capturedRequest.get()).isNotNull();
        assertThat(capturedRequest.get().getMerchantInfo().getMerchantId()).isEqualTo(MERCHANT_ID);
        assertThat(capturedRequest.get().getCardInfo().getCardNo()).isEqualTo("5387380678556554");
        assertThat(capturedRequest.get().getThreeDsInfo().getThreeDsVersion()).isEqualTo("2.2.0");
    }

    /**
     * 构建 OpenAPI 授权接口 MockMvc。
     *
     * @param payloadCrypto   OpenAPI 报文加解密工具
     * @param platformKeyPair 平台 RSA 密钥对
     * @param merchantKey     商户 JWT HS256 签名密钥，测试中只传入内存，不写日志
     * @param capturedRequest 捕获控制器收到的解密 DTO
     * @return MockMvc 实例
     */
    private MockMvc buildMockMvc(OpenApiPayloadCrypto payloadCrypto,
                                 KeyPair platformKeyPair,
                                 String merchantKey,
                                 AtomicReference<ApiMerchantPaymentRequestDTO> capturedRequest) {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        OpenApiPayloadKeyProvider payloadKeyProvider = new TestOpenApiPayloadKeyProvider(platformKeyPair);
        OpenApiPayloadDecoder payloadDecoder = new OpenApiPayloadDecoder(payloadCrypto, payloadKeyProvider);
        OpenApiValidator openApiValidator = new OpenApiValidator(validator);
        OpenApiRequestBodyAdvice requestBodyAdvice = new OpenApiRequestBodyAdvice(payloadDecoder, openApiValidator);
        OpenApiRequestHeaderExtractor headerExtractor = new OpenApiRequestHeaderExtractor(
                new MerchantJwtVerifier(),
                merchantId -> merchantKey);
        OpenApiPaymentService paymentService = (encryptedData, requestDTO) -> {
            capturedRequest.set(requestDTO);
            PaymentCreateVO response = new PaymentCreateVO();
            response.setMerchantOrderNo(requestDTO.getOrderInfo().getTradeNo());
            response.setCurrency(requestDTO.getOrderInfo().getCurrency());
            response.setAmount(requestDTO.getOrderInfo().getAmount().movePointRight(2).longValue());
            return response;
        };
        return MockMvcBuilders.standaloneSetup(new OpenApiPaymentController(paymentService))
                .addInterceptors(new OpenApiHeaderInterceptor(headerExtractor))
                .setCustomArgumentResolvers(new OpenApiRequestArgumentResolver())
                .setControllerAdvice(requestBodyAdvice, new GlobalExceptionHandler())
                .build();
    }

    /**
     * 生成商户 JWT。
     *
     * @param merchantId  商户号
     * @param merchantKey 商户 JWT 签名密钥
     * @param issuedAt    签发秒级时间戳
     * @return JWT 字符串
     */
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
     * 构建授权接口明文业务报文。
     *
     * @return 授权接口业务 JSON
     */
    private String authorizationPlainText() {
        return """
                {
                  "merchantInfo": {
                    "merchantId": "200045",
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
                }""";
    }

    /**
     * 解码 compact 密文中的受保护头，便于测试日志观察算法和 kid。
     *
     * @param encryptedData compact 密文
     * @return 受保护头 JSON
     */
    private String decodeProtectedHeader(String encryptedData) {
        String protectedHeader = encryptedData.split("\\.", -1)[0];
        return new String(Base64.getUrlDecoder().decode(protectedHeader), StandardCharsets.UTF_8);
    }

    /**
     * 测试用 RSA 密钥提供器，模拟服务端从 Nacos/数据库/KMS 中按 kid 查找平台私钥。
     */
    private record TestOpenApiPayloadKeyProvider(KeyPair platformKeyPair) implements OpenApiPayloadKeyProvider {

        @Override
        public PrivateKey getPlatformPrivateKey(String keyId) {
            assertThat(keyId).isEqualTo(KEY_ID);
            return platformKeyPair.getPrivate();
        }

        @Override
        public PublicKey getPlatformPublicKey(String keyId) {
            assertThat(keyId).isEqualTo(KEY_ID);
            return platformKeyPair.getPublic();
        }
    }
}
