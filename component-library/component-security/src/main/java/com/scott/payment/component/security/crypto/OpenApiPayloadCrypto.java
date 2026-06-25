package com.scott.payment.component.security.crypto;

import com.alibaba.fastjson2.TypeReference;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.security.openapi.OpenApiPemUtils;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * OpenAPI 报文混合加密组件，负责 RSA-OAEP-SHA256 包裹 AES-256-GCM 会话密钥。
 * <p>
 * 该组件只处理加解密和 JCA 密钥解析，PEM 展示格式统一委托 {@link OpenApiPemUtils}。
 */
public class OpenApiPayloadCrypto {

    /**
     * 支付框架密文报文类型，写入受保护头，避免把其他系统生成的密文误当作开放 API 业务报文处理。
     */
    private static final String PAYLOAD_TYPE = "PAYMENT-PAYLOAD";

    /**
     * 对称加密算法标识，A256GCM 表示 AES-256-GCM，兼容主流 Java、PHP、Go、C/OpenSSL 加密库。
     */
    private static final String CONTENT_ENCRYPTION_ALGORITHM = "A256GCM";

    /**
     * 密钥封装算法标识，RSA-OAEP-256 表示 RSA-OAEP 使用 SHA-256 摘要和 MGF1-SHA256。
     */
    private static final String KEY_ENCRYPTION_ALGORITHM = "RSA-OAEP-256";

    /**
     * AES-GCM 的 JCE transformation，GCM 同时提供机密性和完整性校验。
     */
    private static final String AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding";

    /**
     * RSA-OAEP 的 JCE transformation，具体 SHA-256 参数通过 OAEPParameterSpec 显式指定。
     */
    private static final String RSA_OAEP_TRANSFORMATION = "RSA/ECB/OAEPPadding";

    /**
     * RSA 密钥算法名称，用于读取 X.509 公钥和 PKCS#8 私钥。
     */
    private static final String RSA_ALGORITHM = "RSA";

    /**
     * AES 会话密钥长度，32 字节即 256 bit。
     */
    private static final int AES_KEY_BYTES = 32;

    /**
     * AES-GCM 推荐使用 96 bit 随机 IV，即 12 字节。
     */
    private static final int GCM_IV_BYTES = 12;

    /**
     * AES-GCM 认证标签长度，128 bit 可以降低伪造概率。
     */
    private static final int GCM_TAG_BITS = 128;

    /**
     * AES-GCM 认证标签字节长度，用于把 JCE 输出拆分为 ciphertext 和 tag 两段。
     */
    private static final int GCM_TAG_BYTES = GCM_TAG_BITS / Byte.SIZE;

    /**
     * OpenAPI 加密片段数量：protectedHeader.encryptedKey.iv.cipherText.tag。
     */
    private static final int COMPACT_PARTS = 5;

    /**
     * 用于 AES 密钥和 IV 的安全随机数生成器。
     */
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 使用接收方公钥加密开放 API 明文业务报文。
     * <p>
     * 商户侧调用该流程：随机生成 AES-256 key 和 IV，使用 AES-GCM 加密 JSON 明文，再用支付平台公钥通过
     * RSA-OAEP-SHA256 加密 AES key，最终形成 compact 格式字符串放入请求体 data 字段。
     *
     * @param plainText          业务 JSON 明文
     * @param recipientPublicKey 接收方 RSA 公钥，请求时为商户独立平台公钥，响应时为商户响应公钥
     * @return compact 密文报文
     */
    public String encrypt(String plainText, PublicKey recipientPublicKey) {
        Objects.requireNonNull(plainText, "plainText can not be null");
        Objects.requireNonNull(recipientPublicKey, "recipientPublicKey can not be null");
        byte[] contentKey = randomBytes(AES_KEY_BYTES);
        byte[] iv = randomBytes(GCM_IV_BYTES);
        String protectedHeader = encodeProtectedHeader();
        byte[] cipherWithTag = aesGcm(Cipher.ENCRYPT_MODE, contentKey, iv, protectedHeader, plainText.getBytes(StandardCharsets.UTF_8));
        byte[] cipherText = Arrays.copyOf(cipherWithTag, cipherWithTag.length - GCM_TAG_BYTES);
        byte[] tag = Arrays.copyOfRange(cipherWithTag, cipherWithTag.length - GCM_TAG_BYTES, cipherWithTag.length);
        byte[] encryptedKey = rsaOaep(Cipher.ENCRYPT_MODE, recipientPublicKey, contentKey);
        return String.join(".",
                protectedHeader,
                base64Url(encryptedKey),
                base64Url(iv),
                base64Url(cipherText),
                base64Url(tag));
    }

    /**
     * 解密开放 API compact 密文报文。
     * <p>
     * 服务端必须先从 JWT 中取得 merchantId，再按 merchantId 查询当前商户独立的平台私钥。受保护头只描述
     * 报文类型和算法，不再携带密钥编号，避免商户或攻击者通过请求体自行选择密钥。
     *
     * @param compactPayload compact 密文报文
     * @param privateKey     按 merchantId 查询到的平台 RSA 私钥或商户响应 RSA 私钥
     * @return 业务 JSON 明文
     */
    public String decrypt(String compactPayload, PrivateKey privateKey) {
        if (!StringUtils.hasText(compactPayload)) {
            throw new ApiException(ApiResultEnum.PARAM_MISSING, "data");
        }
        Objects.requireNonNull(privateKey, "privateKey can not be null");
        String[] parts = compactPayload.split("\\.", -1);
        if (parts.length != COMPACT_PARTS) {
            throw new ApiException(ApiResultEnum.ENCRYPTED_DATA_INVALID, "data");
        }
        Map<String, String> header = decodeProtectedHeader(parts[0]);
        validateProtectedHeader(header);
        byte[] contentKey = rsaOaep(Cipher.DECRYPT_MODE, privateKey, base64UrlDecode(parts[1]));
        byte[] iv = base64UrlDecode(parts[2]);
        byte[] cipherText = base64UrlDecode(parts[3]);
        byte[] tag = base64UrlDecode(parts[4]);
        byte[] cipherWithTag = concat(cipherText, tag);
        byte[] plainText = aesGcm(Cipher.DECRYPT_MODE, contentKey, iv, parts[0], cipherWithTag);
        return new String(plainText, StandardCharsets.UTF_8);
    }

    /**
     * 兼容旧调用形态的解密入口。
     * <p>
     * 新方案中 compact header 不携带密钥编号，调用方应先按 merchantId 查询私钥，再调用
     * {@link #decrypt(String, PrivateKey)}。该方法只在迁移期保留，私钥解析器接收到的参数固定为 null。
     *
     * @param compactPayload     compact 密文报文
     * @param privateKeyResolver 历史按密钥编号查私钥的函数，当前只用于返回调用方已经选定的私钥
     * @return 业务 JSON 明文
     */
    @Deprecated
    public String decrypt(String compactPayload, Function<String, PrivateKey> privateKeyResolver) {
        Objects.requireNonNull(privateKeyResolver, "privateKeyResolver can not be null");
        return decrypt(compactPayload, privateKeyResolver.apply(null));
    }

    /**
     * 读取 X.509 DER Base64 公钥。
     *
     * @param publicKeyBase64 公钥 Base64 或 PEM 文本
     * @return RSA 公钥
     */
    public PublicKey readPublicKey(String publicKeyBase64) {
        try {
            byte[] encoded = Base64.getDecoder().decode(OpenApiPemUtils.normalizePem(publicKeyBase64));
            return KeyFactory.getInstance(RSA_ALGORITHM).generatePublic(new X509EncodedKeySpec(encoded));
        } catch (IllegalArgumentException | GeneralSecurityException exception) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(), "openapi public key can not be parsed");
        }
    }

    /**
     * 读取 PKCS#8 DER Base64 私钥。
     *
     * @param privateKeyBase64 私钥 Base64 或 PEM 文本
     * @return RSA 私钥
     */
    public PrivateKey readPrivateKey(String privateKeyBase64) {
        try {
            byte[] encoded = Base64.getDecoder().decode(OpenApiPemUtils.normalizePem(privateKeyBase64));
            return KeyFactory.getInstance(RSA_ALGORITHM).generatePrivate(new PKCS8EncodedKeySpec(encoded));
        } catch (IllegalArgumentException | GeneralSecurityException exception) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(), "openapi private key can not be parsed");
        }
    }

    /**
     * 生成 RSA 密钥对，主要用于本地联调和单元测试。
     * <p>
     * 生产环境密钥应由密钥管理系统或离线安全流程生成，并通过 Nacos/数据库/KMS 注入。
     *
     * @param keySize RSA 密钥长度，当前建议不低于 2048 bit
     * @return RSA 密钥对
     */
    public KeyPair generateRsaKeyPair(int keySize) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(RSA_ALGORITHM);
            generator.initialize(keySize, secureRandom);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException exception) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(), "openapi rsa key pair can not be generated");
        }
    }

    /**
     * 将 X.509 DER Base64 公钥转换为 PEM 文本。
     * <p>
     * 商户侧 Java、PHP、Go、C/OpenSSL 等不同技术栈通常更容易直接读取 PEM 格式，
     * 因此统一在安全组件中提供格式转换，避免测试类或业务代码散落重复工具逻辑。
     *
     * @param publicKeyBase64 X.509 DER Base64 公钥
     * @return X.509 PEM 公钥
     */
    public String toPublicKeyPem(String publicKeyBase64) {
        return OpenApiPemUtils.toPublicKeyPem(publicKeyBase64);
    }

    /**
     * 将 PKCS#8 DER Base64 私钥转换为 PEM 文本。
     *
     * @param privateKeyBase64 PKCS#8 DER Base64 私钥
     * @return PKCS#8 PEM 私钥
     */
    public String toPrivateKeyPem(String privateKeyBase64) {
        return OpenApiPemUtils.toPrivateKeyPem(privateKeyBase64);
    }

    /**
     * 构建 compact 密文第一段受保护头。
     * <p>
     * 受保护头会作为 AES-GCM AAD 参与完整性校验，所以 `typ`、`alg`、`enc` 任意字段被篡改都会导致解密失败。
     *
     * @return Base64Url 编码后的受保护头
     */
    private String encodeProtectedHeader() {
        Map<String, String> header = new LinkedHashMap<>();
        header.put("typ", PAYLOAD_TYPE);
        header.put("alg", KEY_ENCRYPTION_ALGORITHM);
        header.put("enc", CONTENT_ENCRYPTION_ALGORITHM);
        return base64Url(JsonUtils.toJsonString(header).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 解码 compact 报文第一段受保护头。
     *
     * @param protectedHeader Base64Url 编码后的受保护头
     * @return 受保护头键值对
     */
    private Map<String, String> decodeProtectedHeader(String protectedHeader) {
        try {
            String headerJson = new String(base64UrlDecode(protectedHeader), StandardCharsets.UTF_8);
            return JsonUtils.parseObject(headerJson, new TypeReference<Map<String, String>>() {
            });
        } catch (RuntimeException exception) {
            throw new ApiException(ApiResultEnum.ENCRYPTED_DATA_INVALID, "data.header");
        }
    }

    /**
     * 校验受保护头，防止错误算法、错误报文类型或降级算法进入解密流程。
     *
     * @param header 受保护头键值对
     */
    private void validateProtectedHeader(Map<String, String> header) {
        if (header == null
                || !PAYLOAD_TYPE.equals(header.get("typ"))
                || !KEY_ENCRYPTION_ALGORITHM.equals(header.get("alg"))
                || !CONTENT_ENCRYPTION_ALGORITHM.equals(header.get("enc"))) {
            throw new ApiException(ApiResultEnum.ENCRYPTED_DATA_INVALID, "data.header");
        }
    }

    /**
     * 执行 AES-GCM 加密或解密。
     *
     * @param mode            Cipher 加解密模式
     * @param contentKey      AES 会话密钥
     * @param iv              AES-GCM 随机 IV
     * @param protectedHeader compact 报文第一段，作为 AAD 参与完整性校验
     * @param input           待加密或待解密数据
     * @return 加密或解密结果
     */
    private byte[] aesGcm(int mode, byte[] contentKey, byte[] iv, String protectedHeader, byte[] input) {
        try {
            Cipher cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION);
            SecretKeySpec keySpec = new SecretKeySpec(contentKey, "AES");
            cipher.init(mode, keySpec, new GCMParameterSpec(GCM_TAG_BITS, iv));
            cipher.updateAAD(protectedHeader.getBytes(StandardCharsets.US_ASCII));
            return cipher.doFinal(input);
        } catch (GeneralSecurityException exception) {
            throw new ApiException(ApiResultEnum.ENCRYPTED_DATA_INVALID, "data");
        }
    }

    /**
     * 执行 RSA-OAEP-SHA256 密钥包裹或解包。
     *
     * @param mode  Cipher 加解密模式
     * @param key   RSA 公钥或私钥
     * @param input 待加密或待解密数据
     * @return RSA 运算结果
     */
    private byte[] rsaOaep(int mode, java.security.Key key, byte[] input) {
        try {
            Cipher cipher = Cipher.getInstance(RSA_OAEP_TRANSFORMATION);
            OAEPParameterSpec oaepParameterSpec = new OAEPParameterSpec(
                    "SHA-256",
                    "MGF1",
                    MGF1ParameterSpec.SHA256,
                    PSource.PSpecified.DEFAULT);
            cipher.init(mode, key, oaepParameterSpec);
            return cipher.doFinal(input);
        } catch (GeneralSecurityException exception) {
            throw new ApiException(ApiResultEnum.ENCRYPTED_DATA_INVALID, "data.key");
        }
    }

    /**
     * 生成安全随机字节。
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
     * 执行无填充 Base64Url 编码。
     *
     * @param value 原始字节
     * @return Base64Url 文本
     */
    private String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    /**
     * 执行 Base64Url 解码。
     *
     * @param value Base64Url 文本
     * @return 原始字节
     */
    private byte[] base64UrlDecode(String value) {
        try {
            return Base64.getUrlDecoder().decode(value);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ApiResultEnum.ENCRYPTED_DATA_INVALID, "data");
        }
    }

    /**
     * 拼接两个字节数组。
     *
     * @param left  左侧字节数组
     * @param right 右侧字节数组
     * @return 拼接后的字节数组
     */
    private byte[] concat(byte[] left, byte[] right) {
        byte[] result = Arrays.copyOf(left, left.length + right.length);
        System.arraycopy(right, 0, result, left.length, right.length);
        return result;
    }

}
