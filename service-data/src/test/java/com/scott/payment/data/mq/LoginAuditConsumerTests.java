package com.scott.payment.data.mq;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.mq.message.LoginAuditMessage;
import com.scott.payment.data.service.LoginAuditPersistenceService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : LoginAuditConsumerTests
 * @date : 2026-08-02 22:35
 * @email : scott_x@163.com
 * @description : 登录审计消费者合法消息和畸形消息测试
 * @status : create
 */
@Slf4j
class LoginAuditConsumerTests {

    /** 合法登录审计消息应交给数据库幂等持久化服务。 */
    @Test
    void shouldPersistValidMessage() {
        log.info("测试登录审计合法消息，关键输入: messageId、appId、loginStatus完整");
        LoginAuditPersistenceService persistenceService = mock(LoginAuditPersistenceService.class);
        LoginAuditConsumer consumer = new LoginAuditConsumer(persistenceService);
        LoginAuditMessage message = new LoginAuditMessage();
        message.setMessageId("LOGIN-EVENT-001");
        message.setAppId(2L);
        message.setLoginStatus(0);

        consumer.onMessage(JsonUtils.toJsonString(message));

        verify(persistenceService).persist(org.mockito.ArgumentMatchers.argThat(actual ->
                "LOGIN-EVENT-001".equals(actual.getMessageId())));
        log.info("登录审计合法消息测试完成，结果: 已进入数据库幂等服务");
    }

    /** 缺少 messageId 的畸形消息不得访问数据库。 */
    @Test
    void shouldSkipInvalidMessage() {
        log.info("测试登录审计畸形消息，关键输入: 缺少messageId");
        LoginAuditPersistenceService persistenceService = mock(LoginAuditPersistenceService.class);
        LoginAuditConsumer consumer = new LoginAuditConsumer(persistenceService);

        consumer.onMessage("{\"appId\":2,\"loginStatus\":0}");

        verify(persistenceService, never()).persist(org.mockito.ArgumentMatchers.any());
        log.info("登录审计畸形消息测试完成，结果: 未访问数据库");
    }
}
