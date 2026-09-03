package com.scott.payment.data.security;

import com.scott.payment.data.config.DataCardVaultProperties;
import com.scott.payment.data.entity.DataCheckoutCardVaultDO;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DataCardVaultCryptoService
 * @date : 2026-08-08 18:00
 * @email : scott_x@163.com
 * @description : 卡资料字段级 AES-256-GCM 信封加密服务，随机 DEK 由独立 KEK 包裹且 PAN 使用带 pepper 的 HMAC 索引。
 * @status : create
 */
@Service
@ConditionalOnProperty(prefix = "data.card-vault", name = "enabled", havingValue = "true")
public class DataCardVaultCryptoService {

    /** AES-GCM IV 字节数。 */
    private static final int IV_BYTES = 12;
    /** AES-GCM 认证标签位数。 */
    private static final int TAG_BITS = 128;
    /** AES-GCM 认证标签字节数。 */
    private static final int TAG_BYTES = TAG_BITS / Byte.SIZE;
    /** AES-256 密钥字节数。 */
    private static final int KEY_BYTES = 32;
    /** 安全随机源。 */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** 卡资料库配置。 */
    private final DataCardVaultProperties properties;
    /** 仅保存在 service-data 配置或 KMS 中的 KEK。 */
    private final byte[] kek;
    /** PAN HMAC secret pepper。 */
    private final byte[] hmacKey;

    /**
     * 创建卡资料静态加密服务；启用时密钥长度不正确会阻止启动。
     *
     * @param properties 卡资料库配置
     */
    public DataCardVaultCryptoService(DataCardVaultProperties properties) {
        this.properties = properties;
        this.kek = decodeKek(properties.getKekBase64());
        if (!StringUtils.hasText(properties.getKekVersion())
                || !StringUtils.hasText(properties.getPanHmacKeyVersion())
                || properties.getPanHmacPepper() == null
                || properties.getPanHmacPepper().getBytes(StandardCharsets.UTF_8).length < KEY_BYTES) {
            throw new IllegalStateException("card vault HMAC and KEK versions or pepper are invalid");
        }
        this.hmacKey = properties.getPanHmacPepper().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 使用单条记录独立 DEK 加密 PAN、有效期和持卡人姓名，并用 KEK 包裹 DEK。
     *
     * @param merchantId 商户号
     * @param transactionId 平台交易号
     * @param plaintext 已校验且不含 CVV 的卡资料
     * @return 可直接持久化的密文列集合
     */
    public EncryptedCardData encrypt(String merchantId,
                                     String transactionId,
                                     DataCheckoutCardVaultTransferService.CardVaultPlaintext plaintext) {
        byte[] dek = randomBytes(KEY_BYTES);
        try {
            FieldEnvelope pan = encryptField(plaintext.cardNo(), dek, aad(merchantId, transactionId, "pan"));
            FieldEnvelope expiration = encryptField(
                    plaintext.expirationMonth() + "|" + plaintext.expirationYear(), dek,
                    aad(merchantId, transactionId, "expiration"));
            FieldEnvelope cardholder = StringUtils.hasText(plaintext.cardholderName())
                    ? encryptField(plaintext.cardholderName(), dek, aad(merchantId, transactionId, "cardholder"))
                    : null;
            FieldEnvelope wrappedDek = encryptBytes(dek, kek,
                    aad(merchantId, transactionId, "dek|" + properties.getKekVersion()));
            return new EncryptedCardData(
                    hmacSha256(plaintext.cardNo()),
                    pan,
                    expiration,
                    cardholder,
                    wrappedDek,
                    properties.getPanHmacKeyVersion(),
                    properties.getKekVersion());
        } finally {
            Arrays.fill(dek, (byte) 0);
        }
    }

    /**
     * 在受控 service-data 调用栈内解密卡资料，供后续渠道补偿或密钥轮换使用。
     *
     * @param record 卡资料密文记录
     * @return 不含 CVV 的解密结果
     */
    public DecryptedCardData decrypt(DataCheckoutCardVaultDO record) {
        requireCurrentKek(record);
        byte[] dek = decryptBytes(envelope(record.getWrappedDekIv(), record.getWrappedDekCiphertext(),
                        record.getWrappedDekAuthTag()), kek,
                aad(record.getMerchantId(), record.getTransactionId(), "dek|" + record.getKekVersion()));
        try {
            String pan = decryptField(envelope(record.getPanIv(), record.getPanCiphertext(), record.getPanAuthTag()),
                    dek, aad(record.getMerchantId(), record.getTransactionId(), "pan"));
            String expiration = decryptField(envelope(record.getExpirationIv(), record.getExpirationCiphertext(),
                            record.getExpirationAuthTag()), dek,
                    aad(record.getMerchantId(), record.getTransactionId(), "expiration"));
            String cardholder = record.getCardholderNameCiphertext() == null ? null
                    : decryptField(envelope(record.getCardholderNameIv(), record.getCardholderNameCiphertext(),
                                    record.getCardholderNameAuthTag()), dek,
                            aad(record.getMerchantId(), record.getTransactionId(), "cardholder"));
            String[] expiryParts = expiration.split("\\|", -1);
            if (expiryParts.length != 2) {
                throw new IllegalStateException("card vault expiration plaintext is invalid");
            }
            return new DecryptedCardData(pan, expiryParts[0], expiryParts[1], cardholder);
        } finally {
            Arrays.fill(dek, (byte) 0);
        }
    }

    /**
     * 处理字段安全计算，严格沿用当前算法、密钥边界和敏感日志约束。
     * @param plaintext 敏感认证或加密材料，只能在当前安全边界内使用，禁止明文日志和异常回显
     * @param key 敏感或可识别输入，调用方必须按脱敏、加密或最小必要原则传递
     * @param aad 敏感认证或加密材料，只能在当前安全边界内使用，禁止明文日志和异常回显
     * @return 当前方法生成的 {@code FieldEnvelope} 结果
     */
    private FieldEnvelope encryptField(String plaintext, byte[] key, String aad) {
        return encryptBytes(plaintext.getBytes(StandardCharsets.UTF_8), key, aad);
    }

    /**
     * 使用独立随机 IV 和业务身份 AAD 执行单字段 AES-256-GCM 加密。
     * <p>
     * GCM 输出被拆为密文和认证标签分别持久化；AAD 绑定商户、交易和字段用途，防止不同记录或字段之间替换密文。
     */
    private FieldEnvelope encryptBytes(byte[] plaintext, byte[] key, String aad) {
        byte[] iv = randomBytes(IV_BYTES);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(plaintext);
            byte[] ciphertext = Arrays.copyOf(encrypted, encrypted.length - TAG_BYTES);
            byte[] tag = Arrays.copyOfRange(encrypted, encrypted.length - TAG_BYTES, encrypted.length);
            Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
            return new FieldEnvelope(encoder.encodeToString(iv), encoder.encodeToString(ciphertext),
                    encoder.encodeToString(tag));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("card vault encryption failed", exception);
        }
    }

    /**
     * 处理字段安全计算，严格沿用当前算法、密钥边界和敏感日志约束。
     * @param envelope 敏感认证或加密材料，只能在当前安全边界内使用，禁止明文日志和异常回显
     * @param key 敏感或可识别输入，调用方必须按脱敏、加密或最小必要原则传递
     * @param aad 敏感认证或加密材料，只能在当前安全边界内使用，禁止明文日志和异常回显
     * @return 当前方法生成或规范化后的文本值
     */
    private String decryptField(FieldEnvelope envelope, byte[] key, String aad) {
        return new String(decryptBytes(envelope, key, aad), StandardCharsets.UTF_8);
    }

    /**
     * 按持久化信封重组 GCM 密文并校验 AAD 和认证标签后解密。
     * <p>
     * IV、标签长度或认证结果不合法时统一失败，禁止在未通过完整性校验的情况下返回任何卡资料明文。
     */
    private byte[] decryptBytes(FieldEnvelope envelope, byte[] key, String aad) {
        try {
            Base64.Decoder decoder = Base64.getUrlDecoder();
            byte[] iv = decoder.decode(envelope.iv());
            byte[] ciphertext = decoder.decode(envelope.ciphertext());
            byte[] tag = decoder.decode(envelope.authTag());
            if (iv.length != IV_BYTES || tag.length != TAG_BYTES) {
                throw new IllegalStateException("card vault envelope is invalid");
            }
            byte[] encrypted = new byte[ciphertext.length + tag.length];
            System.arraycopy(ciphertext, 0, encrypted, 0, ciphertext.length);
            System.arraycopy(tag, 0, encrypted, ciphertext.length, tag.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            return cipher.doFinal(encrypted);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("card vault decryption failed", exception);
        }
    }

    /**
     * 处理{@code hmacSha256}安全计算，严格沿用当前算法、密钥边界和敏感日志约束。
     * @param pan 敏感认证或加密材料，只能在当前安全边界内使用，禁止明文日志和异常回显
     * @return 当前方法生成或规范化后的文本值
     */
    private String hmacSha256(String pan) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacKey, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(pan.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("card vault PAN HMAC failed", exception);
        }
    }

    private void requireCurrentKek(DataCheckoutCardVaultDO record) {
        if (record == null || !properties.getKekVersion().equals(record.getKekVersion())) {
            throw new IllegalStateException("card vault KEK version is unavailable");
        }
    }

    private String aad(String merchantId, String transactionId, String purpose) {
        return "card-vault-at-rest-v1|" + merchantId + "|" + transactionId + "|" + purpose;
    }

    private FieldEnvelope envelope(String iv, String ciphertext, String authTag) {
        if (!StringUtils.hasText(iv) || !StringUtils.hasText(ciphertext) || !StringUtils.hasText(authTag)) {
            throw new IllegalStateException("card vault envelope is incomplete");
        }
        return new FieldEnvelope(iv, ciphertext, authTag);
    }

    private byte[] decodeKek(String value) {
        try {
            byte[] decoded = Base64.getDecoder().decode(value == null ? "" : value);
            if (decoded.length != KEY_BYTES) {
                throw new IllegalStateException("card vault KEK must be 32 bytes");
            }
            return decoded;
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("card vault KEK is invalid", exception);
        }
    }

    private byte[] randomBytes(int length) {
        byte[] result = new byte[length];
        SECURE_RANDOM.nextBytes(result);
        return result;
    }

    /** 单字段 AES-GCM 分离密文列。 */
    public record FieldEnvelope(String iv, String ciphertext, String authTag) {
    }

    /** 一条卡资料记录的全部持久化密文列。 */
    public record EncryptedCardData(String panHmac,
                                    FieldEnvelope pan,
                                    FieldEnvelope expiration,
                                    FieldEnvelope cardholderName,
                                    FieldEnvelope wrappedDek,
                                    String panHmacKeyVersion,
                                    String kekVersion) {
    }

    /** 受控解密结果，刻意不包含 CVV。 */
    public record DecryptedCardData(String cardNo,
                                    String expirationMonth,
                                    String expirationYear,
                                    String cardholderName) {
    }
}
