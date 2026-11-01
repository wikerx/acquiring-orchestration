package com.scott.payment.data.security;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.scott.payment.component.mq.message.CheckoutCardVaultStoreMessage;
import com.scott.payment.component.security.crypto.CheckoutCardEnvelopeCipher;
import com.scott.payment.data.config.DataCardVaultProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.PrivateKey;
import java.time.YearMonth;
import java.util.Locale;
import java.util.Set;

/**
 * 解开 checkout 到 service-data 的卡资料传输信封并校验业务内容。
 *
 * <p>传输 AAD 绑定消息身份，算法和密钥版本必须与本实例配置一致。解密模型不定义 CVV 字段，
 * 从协议入口阻止安全码进入持久化链路。</p>
 */
@Service
@ConditionalOnProperty(prefix = "data.card-vault", name = "enabled", havingValue = "true")
public class DataCheckoutCardVaultTransferService {

    /**
     * 卡资料传输明文允许的完整字段集合，任何额外字段都在进入强类型模型前失败关闭。
     */
    private static final Set<String> PLAINTEXT_FIELDS = Set.of(
            "cardNo",
            "expirationMonth",
            "expirationYear",
            "cardholderName",
            "cardBrand");

    /**
     * 传输信封配置。
     */
    private final DataCardVaultProperties properties;
    /**
     * RSA/AES-GCM 混合加密组件。
     */
    private final CheckoutCardEnvelopeCipher cipher;
    /**
     * service-data 传输私钥。
     */
    private final PrivateKey privateKey;

    /**
     * 创建传输解密服务，启用时缺失私钥或版本会阻止服务启动。
     *
     * @param properties 卡资料库配置
     */
    public DataCheckoutCardVaultTransferService(DataCardVaultProperties properties) {
        this(properties, new CheckoutCardEnvelopeCipher());
    }

    /**
     * 创建使用指定密码组件的传输解密服务，供确定性安全测试注入实现。
     *
     * @param properties 卡资料库配置
     * @param cipher RSA/AES-GCM 传输信封组件
     */
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
            // Fastjson 的 record 路径会忽略未知属性，因此必须在强类型转换前显式执行字段白名单。
            JSONObject payload = JSON.parseObject(json);
            if (payload == null || !PLAINTEXT_FIELDS.containsAll(payload.keySet())) {
                throw new IllegalArgumentException("card vault transfer payload contains unsupported fields");
            }
            plaintext = payload.toJavaObject(CardVaultPlaintext.class);
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

    /**
     * 在解密前校验消息身份、分片时间、算法和密钥版本。
     *
     * @param message 卡资料密文消息
     */
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

    /**
     * 校验 PAN 格式、Luhn 校验位和未过期的年月。
     *
     * @param plaintext 当前调用栈中的解密卡资料
     */
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

    /**
     * 验证卡号的 Luhn 校验位。
     *
     * @param value 仅包含数字的卡号
     * @return true 表示校验位有效
     */
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

    /**
     * 规范化卡品牌，缺失值使用受控 UNKNOWN 标识。
     *
     * @param value 上游卡品牌
     * @return 大写卡品牌
     */
    private String normalizeBrand(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "UNKNOWN";
    }

    /**
     * 去除可选文本首尾空白，空白值归一为空。
     *
     * @param value 可选文本
     * @return 规范化文本
     */
    private String normalizeOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * 当前消费栈内的卡资料值对象，刻意不定义 CVV 字段。
     *
     * @param cardNo 卡号
     * @param expirationMonth 两位有效期月份
     * @param expirationYear 四位有效期年份
     * @param cardholderName 持卡人姓名
     * @param cardBrand 卡品牌
     */
    public record CardVaultPlaintext(String cardNo,
                                     String expirationMonth,
                                     String expirationYear,
                                     String cardholderName,
                                     String cardBrand) {
    }
}
