package com.scott.payment.openapi;

import com.scott.payment.component.security.key.OpenApiKeyMaterialFactory;
import com.scott.payment.component.security.key.OpenApiKeyMaterialFactory.MerchantJwtKey;
import com.scott.payment.component.security.key.OpenApiKeyMaterialFactory.MerchantOpenApiCredential;
import com.scott.payment.component.security.key.OpenApiKeyMaterialFactory.OpenApiMerchantOnboardingMaterial;
import com.scott.payment.component.security.key.OpenApiKeyMaterialFactory.RsaKeyMaterial;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantOpenApiKeyGenerationPureJavaTests
 * @date : 2026-06-02 15:10
 * @email : scott_x@163.com
 * @description : 纯 Java 商户 OpenAPI 密钥生成测试，只根据商户号生成商户和平台双方需要的全部安全材料
 * @status : create
 */
@Slf4j
class MerchantOpenApiKeyGenerationPureJavaTests {

    /**
     * 示例商户号。把这里替换为任意商户号，即可生成该商户的一套 OpenAPI 对接密钥材料。
     */
    private static final String MERCHANT_ID = "210001";

    /**
     * OpenAPI 密钥生成工具包入口，内部负责生成 merchantKey、平台请求体 RSA 密钥对、商户响应 RSA 密钥对。
     */
    private final OpenApiKeyMaterialFactory keyMaterialFactory = new OpenApiKeyMaterialFactory();

    /**
     * 根据商户号生成商户侧和平台侧全部密钥，并打印安全可读的中文日志。
     */
    @Test
    void shouldGenerateAllMerchantAndPlatformKeyMaterials() {
        GeneratedOpenApiKeyMaterial keyMaterial = generateOpenApiKeyMaterial(MERCHANT_ID);

        assertThat(keyMaterial.merchantId()).isEqualTo(MERCHANT_ID);
        assertThat(keyMaterial.merchantJwtKey().merchantKey()).isNotBlank();
        assertThat(keyMaterial.platformPayloadKey().publicKeyX509Base64()).isNotBlank();
        assertThat(keyMaterial.platformPayloadKey().privateKeyPkcs8Base64()).isNotBlank();
        assertThat(keyMaterial.merchantResponseKey().publicKeyX509Base64()).isNotBlank();
        assertThat(keyMaterial.merchantResponseKey().privateKeyPkcs8Base64()).isNotBlank();
        assertThat(keyMaterial.merchantCredential().merchantKey()).isEqualTo(keyMaterial.merchantJwtKey().merchantKey());
        assertThat(keyMaterial.merchantCredential().platformPublicKeyX509Base64())
                .isEqualTo(keyMaterial.platformPayloadKey().publicKeyX509Base64());
        assertThat(keyMaterial.merchantCredential().merchantResponsePrivateKeyPkcs8Base64())
                .isEqualTo(keyMaterial.merchantResponseKey().privateKeyPkcs8Base64());
    }

    /**
     * 生成指定商户号的 OpenAPI 全量安全材料。
     *
     * @param merchantId 支付框架颁发的商户号
     * @return 商户和平台双方需要的密钥材料
     */
    private GeneratedOpenApiKeyMaterial generateOpenApiKeyMaterial(String merchantId) {
        OpenApiMerchantOnboardingMaterial onboardingMaterial = keyMaterialFactory.generateDemoOnboardingMaterial(merchantId);
        MerchantOpenApiCredential merchantCredential = onboardingMaterial.merchantCredential();
        MerchantJwtKey merchantJwtKey = new MerchantJwtKey(
                merchantCredential.merchantId(),
                merchantCredential.merchantKey(),
                merchantCredential.jwtAlgorithm(),
                merchantCredential.jwtExpiresSeconds()
        );
        GeneratedOpenApiKeyMaterial keyMaterial = new GeneratedOpenApiKeyMaterial(
                merchantId,
                merchantJwtKey,
                onboardingMaterial.platformPayloadKey(),
                onboardingMaterial.merchantResponseKey(),
                merchantCredential
        );
        logGeneratedKeyMaterial(keyMaterial);
        return keyMaterial;
    }

    /**
     * 打印密钥材料说明。
     * <p>
     * 为满足支付系统安全规范，日志只打印指纹、长度、算法、用途和公钥 PEM。merchantKey、平台私钥和商户响应私钥
     * 不打印完整明文，真实交付应走密钥交付流程、KMS 或安全文件通道。
     *
     * @param keyMaterial 商户和平台双方需要的密钥材料
     */
    private void logGeneratedKeyMaterial(GeneratedOpenApiKeyMaterial keyMaterial) {
        log.info("========== OpenAPI商户密钥生成开始 ==========");
        log.info("商户号：{}", keyMaterial.merchantId());
        log.info("密钥关联关系：所有材料均按merchantId={}绑定，请求体和响应体不携带keyId。", keyMaterial.merchantId());

        log.info("【1. 商户秘钥 merchantKey】持有方：商户、平台；用途：商户生成JWT HS256，平台验签JWT；算法：{}；有效期上限：{}秒；长度：{}；指纹：{}",
                keyMaterial.merchantJwtKey().algorithm(),
                keyMaterial.merchantJwtKey().expiresSeconds(),
                keyMaterial.merchantJwtKey().merchantKey().length(),
                keyMaterialFactory.fingerprint(keyMaterial.merchantJwtKey().merchantKey()));

        log.info("【2. 商户加密使用的密钥】持有方：商户；用途：商户用平台公钥加密请求体AES会话密钥；算法：RSA-OAEP-256；RSA位数：{}；Base64长度：{}；指纹：{}",
                keyMaterial.platformPayloadKey().keySize(),
                keyMaterial.platformPayloadKey().publicKeyX509Base64().length(),
                keyMaterialFactory.fingerprint(keyMaterial.platformPayloadKey().publicKeyX509Base64()));
        log.info("【2. 商户加密使用的密钥-平台公钥PEM】\n{}", keyMaterial.platformPayloadKey().publicKeyPem());

        log.info("【3. 商户解密用的密钥】持有方：商户；用途：商户解密平台响应data；算法：RSA-OAEP-256；RSA位数：{}；Base64长度：{}；指纹：{}",
                keyMaterial.merchantResponseKey().keySize(),
                keyMaterial.merchantResponseKey().privateKeyPkcs8Base64().length(),
                keyMaterialFactory.fingerprint(keyMaterial.merchantResponseKey().privateKeyPkcs8Base64()));

        log.info("【4. 平台加密用的密钥】持有方：平台；用途：平台加密响应data；算法：RSA-OAEP-256；RSA位数：{}；Base64长度：{}；指纹：{}",
                keyMaterial.merchantResponseKey().keySize(),
                keyMaterial.merchantResponseKey().publicKeyX509Base64().length(),
                keyMaterialFactory.fingerprint(keyMaterial.merchantResponseKey().publicKeyX509Base64()));
        log.info("【4. 平台加密用的密钥-商户响应公钥PEM】\n{}", keyMaterial.merchantResponseKey().publicKeyPem());

        log.info("【5. 平台解密用的密钥】持有方：平台；用途：平台解密商户请求体AES会话密钥；算法：RSA-OAEP-256；RSA位数：{}；Base64长度：{}；指纹：{}",
                keyMaterial.platformPayloadKey().keySize(),
                keyMaterial.platformPayloadKey().privateKeyPkcs8Base64().length(),
                keyMaterialFactory.fingerprint(keyMaterial.platformPayloadKey().privateKeyPkcs8Base64()));

        log.info("商户最终需要保存：merchantKey指纹={}，平台公钥指纹={}，商户响应私钥指纹={}",
                keyMaterialFactory.fingerprint(keyMaterial.merchantCredential().merchantKey()),
                keyMaterialFactory.fingerprint(keyMaterial.merchantCredential().platformPublicKeyX509Base64()),
                keyMaterialFactory.fingerprint(keyMaterial.merchantCredential().merchantResponsePrivateKeyPkcs8Base64()));
        log.info("平台最终需要保存：merchantKey指纹={}，平台私钥指纹={}，商户响应公钥指纹={}",
                keyMaterialFactory.fingerprint(keyMaterial.merchantJwtKey().merchantKey()),
                keyMaterialFactory.fingerprint(keyMaterial.platformPayloadKey().privateKeyPkcs8Base64()),
                keyMaterialFactory.fingerprint(keyMaterial.merchantResponseKey().publicKeyX509Base64()));
        log.info("========== OpenAPI商户密钥生成结束 ==========");
    }

    /**
     * 商户 OpenAPI 全量密钥材料。
     *
     * @param merchantId          商户号
     * @param merchantJwtKey      merchantKey，商户用于签JWT，平台用于验签
     * @param platformPayloadKey  平台请求体RSA密钥对，公钥给商户加密请求，私钥平台解密请求
     * @param merchantResponseKey 商户响应RSA密钥对，公钥平台加密响应，私钥商户解密响应
     * @param merchantCredential  商户侧最终交付材料视图
     */
    private record GeneratedOpenApiKeyMaterial(String merchantId,
                                               MerchantJwtKey merchantJwtKey,
                                               RsaKeyMaterial platformPayloadKey,
                                               RsaKeyMaterial merchantResponseKey,
                                               MerchantOpenApiCredential merchantCredential) {
    }
}
