package com.scott.payment.data.service;

import com.scott.payment.component.mq.message.LoginAuditMessage;
import com.scott.payment.data.mapper.DataLoginAuditMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : LoginAuditPersistenceServiceTests
 * @date : 2026-08-02 22:35
 * @email : scott_x@163.com
 * @description : 登录审计数据库最终幂等和重复消息测试
 * @status : create
 */
@Slf4j
class LoginAuditPersistenceServiceTests {

    /** 首次消费取得数据库唯一键后应写入一条登录日志。 */
    @Test
    void shouldPersistFirstDelivery() {
        log.info("测试登录审计首次消费，关键输入: 唯一键插入成功");
        DataLoginAuditMapper mapper = mock(DataLoginAuditMapper.class);
        when(mapper.insertConsumeRecord(any(), eq("LOGIN-EVENT-001"), any(), any())).thenReturn(1);
        LoginAuditPersistenceService service = new LoginAuditPersistenceService(mapper);

        assertThat(service.persist(message())).isTrue();

        verify(mapper).insertLoginLog(any(LoginAuditMessage.class), any(LocalDateTime.class));
        log.info("登录审计首次消费测试完成，结果: 登录日志写入1次");
    }

    /** 重复消息未取得数据库唯一键时不得再次写登录日志。 */
    @Test
    void shouldSkipDuplicateDelivery() {
        log.info("测试登录审计重复消费，关键输入: 唯一键已存在");
        DataLoginAuditMapper mapper = mock(DataLoginAuditMapper.class);
        when(mapper.insertConsumeRecord(any(), eq("LOGIN-EVENT-001"), any(), any())).thenReturn(0);
        LoginAuditPersistenceService service = new LoginAuditPersistenceService(mapper);

        assertThat(service.persist(message())).isFalse();

        verify(mapper, never()).insertLoginLog(any(), any());
        log.info("登录审计重复消费测试完成，结果: 未重复写登录日志");
    }

    /** 创建最小合法登录审计消息。 */
    private LoginAuditMessage message() {
        LoginAuditMessage message = new LoginAuditMessage();
        message.setMessageId("LOGIN-EVENT-001");
        message.setAppId(2L);
        message.setAccountId(60L);
        message.setLoginStatus(0);
        message.setLoginAt(LocalDateTime.of(2026, 8, 2, 22, 30));
        return message;
    }
}
