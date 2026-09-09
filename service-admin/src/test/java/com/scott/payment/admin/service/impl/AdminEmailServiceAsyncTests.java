package com.scott.payment.admin.service.impl;

import com.scott.payment.admin.dto.email.EmailDTOs.EmailSendRequest;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailSendResult;
import com.scott.payment.admin.entity.email.EmailEntities.EmailAccountDO;
import com.scott.payment.admin.entity.email.EmailEntities.EmailSendRecordDO;
import com.scott.payment.admin.entity.email.EmailEntities.EmailTemplateDO;
import com.scott.payment.admin.mapper.EmailAccountMapper;
import com.scott.payment.admin.mapper.EmailSendRecordMapper;
import com.scott.payment.admin.mapper.EmailTemplateMapper;
import com.scott.payment.admin.service.AdminConfigService;
import com.scott.payment.component.mq.email.EmailPayloadCrypto;
import com.scott.payment.component.db.email.model.EnabledEmailTemplateSnapshot;
import com.scott.payment.component.db.email.service.EnabledEmailTemplateCacheReader;
import com.scott.payment.component.mq.enums.EmailDeliveryStatus;
import com.scott.payment.component.mq.properties.EmailDeliveryProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminEmailServiceAsyncTests
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 验证 Admin 业务邮件只写记录与 Outbox，不在请求线程调用 SMTP。
 * @status : create
 */
class AdminEmailServiceAsyncTests {

    /** 邮件账户查询依赖。 */
    private EmailAccountMapper accountMapper;
    /** 邮件模板查询依赖。 */
    private EmailTemplateMapper templateMapper;
    /** 邮件发送记录持久化依赖。 */
    private EmailSendRecordMapper recordMapper;
    /** 可靠 Outbox 入队依赖。 */
    private AdminEmailDeliveryService deliveryService;
    /** 用于断言请求线程未调用 SMTP 的适配器。 */
    private AdminSmtpEmailSender smtpEmailSender;
    /** 邮件正文加密依赖。 */
    private EmailPayloadCrypto payloadCrypto;
    /** 被测 Admin 邮件服务。 */
    private AdminEmailServiceImpl emailService;
    private EnabledEmailTemplateCacheReader enabledTemplateCacheReader;

    @BeforeEach
    void setUp() {
        accountMapper = mock(EmailAccountMapper.class);
        templateMapper = mock(EmailTemplateMapper.class);
        recordMapper = mock(EmailSendRecordMapper.class);
        deliveryService = mock(AdminEmailDeliveryService.class);
        smtpEmailSender = mock(AdminSmtpEmailSender.class);
        payloadCrypto = mock(EmailPayloadCrypto.class);
        enabledTemplateCacheReader = mock(EnabledEmailTemplateCacheReader.class);
        AdminConfigService configService = mock(AdminConfigService.class);
        when(configService.enabledConfigValues(any())).thenReturn(Map.of());
        EmailDeliveryProperties properties = new EmailDeliveryProperties();
        properties.setDefaultMaxRetryCount(3);
        emailService = new AdminEmailServiceImpl(accountMapper, templateMapper, recordMapper, configService,
                payloadCrypto, deliveryService, smtpEmailSender, properties,
                enabledTemplateCacheReader,
                mock(com.scott.payment.component.db.cache.service.ManagedCacheInvalidationCoordinator.class));
    }

    @Test
    void shouldPersistEncryptedPendingRecordAndEnqueueWithoutSmtp() {
        when(enabledTemplateCacheReader.findEnabled("LOGIN_NOTICE", "zh-CN"))
                .thenReturn(templateSnapshot());
        when(accountMapper.selectOne(any())).thenReturn(account());
        when(payloadCrypto.encrypt("Hello 123456")).thenReturn("cipher-content");
        doAnswer(invocation -> {
            EmailSendRecordDO record = invocation.getArgument(0);
            record.setId(10L);
            return 1;
        }).when(recordMapper).insert(any(EmailSendRecordDO.class));

        EmailSendResult result = emailService.sendByTemplate(request());

        ArgumentCaptor<EmailSendRecordDO> recordCaptor = ArgumentCaptor.forClass(EmailSendRecordDO.class);
        verify(recordMapper).insert(recordCaptor.capture());
        EmailSendRecordDO record = recordCaptor.getValue();
        assertThat(record.getSendStatus()).isEqualTo(EmailDeliveryStatus.PENDING.getCode());
        assertThat(record.getMaxRetryCount()).isEqualTo(3);
        assertThat(record.getDeliveryContentCipher()).isEqualTo("cipher-content");
        assertThat(record.getContentSnapshot()).isEqualTo("Hello ******");
        assertThat(record.getVariablesSnapshot()).doesNotContain("123456");
        verify(deliveryService).enqueue(record);
        verify(smtpEmailSender, never()).send(any(), any(), any(), any(Boolean.class));
        assertThat(result.getRecordId()).isEqualTo(10L);
        assertThat(result.getSendStatus()).isEqualTo(EmailDeliveryStatus.PENDING.getCode());
    }

    private EmailTemplateDO template() {
        EmailTemplateDO template = new EmailTemplateDO();
        template.setTemplateCode("LOGIN_NOTICE");
        template.setTemplateName("Login notice");
        template.setSceneCode("COMMON");
        template.setLocale("zh-CN");
        template.setSubjectTemplate("Notice ${otp}");
        template.setContentTemplate("Hello ${otp}");
        template.setContentType("HTML");
        template.setSensitiveVariableNames("[\"otp\"]");
        template.setStatus(1);
        template.setDeleted(0L);
        return template;
    }

    private EnabledEmailTemplateSnapshot templateSnapshot() {
        EmailTemplateDO template = template();
        EnabledEmailTemplateSnapshot snapshot = new EnabledEmailTemplateSnapshot();
        snapshot.setId(template.getId());
        snapshot.setTemplateCode(template.getTemplateCode());
        snapshot.setTemplateName(template.getTemplateName());
        snapshot.setAppCode(template.getAppCode());
        snapshot.setSceneCode(template.getSceneCode());
        snapshot.setLocale(template.getLocale());
        snapshot.setSubjectTemplate(template.getSubjectTemplate());
        snapshot.setContentType(template.getContentType());
        snapshot.setContentTemplate(template.getContentTemplate());
        snapshot.setVariableSchema(template.getVariableSchema());
        snapshot.setSensitiveVariableNames(template.getSensitiveVariableNames());
        return snapshot;
    }

    private EmailAccountDO account() {
        EmailAccountDO account = new EmailAccountDO();
        account.setId(20L);
        account.setAccountCode("ADMIN_DEFAULT");
        account.setAppCode("ADMIN");
        account.setProviderType("SMTP");
        account.setFromName("System");
        account.setFromEmail("masked@example.invalid");
        return account;
    }

    private EmailSendRequest request() {
        EmailSendRequest request = new EmailSendRequest();
        request.setAppCode("ADMIN");
        request.setTemplateCode("LOGIN_NOTICE");
        request.setSceneCode("COMMON");
        request.setLocale("zh-CN");
        request.setToEmails(List.of("masked@example.invalid"));
        request.setVariables(Map.of("otp", "123456"));
        return request;
    }
}
