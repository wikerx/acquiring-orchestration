package com.scott.payment.component.security.key;

import com.scott.payment.component.core.enums.ApiCoResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.security.crypto.OpenApiPayloadCrypto;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiKeyMaterialFactory
 * @date : 2026-05-29 22:40
 * @email : scott_x@163.com
 * @description : 支付框架 OpenAPI 商户接入密钥材料生成入口
 * @status : create
 */
public class OpenApiKeyMaterialFactory {

    /**
     * 默认商户 JWT HS256 密钥长度，32 字节即 256 bit。
     */
    private static final int DEFAULT_MERCHANT_KEY_BYTES = 32;

    /**
     * 默认 RSA 密钥长度，当前按 RSA2048 起步，后续可平滑升级 RSA3072。
     */
    private static final int DEFAULT_RSA_KEY_SIZE = 2048;

    /**
     * PEM 文本每行字符数，使用 64 字符便于 OpenSSL、PHP、Go 等工具读取。
     */
    private static final int PEM_LINE_LENGTH = 64;

    /**
     * OpenAPI JWT 固定算法名称。
     */
    private static final String JWT_ALGORITHM = "HS256";

    /**
     * OpenAPI JWT 最大有效期，单位秒。
     */
    private static final long JWT_EXPIRES_SECONDS = 180L;

    /**
     * X.509 公钥 PEM 开始标识。
     */
    private static final String PUBLIC_KEY_BEGIN = "-----BEGIN PUBLIC KEY-----";

    /**
     * X.509 公钥 PEM 结束标识。
     */
    private static final String PUBLIC_KEY_END = "-----END PUBLIC KEY-----";

    /**
     * PKCS#8 私钥 PEM 开始标识。
     */
    private static final String PRIVATE_KEY_BEGIN = "-----BEGIN PRIVATE KEY-----";

    /**
     * PKCS#8 私钥 PEM 结束标识。
     */
    private static final String PRIVATE_KEY_END = "-----END PRIVATE KEY-----";

    /**
     * 支付框架 OpenAPI 报文混合加密工具，用于生成 RSA 密钥对。
     */
    private final OpenApiPayloadCrypto payloadCrypto;

    /**
     * 安全随机数生成器，用于生成 merchantKey。
     */
    private final SecureRandom secureRandom;

    /**
     * 创建默认密钥材料生成器。
     * <p>
     * 默认使用 JDK `SecureRandom` 和当前 OpenAPI 报文加密组件，适合单元测试、沙箱和本地初始化脚本复用。
     */
    public OpenApiKeyMaterialFactory() {
        this(new OpenApiPayloadCrypto(), new SecureRandom());
    }

    /**
     * 创建可注入依赖的密钥材料生成器。
     *
     * @param payloadCrypto OpenAPI 报文加密组件，用于生成 RSA 密钥对
     * @param secureRandom  安全随机数生成器，用于生成 merchantKey
     */
    public OpenApiKeyMaterialFactory(OpenApiPayloadCrypto payloadCrypto, SecureRandom secureRandom) {
        this.payloadCrypto = Objects.requireNonNull(payloadCrypto, "payloadCrypto can not be null");
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom can not be null");
    }

    /**
     * 为商户生成 JWT HS256 签名密钥。
     * <p>
     * 该密钥由支付平台生成并安全交付给商户；商户用它签发 JWT，平台用它验签。
     *
     * @param merchantId 商户号
     * @return 商户 JWT 签名密钥材料
     */
    public MerchantJwtKey generateMerchantJwtKey(String merchantId) {
        byte[] secret = randomBytes(DEFAULT_MERCHANT_KEY_BYTES);
        return new MerchantJwtKey(
                merchantId,
                Base64.getUrlEncoder().withoutPadding().encodeToString(secret),
                JWT_ALGORITHM,
                JWT_EXPIRES_SECONDS
        );
    }

    /**
     * 生成商户独立的平台请求体解密 RSA 密钥对。
     * <p>
     * 平台私钥只允许平台服务端保存；平台公钥下发给对应商户用于加密请求体 data。密钥查询统一通过
     * merchantId 完成，不再依赖请求体 header 中的密钥编号。
     *
     * @param merchantId 支付框架颁发的商户号
     * @return 平台请求体解密密钥材料
     */
    public RsaKeyMaterial generatePlatformPayloadRsaKey(String merchantId) {
        return generateRsaKeyMaterial(merchantId, "payment-platform-payload", DEFAULT_RSA_KEY_SIZE);
    }

    /**
     * 生成商户独立的响应解密 RSA 密钥对。
     * <p>
     * 平台只保存响应公钥，用于加密响应 data；响应私钥安全交付给商户，由商户保存并用于解密平台响应。
     *
     * @param merchantId 支付框架颁发的商户号
     * @return 商户响应加密密钥材料
     */
    public RsaKeyMaterial generateMerchantResponseRsaKey(String merchantId) {
        return generateRsaKeyMaterial(merchantId, "merchant-response-payload", DEFAULT_RSA_KEY_SIZE);
    }

    /**
     * 生成商户侧真正需要保存的对接材料。
     *
     * @param merchantId           商户号
     * @param platformPayloadKey   平台请求体 RSA 密钥材料
     * @param merchantResponseKey  商户响应 RSA 密钥材料
     * @return 商户对接材料
     */
    public MerchantOpenApiCredential generateMerchantCredential(String merchantId,
                                                                RsaKeyMaterial platformPayloadKey,
                                                                RsaKeyMaterial merchantResponseKey) {
        MerchantJwtKey merchantJwtKey = generateMerchantJwtKey(merchantId);
        return new MerchantOpenApiCredential(
                merchantJwtKey.merchantId(),
                merchantJwtKey.merchantKey(),
                merchantJwtKey.algorithm(),
                merchantJwtKey.expiresSeconds(),
                platformPayloadKey.publicKeyX509Base64(),
                platformPayloadKey.publicKeyPem(),
                merchantResponseKey.publicKeyX509Base64(),
                merchantResponseKey.privateKeyPkcs8Base64(),
                merchantResponseKey.privateKeyPem()
        );
    }

    /**
     * 生成一套本地联调用商户接入材料。
     * <p>
     * 该方法会同时生成商户可见材料和平台服务端内部材料，方便单元测试和沙箱中观察完整流程。
     * 商户侧只需要保存返回对象中的 `merchantCredential`，不要接触平台 RSA 私钥。
     *
     * @param merchantId 商户号
     * @return 本地联调用商户接入材料
     */
    public OpenApiMerchantOnboardingMaterial generateDemoOnboardingMaterial(String merchantId) {
        RsaKeyMaterial platformPayloadKey = generatePlatformPayloadRsaKey(merchantId);
        RsaKeyMaterial merchantResponseKey = generateMerchantResponseRsaKey(merchantId);
        return new OpenApiMerchantOnboardingMaterial(
                generateMerchantCredential(merchantId, platformPayloadKey, merchantResponseKey),
                platformPayloadKey,
                merchantResponseKey
        );
    }

    /**
     * 计算密钥或密文的安全指纹，方便日志和排查时确认材料是否一致。
     * <p>
     * 指纹只取 SHA-256 的前 16 个十六进制字符，不替代真实验签，也不能反推出原始密钥。
     *
     * @param value 需要计算指纹的文本
     * @return 短指纹
     */
    public String fingerprint(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 16);
        } catch (Exception exception) {
            throw new ServiceException(ApiCoResultEnum.CO_INTERNAL_SERVER_ERROR.getCode(), "fingerprint can not be calculated");
        }
    }

    /**
     * 生成 RSA 密钥材料。
     *
     * @param merchantId 商户号，作为当前密钥材料的归属标识
     * @param owner   密钥归属说明
     * @param keySize RSA 密钥长度
     * @return RSA 密钥材料
     */
    private RsaKeyMaterial generateRsaKeyMaterial(String merchantId, String owner, int keySize) {
        KeyPair keyPair = payloadCrypto.generateRsaKeyPair(keySize);
        String publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        String privateKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
        return new RsaKeyMaterial(
                merchantId,
                owner,
                keySize,
                publicKeyBase64,
                privateKeyBase64,
                toPem(publicKeyBase64, PUBLIC_KEY_BEGIN, PUBLIC_KEY_END),
                toPem(privateKeyBase64, PRIVATE_KEY_BEGIN, PRIVATE_KEY_END)
        );
    }

    /**
     * 生成指定长度的安全随机字节。
     *
     * @param length 字节长度
     * @return 随机字节
     */
    private byte[] randomBytes(int length) {
        byte[] value = new byte[length];
        secureRandom.nextBytes(value);
        return value;
    }

    /**
     * 将 Base64 DER 文本转换为 PEM 文本。
     *
     * @param base64 DER Base64 文本
     * @param begin  PEM 开始标识
     * @param end    PEM 结束标识
     * @return PEM 文本
     */
    private String toPem(String base64, String begin, String end) {
        StringBuilder builder = new StringBuilder(begin).append('\n');
        for (int index = 0; index < base64.length(); index += PEM_LINE_LENGTH) {
            builder.append(base64, index, Math.min(index + PEM_LINE_LENGTH, base64.length())).append('\n');
        }
        return builder.append(end).toString();
    }

    /**
     * 商户 JWT 签名密钥材料。
     *
     * @param merchantId     商户号
     * @param merchantKey    商户 JWT HS256 签名密钥
     * @param algorithm      JWT 签名算法
     * @param expiresSeconds JWT 最大有效期，单位秒
     */
    public record MerchantJwtKey(String merchantId, String merchantKey, String algorithm, long expiresSeconds) {
    }

    /**
     * RSA 密钥材料。
     *
     * @param merchantId             商户号，标识该 RSA 密钥材料属于哪个商户
     * @param owner                  密钥归属说明
     * @param keySize                RSA 密钥长度
     * @param publicKeyX509Base64    X.509 DER Base64 公钥
     * @param privateKeyPkcs8Base64  PKCS#8 DER Base64 私钥
     * @param publicKeyPem           X.509 PEM 公钥
     * @param privateKeyPem          PKCS#8 PEM 私钥
     */
    public record RsaKeyMaterial(String merchantId,
                                 String owner,
                                 int keySize,
                                 String publicKeyX509Base64,
                                 String privateKeyPkcs8Base64,
                                 String publicKeyPem,
                                 String privateKeyPem) {
    }

    /**
     * 商户侧 OpenAPI 对接材料。
     *
     * @param merchantId                商户号
     * @param merchantKey               商户 JWT HS256 签名密钥，商户服务端需要安全保存
     * @param jwtAlgorithm              JWT 签名算法
     * @param jwtExpiresSeconds         JWT 最大有效期，单位秒
     * @param platformPublicKeyX509Base64       X.509 DER Base64 平台公钥
     * @param platformPublicKeyPem              X.509 PEM 平台公钥
     * @param merchantResponsePublicKeyX509Base64 商户响应 X.509 DER Base64 公钥，平台保存用于响应加密
     * @param merchantResponsePrivateKeyPkcs8Base64 商户响应 PKCS#8 DER Base64 私钥，商户保存用于响应解密
     * @param merchantResponsePrivateKeyPem     商户响应 PKCS#8 PEM 私钥
     */
    public record MerchantOpenApiCredential(String merchantId,
                                            String merchantKey,
                                            String jwtAlgorithm,
                                            long jwtExpiresSeconds,
                                            String platformPublicKeyX509Base64,
                                            String platformPublicKeyPem,
                                            String merchantResponsePublicKeyX509Base64,
                                            String merchantResponsePrivateKeyPkcs8Base64,
                                            String merchantResponsePrivateKeyPem) {
    }

    /**
     * 本地联调用商户接入材料，拆分商户可见信息和平台服务端内部信息。
     *
     * @param merchantCredential 商户实际需要保存和使用的对接材料
     * @param platformPayloadKey 平台服务端内部 RSA 密钥材料，包含私钥，只能由平台保存
     * @param merchantResponseKey 商户响应 RSA 密钥材料；平台只保存公钥，私钥交付给商户
     */
    public record OpenApiMerchantOnboardingMaterial(MerchantOpenApiCredential merchantCredential,
                                                    RsaKeyMaterial platformPayloadKey,
                                                    RsaKeyMaterial merchantResponseKey) {
    }
}
