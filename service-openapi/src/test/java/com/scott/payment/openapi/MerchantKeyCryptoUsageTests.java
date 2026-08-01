package com.scott.payment.openapi;

import com.scott.payment.component.core.util.SensitiveDataMaskUtils;
import com.scott.payment.component.db.auth.service.MerchantKeyMetadataCacheService;
import com.scott.payment.component.db.auth.service.MerchantRuntimeProfileCacheService;
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
     * 重初始化缓存一致性测试专用商户号，避免与本类其他加解密场景共享 JVM 敏感材料。
     */
    private static final String REPROVISION_MERCHANT_ID = "220002";

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

    /**
     * 非敏感密钥版本永久缓存，只用于清理当前测试商户的历史测试数据。
     */
    private final MerchantKeyMetadataCacheService keyMetadataCacheService;

    /**
     * 完整商户资料永久缓存，只用于清理当前测试商户的历史测试数据。
     */
    private final MerchantRuntimeProfileCacheService runtimeProfileCacheService;

    MerchantKeyCryptoUsageTests(MerchantSecurityService merchantSecurityService,
                                OpenApiPayloadCrypto payloadCrypto,
                                JdbcTemplate jdbcTemplate,
                                OpenApiKeyMaterialFactory keyMaterialFactory,
                                MerchantKeyMetadataCacheService keyMetadataCacheService,
                                MerchantRuntimeProfileCacheService runtimeProfileCacheService) {
        this.merchantSecurityService = merchantSecurityService;
        this.payloadCrypto = payloadCrypto;
        this.jdbcTemplate = jdbcTemplate;
        this.keyMaterialFactory = keyMaterialFactory;
        this.keyMetadataCacheService = keyMetadataCacheService;
        this.runtimeProfileCacheService = runtimeProfileCacheService;
    }

    /**
     * 每个用例执行前清理本类测试商户。
     */
    @BeforeEach
    void cleanMerchantData() {
        MerchantOpenApiTestSupport.cleanMerchantSecurityData(
                jdbcTemplate,
                List.of(MERCHANT_ID, REPROVISION_MERCHANT_ID)
        );
        keyMetadataCacheService.evictKeyMetadata(MERCHANT_ID);
        keyMetadataCacheService.evictKeyMetadata(REPROVISION_MERCHANT_ID);
        runtimeProfileCacheService.evictRuntimeProfile(MERCHANT_ID);
        runtimeProfileCacheService.evictRuntimeProfile(REPROVISION_MERCHANT_ID);
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

    /**
     * 验证同一商户重新初始化安全材料后，OpenAPI 立即读取新 JWT Secret 和新平台私钥。
     *
     * <p>该场景先主动读取第一次材料，使非敏感 Redis revision 与 JVM 敏感材料都命中，
     * 再执行第二次初始化，防止永久缓存继续引用旧数据库记录或本地缓存继续返回旧密钥。</p>
     */
    @Test
    void shouldUseLatestKeysAfterReprovisioningSameMerchant() {
        log.info("测试商户安全材料重初始化，关键输入：同一商户连续初始化两次并在首次初始化后命中缓存");
        MerchantSecurityMaterialDTO firstMaterial = merchantSecurityService.provisionMerchantSecurityMaterial(
                MerchantOpenApiTestSupport.buildMerchantSeed(REPROVISION_MERCHANT_ID)
        );
        String firstCachedMerchantKey = merchantSecurityService.getMerchantKey(REPROVISION_MERCHANT_ID);
        merchantSecurityService.getPlatformPrivateKey(REPROVISION_MERCHANT_ID);

        MerchantSecurityMaterialDTO secondMaterial = merchantSecurityService.provisionMerchantSecurityMaterial(
                MerchantOpenApiTestSupport.buildMerchantSeed(REPROVISION_MERCHANT_ID)
        );
        String latestMerchantKey = merchantSecurityService.getMerchantKey(REPROVISION_MERCHANT_ID);
        String plainText = MerchantOpenApiTestSupport.authorizationPlainText(
                REPROVISION_MERCHANT_ID,
                "202608010001"
        );
        String encryptedData = payloadCrypto.encrypt(
                plainText,
                payloadCrypto.readPublicKey(secondMaterial.getPlatformPublicKeyX509Base64())
        );

        assertThat(firstCachedMerchantKey).isEqualTo(firstMaterial.getMerchantKey());
        assertThat(secondMaterial.getMerchantKey()).isNotEqualTo(firstMaterial.getMerchantKey());
        assertThat(latestMerchantKey).isEqualTo(secondMaterial.getMerchantKey());
        assertThat(payloadCrypto.decrypt(
                encryptedData,
                merchantSecurityService.getPlatformPrivateKey(REPROVISION_MERCHANT_ID)
        )).isEqualTo(plainText);
        log.info("商户安全材料重初始化完成，结果：JWT Secret 与平台私钥均已切换到第二次初始化版本");
    }
}
