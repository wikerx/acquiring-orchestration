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
 * 为卡资料提供字段级 AES-256-GCM 信封加密和受控解密。
 *
 * <p>每条记录使用独立随机 DEK，DEK 由版本化 KEK 包裹；AAD 绑定商户、交易和字段用途，
 * 防止密文跨记录或跨字段替换。PAN 查询索引使用版本化 HMAC 密钥，不保存可离线枚举的裸哈希。</p>
 */
@Service
@ConditionalOnProperty(prefix = "data.card-vault", name = "enabled", havingValue = "true")
public class DataCardVaultCryptoService {

    /**
     * AES-GCM IV 字节数。
     */
    private static final int IV_BYTES = 12;
    /**
     * AES-GCM 认证标签位数。
     */
    private static final int TAG_BITS = 128;
    /**
     * AES-GCM 认证标签字节数。
     */
    private static final int TAG_BYTES = TAG_BITS / Byte.SIZE;
    /**
     * AES-256 密钥字节数。
     */
    private static final int KEY_BYTES = 32;
    /**
     * 安全随机源。
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * 卡资料库配置。
     */
    private final DataCardVaultProperties properties;
    /**
     * 仅保存在 service-data 配置或 KMS 中的 KEK。
     */
    private final byte[] kek;
    /**
     * PAN HMAC secret pepper。
     */
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
     * 将 UTF-8 文本按指定 AAD 加密为分离的 GCM 密文列。
     *
     * @param plaintext 仅在当前调用栈存在的敏感明文
     * @param key 当前记录 DEK 或 KEK
     * @param aad 绑定记录身份和字段用途的附加认证数据
     * @return 包含随机 IV、密文和认证标签的字段信封
     */
    private FieldEnvelope encryptField(String plaintext, byte[] key, String aad) {
        return encryptBytes(plaintext.getBytes(StandardCharsets.UTF_8), key, aad);
    }

    /**
     * 使用独立随机 IV 和业务身份 AAD 执行单字段 AES-256-GCM 加密。
     *
     * <p>GCM 输出拆分为密文和认证标签分别持久化，任何密文、标签或 AAD 变化都会导致解密失败。</p>
     *
     * @param plaintext 待加密字节
     * @param key 32 字节 AES 密钥
     * @param aad 记录与字段绑定信息
     * @return 可持久化字段信封
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
     * 解密 UTF-8 字段并在认证标签校验失败时拒绝返回明文。
     *
     * @param envelope 持久化字段信封
     * @param key 当前记录 DEK 或 KEK
     * @param aad 必须与加密时完全一致的记录和字段绑定信息
     * @return 解密后的 UTF-8 文本
     */
    private String decryptField(FieldEnvelope envelope, byte[] key, String aad) {
        return new String(decryptBytes(envelope, key, aad), StandardCharsets.UTF_8);
    }

    /**
     * 按持久化信封重组 GCM 密文并校验 AAD 和认证标签后解密。
     *
     * <p>IV、标签长度或认证结果不合法时统一失败，禁止在未通过完整性校验时返回任何卡资料明文。</p>
     *
     * @param envelope 持久化字段信封
     * @param key 32 字节 AES 密钥
     * @param aad 记录与字段绑定信息
     * @return 通过完整性校验的明文字节
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
     * 使用版本化服务端密钥计算 PAN 的 HMAC-SHA-256 查询标识。
     *
     * @param pan 已校验卡号，仅在当前调用栈使用
     * @return 小写十六进制 HMAC，不可替代加密后的 PAN
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

    /**
     * 确认记录使用当前实例可解开的 KEK 版本，未知版本失败关闭。
     *
     * @param record 卡资料密文记录
     */
    private void requireCurrentKek(DataCheckoutCardVaultDO record) {
        if (record == null || !properties.getKekVersion().equals(record.getKekVersion())) {
            throw new IllegalStateException("card vault KEK version is unavailable");
        }
    }

    /**
     * 构造绑定商户、交易和字段用途的稳定 AAD。
     *
     * @param merchantId 商户号
     * @param transactionId 平台交易号
     * @param purpose 字段用途或 DEK 包裹用途
     * @return 版本化 AAD 文本
     */
    private String aad(String merchantId, String transactionId, String purpose) {
        return "card-vault-at-rest-v1|" + merchantId + "|" + transactionId + "|" + purpose;
    }

    /**
     * 校验并组装数据库中的分离 GCM 列。
     *
     * @param iv Base64URL IV
     * @param ciphertext Base64URL 密文
     * @param authTag Base64URL 认证标签
     * @return 完整字段信封
     */
    private FieldEnvelope envelope(String iv, String ciphertext, String authTag) {
        if (!StringUtils.hasText(iv) || !StringUtils.hasText(ciphertext) || !StringUtils.hasText(authTag)) {
            throw new IllegalStateException("card vault envelope is incomplete");
        }
        return new FieldEnvelope(iv, ciphertext, authTag);
    }

    /**
     * 解码并校验配置中的 AES-256 KEK。
     *
     * @param value 标准 Base64 编码密钥
     * @return 32 字节 KEK
     */
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

    /**
     * 使用进程级安全随机源生成密钥或 IV。
     *
     * @param length 目标字节数
     * @return 新随机字节数组
     */
    private byte[] randomBytes(int length) {
        byte[] result = new byte[length];
        SECURE_RANDOM.nextBytes(result);
        return result;
    }

    /**
     * 单字段 AES-GCM 分离密文列。
     *
     * @param iv Base64URL IV
     * @param ciphertext Base64URL 密文
     * @param authTag Base64URL 认证标签
     */
    public record FieldEnvelope(String iv, String ciphertext, String authTag) {
    }

    /**
     * 一条卡资料记录的全部持久化密文列及密钥版本。
     *
     * @param panHmac PAN 查询 HMAC
     * @param pan PAN 密文信封
     * @param expiration 有效期密文信封
     * @param cardholderName 持卡人姓名密文信封；未提供时为空
     * @param wrappedDek KEK 包裹后的记录 DEK
     * @param panHmacKeyVersion HMAC 密钥版本
     * @param kekVersion KEK 版本
     */
    public record EncryptedCardData(String panHmac,
                                    FieldEnvelope pan,
                                    FieldEnvelope expiration,
                                    FieldEnvelope cardholderName,
                                    FieldEnvelope wrappedDek,
                                    String panHmacKeyVersion,
                                    String kekVersion) {
    }

    /**
     * 受控解密结果，模型刻意不定义 CVV 字段。
     *
     * @param cardNo 卡号明文
     * @param expirationMonth 两位有效期月份
     * @param expirationYear 四位有效期年份
     * @param cardholderName 持卡人姓名；未提供时为空
     */
    public record DecryptedCardData(String cardNo,
                                    String expirationMonth,
                                    String expirationYear,
                                    String cardholderName) {
    }
}
