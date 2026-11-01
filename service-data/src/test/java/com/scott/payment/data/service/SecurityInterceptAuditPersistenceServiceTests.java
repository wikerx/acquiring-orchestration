package com.scott.payment.data.service;

import com.scott.payment.component.db.security.entity.SecurityInterceptEventDO;
import com.scott.payment.component.db.security.mapper.SecurityInterceptEventMapper;
import com.scott.payment.component.mq.message.SecurityInterceptAuditMessage;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 安全拦截审计持久化测试，验证 event_no 唯一键幂等与脱敏字段映射
 */
@Slf4j
class SecurityInterceptAuditPersistenceServiceTests {

    /**
     * event_no 唯一键冲突应视为已完成的重复消费。
     */
    @Test
    void shouldTreatExistingEventAsCompletedDuplicate() {
        log.info("测试安全审计数据库最终幂等，关键输入: event_no 唯一键冲突");
        SecurityInterceptEventMapper mapper = mock(SecurityInterceptEventMapper.class);
        when(mapper.insert(any(SecurityInterceptEventDO.class)))
                .thenThrow(new DuplicateKeyException("duplicate event_no"));
        when(mapper.selectCount(any())).thenReturn(1L);
        SecurityInterceptAuditPersistenceService service =
                new SecurityInterceptAuditPersistenceService(mapper);

        assertThatCode(() -> service.persist(message())).doesNotThrowAnyException();
        log.info("安全审计数据库最终幂等完成，结果: 重复事件已正常确认");
    }

    /**
     * 唯一键冲突但 eventNo 不存在时必须上抛，禁止把其他约束冲突当作重复消息。
     */
    @Test
    void shouldRethrowConflictWhenEventDoesNotExist() {
        SecurityInterceptEventMapper mapper = mock(SecurityInterceptEventMapper.class);
        DuplicateKeyException conflict = new DuplicateKeyException("unrelated unique key");
        when(mapper.insert(any(SecurityInterceptEventDO.class))).thenThrow(conflict);
        when(mapper.selectCount(any())).thenReturn(0L);
        SecurityInterceptAuditPersistenceService service =
                new SecurityInterceptAuditPersistenceService(mapper);

        assertThatThrownBy(() -> service.persist(message())).isSameAs(conflict);
    }

    /**
     * 有效消息应完整映射到待处理安全事件记录。
     */
    @Test
    void shouldPersistSanitizedEventFields() {
        log.info("测试安全审计字段映射，关键输入: 脱敏头摘要与毫秒事件时间");
        SecurityInterceptEventMapper mapper = mock(SecurityInterceptEventMapper.class);
        SecurityInterceptAuditPersistenceService service =
                new SecurityInterceptAuditPersistenceService(mapper);
        SecurityInterceptAuditMessage message = message();

        service.persist(message);

        ArgumentCaptor<SecurityInterceptEventDO> captor =
                ArgumentCaptor.forClass(SecurityInterceptEventDO.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getEventNo()).isEqualTo("SIE202608010001");
        assertThat(captor.getValue().getHeaderSummary()).isEqualTo("{\"authorizationPresent\":true}");
        assertThat(captor.getValue().getProcessStatus()).isZero();
        assertThat(captor.getValue().getGmtCreate()).isNotNull();
        log.info("安全审计字段映射完成，结果: 事件以未处理状态提交给 Mapper");
    }

    /**
     * 创建不含任何认证原文的安全审计消息。
     */
    private SecurityInterceptAuditMessage message() {
        SecurityInterceptAuditMessage message = new SecurityInterceptAuditMessage();
        message.setEventNo("SIE202608010001");
        message.setEventTime(LocalDateTime.of(2026, 8, 1, 18, 0));
        message.setSourceLayer("OPENAPI");
        message.setEventType("OPENAPI_JWT_INVALID");
        message.setRiskLevel("HIGH");
        message.setAction("BLOCK");
        message.setHeaderSummary("{\"authorizationPresent\":true}");
        return message;
    }
}
