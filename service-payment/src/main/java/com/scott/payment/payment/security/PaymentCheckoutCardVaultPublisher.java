package com.scott.payment.payment.security;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.mq.constant.MqTag;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.component.mq.message.CheckoutCardVaultStoreMessage;
import com.scott.payment.component.mq.publisher.ReliableMqPublisher;
import com.scott.payment.component.security.crypto.CheckoutCardEnvelopeCipher;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutPaymentSubmitCommandDTO;
import com.scott.payment.payment.config.PaymentCheckoutProperties;
import com.scott.payment.payment.entity.PaymentCheckoutAttemptDO;
import com.scott.payment.payment.entity.PaymentCheckoutSessionDO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.PublicKey;

/**
 * 在新收银台支付尝试事务内生成无 CVV 卡资料密文，并通过可靠 Outbox 投递给 service-data。
 */
@Service
public class PaymentCheckoutCardVaultPublisher {

    /**
     * 卡资料消息号前缀，消息号由支付尝试号确定以保持重投稳定。
     */
    private static final String MESSAGE_ID_PREFIX = "CV";

    /**
     * Hosted Checkout 配置。
     */
    private final PaymentCheckoutProperties properties;
    /**
     * 混合加密组件。
     */
    private final CheckoutCardEnvelopeCipher cipher;
    /**
     * 可靠 MQ Outbox 发布器。
     */
    private final ReliableMqPublisher mqPublisher;
    /**
     * service-data 卡资料传输公钥；功能关闭时为空。
     */
    private final PublicKey transferPublicKey;

    /**
     * 创建卡资料密文发布器；启用功能时缺失公钥会阻止服务启动。
     *
     * @param properties 收银台配置
     * @param mqPublisher 可靠 MQ 发布器
     */
    @Autowired
    public PaymentCheckoutCardVaultPublisher(PaymentCheckoutProperties properties,
                                             ReliableMqPublisher mqPublisher) {
        this(properties, mqPublisher, new CheckoutCardEnvelopeCipher());
    }

    /**
     * 使用可替换加密器创建卡资料密文发布器，供协议和加密单元测试使用。
     *
     * @param properties 收银台卡资料保险库配置
     * @param mqPublisher 可靠 MQ 发布器
     * @param cipher 只在内存中处理卡资料密文信封的加密器
     */
    PaymentCheckoutCardVaultPublisher(PaymentCheckoutProperties properties,
                                      ReliableMqPublisher mqPublisher,
                                      CheckoutCardEnvelopeCipher cipher) {
        this.properties = properties;
        this.mqPublisher = mqPublisher;
        this.cipher = cipher;
        PaymentCheckoutProperties.CardVault config = properties.getCardVault();
        if (!config.isEnabled()) {
            this.transferPublicKey = null;
            return;
        }
        if (!StringUtils.hasText(config.getKeyId()) || !StringUtils.hasText(config.getPublicKeyX509Base64())) {
            throw new IllegalStateException("checkout card vault transfer public key is required when enabled");
        }
        this.transferPublicKey = cipher.readPublicKey(config.getPublicKeyX509Base64());
    }

    /**
     * 为新支付尝试写入卡资料 Outbox；CVV 不进入明文快照、密文信封或 MQ 消息。
     *
     * @param sessionDO 收银台会话
     * @param attemptDO 新建支付尝试
     * @param cardInfo 当前调用栈内已解密卡资料
     */
    public void publishIfEnabled(PaymentCheckoutSessionDO sessionDO,
                                 PaymentCheckoutAttemptDO attemptDO,
                                 PaymentCheckoutPaymentSubmitCommandDTO.CardInfoDTO cardInfo) {
        if (!properties.getCardVault().isEnabled()) {
            return;
        }
        if (sessionDO == null || attemptDO == null || cardInfo == null
                || !StringUtils.hasText(cardInfo.getCardNo())) {
            throw new IllegalArgumentException("checkout card vault source is incomplete");
        }
        CheckoutCardVaultStoreMessage message = new CheckoutCardVaultStoreMessage();
        message.setMessageId(MESSAGE_ID_PREFIX + attemptDO.getCheckoutAttemptId());
        message.setMerchantId(sessionDO.getMerchantId());
        message.setTransactionId(attemptDO.getTransactionId());
        message.setTransactionDateTime(attemptDO.getTransactionDateTime());
        message.setCheckoutAttemptId(attemptDO.getCheckoutAttemptId());
        message.setAlgorithm(CheckoutCardEnvelopeCipher.ALGORITHM);
        message.setKeyId(properties.getCardVault().getKeyId());

        CardVaultPlaintext plaintext = new CardVaultPlaintext(
                cardInfo.getCardNo(),
                cardInfo.getExpirationMonth(),
                cardInfo.getExpirationYear(),
                cardInfo.getCardholderName(),
                attemptDO.getPaymentBrand());
        CheckoutCardEnvelopeCipher.EncryptedEnvelope envelope = cipher.encrypt(
                JsonUtils.toJsonString(plaintext), transferPublicKey, message.transferAad());
        message.setEncryptedKey(envelope.encryptedKey());
        message.setIv(envelope.iv());
        message.setCiphertext(envelope.ciphertext());
        mqPublisher.publish(MqTopic.CHECKOUT_CARD_VAULT, MqTag.CHECKOUT_CARD_VAULT_STORE, message);
    }

    /**
     * 明文仅存在于当前加密调用栈，字段定义刻意不包含 securityCode/CVV。
     */
    private record CardVaultPlaintext(String cardNo,
                                      String expirationMonth,
                                      String expirationYear,
                                      String cardholderName,
                                      String cardBrand) {
    }
}
