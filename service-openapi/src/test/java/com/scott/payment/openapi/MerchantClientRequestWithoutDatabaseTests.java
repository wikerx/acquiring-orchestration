package com.scott.payment.openapi;

import com.scott.payment.component.core.util.SensitiveDataMaskUtils;
import com.scott.payment.component.security.crypto.OpenApiPayloadCrypto;
import com.scott.payment.component.security.jwt.JwtMerchantClaims;
import com.scott.payment.component.security.jwt.MerchantJwtVerifier;
import com.scott.payment.component.security.key.OpenApiKeyMaterialFactory;
import com.scott.payment.openapi.support.MerchantOpenApiTestSupport;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantClientRequestWithoutDatabaseTests
 * @date : 2026-06-02 21:35
 * @email : scott_x@163.com
 * @description : 不连接数据库的商户请求封装测试，使用固定密钥模拟商户加密请求并模拟 OpenAPI 服务端解包
 * @status : create
 */
@Slf4j
class MerchantClientRequestWithoutDatabaseTests {

    /**
     * 固定测试商户号，模拟平台开户后颁发给商户的 merchantId。
     */
    private static final String MERCHANT_ID = "260001";

    /**
     * 固定商户订单号，同时作为 JWT jti，便于链路追踪和防重放。
     */
    private static final String TRADE_NO = "202606020001";

    /**
     * 固定商户 JWT HS256 签名密钥。
     * <p>
     * 商户用该密钥生成 authorization JWT；平台根据 merchantId 查询同一个密钥完成验签。
     */
    private static final String MERCHANT_KEY = "h6pKuPIVhtWJMRi5K9XZbPII63QoPpZgfZrbEuXU9c4";

    /**
     * 固定平台请求体公钥，商户使用它加密每次请求随机生成的 AES-256-GCM 会话密钥。
     */
    private static final String PLATFORM_PUBLIC_KEY_X509_BASE64 = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAtsf3Qe+9efrYU2NqcauP/wOOyjYVoBnmYRmIdgD3Rhtt7BwHMLZaPfxu72qvWZitAPfSDapnxXdXO7+gDz2/ZHMkzyqFGkXVCJZ3C4wtbsvsoZbt0eH5ZOdIAIFgJMEtzMnD6rzUqxSUm/9xsHSTLnJewq+VClrIYY02VJpIGZ8+6hIfkQA8EBDtKD3HIdRvo+33qobrTsKDDUOvKiP63yb6Qamiu54xegccNvhvnIa3LVXk7SgnBb5L8TJPEEsXcYrKnF3MiXk7eSfc1lhmVOfqayPACA93yMka23kZWaozEgvV3hxrqCML3OldH4UkziCF/ToUiOFMJbvKxhQVOQIDAQAB";

    /**
     * 固定平台请求体私钥。
     * <p>
     * 生产环境只允许平台服务端持有；本测试用它模拟 OpenAPI 服务端按 merchantId 查到私钥并解密请求体。
     */
    private static final String PLATFORM_PRIVATE_KEY_PKCS8_BASE64 = "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQC2x/dB7715+thTY2pxq4//A47KNhWgGeZhGYh2APdGG23sHAcwtlo9/G7vaq9ZmK0A99INqmfFd1c7v6APPb9kcyTPKoUaRdUIlncLjC1uy+yhlu3R4flk50gAgWAkwS3MycPqvNSrFJSb/3GwdJMucl7Cr5UKWshhjTZUmkgZnz7qEh+RADwQEO0oPcch1G+j7feqhutOwoMNQ68qI/rfJvpBqaK7njF6Bxw2+G+chrctVeTtKCcFvkvxMk8QSxdxisqcXcyJeTt5J9zWWGZU5+prI8AID3fIyRrbeRlZqjMSC9XeHGuoIwvc6V0fhSTOIIX9OhSI4Uwlu8rGFBU5AgMBAAECggEACqz8+Z/FlYl7t3R6jp9tKSwYMSJd5VHYnWTu41Q6HSD+rMg++UQ6sB0oOO6mcEQVHWsxccqOsL9q28VdABbksLrIScYGK9sr+V+ZyMBhakkZ40mb+WLKp29EytbDpVHtLrq9fok/SKt1OfP+ZsaK+YRaRDfE1kEoLAkh774VJlMymHmgO1bL50zRKSwTIWIWtWig5B7BqP6SXL+TbKR9Tv4G+Rjj2FtYwWXue2MyhPgprkmoG18niFcj+lNTGpE90OdEZQUat9Lkxz9VrZ9xJ6Km+GAZEZf2G0RTL/XTAxMsx2ttFo6Ew47qPsu2KfJTj5ZI77Oe4QL2WFoFIm9liQKBgQDr6bwjnNiHP/RfQOZImyED/RDcKfU1gLLoUWGIDuPruXnAimY/eh71JIJA8bzLRCA4UG1DJ7ykQPKoyJk/unP/Km6bzpZvSEc2eI5yUI4tuIuK2Hop4agn9O9kMkw1cARa9guVF3rj8UPRWwogLp0IPLy1m3irWW6ed73I+t8zrQKBgQDGWBljt9ndn+wDpOSwJyOXjsmDpiJIa126abdRDuIqORrO7L3m5qQLeVp2qer1juTrYG+wZ4+K3148dSouEr/DE3Dae/Px+aC67MX8B+qa8HwT7nAE2sua6ro9M5GREWq7uWiEJWQJQMN8sR0MNZ0pmpiUYax2GzpSDqyYLFh5PQKBgDPXv5KL60F9mnQ8TN7zyEOaH1RinBJP4AERsT83Fns83Taks2eLrLXueflPpk98/x+g/QHe/6OQ6kKRIqxQiyEt7/SpZ4G4/n1H7PXOIhCGF5RBkkcV4eA2AU2hiAHORga/PzhaWpUw9dhSC12bIMMopce7DL+K/bYxVjGOf/JZAoGAFYbwUoNc0RIPYqHd9ER7N3LW6kP4ypVkmdvpepG7+Es0XqsRPWNhAKHOMLzmdHpq3CUeWi4TRUZTCwrIZjHAwGJ2yC/V3ThzunYCUwVk8CYTwXIKlGxO1uSNDCFxtiYGyJMqBdWtEtgFn531giK7iQ1vbANh3Xu9C7TuBKIjhB0CgYEAqvqzf9rtpTcSrRVQgLx3pkZO+/H9ketzQLatrsLS9hQxoU5FYy/W5cktBl6nbyxzfkawQTrHBXWiJBNgoksu3gFSVj2FWMS19bu/LQpV5o+W5D1vV8AkOUUtis7RyYxjIaYkbjyCVMENgL0FH8iX9qjs2gvun53aaAPpO2Pl5Rw=";

    /**
     * OpenAPI 报文混合加密工具，商户和平台都调用同一套标准实现。
     */
    private final OpenApiPayloadCrypto payloadCrypto = new OpenApiPayloadCrypto();

    /**
     * 商户 JWT 验签工具，用于模拟 OpenAPI 服务端校验 authorization。
     */
    private final MerchantJwtVerifier merchantJwtVerifier = new MerchantJwtVerifier();

    /**
     * 密钥材料工厂只用于计算安全日志指纹，不输出任何完整密钥。
     */
    private final OpenApiKeyMaterialFactory keyMaterialFactory = new OpenApiKeyMaterialFactory();

    /**
     * 使用固定商户密钥和平台公钥模拟商户请求封装，并模拟 OpenAPI 服务端完成 JWT 验签和请求体解密。
     */
    @Test
    void shouldBuildMerchantEncryptedHttpRequestWithoutDatabase() {
        String plainRequestJson = MerchantOpenApiTestSupport.authorizationPlainText(MERCHANT_ID, TRADE_NO);
        log.info("明文请求参数：{}" , plainRequestJson);

        String encryptedData = payloadCrypto.encrypt(
                plainRequestJson,
                payloadCrypto.readPublicKey(PLATFORM_PUBLIC_KEY_X509_BASE64)
        );
        log.info("请求参数加密：{}" , encryptedData);


        String authorization = MerchantOpenApiTestSupport.createMerchantJwt(
                MERCHANT_ID,
                MERCHANT_KEY,
                System.currentTimeMillis() / 1000L,
                TRADE_NO
        );
        log.info("authorization：{}" , authorization);

        String httpRequestBody = MerchantOpenApiTestSupport.wrapEncryptedData(encryptedData);
        log.info("请求参数：{}" , httpRequestBody);

        log.info("商户侧固定密钥摘要-merchantKey指纹：{}，平台公钥指纹：{}",
                keyMaterialFactory.fingerprint(MERCHANT_KEY),
                keyMaterialFactory.fingerprint(PLATFORM_PUBLIC_KEY_X509_BASE64));
        log.info("商户侧明文请求脱敏：{}", SensitiveDataMaskUtils.maskJson(plainRequestJson));
        log.info("商户侧HTTP请求参数摘要：{}",
                MerchantOpenApiTestSupport.safeHttpCallSummary(authorization, httpRequestBody, keyMaterialFactory));

        JwtMerchantClaims claims = merchantJwtVerifier.verify(authorization, MERCHANT_KEY);
        String serverPlainText = payloadCrypto.decrypt(
                encryptedData,
                payloadCrypto.readPrivateKey(PLATFORM_PRIVATE_KEY_PKCS8_BASE64)
        );

        assertThat(authorization.split("\\.")).hasSize(3);
        assertThat(MerchantOpenApiTestSupport.compactPartCount(encryptedData)).isEqualTo(5);
        assertThat(claims.getMerchantId()).isEqualTo(MERCHANT_ID);
        assertThat(claims.getJwtId()).isEqualTo(TRADE_NO);
        assertThat(serverPlainText).isEqualTo(plainRequestJson);
        log.info("模拟OpenAPI服务端验签解密成功-商户号：{}，jti：{}，解密后请求脱敏：{}",
                claims.getMerchantId(),
                claims.getJwtId(),
                SensitiveDataMaskUtils.maskJson(serverPlainText));
    }
}
