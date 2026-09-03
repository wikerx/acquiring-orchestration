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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.security.KeyPair;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentCheckoutCardVaultPublisherTests
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 收银台卡资料密文发布安全边界测试。
 * @status : create
 */
class PaymentCheckoutCardVaultPublisherTests {

    @Test
    void shouldPublishOnlyEncryptedCardDataWithoutCvv() {
        CheckoutCardEnvelopeCipher cipher = new CheckoutCardEnvelopeCipher();
        KeyPair keyPair = cipher.generateRsaKeyPair();
        PaymentCheckoutProperties properties = properties(keyPair);
        ReliableMqPublisher mqPublisher = mock(ReliableMqPublisher.class);
        PaymentCheckoutCardVaultPublisher publisher =
                new PaymentCheckoutCardVaultPublisher(properties, mqPublisher, cipher);

        PaymentCheckoutSessionDO session = new PaymentCheckoutSessionDO();
        session.setMerchantId("M1001");
        PaymentCheckoutAttemptDO attempt = new PaymentCheckoutAttemptDO();
        attempt.setCheckoutAttemptId("A1001");
        attempt.setTransactionId("T1001");
        attempt.setTransactionDateTime(LocalDateTime.of(2030, 1, 2, 3, 4, 5));
        attempt.setPaymentBrand("VISA");
        PaymentCheckoutPaymentSubmitCommandDTO.CardInfoDTO card = cardInfo();

        publisher.publishIfEnabled(session, attempt, card);

        ArgumentCaptor<CheckoutCardVaultStoreMessage> captor =
                ArgumentCaptor.forClass(CheckoutCardVaultStoreMessage.class);
        verify(mqPublisher).publish(eq(MqTopic.CHECKOUT_CARD_VAULT),
                eq(MqTag.CHECKOUT_CARD_VAULT_STORE), captor.capture());
        CheckoutCardVaultStoreMessage message = captor.getValue();
        String mqJson = JsonUtils.toJsonString(message);
        @SuppressWarnings("unchecked")
        Map<String, Object> mqPayload = JsonUtils.parseObject(mqJson, Map.class);
        assertThat(mqPayload).doesNotContainKeys(
                "cardNo",
                "securityCode",
                "cvv",
                "cvc",
                "expirationMonth",
                "expirationYear",
                "cardholderName");

        String plaintext = cipher.decrypt(message.getEncryptedKey(), message.getIv(), message.getCiphertext(),
                keyPair.getPrivate(), message.transferAad());
        @SuppressWarnings("unchecked")
        Map<String, Object> decoded = JsonUtils.parseObject(plaintext, Map.class);
        assertThat(decoded)
                .containsEntry("cardNo", card.getCardNo())
                .containsEntry("expirationMonth", card.getExpirationMonth())
                .containsEntry("expirationYear", card.getExpirationYear())
                .doesNotContainKeys("securityCode", "cvv", "cvc");
    }

    @Test
    void shouldNotPublishWhenFeatureIsDisabled() {
        PaymentCheckoutProperties properties = new PaymentCheckoutProperties();
        ReliableMqPublisher mqPublisher = mock(ReliableMqPublisher.class);
        PaymentCheckoutCardVaultPublisher publisher =
                new PaymentCheckoutCardVaultPublisher(properties, mqPublisher, new CheckoutCardEnvelopeCipher());

        publisher.publishIfEnabled(null, null, null);

        verify(mqPublisher, never()).publish(eq(MqTopic.CHECKOUT_CARD_VAULT),
                eq(MqTag.CHECKOUT_CARD_VAULT_STORE), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldCreateCardVaultPublisherThroughSpringContext() {
        new ApplicationContextRunner()
                .withBean(PaymentCheckoutProperties.class)
                .withBean(ReliableMqPublisher.class, () -> mock(ReliableMqPublisher.class))
                .withBean(PaymentCheckoutCardVaultPublisher.class)
                .run(context -> assertThat(context)
                        .hasSingleBean(PaymentCheckoutCardVaultPublisher.class));
    }

    private PaymentCheckoutProperties properties(KeyPair keyPair) {
        PaymentCheckoutProperties properties = new PaymentCheckoutProperties();
        properties.getCardVault().setEnabled(true);
        properties.getCardVault().setKeyId("vault-key-v1");
        properties.getCardVault().setPublicKeyX509Base64(
                Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
        return properties;
    }

    private PaymentCheckoutPaymentSubmitCommandDTO.CardInfoDTO cardInfo() {
        PaymentCheckoutPaymentSubmitCommandDTO.CardInfoDTO card =
                new PaymentCheckoutPaymentSubmitCommandDTO.CardInfoDTO();
        card.setCardNo("4111111111111111");
        card.setExpirationMonth("12");
        card.setExpirationYear("2030");
        card.setSecurityCode("123");
        card.setCardholderName("Test User");
        return card;
    }
}
