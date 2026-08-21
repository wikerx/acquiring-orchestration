package com.scott.payment.merchant.mq;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.mq.message.EmailDeliveryMessage;
import com.scott.payment.merchant.service.impl.MerchantEmailDeliveryService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantEmailDeliveryConsumerTests
 * @date : 2026-08-21 18:30
 * @email : scott_x@163.com
 * @description : 商户端邮件 MQ 消费入口契约测试，确保毒消息不会被 Broker 静默确认
 * @status : create
 */
class MerchantEmailDeliveryConsumerTests {

    /** 完整的商户端邮件定位消息应进入数据库 CAS 投递服务。 */
    @Test
    void shouldDeliverValidMessage() {
        MerchantEmailDeliveryService deliveryService = mock(MerchantEmailDeliveryService.class);
        MerchantEmailDeliveryConsumer consumer = new MerchantEmailDeliveryConsumer(deliveryService);
        EmailDeliveryMessage message = message("MERCHANT");
        when(deliveryService.deliver(message)).thenReturn(true);

        consumer.onMessage(JsonUtils.toJsonString(message));

        verify(deliveryService).deliver(message);
    }

    /** 反序列化失败必须抛出异常交给 RocketMQ 重试。 */
    @Test
    void shouldRejectMalformedPayload() {
        MerchantEmailDeliveryService deliveryService = mock(MerchantEmailDeliveryService.class);
        MerchantEmailDeliveryConsumer consumer = new MerchantEmailDeliveryConsumer(deliveryService);

        assertThatThrownBy(() -> consumer.onMessage("{invalid-json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("merchant email delivery payload is invalid");

        verify(deliveryService, never()).deliver(org.mockito.ArgumentMatchers.any());
    }

    /** 当前消费组收到跨应用定位消息时必须拒绝，避免错误确认。 */
    @Test
    void shouldRejectCrossApplicationMessage() {
        MerchantEmailDeliveryService deliveryService = mock(MerchantEmailDeliveryService.class);
        MerchantEmailDeliveryConsumer consumer = new MerchantEmailDeliveryConsumer(deliveryService);

        assertThatThrownBy(() -> consumer.onMessage(JsonUtils.toJsonString(message("ADMIN"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("merchant email delivery message required fields are missing");

        verify(deliveryService, never()).deliver(org.mockito.ArgumentMatchers.any());
    }

    /** 创建最小合法邮件定位消息。 */
    private EmailDeliveryMessage message(String appCode) {
        EmailDeliveryMessage message = new EmailDeliveryMessage();
        message.setMessageId("email-merchant-1-0");
        message.setRecordId(1L);
        message.setEmailNo("EMAIL-MERCHANT-001");
        message.setAppCode(appCode);
        return message;
    }
}
