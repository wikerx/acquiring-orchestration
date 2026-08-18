package com.scott.payment.component.security.crypto;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.security.openapi.OpenApiPemUtils;

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

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : CheckoutCardEnvelopeCipher
 * @date : 2026-08-08 15:30
 * @email : scott_x@163.com
 * @description : 收银台卡数据混合加密组件，使用 RSA-OAEP-256 包裹 AES-256 密钥，并用 AES-GCM AAD 绑定会话、尝试和 nonce。
 * @status : create
 */
public final class CheckoutCardEnvelopeCipher {

    public static final String ALGORITHM = "RSA-OAEP-256+A256GCM";
    private static final String AES_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String RSA_TRANSFORMATION = "RSA/ECB/OAEPPadding";
    private static final int AES_KEY_BYTES = 32;
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final int MAX_CIPHERTEXT_BYTES = 4096;
    private final SecureRandom secureRandom = new SecureRandom();

    /** 使用浏览器协议相同的算法生成密文，供协议测试和受控服务端调用复用。 */
    public EncryptedEnvelope encrypt(String plainText, PublicKey publicKey, String aad) {
        byte[] contentKey = randomBytes(AES_KEY_BYTES);
        byte[] iv = randomBytes(GCM_IV_BYTES);
        try {
            Cipher aes = Cipher.getInstance(AES_TRANSFORMATION);
            aes.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(contentKey, "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, iv));
            aes.updateAAD(aadBytes(aad));
            byte[] ciphertext = aes.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            Cipher rsa = rsaCipher(Cipher.ENCRYPT_MODE, publicKey);
            byte[] encryptedKey = rsa.doFinal(contentKey);
            return new EncryptedEnvelope(base64Url(encryptedKey), base64Url(iv), base64Url(ciphertext));
        } catch (GeneralSecurityException exception) {
            throw invalidEnvelope();
        } finally {
            Arrays.fill(contentKey, (byte) 0);
        }
    }

    /** 解密并验证 GCM AAD；任一字段、密钥或绑定标识被篡改都统一拒绝。 */
    public String decrypt(String encryptedKeyValue,
                          String ivValue,
                          String ciphertextValue,
                          PrivateKey privateKey,
                          String aad) {
        byte[] encryptedKey = decode(encryptedKeyValue);
        byte[] iv = decode(ivValue);
        byte[] ciphertext = decode(ciphertextValue);
        if (iv.length != GCM_IV_BYTES || ciphertext.length == 0 || ciphertext.length > MAX_CIPHERTEXT_BYTES) {
            throw invalidEnvelope();
        }
        byte[] contentKey = null;
        try {
            contentKey = rsaCipher(Cipher.DECRYPT_MODE, privateKey).doFinal(encryptedKey);
            if (contentKey.length != AES_KEY_BYTES) {
                throw invalidEnvelope();
            }
            Cipher aes = Cipher.getInstance(AES_TRANSFORMATION);
            aes.init(Cipher.DECRYPT_MODE, new SecretKeySpec(contentKey, "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, iv));
            aes.updateAAD(aadBytes(aad));
            return new String(aes.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw invalidEnvelope();
        } finally {
            if (contentKey != null) {
                Arrays.fill(contentKey, (byte) 0);
            }
        }
    }

    /** 读取 X.509 DER Base64 或 PEM RSA 公钥。 */
    public PublicKey readPublicKey(String value) {
        try {
            byte[] encoded = Base64.getDecoder().decode(OpenApiPemUtils.normalizePem(value));
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(encoded));
        } catch (IllegalArgumentException | GeneralSecurityException exception) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(),
                    "checkout card encryption public key is invalid");
        }
    }

    /** 读取 PKCS#8 DER Base64 或 PEM RSA 私钥。 */
    public PrivateKey readPrivateKey(String value) {
        try {
            byte[] encoded = Base64.getDecoder().decode(OpenApiPemUtils.normalizePem(value));
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(encoded));
        } catch (IllegalArgumentException | GeneralSecurityException exception) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(),
                    "checkout card encryption private key is invalid");
        }
    }

    /** 仅为本地开发和单元测试生成临时 RSA 密钥对。 */
    public KeyPair generateRsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048, secureRandom);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException exception) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(),
                    "checkout card encryption key generation failed");
        }
    }

    private Cipher rsaCipher(int mode, java.security.Key key) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(RSA_TRANSFORMATION);
        cipher.init(mode, key, new OAEPParameterSpec(
                "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT));
        return cipher;
    }

    private byte[] randomBytes(int length) {
        byte[] value = new byte[length];
        secureRandom.nextBytes(value);
        return value;
    }

    private byte[] aadBytes(String aad) {
        if (aad == null || aad.isBlank() || aad.length() > 512) {
            throw invalidEnvelope();
        }
        return aad.getBytes(StandardCharsets.UTF_8);
    }

    private String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private byte[] decode(String value) {
        try {
            if (value == null || value.isBlank() || value.length() > 8192) {
                throw invalidEnvelope();
            }
            return Base64.getUrlDecoder().decode(value);
        } catch (IllegalArgumentException exception) {
            throw invalidEnvelope();
        }
    }

    private ServiceException invalidEnvelope() {
        return new ServiceException(ApiResultEnum.ENCRYPTED_DATA_INVALID.getCode(), "card data envelope is invalid");
    }

    /** Base64Url 编码的 RSA 包裹密钥、GCM IV 和包含认证标签的密文。 */
    public record EncryptedEnvelope(String encryptedKey, String iv, String ciphertext) {
    }
}
