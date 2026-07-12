package com.scott.payment.openapi;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.util.SensitiveDataMaskUtils;
import com.scott.payment.component.security.crypto.OpenApiPayloadCrypto;
import com.scott.payment.component.security.jwt.JwtMerchantClaims;
import com.scott.payment.component.security.jwt.MerchantJwtVerifier;
import com.scott.payment.component.security.key.OpenApiKeyMaterialFactory;
import com.scott.payment.component.security.key.OpenApiKeyMaterialFactory.MerchantJwtKey;
import com.scott.payment.component.security.key.OpenApiKeyMaterialFactory.RsaKeyMaterial;
import com.scott.payment.openapi.support.MerchantOpenApiTestSupport;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiSecurityFlowTests
 * @date : 2026-06-02 16:25
 * @email : scott_x@163.com
 * @description : OpenAPI 无 keyId 安全流程单元测试
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiSecurityFlowTests
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 商户 OpenAPIOpen Api Security Flow Tests，位于 service-openapi 的测试层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Slf4j
class OpenApiSecurityFlowTests {

    /**
     * 测试商户号。
     */
    private static final String MERCHANT_ID = "200045";

    /**
     * 测试商户订单号，同时作为 JWT jti，便于关联请求和交易。
     */
    private static final String TRADE_NO = "20250116140182865587";

    /**
     * 固定过期 JWT 测试时间，单位秒。
     */
    private static final long EXPIRED_ISSUED_AT = 1_704_960_018L;

    /**
     * OpenAPI 报文加解密工具。
     */
    private final OpenApiPayloadCrypto payloadCrypto = new OpenApiPayloadCrypto();

    /**
     * OpenAPI JWT 验签器。
     */
    private final MerchantJwtVerifier jwtVerifier = new MerchantJwtVerifier();

    /**
     * OpenAPI 密钥材料生成器。
     */
    private final OpenApiKeyMaterialFactory keyFactory = new OpenApiKeyMaterialFactory(payloadCrypto, new java.security.SecureRandom());

    /**
     * 覆盖商户开户、JWT、请求体加密、服务端解密、响应强制加密和商户解密的核心流程。
     */
    @Test
    void shouldCompleteSecurityFlowWithoutKeyId() {
        MerchantJwtKey merchantJwtKey = keyFactory.generateMerchantJwtKey(MERCHANT_ID);
        RsaKeyMaterial platformPayloadKey = keyFactory.generatePlatformPayloadRsaKey(MERCHANT_ID);
        RsaKeyMaterial merchantResponseKey = keyFactory.generateMerchantResponseRsaKey(MERCHANT_ID);

        log.info("系统生成密钥-给商户：merchantKey指纹={}，平台公钥指纹={}，商户响应私钥指纹={}",
                keyFactory.fingerprint(merchantJwtKey.merchantKey()),
                keyFactory.fingerprint(platformPayloadKey.publicKeyX509Base64()),
                keyFactory.fingerprint(merchantResponseKey.privateKeyPkcs8Base64()));
        log.info("系统生成密钥-平台保留：merchantKey用于验签，平台私钥指纹={}，商户响应公钥指纹={}",
                keyFactory.fingerprint(platformPayloadKey.privateKeyPkcs8Base64()),
                keyFactory.fingerprint(merchantResponseKey.publicKeyX509Base64()));

        String merchantRequestJson = MerchantOpenApiTestSupport.authorizationPlainText(MERCHANT_ID, TRADE_NO);
        String encryptedRequestData = payloadCrypto.encrypt(
                merchantRequestJson,
                payloadCrypto.readPublicKey(platformPayloadKey.publicKeyX509Base64())
        );
        String authorization = MerchantOpenApiTestSupport.createMerchantJwt(
                MERCHANT_ID,
                merchantJwtKey.merchantKey(),
                System.currentTimeMillis() / 1000L,
                TRADE_NO
        );
        log.info("商户封装请求-请求明文脱敏={}，JWT摘要={}，data摘要={}",
                SensitiveDataMaskUtils.maskJson(merchantRequestJson),
                MerchantOpenApiTestSupport.safeSecretSummary(authorization, keyFactory),
                MerchantOpenApiTestSupport.safeSecretSummary(encryptedRequestData, keyFactory));

        JwtMerchantClaims claims = jwtVerifier.verify(authorization, merchantJwtKey.merchantKey());
        String decryptedRequestJson = payloadCrypto.decrypt(
                encryptedRequestData,
                payloadCrypto.readPrivateKey(platformPayloadKey.privateKeyPkcs8Base64())
        );
        assertThat(claims.getMerchantId()).isEqualTo(MERCHANT_ID);
        assertThat(decryptedRequestJson).isEqualTo(merchantRequestJson);
        log.info("服务端处理请求-JWT验签成功，商户号={}，请求体解密脱敏={}",
                claims.getMerchantId(),
                SensitiveDataMaskUtils.maskJson(decryptedRequestJson));

        String responseDataJson = JsonUtils.toJsonString(java.util.Map.of(
                "merchantOrderNo", TRADE_NO,
                "transactionStatus", "SUCCESS",
                "currency", "USD",
                "amount", 1_238_945L
        ));
        String encryptedResponseData = payloadCrypto.encrypt(
                responseDataJson,
                payloadCrypto.readPublicKey(merchantResponseKey.publicKeyX509Base64())
        );
        String merchantPlainResponse = payloadCrypto.decrypt(
                encryptedResponseData,
                payloadCrypto.readPrivateKey(merchantResponseKey.privateKeyPkcs8Base64())
        );
        assertThat(merchantPlainResponse).isEqualTo(responseDataJson);
        log.info("响应加密完成-code/message保持明文，data密文摘要={}，商户解密后响应={}",
                MerchantOpenApiTestSupport.safeSecretSummary(encryptedResponseData, keyFactory),
                merchantPlainResponse);
    }

    /**
     * 覆盖错误 merchantKey、过期 JWT 和篡改 data 的异常分支。
     */
    @Test
    void shouldRejectInvalidJwtAndTamperedPayloadWithoutKeyId() {
        MerchantJwtKey merchantJwtKey = keyFactory.generateMerchantJwtKey(MERCHANT_ID);
        RsaKeyMaterial platformPayloadKey = keyFactory.generatePlatformPayloadRsaKey(MERCHANT_ID);
        String merchantRequestJson = MerchantOpenApiTestSupport.authorizationPlainText(MERCHANT_ID, TRADE_NO);
        String encryptedRequestData = payloadCrypto.encrypt(
                merchantRequestJson,
                payloadCrypto.readPublicKey(platformPayloadKey.publicKeyX509Base64())
        );
        String authorization = MerchantOpenApiTestSupport.createMerchantJwt(
                MERCHANT_ID,
                merchantJwtKey.merchantKey(),
                System.currentTimeMillis() / 1000L,
                TRADE_NO
        );

        assertThatThrownBy(() -> jwtVerifier.verify(authorization, "wrong-merchant-key"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining(ApiResultEnum.AUTHORIZATION_JWT_SIGNATURE_INVALID.getMessage());
        log.info("异常分支-错误merchantKey验签失败，预期错误码：{}", ApiResultEnum.AUTHORIZATION_JWT_SIGNATURE_INVALID.getCode());

        String expiredJwt = MerchantOpenApiTestSupport.createMerchantJwt(
                MERCHANT_ID,
                merchantJwtKey.merchantKey(),
                EXPIRED_ISSUED_AT,
                "expired-jwt"
        );
        assertThatThrownBy(() -> jwtVerifier.verify(expiredJwt, merchantJwtKey.merchantKey(), EXPIRED_ISSUED_AT + 181L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining(ApiResultEnum.AUTHORIZATION_JWT_EXPIRED.getMessage());
        log.info("异常分支-JWT过期被拒绝，预期错误码：{}", ApiResultEnum.AUTHORIZATION_JWT_EXPIRED.getCode());

        String tamperedData = MerchantOpenApiTestSupport.tamperCiphertextSegment(encryptedRequestData);
        assertThatThrownBy(() -> payloadCrypto.decrypt(
                tamperedData,
                payloadCrypto.readPrivateKey(platformPayloadKey.privateKeyPkcs8Base64())
        )).isInstanceOf(ApiException.class)
                .hasMessageContaining(ApiResultEnum.ENCRYPTED_DATA_INVALID.getMessage());
        log.info("异常分支-data密文被篡改，AES-GCM认证失败，预期错误码：{}", ApiResultEnum.ENCRYPTED_DATA_INVALID.getCode());
    }
}
