package com.scott.payment.data.security;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.mq.message.CheckoutCardVaultStoreMessage;
import com.scott.payment.component.security.crypto.CheckoutCardEnvelopeCipher;
import com.scott.payment.data.config.DataCardVaultProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.PrivateKey;
import java.time.YearMonth;
import java.util.Locale;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DataCheckoutCardVaultTransferService
 * @date : 2026-08-08 18:00
 * @email : scott_x@163.com
 * @description : service-data 卡资料传输信封解密服务，校验 AAD、卡号和有效期且不接受 CVV 字段。
 * @status : create
 */
@Service
@ConditionalOnProperty(prefix = "data.card-vault", name = "enabled", havingValue = "true")
public class DataCheckoutCardVaultTransferService {

    /** 传输信封配置。 */
    private final DataCardVaultProperties properties;
    /** RSA/AES-GCM 混合加密组件。 */
    private final CheckoutCardEnvelopeCipher cipher;
    /** service-data 传输私钥。 */
    private final PrivateKey privateKey;

    /**
     * 创建传输解密服务，启用时缺失私钥或版本会阻止服务启动。
     *
     * @param properties 卡资料库配置
     */
    public DataCheckoutCardVaultTransferService(DataCardVaultProperties properties) {
        this(properties, new CheckoutCardEnvelopeCipher());
    }

    DataCheckoutCardVaultTransferService(DataCardVaultProperties properties,
                                         CheckoutCardEnvelopeCipher cipher) {
        this.properties = properties;
        this.cipher = cipher;
        if (!StringUtils.hasText(properties.getTransferKeyId())
                || !StringUtils.hasText(properties.getTransferPrivateKeyPkcs8Base64())) {
            throw new IllegalStateException("card vault transfer private key is required when enabled");
        }
        this.privateKey = cipher.readPrivateKey(properties.getTransferPrivateKeyPkcs8Base64());
    }

    /**
     * 解密并校验一条卡资料消息；返回对象定义中不存在 securityCode/CVV。
     *
     * @param message 卡资料密文消息
     * @return 当前消费调用栈内的卡资料
     */
    public CardVaultPlaintext decrypt(CheckoutCardVaultStoreMessage message) {
        validateMessage(message);
        String json = cipher.decrypt(message.getEncryptedKey(), message.getIv(), message.getCiphertext(),
                privateKey, message.transferAad());
        CardVaultPlaintext plaintext;
        try {
            plaintext = JsonUtils.parseObject(json, CardVaultPlaintext.class);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("card vault transfer payload is invalid", exception);
        }
        validateCard(plaintext);
        return new CardVaultPlaintext(
                plaintext.cardNo().trim(),
                plaintext.expirationMonth(),
                plaintext.expirationYear(),
                normalizeOptional(plaintext.cardholderName()),
                normalizeBrand(plaintext.cardBrand()));
    }

    private void validateMessage(CheckoutCardVaultStoreMessage message) {
        if (message == null
                || !StringUtils.hasText(message.getMessageId())
                || !StringUtils.hasText(message.getMerchantId())
                || !StringUtils.hasText(message.getTransactionId())
                || message.getTransactionDateTime() == null
                || !StringUtils.hasText(message.getCheckoutAttemptId())
                || !CheckoutCardEnvelopeCipher.ALGORITHM.equals(message.getAlgorithm())
                || !properties.getTransferKeyId().equals(message.getKeyId())) {
            throw new IllegalArgumentException("card vault message metadata is invalid");
        }
    }

    private void validateCard(CardVaultPlaintext plaintext) {
        if (plaintext == null
                || plaintext.cardNo() == null
                || !plaintext.cardNo().trim().matches("\\d{12,19}")
                || !luhnValid(plaintext.cardNo().trim())
                || plaintext.expirationMonth() == null
                || !plaintext.expirationMonth().matches("0[1-9]|1[0-2]")
                || plaintext.expirationYear() == null
                || !plaintext.expirationYear().matches("20\\d{2}")) {
            throw new IllegalArgumentException("card vault card data is invalid");
        }
        YearMonth expiry = YearMonth.of(Integer.parseInt(plaintext.expirationYear()),
                Integer.parseInt(plaintext.expirationMonth()));
        if (expiry.isBefore(YearMonth.now())) {
            throw new IllegalArgumentException("card vault card data is invalid");
        }
    }

    private boolean luhnValid(String value) {
        int sum = 0;
        boolean doubleDigit = false;
        for (int index = value.length() - 1; index >= 0; index--) {
            int digit = value.charAt(index) - '0';
            if (doubleDigit && (digit *= 2) > 9) {
                digit -= 9;
            }
            sum += digit;
            doubleDigit = !doubleDigit;
        }
        return sum > 0 && sum % 10 == 0;
    }

    private String normalizeBrand(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "UNKNOWN";
    }

    private String normalizeOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /** 当前消费栈内的卡资料值对象，刻意不定义 CVV 字段。 */
    public record CardVaultPlaintext(String cardNo,
                                     String expirationMonth,
                                     String expirationYear,
                                     String cardholderName,
                                     String cardBrand) {
    }
}
