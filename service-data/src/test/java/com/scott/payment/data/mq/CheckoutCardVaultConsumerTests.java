package com.scott.payment.data.mq;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.mq.message.CheckoutCardVaultStoreMessage;
import com.scott.payment.data.security.DataCheckoutCardVaultTransferService;
import com.scott.payment.data.service.CheckoutCardVaultPersistenceService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : CheckoutCardVaultConsumerTests
 * @date : 2026-08-21 18:30
 * @email : scott_x@163.com
 * @description : 收银台卡资料 MQ 消费入口测试，确保密文毒消息不会被 Broker 静默确认
 * @status : create
 */
class CheckoutCardVaultConsumerTests {

    /** 带稳定消息身份的密文消息应进入解密和数据库幂等链路。 */
    @Test
    void shouldPersistValidEnvelope() {
        DataCheckoutCardVaultTransferService transferService = mock(DataCheckoutCardVaultTransferService.class);
        CheckoutCardVaultPersistenceService persistenceService = mock(CheckoutCardVaultPersistenceService.class);
        CheckoutCardVaultConsumer consumer = new CheckoutCardVaultConsumer(transferService, persistenceService);
        CheckoutCardVaultStoreMessage message = new CheckoutCardVaultStoreMessage();
        message.setMessageId("VAULT-EVENT-001");
        DataCheckoutCardVaultTransferService.CardVaultPlaintext plaintext =
                new DataCheckoutCardVaultTransferService.CardVaultPlaintext(
                        "4111111111111111", "12", "2030", null, "VISA");
        when(transferService.decrypt(message)).thenReturn(plaintext);
        when(persistenceService.persist(message, plaintext)).thenReturn(true);

        consumer.onMessage(JsonUtils.toJsonString(message));

        verify(transferService).decrypt(message);
        verify(persistenceService).persist(message, plaintext);
    }

    /** 缺少消息幂等身份必须触发 Broker 重试。 */
    @Test
    void shouldRejectMessageWithoutMessageId() {
        DataCheckoutCardVaultTransferService transferService = mock(DataCheckoutCardVaultTransferService.class);
        CheckoutCardVaultPersistenceService persistenceService = mock(CheckoutCardVaultPersistenceService.class);
        CheckoutCardVaultConsumer consumer = new CheckoutCardVaultConsumer(transferService, persistenceService);

        assertThatThrownBy(() -> consumer.onMessage("{}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("card vault message required fields are missing");

        verify(transferService, never()).decrypt(org.mockito.ArgumentMatchers.any());
        verify(persistenceService, never()).persist(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    /** 畸形 JSON 必须触发 Broker 重试且日志不得包含密文正文。 */
    @Test
    void shouldRejectMalformedPayload() {
        DataCheckoutCardVaultTransferService transferService = mock(DataCheckoutCardVaultTransferService.class);
        CheckoutCardVaultPersistenceService persistenceService = mock(CheckoutCardVaultPersistenceService.class);
        CheckoutCardVaultConsumer consumer = new CheckoutCardVaultConsumer(transferService, persistenceService);

        assertThatThrownBy(() -> consumer.onMessage("{invalid-json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("card vault payload is invalid");

        verify(transferService, never()).decrypt(org.mockito.ArgumentMatchers.any());
    }
}
