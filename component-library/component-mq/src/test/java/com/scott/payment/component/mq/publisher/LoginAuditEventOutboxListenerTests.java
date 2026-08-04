package com.scott.payment.component.mq.publisher;

import com.scott.payment.component.db.auth.event.LoginAuditEvent;
import com.scott.payment.component.mq.constant.MqTag;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.component.mq.message.LoginAuditMessage;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : LoginAuditEventOutboxListenerTests
 * @date : 2026-08-02 22:35
 * @email : scott_x@163.com
 * @description : 登录认证事件到可靠 MQ 契约的映射测试
 * @status : create
 */
@Slf4j
class LoginAuditEventOutboxListenerTests {

    /** 登录失败事件应保留稳定消息号和审计字段，且进入独立 Outbox。 */
    @Test
    void shouldPublishLoginAuditThroughIndependentOutbox() {
        log.info("测试登录审计事件入Outbox，关键输入: MERCHANT密码错误");
        IndependentReliableMqPublisher publisher = mock(IndependentReliableMqPublisher.class);
        LoginAuditEventOutboxListener listener = new LoginAuditEventOutboxListener(publisher);
        LocalDateTime loginAt = LocalDateTime.of(2026, 8, 2, 22, 30);

        listener.onLoginAudit(new LoginAuditEvent(
                "LOGIN-EVENT-001", 2L, 60L, 70L, "200045",
                "merchant-user", "127.0.0.1", "merchant-browser", 0,
                "password mismatch", loginAt));

        ArgumentCaptor<LoginAuditMessage> captor = ArgumentCaptor.forClass(LoginAuditMessage.class);
        verify(publisher).publish(
                org.mockito.ArgumentMatchers.eq(MqTopic.LOGIN_AUDIT),
                org.mockito.ArgumentMatchers.eq(MqTag.LOGIN_AUDIT),
                captor.capture());
        assertThat(captor.getValue().getMessageId()).isEqualTo("LOGIN-EVENT-001");
        assertThat(captor.getValue().getLoginStatus()).isZero();
        assertThat(captor.getValue().getLoginAt()).isEqualTo(loginAt);
        log.info("登录审计事件入Outbox测试完成，结果: 消息契约完整");
    }
}
