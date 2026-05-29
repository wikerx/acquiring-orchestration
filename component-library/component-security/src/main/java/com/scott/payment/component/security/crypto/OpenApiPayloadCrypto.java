package com.scott.payment.component.security.crypto;

import com.alibaba.fastjson2.TypeReference;
import com.scott.payment.component.core.enums.ApiCoResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.json.JsonUtils;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
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
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiPayloadCrypto
 * @date : 2026-05-29 21:45
 * @email : scott_x@163.com
 * @description : 支付框架 OpenAPI 报文混合加密工具，使用 RSA-OAEP-SHA256 包裹 AES-256-GCM 会话密钥
 * @status : create
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
     * 使用平台公钥加密开放 API 明文业务报文。
     * <p>
     * 商户侧调用该流程：随机生成 AES-256 key 和 IV，使用 AES-GCM 加密 JSON 明文，再用支付平台公钥通过
     * RSA-OAEP-SHA256 加密 AES key，最终形成 compact 格式字符串放入请求体 data 字段。
     *
     * @param plainText          业务 JSON 明文
     * @param recipientPublicKey 接收方 RSA 公钥，请求时为支付平台公钥，响应加密时可扩展为商户公钥
     * @param keyId              RSA 密钥编号，用于支持后续密钥轮换
     * @return compact 密文报文
     */
    public String encrypt(String plainText, PublicKey recipientPublicKey, String keyId) {
        Objects.requireNonNull(plainText, "plainText can not be null");
        Objects.requireNonNull(recipientPublicKey, "recipientPublicKey can not be null");
        byte[] contentKey = randomBytes(AES_KEY_BYTES);
        byte[] iv = randomBytes(GCM_IV_BYTES);
        String protectedHeader = encodeProtectedHeader(keyId);
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
     * 服务端先从受保护头读取 kid，再通过私钥解析器找到对应 RSA 私钥，解开 AES key 后使用 AES-GCM
     * 解密业务报文。如果密文、认证标签或 header 被篡改，AES-GCM 会直接解密失败。
     *
     * @param compactPayload     compact 密文报文
     * @param privateKeyResolver 按 kid 解析 RSA 私钥的函数
     * @return 业务 JSON 明文
     */
    public String decrypt(String compactPayload, Function<String, PrivateKey> privateKeyResolver) {
        if (!StringUtils.hasText(compactPayload)) {
            throw new ApiException(ApiCoResultEnum.CO_REQUIRED_PARAMETER_MISSING, "data");
        }
        Objects.requireNonNull(privateKeyResolver, "privateKeyResolver can not be null");
        String[] parts = compactPayload.split("\\.", -1);
        if (parts.length != COMPACT_PARTS) {
            throw new ApiException(ApiCoResultEnum.CO_REQUIRED_PARAMETER_ILLEGAL, "data");
        }
        Map<String, String> header = decodeProtectedHeader(parts[0]);
        validateProtectedHeader(header);
        PrivateKey privateKey = privateKeyResolver.apply(header.get("kid"));
        if (privateKey == null) {
            throw new ApiException(ApiCoResultEnum.CO_REQUIRED_PARAMETER_ILLEGAL, "data.kid");
        }
        byte[] contentKey = rsaOaep(Cipher.DECRYPT_MODE, privateKey, base64UrlDecode(parts[1]));
        byte[] iv = base64UrlDecode(parts[2]);
        byte[] cipherText = base64UrlDecode(parts[3]);
        byte[] tag = base64UrlDecode(parts[4]);
        byte[] cipherWithTag = concat(cipherText, tag);
        byte[] plainText = aesGcm(Cipher.DECRYPT_MODE, contentKey, iv, parts[0], cipherWithTag);
        return new String(plainText, StandardCharsets.UTF_8);
    }

    /**
     * 读取 X.509 DER Base64 公钥。
     *
     * @param publicKeyBase64 公钥 Base64 或 PEM 文本
     * @return RSA 公钥
     */
    public PublicKey readPublicKey(String publicKeyBase64) {
        try {
            byte[] encoded = Base64.getDecoder().decode(normalizePem(publicKeyBase64));
            return KeyFactory.getInstance(RSA_ALGORITHM).generatePublic(new X509EncodedKeySpec(encoded));
        } catch (Exception exception) {
            throw new ServiceException(ApiCoResultEnum.CO_INTERNAL_SERVER_ERROR.getCode(), "openapi public key can not be parsed");
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
            byte[] encoded = Base64.getDecoder().decode(normalizePem(privateKeyBase64));
            return KeyFactory.getInstance(RSA_ALGORITHM).generatePrivate(new PKCS8EncodedKeySpec(encoded));
        } catch (Exception exception) {
            throw new ServiceException(ApiCoResultEnum.CO_INTERNAL_SERVER_ERROR.getCode(), "openapi private key can not be parsed");
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
        } catch (Exception exception) {
            throw new ServiceException(ApiCoResultEnum.CO_INTERNAL_SERVER_ERROR.getCode(), "openapi rsa key pair can not be generated");
        }
    }

    /**
     * 构建 compact 密文第一段受保护头。
     * <p>
     * 受保护头会作为 AES-GCM AAD 参与完整性校验，所以 `typ`、`alg`、`enc`、`kid` 任意字段被篡改都会导致解密失败。
     *
     * @param keyId RSA 密钥编号
     * @return Base64Url 编码后的受保护头
     */
    private String encodeProtectedHeader(String keyId) {
        Map<String, String> header = new LinkedHashMap<>();
        header.put("typ", PAYLOAD_TYPE);
        header.put("alg", KEY_ENCRYPTION_ALGORITHM);
        header.put("enc", CONTENT_ENCRYPTION_ALGORITHM);
        header.put("kid", StringUtils.hasText(keyId) ? keyId : "default");
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
        } catch (Exception exception) {
            throw new ApiException(ApiCoResultEnum.CO_REQUIRED_PARAMETER_ILLEGAL, "data.header");
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
            throw new ApiException(ApiCoResultEnum.CO_REQUIRED_PARAMETER_ILLEGAL, "data.header");
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
        } catch (Exception exception) {
            throw new ApiException(ApiCoResultEnum.CO_REQUIRED_PARAMETER_ILLEGAL, "data");
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
        } catch (Exception exception) {
            throw new ApiException(ApiCoResultEnum.CO_REQUIRED_PARAMETER_ILLEGAL, "data.key");
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
            throw new ApiException(ApiCoResultEnum.CO_REQUIRED_PARAMETER_ILLEGAL, "data");
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

    /**
     * 归一化 PEM 或 Base64 密钥文本。
     *
     * @param value PEM 或 Base64 密钥文本
     * @return 去掉 PEM 头尾和空白后的 Base64 文本
     */
    private String normalizePem(String value) {
        if (!StringUtils.hasText(value)) {
            throw new ServiceException(ApiCoResultEnum.CO_INTERNAL_SERVER_ERROR.getCode(), "openapi key can not be blank");
        }
        return value
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
    }
}
