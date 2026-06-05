package com.scott.payment.openapi.support;

import cn.hutool.jwt.JWTHeader;
import cn.hutool.jwt.JWTUtil;
import cn.hutool.jwt.RegisteredPayload;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.security.crypto.OpenApiPayloadCrypto;
import com.scott.payment.component.security.key.OpenApiKeyMaterialFactory;
import com.scott.payment.openapi.dto.security.MerchantSecurityMaterialDTO;
import com.scott.payment.openapi.dto.security.MerchantSecuritySeedDTO;
import com.scott.payment.openapi.enums.MerchantRiskLevelEnum;
import org.assertj.core.api.Assertions;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantOpenApiTestSupport
 * @date : 2026-05-30 09:20
 * @email : scott_x@163.com
 * @description : 商户 OpenAPI 集成测试公共工具，统一构造开户、JWT、密文报文和安全日志摘要
 * @status : create
 */
public final class MerchantOpenApiTestSupport {

    /**
     * OpenAPI 授权接口路径。
     */
    public static final String AUTHORIZATION_PATH = "/api/rest/payment/v1/authorization";

    /**
     * OpenAPI 授权请求头名称。
     */
    public static final String AUTHORIZATION_HEADER = "authorization";

    /**
     * JWT 请求接收方固定值。
     */
    private static final String JWT_AUDIENCE = "gateway";

    /**
     * JWT 请求签发者固定值。
     */
    private static final String JWT_ISSUER = "merchant";

    /**
     * JWT 签名算法固定值。
     */
    private static final String JWT_ALGORITHM = "HS256";

    /**
     * JWT 类型固定值。
     */
    private static final String JWT_TYPE = "JWT";

    /**
     * JWT 最大有效期，单位秒。
     */
    private static final long JWT_EXPIRES_SECONDS = 180L;

    /**
     * 工具类不允许实例化。
     */
    private MerchantOpenApiTestSupport() {
    }

    /**
     * 构造一套外卡收单商户开户初始化参数。
     *
     * @param merchantId 支付框架颁发的商户号
     * @return 商户开户初始化参数
     */
    public static MerchantSecuritySeedDTO buildMerchantSeed(String merchantId) {
        MerchantSecuritySeedDTO seedDTO = new MerchantSecuritySeedDTO();
        seedDTO.setMerchantId(merchantId);
        seedDTO.setMerchantName("Scott Payment Merchant " + merchantId);
        seedDTO.setMerchantShortName("ScottPay" + merchantId);
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
        return seedDTO;
    }

    /**
     * 清理指定商户的测试数据。
     * <p>
     * 该方法只删除测试指定商户，避免误删本地已有业务数据。
     *
     * @param jdbcTemplate   JDBC 模板，仅用于测试数据清理
     * @param merchantIdList 需要清理的商户号列表
     */
    public static void cleanMerchantSecurityData(JdbcTemplate jdbcTemplate,
                                                 List<String> merchantIdList) {
        merchantIdList.forEach(merchantId -> {
            jdbcTemplate.update("DELETE FROM base_merchant_response_key WHERE merchant_id = ?", merchantId);
            jdbcTemplate.update("DELETE FROM base_merchant_jwt_key WHERE merchant_id = ?", merchantId);
            jdbcTemplate.update("DELETE FROM base_platform_payload_key WHERE merchant_id = ?", merchantId);
            jdbcTemplate.update("DELETE FROM base_merchant_info WHERE merchant_id = ?", merchantId);
        });
    }

    /**
     * 使用商户 merchantKey 生成标准 JWT HS256 authorization 请求头。
     *
     * @param merchantId  支付框架颁发的商户号
     * @param merchantKey 商户 JWT HS256 签名密钥
     * @param issuedAt    JWT 签发时间，单位秒
     * @param jwtId       JWT 唯一标识，建议真实接入使用商户订单号
     * @return 可放入 authorization 请求头的 JWT
     */
    public static String createMerchantJwt(String merchantId, String merchantKey, long issuedAt, String jwtId) {
        Map<String, Object> header = new LinkedHashMap<>();
        header.put(JWTHeader.TYPE, JWT_TYPE);
        header.put(JWTHeader.ALGORITHM, JWT_ALGORITHM);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(RegisteredPayload.AUDIENCE, List.of(JWT_AUDIENCE));
        payload.put(RegisteredPayload.ISSUER, JWT_ISSUER);
        payload.put(RegisteredPayload.JWT_ID, jwtId);
        payload.put(RegisteredPayload.ISSUED_AT, issuedAt);
        payload.put(RegisteredPayload.EXPIRES_AT, issuedAt + JWT_EXPIRES_SECONDS);
        payload.put("merchantId", merchantId);
        return JWTUtil.createToken(header, payload, merchantKey.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 构造授权交易明文 JSON。
     *
     * @param merchantId 支付框架颁发的商户号
     * @param tradeNo    商户订单号
     * @return 授权交易明文 JSON
     */
    public static String authorizationPlainText(String merchantId, String tradeNo) {
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
                    "tradeNo": "%s"
                  },
                  "billingCardHolderInfo": {
                    "firstName": "John",
                    "lastName": "Tom",
                    "phone": "+55-5058149876",
                    "email": "username@example.com",
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
                    "transactionId": "txn-%s",
                    "description": "authorize request"
                  }
                }""".formatted(merchantId, tradeNo, tradeNo);
    }

    /**
     * 将 compact 密文封装为 OpenAPI HTTP 请求体 JSON。
     *
     * @param encryptedData compact 密文数据
     * @return HTTP 请求体 JSON
     */
    public static String wrapEncryptedData(String encryptedData) {
        return JsonUtils.toJsonString(Map.of("data", encryptedData));
    }

    /**
     * 构造安全日志摘要，避免日志输出完整密钥、JWT 或密文。
     *
     * @param rawValue           原始敏感文本
     * @param keyMaterialFactory 密钥材料工厂，用于计算短指纹
     * @return 安全摘要
     */
    public static Map<String, Object> safeSecretSummary(String rawValue, OpenApiKeyMaterialFactory keyMaterialFactory) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("length", rawValue == null ? 0 : rawValue.length());
        summary.put("fingerprint", rawValue == null ? null : keyMaterialFactory.fingerprint(rawValue));
        return summary;
    }

    /**
     * 构造 HTTP 调用安全摘要，展示请求头、请求体是否完整，但不输出原始 JWT 和密文。
     *
     * @param authorization      商户 JWT
     * @param httpRequestBody    HTTP 请求体
     * @param keyMaterialFactory 密钥材料工厂，用于计算短指纹
     * @return HTTP 调用安全摘要
     */
    public static Map<String, Object> safeHttpCallSummary(String authorization,
                                                          String httpRequestBody,
                                                          OpenApiKeyMaterialFactory keyMaterialFactory) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("method", "POST");
        summary.put("path", AUTHORIZATION_PATH);
        summary.put("authorizationParts", authorization.split("\\.").length);
        summary.put("authorizationFingerprint", keyMaterialFactory.fingerprint(authorization));
        summary.put("bodyLength", httpRequestBody.length());
        summary.put("bodyFingerprint", keyMaterialFactory.fingerprint(httpRequestBody));
        return summary;
    }

    /**
     * 稳定篡改 compact 密文中的 ciphertext 段，确保 AES-GCM 认证失败。
     *
     * @param encryptedData OpenAPI compact 密文
     * @return 已篡改 ciphertext 段的密文
     */
    public static String tamperCiphertextSegment(String encryptedData) {
        String[] segments = encryptedData.split("\\.");
        Assertions.assertThat(segments).hasSize(5);
        StringBuilder ciphertextBuilder = new StringBuilder(segments[3]);
        int tamperIndex = ciphertextBuilder.length() / 2;
        char currentChar = ciphertextBuilder.charAt(tamperIndex);
        ciphertextBuilder.setCharAt(tamperIndex, currentChar == 'A' ? 'B' : 'A');
        segments[3] = ciphertextBuilder.toString();
        return String.join(".", segments);
    }

    /**
     * 根据商户响应私钥材料解析响应解密私钥。
     *
     * @param merchantMaterial 商户开户时拿到的响应私钥材料
     * @param payloadCrypto    OpenAPI 报文加解密工具
     * @return 商户响应私钥
     */
    public static PrivateKey resolveMerchantResponsePrivateKey(MerchantSecurityMaterialDTO merchantMaterial,
                                                               OpenApiPayloadCrypto payloadCrypto) {
        return payloadCrypto.readPrivateKey(merchantMaterial.getMerchantResponsePrivateKeyPkcs8Base64());
    }

    /**
     * 解析 compact 报文的段数量，用于测试日志证明报文格式完整。
     *
     * @param compactPayload compact 密文
     * @return compact 段数量
     */
    public static int compactPartCount(String compactPayload) {
        return (int) Arrays.stream(compactPayload.split("\\.", -1)).count();
    }
}
