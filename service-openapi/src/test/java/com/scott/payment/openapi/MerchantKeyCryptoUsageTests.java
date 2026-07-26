package com.scott.payment.openapi;

import com.scott.payment.component.core.util.SensitiveDataMaskUtils;
import com.scott.payment.component.security.crypto.OpenApiPayloadCrypto;
import com.scott.payment.component.security.key.OpenApiKeyMaterialFactory;
import com.scott.payment.openapi.dto.security.MerchantSecurityMaterialDTO;
import com.scott.payment.openapi.service.MerchantSecurityService;
import com.scott.payment.openapi.support.MerchantOpenApiTestSupport;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantKeyCryptoUsageTests
 * @date : 2026-05-30 09:35
 * @email : scott_x@163.com
 * @description : 商户侧查询密钥、加密请求数据和解密响应数据测试
 * @status : create
 */
@Slf4j
@ActiveProfiles("mysql-test")
@SpringBootTest(classes = OpenApiApplication.class)
@Sql(scripts = "/sql/openapi-merchant-security-schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class MerchantKeyCryptoUsageTests {

    /**
     * 测试商户号。
     */
    private static final String MERCHANT_ID = "220001";

    /**
     * 商户密钥服务，用于模拟商户查询自己可用的对接材料。
     */
    private final MerchantSecurityService merchantSecurityService;

    /**
     * OpenAPI 报文混合加密工具。
     */
    private final OpenApiPayloadCrypto payloadCrypto;

    /**
     * JDBC 模板只用于测试数据清理。
     */
    private final JdbcTemplate jdbcTemplate;

    /**
     * 密钥材料工厂用于计算日志指纹，避免输出原文密钥。
     */
    private final OpenApiKeyMaterialFactory keyMaterialFactory;

    MerchantKeyCryptoUsageTests(MerchantSecurityService merchantSecurityService,
                                OpenApiPayloadCrypto payloadCrypto,
                                JdbcTemplate jdbcTemplate,
                                OpenApiKeyMaterialFactory keyMaterialFactory) {
        this.merchantSecurityService = merchantSecurityService;
        this.payloadCrypto = payloadCrypto;
        this.jdbcTemplate = jdbcTemplate;
        this.keyMaterialFactory = keyMaterialFactory;
    }

    /**
     * 每个用例执行前清理本类测试商户。
     */
    @BeforeEach
    void cleanMerchantData() {
        MerchantOpenApiTestSupport.cleanMerchantSecurityData(
                jdbcTemplate,
                List.of(MERCHANT_ID)
        );
    }

    /**
     * 验证商户根据商户号查询对接密钥后，可以使用平台公钥加密请求体 data。
     */
    @Test
    void shouldQueryMerchantKeysAndEncryptRequestData() {
        merchantSecurityService.provisionMerchantSecurityMaterial(
                MerchantOpenApiTestSupport.buildMerchantSeed(MERCHANT_ID)
        );
        MerchantSecurityMaterialDTO clientMaterial = merchantSecurityService.getMerchantClientSecurityMaterial(MERCHANT_ID);
        String plainText = MerchantOpenApiTestSupport.authorizationPlainText(MERCHANT_ID, "202605300001");

        String encryptedData = payloadCrypto.encrypt(
                plainText,
                payloadCrypto.readPublicKey(clientMaterial.getPlatformPublicKeyX509Base64())
        );
        String decryptedText = payloadCrypto.decrypt(encryptedData, merchantSecurityService.getPlatformPrivateKey(MERCHANT_ID));

        assertThat(MerchantOpenApiTestSupport.compactPartCount(encryptedData)).isEqualTo(5);
        assertThat(decryptedText).isEqualTo(plainText);
        log.info("商户查询密钥成功-商户号：{}，merchantKey指纹：{}，平台公钥指纹：{}",
                clientMaterial.getMerchantId(),
                keyMaterialFactory.fingerprint(clientMaterial.getMerchantKey()),
                keyMaterialFactory.fingerprint(clientMaterial.getPlatformPublicKeyX509Base64()));
        log.info("商户请求加密成功-data段数：{}，data摘要：{}，解密后脱敏明文：{}",
                MerchantOpenApiTestSupport.compactPartCount(encryptedData),
                MerchantOpenApiTestSupport.safeSecretSummary(encryptedData, keyMaterialFactory),
                SensitiveDataMaskUtils.maskJson(decryptedText));
    }

    /**
     * 验证响应加密增强模式下，平台使用商户响应公钥加密，商户使用自己的响应私钥解密。
     */
    @Test
    void shouldDecryptEncryptedResponseWithMerchantPrivateKey() {
        MerchantSecurityMaterialDTO onboardingMaterial = merchantSecurityService.provisionMerchantSecurityMaterial(
                MerchantOpenApiTestSupport.buildMerchantSeed(MERCHANT_ID)
        );
        String plainResponseData = """
                {
                  "orderInfo": {
                    "orderNo": "202605300002",
                    "amount": 12389.45,
                    "currency": "USD"
                  },
                  "transactionInfo": {
                    "code": "T200",
                    "message": "Success",
                    "transactionId": "202607160954270000001"
                  }
                }""";

        String encryptedResponseData = payloadCrypto.encrypt(
                plainResponseData,
                merchantSecurityService.getMerchantResponsePublicKey(MERCHANT_ID)
        );
        String decryptedResponseData = payloadCrypto.decrypt(
                encryptedResponseData,
                MerchantOpenApiTestSupport.resolveMerchantResponsePrivateKey(onboardingMaterial, payloadCrypto)
        );

        assertThat(decryptedResponseData).isEqualTo(plainResponseData);
        log.info("平台响应加密成功-商户号：{}，密文摘要：{}",
                MERCHANT_ID,
                MerchantOpenApiTestSupport.safeSecretSummary(encryptedResponseData, keyMaterialFactory));
        log.info("商户响应解密成功-使用商户响应私钥指纹：{}，响应明文：{}",
                keyMaterialFactory.fingerprint(onboardingMaterial.getMerchantResponsePrivateKeyPkcs8Base64()),
                decryptedResponseData);
    }
}
