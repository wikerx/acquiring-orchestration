package com.scott.payment.component.mq.publisher;

import com.scott.payment.component.db.auth.event.LoginAuditEvent;
import com.scott.payment.component.mq.constant.MqTag;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.component.mq.message.LoginAuditMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : LoginAuditEventOutboxListener
 * @date : 2026-08-02 22:30
 * @email : scott_x@163.com
 * @description : 将认证层登录事件以独立事务写入可靠 Outbox，外层认证回滚不影响审计意图
 * @status : create
 */
@Slf4j
@Component
public class LoginAuditEventOutboxListener {

    /** 独立事务可靠消息发布器。 */
    private final IndependentReliableMqPublisher mqPublisher;

    /** 创建登录审计事件监听器。 */
    public LoginAuditEventOutboxListener(IndependentReliableMqPublisher mqPublisher) {
        this.mqPublisher = mqPublisher;
    }

    /**
     * 保存登录审计事件；Outbox 异常不覆盖原始登录结果。
     *
     * @param event 已限制长度且不含认证秘密的登录事件
     */
    @EventListener
    public void onLoginAudit(LoginAuditEvent event) {
        if (event == null || event.eventId() == null || event.appId() == null) {
            return;
        }
        try {
            mqPublisher.publish(MqTopic.LOGIN_AUDIT, MqTag.LOGIN_AUDIT, toMessage(event));
        } catch (RuntimeException exception) {
            log.warn("event: LOGIN_AUDIT_OUTBOX_FAILED eventId: {} exceptionType: {}",
                    event.eventId(), exception.getClass().getSimpleName());
        }
    }

    /** 把认证事件转换为公共 MQ 契约。 */
    private LoginAuditMessage toMessage(LoginAuditEvent event) {
        LoginAuditMessage message = new LoginAuditMessage();
        message.setMessageId(event.eventId());
        message.setCreatedAt(event.loginAt());
        message.setAppId(event.appId());
        message.setAccountId(event.accountId());
        message.setUserId(event.userId());
        message.setMerchantId(event.merchantId());
        message.setLoginAccount(event.loginAccount());
        message.setClientIp(event.clientIp());
        message.setUserAgent(event.userAgent());
        message.setLoginStatus(event.loginStatus());
        message.setFailReason(event.failReason());
        message.setLoginAt(event.loginAt());
        return message;
    }
}
