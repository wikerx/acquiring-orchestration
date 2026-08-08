package com.scott.payment.payment.security;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.redis.config.PaymentRedisProperties;
import com.scott.payment.component.security.crypto.CheckoutCardEnvelopeCipher;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutPaymentSubmitCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutSessionQueryResultDTO;
import com.scott.payment.payment.config.PaymentCheckoutProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.security.PublicKey;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentCheckoutCardEnvelopeServiceTests
 * @date : 2026-08-08 15:45
 * @email : scott_x@163.com
 * @description : 验证收银台卡数据混合加密协议、AAD 绑定和一次性 nonce 防重放边界。
 * @status : create
 */
class PaymentCheckoutCardEnvelopeServiceTests {

    private static final String SESSION_ID = "CS202608080001";
    private static final String ATTEMPT_REQUEST_ID = "ATT202608080001";
    private PaymentCheckoutCardEnvelopeService service;
    private CheckoutCardEnvelopeCipher cipher;

    @BeforeEach
    void setUp() {
        PaymentCheckoutProperties properties = new PaymentCheckoutProperties();
        properties.getCardEncryption().setAllowEphemeralKey(true);
        properties.getCardEncryption().setReplayStoreRequired(false);
        properties.getCardEncryption().setKeyId("checkout-card-test-v1");
        service = new PaymentCheckoutCardEnvelopeService(
                properties, (StringRedisTemplate) null, new PaymentRedisProperties());
        cipher = new CheckoutCardEnvelopeCipher();
    }

    /** 正确信封只允许解密一次，并返回仅驻留当前调用栈的卡对象。 */
    @Test
    void shouldDecryptValidEnvelopeOnlyOnce() {
        PaymentCheckoutSessionQueryResultDTO.CardEncryptionDTO metadata = service.issue(SESSION_ID);
        PaymentCheckoutPaymentSubmitCommandDTO.CardDataEnvelopeDTO envelope =
                encrypt(metadata, ATTEMPT_REQUEST_ID);

        PaymentCheckoutPaymentSubmitCommandDTO.CardInfoDTO cardInfo =
                service.decryptAndConsume(envelope, SESSION_ID, ATTEMPT_REQUEST_ID);

        assertThat(cardInfo.getCardNo()).endsWith("1111");
        assertThat(cardInfo.getSecurityCode()).hasSize(3);
        assertThatThrownBy(() -> service.decryptAndConsume(envelope, SESSION_ID, ATTEMPT_REQUEST_ID))
                .isInstanceOf(ServiceException.class)
                .extracting("code")
                .isEqualTo(ApiResultEnum.ENCRYPTED_DATA_INVALID.getCode());
    }

    /** 修改 attemptRequestId 会导致 GCM AAD 校验失败，信封不能跨尝试复用。 */
    @Test
    void shouldRejectEnvelopeWhenAttemptBindingIsChanged() {
        PaymentCheckoutSessionQueryResultDTO.CardEncryptionDTO metadata = service.issue(SESSION_ID);
        PaymentCheckoutPaymentSubmitCommandDTO.CardDataEnvelopeDTO envelope =
                encrypt(metadata, ATTEMPT_REQUEST_ID);

        assertThatThrownBy(() -> service.decryptAndConsume(envelope, SESSION_ID, "ATT-TAMPERED"))
                .isInstanceOf(ServiceException.class)
                .extracting("code")
                .isEqualTo(ApiResultEnum.ENCRYPTED_DATA_INVALID.getCode());
    }

    /** 生产 Bean 必须由 Spring 使用依赖构造器创建，不能退回不存在的无参构造器。 */
    @Test
    void shouldCreateCardEnvelopeServiceThroughSpringContext() {
        new ApplicationContextRunner()
                .withBean(PaymentCheckoutProperties.class, () -> {
                    PaymentCheckoutProperties properties = new PaymentCheckoutProperties();
                    properties.getCardEncryption().setAllowEphemeralKey(true);
                    properties.getCardEncryption().setReplayStoreRequired(false);
                    properties.getCardEncryption().setKeyId("checkout-card-context-test-v1");
                    return properties;
                })
                .withBean(PaymentRedisProperties.class)
                .withBean(PaymentCheckoutCardEnvelopeService.class)
                .run(context -> assertThat(context)
                        .hasSingleBean(PaymentCheckoutCardEnvelopeService.class));
    }

    /** 使用下发公钥构建与浏览器相同字段结构的测试信封。 */
    private PaymentCheckoutPaymentSubmitCommandDTO.CardDataEnvelopeDTO encrypt(
            PaymentCheckoutSessionQueryResultDTO.CardEncryptionDTO metadata,
            String attemptRequestId) {
        PublicKey publicKey = cipher.readPublicKey(metadata.getPublicKey());
        String expiryYear = String.valueOf(YearMonth.now().plusYears(2).getYear());
        String plainText = "{\"cardNo\":\"4111111111111111\",\"expirationMonth\":\"12\","
                + "\"expirationYear\":\"" + expiryYear + "\",\"securityCode\":\"123\","
                + "\"cardholderName\":\"TEST BUYER\"}";
        CheckoutCardEnvelopeCipher.EncryptedEnvelope encrypted = cipher.encrypt(
                plainText,
                publicKey,
                PaymentCheckoutCardEnvelopeService.aad(SESSION_ID, attemptRequestId, metadata.getNonce()));
        PaymentCheckoutPaymentSubmitCommandDTO.CardDataEnvelopeDTO envelope =
                new PaymentCheckoutPaymentSubmitCommandDTO.CardDataEnvelopeDTO();
        envelope.setAlgorithm(metadata.getAlgorithm());
        envelope.setKeyId(metadata.getKeyId());
        envelope.setEncryptedKey(encrypted.encryptedKey());
        envelope.setIv(encrypted.iv());
        envelope.setCiphertext(encrypted.ciphertext());
        envelope.setNonce(metadata.getNonce());
        return envelope;
    }
}
