package com.scott.payment.openapi;

import cn.hutool.jwt.JWTHeader;
import cn.hutool.jwt.JWTUtil;
import cn.hutool.jwt.RegisteredPayload;
import com.alibaba.fastjson2.TypeReference;
import com.scott.payment.component.core.enums.ApiCoResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.util.SensitiveDataMaskUtils;
import com.scott.payment.component.core.util.identity.PaymentOrderNoGenerator;
import com.scott.payment.component.security.crypto.OpenApiPayloadCrypto;
import com.scott.payment.component.security.jwt.JwtMerchantClaims;
import com.scott.payment.component.security.jwt.MerchantJwtVerifier;
import com.scott.payment.component.security.key.OpenApiKeyMaterialFactory;
import com.scott.payment.component.security.key.OpenApiKeyMaterialFactory.MerchantOpenApiCredential;
import com.scott.payment.component.security.key.OpenApiKeyMaterialFactory.OpenApiMerchantOnboardingMaterial;
import com.scott.payment.component.security.key.OpenApiKeyMaterialFactory.RsaKeyMaterial;
import com.scott.payment.component.web.handler.GlobalExceptionHandler;
import com.scott.payment.openapi.api.rest.payment.v1.OpenApiPaymentController;
import com.scott.payment.openapi.dto.body.ApiMerchantPaymentRequestDTO;
import com.scott.payment.openapi.security.MerchantKeyProvider;
import com.scott.payment.openapi.security.OpenApiPayloadKeyProvider;
import com.scott.payment.openapi.service.PaymentService;
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
import org.springframework.test.web.servlet.MvcResult;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
     * 商户响应加密公钥编号，模拟响应加密增强模式下商户上传的平台侧配置。
     */
    private static final String MERCHANT_RESPONSE_KEY_ID = "merchant-200045-response-rsa-001";

    /**
     * OpenAPI 授权测试路径，模拟商户真实发起收单授权请求。
     */
    private static final String AUTHORIZATION_PATH = "/api/rest/payment/v1/authorization";

    /**
     * OpenAPI 授权请求头名称，测试日志只打印摘要，不打印完整 JWT。
     */
    private static final String AUTHORIZATION_HEADER = "authorization";

    /**
     * 固定测试时间，避免 JWT iat/exp 因真实时钟变化导致测试不稳定。
     */
    private static final long NOW_EPOCH_SECONDS = 1_704_960_018L;

    /**
     * 测试用平台收单订单号前缀，保持与 service-payment 模拟实现一致。
     */
    private static final String PAYMENT_ORDER_PREFIX = "PA";

    /**
     * 测试用交易已接收状态，表示 OpenAPI 请求已完成鉴权、解密和参数校验。
     */
    private static final String STATUS_RECEIVED = "RECEIVED";

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

        log.info("商户对接材料已生成，商户号：{}，JWT算法：{}，JWT有效期秒数：{}，merchantKey安全指纹：{}",
                material.merchantCredential().merchantId(),
                material.merchantCredential().jwtAlgorithm(),
                material.merchantCredential().jwtExpiresSeconds(),
                keyMaterialFactory.fingerprint(material.merchantCredential().merchantKey()));
        log.info("下发给商户的平台公钥摘要，keyId：{}，公钥长度：{}，公钥指纹：{}",
                material.merchantCredential().platformPayloadKeyId(),
                material.merchantCredential().platformPublicKeyX509Base64().length(),
                keyMaterialFactory.fingerprint(material.merchantCredential().platformPublicKeyX509Base64()));
        log.info("平台服务端内部私钥材料摘要，keyId：{}，密钥位数：{}，公钥指纹：{}，私钥仅平台保存：{}",
                material.platformPayloadKey().keyId(),
                material.platformPayloadKey().keySize(),
                keyMaterialFactory.fingerprint(material.platformPayloadKey().publicKeyX509Base64()),
                Boolean.TRUE);

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
        log.info("授权请求明文已生成，脱敏后的明文参数：{}", SensitiveDataMaskUtils.maskJson(plainText));

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
     * 验证商户从开户拿到密钥后，完成一次带响应加密的 OpenAPI 请求闭环。
     * <p>
     * 该用例把数据库表、商户侧动作、服务端动作和响应解密放在同一条链路中，方便观察真实接入时每个密钥的用途。
     */
    @Test
    void shouldCompleteMerchantOpenApiRoundTripWithDatabaseKeysAndEncryptedResponse() throws Exception {
        OpenApiPayloadCrypto payloadCrypto = new OpenApiPayloadCrypto();
        ProvisionedOpenApiEnvironment environment = provisionOpenApiEnvironment(payloadCrypto);
        String plainRequestJson = authorizationPlainText();
        long issuedAt = System.currentTimeMillis() / 1000L;

        logMerchantKeyTopology(environment);
        String encryptedRequestData = buildMerchantEncryptedRequest(payloadCrypto, environment, plainRequestJson);
        String authorization = createMerchantJwt(MERCHANT_ID, environment.clientKeyBox().merchantKey(), issuedAt);
        String httpRequestBody = JsonUtils.toJsonString(Map.of("data", encryptedRequestData));
        logMerchantHttpRequest(authorization, httpRequestBody);

        verifyServerSecurityChecks(payloadCrypto, environment, authorization, encryptedRequestData, issuedAt);

        AtomicReference<ApiMerchantPaymentRequestDTO> capturedRequest = new AtomicReference<>();
        MockMvc mockMvc = buildMockMvc(payloadCrypto, environment.database(), environment.database(), capturedRequest);
        MvcResult mvcResult = mockMvc.perform(post(AUTHORIZATION_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(AUTHORIZATION_HEADER, authorization)
                        .content(httpRequestBody))
                .andDo(result -> log.info("商户HTTP请求已进入服务端，HTTP状态：{}，服务端明文响应摘要：{}",
                        result.getResponse().getStatus(),
                        SensitiveDataMaskUtils.maskJson(result.getResponse().getContentAsString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ApiCoResultEnum.SUCCESS.getCode()))
                .andReturn();

        assertThat(capturedRequest.get()).isNotNull();
        assertThat(capturedRequest.get().getMerchantInfo().getMerchantId()).isEqualTo(MERCHANT_ID);
        log.info("服务端完成DTO解析，商户订单号：{}，脱敏卡号：{}，交易金额：{} {}",
                capturedRequest.get().getOrderInfo().getTradeNo(),
                SensitiveDataMaskUtils.maskPan(capturedRequest.get().getCardInfo().getCardNo()),
                capturedRequest.get().getOrderInfo().getAmount(),
                capturedRequest.get().getOrderInfo().getCurrency());

        String encryptedResponseJson = encryptServerResponseData(payloadCrypto, environment, mvcResult.getResponse().getContentAsString());
        PaymentCreateVO merchantResponse = decryptMerchantResponseData(payloadCrypto, environment, encryptedResponseJson);

        assertThat(merchantResponse.getMerchantOrderNo()).isEqualTo("20250116140182865587");
        assertThat(merchantResponse.getCurrency()).isEqualTo("USD");
        assertThat(merchantResponse.getAmount()).isEqualTo(1_238_945L);
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
        return buildMockMvc(payloadCrypto,
                new TestOpenApiPayloadKeyProvider(platformKeyPair),
                merchantId -> merchantKey,
                capturedRequest);
    }

    /**
     * 构建 OpenAPI 授权接口 MockMvc。
     *
     * @param payloadCrypto      OpenAPI 报文加解密工具
     * @param payloadKeyProvider 平台 RSA 密钥提供器，模拟服务端按 kid 查找私钥
     * @param merchantKeyProvider 商户 JWT 密钥提供器，模拟服务端按 merchantId 查找 merchantKey
     * @param capturedRequest    捕获控制器收到的解密 DTO
     * @return MockMvc 实例
     */
    private MockMvc buildMockMvc(OpenApiPayloadCrypto payloadCrypto,
                                 OpenApiPayloadKeyProvider payloadKeyProvider,
                                 MerchantKeyProvider merchantKeyProvider,
                                 AtomicReference<ApiMerchantPaymentRequestDTO> capturedRequest) {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        OpenApiPayloadDecoder payloadDecoder = new OpenApiPayloadDecoder(payloadCrypto, payloadKeyProvider);
        OpenApiValidator openApiValidator = new OpenApiValidator(validator);
        OpenApiRequestBodyAdvice requestBodyAdvice = new OpenApiRequestBodyAdvice(payloadDecoder, openApiValidator);
        OpenApiRequestHeaderExtractor headerExtractor = new OpenApiRequestHeaderExtractor(
                new MerchantJwtVerifier(),
                merchantKeyProvider);
        PaymentService paymentService = (encryptedData, requestDTO) -> {
            capturedRequest.set(requestDTO);
            PaymentCreateVO response = new PaymentCreateVO();
            response.setPaymentOrderNo(PaymentOrderNoGenerator.nextOrderNo(PAYMENT_ORDER_PREFIX));
            response.setMerchantOrderNo(requestDTO.getOrderInfo().getTradeNo());
            response.setCurrency(requestDTO.getOrderInfo().getCurrency());
            response.setStatus(STATUS_RECEIVED);
            response.setAmount(requestDTO.getOrderInfo().getAmount().movePointRight(2).longValueExact());
            return response;
        };
        return MockMvcBuilders.standaloneSetup(new OpenApiPaymentController(paymentService))
                .addInterceptors(new OpenApiHeaderInterceptor(headerExtractor))
                .setCustomArgumentResolvers(new OpenApiRequestArgumentResolver())
                .setControllerAdvice(requestBodyAdvice, new GlobalExceptionHandler())
                .build();
    }

    /**
     * 初始化测试用 OpenAPI 安全环境，模拟平台开户流程和数据库落库。
     *
     * @param payloadCrypto OpenAPI 报文加解密工具
     * @return 已完成开户和密钥落库的测试环境
     */
    private ProvisionedOpenApiEnvironment provisionOpenApiEnvironment(OpenApiPayloadCrypto payloadCrypto) {
        RsaKeyMaterial platformPayloadKey = keyMaterialFactory.generatePlatformPayloadRsaKey(KEY_ID);
        MerchantOpenApiCredential merchantCredential = keyMaterialFactory.generateMerchantCredential(MERCHANT_ID, platformPayloadKey);
        KeyPair merchantResponseKeyPair = payloadCrypto.generateRsaKeyPair(2048);
        MerchantResponseKeyRow responseKeyRow = new MerchantResponseKeyRow(
                MERCHANT_ID,
                MERCHANT_RESPONSE_KEY_ID,
                Base64.getEncoder().encodeToString(merchantResponseKeyPair.getPublic().getEncoded()),
                Boolean.TRUE
        );
        TestOpenApiSecurityDatabase database = new TestOpenApiSecurityDatabase(
                payloadCrypto,
                new MerchantInfoRow(MERCHANT_ID, "Scott Test Merchant", "ACTIVE"),
                new MerchantJwtKeyRow(MERCHANT_ID, "jwt-v1", merchantCredential.merchantKey(), Boolean.TRUE),
                new PlatformPayloadKeyRow(
                        platformPayloadKey.keyId(),
                        platformPayloadKey.publicKeyX509Base64(),
                        platformPayloadKey.privateKeyPkcs8Base64(),
                        Boolean.TRUE
                ),
                responseKeyRow
        );
        MerchantClientKeyBox clientKeyBox = new MerchantClientKeyBox(
                MERCHANT_ID,
                merchantCredential.merchantKey(),
                merchantCredential.platformPayloadKeyId(),
                merchantCredential.platformPublicKeyX509Base64(),
                responseKeyRow.responseKeyId(),
                Base64.getEncoder().encodeToString(merchantResponseKeyPair.getPrivate().getEncoded())
        );
        return new ProvisionedOpenApiEnvironment(database, clientKeyBox);
    }

    /**
     * 打印商户开户后的密钥拓扑，日志只输出密钥长度、kid 和指纹。
     *
     * @param environment 测试环境
     */
    private void logMerchantKeyTopology(ProvisionedOpenApiEnvironment environment) {
        TestOpenApiSecurityDatabase database = environment.database();
        MerchantClientKeyBox clientKeyBox = environment.clientKeyBox();
        log.info("密钥拓扑-商户信息表，商户号：{}，商户名称：{}，状态：{}",
                database.merchantInfoRow().merchantId(),
                database.merchantInfoRow().merchantName(),
                database.merchantInfoRow().status());
        log.info("密钥拓扑-商户默认必需材料，merchantKey只在开户时安全交付，日志仅打印指纹：{}，平台公钥ID：{}，平台公钥指纹：{}",
                keyMaterialFactory.fingerprint(clientKeyBox.merchantKey()),
                clientKeyBox.platformPayloadKeyId(),
                keyMaterialFactory.fingerprint(clientKeyBox.platformPublicKeyX509Base64()));
        log.info("密钥拓扑-平台保留材料，平台私钥ID：{}，私钥仅平台保存：{}，响应加密增强公钥ID：{}，商户响应公钥指纹：{}",
                database.platformPayloadKeyRow().platformKeyId(),
                Boolean.TRUE,
                database.merchantResponseKeyRow().responseKeyId(),
                keyMaterialFactory.fingerprint(database.merchantResponseKeyRow().publicKeyX509Base64()));
        log.info("密钥拓扑-关联关系，merchantId关联merchantKey，platformKeyId关联平台公私钥；responseKeyId只在响应加密增强模式下关联商户响应公钥和商户侧私钥");
    }

    /**
     * 模拟商户使用平台公钥加密请求体 data。
     *
     * @param payloadCrypto    OpenAPI 报文加解密工具
     * @param environment      测试环境
     * @param plainRequestJson 商户业务 JSON 明文
     * @return compact 密文 data
     */
    private String buildMerchantEncryptedRequest(OpenApiPayloadCrypto payloadCrypto,
                                                 ProvisionedOpenApiEnvironment environment,
                                                 String plainRequestJson) {
        MerchantClientKeyBox clientKeyBox = environment.clientKeyBox();
        String encryptedData = payloadCrypto.encrypt(
                plainRequestJson,
                clientKeyBox.platformPayloadPublicKey(payloadCrypto),
                clientKeyBox.platformPayloadKeyId()
        );
        log.info("商户完成请求体加密，明文参数脱敏：{}",
                SensitiveDataMaskUtils.maskJson(plainRequestJson));
        log.info("商户完成请求体加密，data段数：{}，data长度：{}，data指纹：{}",
                encryptedData.split("\\.").length,
                encryptedData.length(),
                keyMaterialFactory.fingerprint(encryptedData));
        return encryptedData;
    }

    /**
     * 打印商户 HTTP 调用过程，避免输出完整 JWT 和完整密文。
     *
     * @param authorization   商户 JWT
     * @param httpRequestBody HTTP 请求体
     */
    private void logMerchantHttpRequest(String authorization, String httpRequestBody) {
        Map<String, Object> safeHeaders = new LinkedHashMap<>();
        safeHeaders.put("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        safeHeaders.put(AUTHORIZATION_HEADER + "Parts", authorization.split("\\.").length);
        safeHeaders.put(AUTHORIZATION_HEADER + "Fingerprint", keyMaterialFactory.fingerprint(authorization));
        Map<String, Object> safeBody = new LinkedHashMap<>();
        safeBody.put("data", "<compact密文省略>");
        safeBody.put("bodyLength", httpRequestBody.length());
        safeBody.put("bodyFingerprint", keyMaterialFactory.fingerprint(httpRequestBody));
        log.info("商户发起HTTP请求，method：POST，path：{}，headers安全摘要：{}，body安全摘要：{}",
                AUTHORIZATION_PATH,
                JsonUtils.toJsonString(safeHeaders),
                JsonUtils.toJsonString(safeBody));
    }

    /**
     * 模拟服务端收到请求后的安全校验，包括成功验签、错误密钥、过期 JWT 和密文篡改。
     *
     * @param payloadCrypto     OpenAPI 报文加解密工具
     * @param environment       测试环境
     * @param authorization     商户 JWT
     * @param encryptedData     商户请求体密文
     * @param nowEpochSeconds   当前秒级时间戳
     */
    private void verifyServerSecurityChecks(OpenApiPayloadCrypto payloadCrypto,
                                            ProvisionedOpenApiEnvironment environment,
                                            String authorization,
                                            String encryptedData,
                                            long nowEpochSeconds) {
        TestOpenApiSecurityDatabase database = environment.database();
        MerchantJwtVerifier verifier = new MerchantJwtVerifier();
        JwtMerchantClaims claims = verifier.verify(
                authorization,
                database.getMerchantKey(MERCHANT_ID),
                nowEpochSeconds + 1L
        );
        String decryptedJson = payloadCrypto.decrypt(encryptedData, database::getPlatformPrivateKey);
        log.info("服务端验签成功，商户号：{}，jti：{}，请求体解密摘要：{}",
                claims.getMerchantId(),
                claims.getJwtId(),
                SensitiveDataMaskUtils.maskJson(decryptedJson));

        assertThatThrownBy(() -> verifier.verify(authorization, "wrong-merchant-key", nowEpochSeconds + 1L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining(ApiCoResultEnum.CO_UNAUTHORIZED_JWT_SIGN.getMessage());
        log.info("反向用例校验通过-错误merchantKey会被拒绝，预期错误码：{}",
                ApiCoResultEnum.CO_UNAUTHORIZED_JWT_SIGN.getCode());

        String expiredJwt = createMerchantJwt(MERCHANT_ID, database.getMerchantKey(MERCHANT_ID), NOW_EPOCH_SECONDS);
        assertThatThrownBy(() -> verifier.verify(expiredJwt, database.getMerchantKey(MERCHANT_ID), NOW_EPOCH_SECONDS + 181L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining(ApiCoResultEnum.CO_UNAUTHORIZED_JWT_EXP.getMessage());
        log.info("反向用例校验通过-JWT过期会被拒绝，预期错误码：{}",
                ApiCoResultEnum.CO_UNAUTHORIZED_JWT_EXP.getCode());

        String tamperedData = tamperCiphertextSegment(encryptedData);
        assertThatThrownBy(() -> payloadCrypto.decrypt(tamperedData, database::getPlatformPrivateKey))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining(ApiCoResultEnum.CO_REQUIRED_PARAMETER_ILLEGAL.getMessage());
        log.info("反向用例校验通过-data密文被篡改时，AES-GCM认证失败，服务端拒绝解析");
    }

    /**
     * 模拟服务端响应加密增强模式。
     *
     * @param payloadCrypto       OpenAPI 报文加解密工具
     * @param environment         测试环境
     * @param plainResponseBody   控制器返回的明文 CommonResult
     * @return 服务端加密后的响应 JSON
     */
    private String encryptServerResponseData(OpenApiPayloadCrypto payloadCrypto,
                                             ProvisionedOpenApiEnvironment environment,
                                             String plainResponseBody) {
        Map<String, Object> responseMap = JsonUtils.parseObject(plainResponseBody, new TypeReference<Map<String, Object>>() {
        });
        String plainResponseData = JsonUtils.toJsonString(responseMap.get("data"));
        String encryptedResponseData = payloadCrypto.encrypt(
                plainResponseData,
                environment.database().getMerchantResponsePublicKey(MERCHANT_ID),
                environment.database().getMerchantResponseKeyId(MERCHANT_ID)
        );
        Map<String, Object> encryptedResponse = new LinkedHashMap<>();
        encryptedResponse.put("code", responseMap.get("code"));
        encryptedResponse.put("message", responseMap.get("message"));
        encryptedResponse.put("data", encryptedResponseData);
        log.info("增强模式下服务端完成响应加密，使用商户响应公钥ID：{}，响应data长度：{}，响应data指纹：{}",
                environment.database().getMerchantResponseKeyId(MERCHANT_ID),
                encryptedResponseData.length(),
                keyMaterialFactory.fingerprint(encryptedResponseData));
        return JsonUtils.toJsonString(encryptedResponse);
    }

    /**
     * 模拟商户收到响应后，使用商户侧响应私钥解密 data。
     *
     * @param payloadCrypto         OpenAPI 报文加解密工具
     * @param environment           测试环境
     * @param encryptedResponseJson 服务端加密响应 JSON
     * @return 商户解密后的业务响应
     */
    private PaymentCreateVO decryptMerchantResponseData(OpenApiPayloadCrypto payloadCrypto,
                                                        ProvisionedOpenApiEnvironment environment,
                                                        String encryptedResponseJson) {
        Map<String, Object> responseMap = JsonUtils.parseObject(encryptedResponseJson, new TypeReference<Map<String, Object>>() {
        });
        String encryptedResponseData = String.valueOf(responseMap.get("data"));
        String plainResponseData = payloadCrypto.decrypt(
                encryptedResponseData,
                keyId -> environment.clientKeyBox().merchantResponsePrivateKey(payloadCrypto, keyId)
        );
        PaymentCreateVO merchantResponse = JsonUtils.parseObject(plainResponseData, PaymentCreateVO.class);
        log.info("商户完成响应解密，响应码：{}，响应消息：{}，订单号：{}，金额：{}，币种：{}",
                responseMap.get("code"),
                responseMap.get("message"),
                merchantResponse.getMerchantOrderNo(),
                merchantResponse.getAmount(),
                merchantResponse.getCurrency());
        return merchantResponse;
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

    /**
     * 已完成开户初始化的 OpenAPI 测试环境。
     *
     * @param database     模拟平台侧数据库表
     * @param clientKeyBox 模拟商户侧安全保存的密钥材料
     */
    private record ProvisionedOpenApiEnvironment(TestOpenApiSecurityDatabase database,
                                                 MerchantClientKeyBox clientKeyBox) {
    }

    /**
     * 商户基础信息表记录。
     *
     * @param merchantId   支付平台分配的商户号
     * @param merchantName 商户名称
     * @param status       商户状态，生产环境可扩展为启用、冻结、注销等状态
     */
    private record MerchantInfoRow(String merchantId, String merchantName, String status) {
    }

    /**
     * 商户 JWT 密钥表记录。
     * <p>
     * 生产数据库中 merchantKey 必须加密存储；当前测试为了完整验签链路，使用内存明文模拟解密后的密钥值。
     *
     * @param merchantId  商户号
     * @param keyVersion  商户 JWT 密钥版本号
     * @param merchantKey 商户 JWT HS256 签名密钥
     * @param enabled     当前密钥是否启用
     */
    private record MerchantJwtKeyRow(String merchantId, String keyVersion, String merchantKey, Boolean enabled) {
    }

    /**
     * 平台请求体 RSA 密钥表记录。
     * <p>
     * publicKeyX509Base64 可下发给商户；privateKeyPkcs8Base64 只能由平台服务端/KMS 保存。
     *
     * @param platformKeyId        平台 RSA kid
     * @param publicKeyX509Base64  X.509 DER Base64 公钥
     * @param privateKeyPkcs8Base64 PKCS#8 DER Base64 私钥
     * @param enabled              当前平台密钥是否启用
     */
    private record PlatformPayloadKeyRow(String platformKeyId,
                                         String publicKeyX509Base64,
                                         String privateKeyPkcs8Base64,
                                         Boolean enabled) {
    }

    /**
     * 商户响应公钥表记录。
     * <p>
     * 该表只保存商户上传的响应公钥；响应私钥留在商户侧，用于商户解密平台响应 data。
     *
     * @param merchantId          商户号
     * @param responseKeyId       商户响应公钥 kid
     * @param publicKeyX509Base64 X.509 DER Base64 商户响应公钥
     * @param enabled             当前响应公钥是否启用
     */
    private record MerchantResponseKeyRow(String merchantId,
                                          String responseKeyId,
                                          String publicKeyX509Base64,
                                          Boolean enabled) {
    }

    /**
     * 商户侧安全保存的密钥材料。
     * <p>
     * merchantKey、平台公钥和商户响应私钥都只应出现在商户服务端，不应进入浏览器、App 包或前端代码。
     *
     * @param merchantId                       商户号
     * @param merchantKey                      商户 JWT HS256 签名密钥
     * @param platformPayloadKeyId             平台请求体公钥 kid
     * @param platformPublicKeyX509Base64      平台请求体 X.509 DER Base64 公钥
     * @param merchantResponseKeyId            商户响应私钥 kid
     * @param merchantResponsePrivateKeyBase64 商户响应 PKCS#8 DER Base64 私钥
     */
    private record MerchantClientKeyBox(String merchantId,
                                        String merchantKey,
                                        String platformPayloadKeyId,
                                        String platformPublicKeyX509Base64,
                                        String merchantResponseKeyId,
                                        String merchantResponsePrivateKeyBase64) {

        /**
         * 读取平台请求体公钥。
         *
         * @param payloadCrypto OpenAPI 报文加解密工具
         * @return 平台 RSA 公钥
         */
        private PublicKey platformPayloadPublicKey(OpenApiPayloadCrypto payloadCrypto) {
            return payloadCrypto.readPublicKey(platformPublicKeyX509Base64);
        }

        /**
         * 读取商户响应私钥。
         *
         * @param payloadCrypto OpenAPI 报文加解密工具
         * @param responseKeyId 响应密文受保护头中的 kid
         * @return 商户响应 RSA 私钥
         */
        private PrivateKey merchantResponsePrivateKey(OpenApiPayloadCrypto payloadCrypto, String responseKeyId) {
            assertThat(responseKeyId).isEqualTo(merchantResponseKeyId);
            return payloadCrypto.readPrivateKey(merchantResponsePrivateKeyBase64);
        }
    }

    /**
     * OpenAPI 安全测试数据库。
     * <p>
     * 该类同时实现商户密钥查询和平台私钥查询接口，用内存表模拟后续 MySQL/Nacos/KMS 的组合查询。
     */
    private static final class TestOpenApiSecurityDatabase implements MerchantKeyProvider, OpenApiPayloadKeyProvider {

        /**
         * OpenAPI 报文加解密工具，用于把表中的 Base64 密钥恢复为 JCA Key。
         */
        private final OpenApiPayloadCrypto payloadCrypto;

        /**
         * 商户基础信息表。
         */
        private final MerchantInfoRow merchantInfoRow;

        /**
         * 商户 JWT 密钥表。
         */
        private final MerchantJwtKeyRow merchantJwtKeyRow;

        /**
         * 平台请求体 RSA 密钥表。
         */
        private final PlatformPayloadKeyRow platformPayloadKeyRow;

        /**
         * 商户响应公钥表。
         */
        private final MerchantResponseKeyRow merchantResponseKeyRow;

        private TestOpenApiSecurityDatabase(OpenApiPayloadCrypto payloadCrypto,
                                            MerchantInfoRow merchantInfoRow,
                                            MerchantJwtKeyRow merchantJwtKeyRow,
                                            PlatformPayloadKeyRow platformPayloadKeyRow,
                                            MerchantResponseKeyRow merchantResponseKeyRow) {
            this.payloadCrypto = payloadCrypto;
            this.merchantInfoRow = merchantInfoRow;
            this.merchantJwtKeyRow = merchantJwtKeyRow;
            this.platformPayloadKeyRow = platformPayloadKeyRow;
            this.merchantResponseKeyRow = merchantResponseKeyRow;
        }

        /**
         * 获取商户基础信息表记录。
         *
         * @return 商户基础信息
         */
        private MerchantInfoRow merchantInfoRow() {
            return merchantInfoRow;
        }

        /**
         * 获取平台 RSA 密钥表记录。
         *
         * @return 平台请求体 RSA 密钥记录
         */
        private PlatformPayloadKeyRow platformPayloadKeyRow() {
            return platformPayloadKeyRow;
        }

        /**
         * 获取商户响应公钥表记录。
         *
         * @return 商户响应公钥记录
         */
        private MerchantResponseKeyRow merchantResponseKeyRow() {
            return merchantResponseKeyRow;
        }

        /**
         * 根据 merchantId 查询商户 JWT 签名密钥。
         *
         * @param merchantId 支付平台分配的商户号
         * @return 商户 JWT HS256 签名密钥
         */
        @Override
        public String getMerchantKey(String merchantId) {
            assertThat(merchantId).isEqualTo(merchantJwtKeyRow.merchantId());
            assertThat(merchantJwtKeyRow.enabled()).isTrue();
            return merchantJwtKeyRow.merchantKey();
        }

        /**
         * 根据 kid 查询平台请求体解密私钥。
         *
         * @param keyId 平台 RSA kid
         * @return 平台 RSA 私钥
         */
        @Override
        public PrivateKey getPlatformPrivateKey(String keyId) {
            assertThat(keyId).isEqualTo(platformPayloadKeyRow.platformKeyId());
            assertThat(platformPayloadKeyRow.enabled()).isTrue();
            return payloadCrypto.readPrivateKey(platformPayloadKeyRow.privateKeyPkcs8Base64());
        }

        /**
         * 根据 kid 查询平台请求体加密公钥。
         *
         * @param keyId 平台 RSA kid
         * @return 平台 RSA 公钥
         */
        @Override
        public PublicKey getPlatformPublicKey(String keyId) {
            assertThat(keyId).isEqualTo(platformPayloadKeyRow.platformKeyId());
            assertThat(platformPayloadKeyRow.enabled()).isTrue();
            return payloadCrypto.readPublicKey(platformPayloadKeyRow.publicKeyX509Base64());
        }

        /**
         * 根据 merchantId 查询商户响应公钥。
         *
         * @param merchantId 商户号
         * @return 商户响应 RSA 公钥
         */
        private PublicKey getMerchantResponsePublicKey(String merchantId) {
            assertThat(merchantId).isEqualTo(merchantResponseKeyRow.merchantId());
            assertThat(merchantResponseKeyRow.enabled()).isTrue();
            return payloadCrypto.readPublicKey(merchantResponseKeyRow.publicKeyX509Base64());
        }

        /**
         * 根据 merchantId 查询商户响应公钥 kid。
         *
         * @param merchantId 商户号
         * @return 商户响应公钥 kid
         */
        private String getMerchantResponseKeyId(String merchantId) {
            assertThat(merchantId).isEqualTo(merchantResponseKeyRow.merchantId());
            return merchantResponseKeyRow.responseKeyId();
        }
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
