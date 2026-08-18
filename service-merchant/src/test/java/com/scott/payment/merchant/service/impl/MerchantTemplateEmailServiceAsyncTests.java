package com.scott.payment.merchant.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scott.payment.component.mq.email.EmailPayloadCrypto;
import com.scott.payment.component.mq.enums.EmailDeliveryStatus;
import com.scott.payment.component.mq.properties.EmailDeliveryProperties;
import com.scott.payment.merchant.entity.email.MerchantEmailEntities.MerchantEmailAccountDO;
import com.scott.payment.merchant.entity.email.MerchantEmailEntities.MerchantEmailSendRecordDO;
import com.scott.payment.merchant.entity.email.MerchantEmailEntities.MerchantEmailTemplateDO;
import com.scott.payment.merchant.mapper.MerchantEmailAccountMapper;
import com.scott.payment.merchant.mapper.MerchantEmailSendRecordMapper;
import com.scott.payment.merchant.mapper.MerchantEmailTemplateMapper;
import com.scott.payment.merchant.service.MerchantTemplateEmailService.MerchantEmailSendCommand;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证 Merchant 模板邮件只保存加密记录并进入可靠队列。 */
class MerchantTemplateEmailServiceAsyncTests {

    @Test
    void shouldPersistEncryptedPendingRecordAndEnqueue() {
        MerchantEmailAccountMapper accountMapper = mock(MerchantEmailAccountMapper.class);
        MerchantEmailTemplateMapper templateMapper = mock(MerchantEmailTemplateMapper.class);
        MerchantEmailSendRecordMapper recordMapper = mock(MerchantEmailSendRecordMapper.class);
        EmailPayloadCrypto payloadCrypto = mock(EmailPayloadCrypto.class);
        MerchantEmailDeliveryService deliveryService = mock(MerchantEmailDeliveryService.class);
        EmailDeliveryProperties properties = new EmailDeliveryProperties();
        properties.setDefaultMaxRetryCount(3);
        MerchantTemplateEmailServiceImpl service = new MerchantTemplateEmailServiceImpl(
                accountMapper, templateMapper, recordMapper, new ObjectMapper(), payloadCrypto,
                deliveryService, properties);
        when(templateMapper.selectOne(any())).thenReturn(template());
        when(accountMapper.selectOne(any())).thenReturn(account());
        when(payloadCrypto.encrypt("Code 123456")).thenReturn("cipher-content");
        doAnswer(invocation -> {
            MerchantEmailSendRecordDO record = invocation.getArgument(0);
            record.setId(10L);
            return 1;
        }).when(recordMapper).insert(any(MerchantEmailSendRecordDO.class));

        service.sendByTemplate(new MerchantEmailSendCommand(
                "MERCHANT", "M100", "M100", "Merchant", "MFA_CODE", "MERCHANT_MFA", "zh-CN",
                List.of("masked@example.invalid"), Map.of("otp", "123456"), "MERCHANT_MFA", "10"));

        ArgumentCaptor<MerchantEmailSendRecordDO> recordCaptor = ArgumentCaptor.forClass(MerchantEmailSendRecordDO.class);
        verify(recordMapper).insert(recordCaptor.capture());
        MerchantEmailSendRecordDO record = recordCaptor.getValue();
        assertThat(record.getSendStatus()).isEqualTo(EmailDeliveryStatus.PENDING.getCode());
        assertThat(record.getMaxRetryCount()).isEqualTo(3);
        assertThat(record.getDeliveryContentCipher()).isEqualTo("cipher-content");
        assertThat(record.getContentSnapshot()).isEqualTo("Code ******");
        assertThat(record.getVariablesSnapshot()).doesNotContain("123456");
        verify(deliveryService).enqueue(record);
    }

    private MerchantEmailTemplateDO template() {
        MerchantEmailTemplateDO template = new MerchantEmailTemplateDO();
        template.setTemplateCode("MFA_CODE");
        template.setTemplateName("MFA code");
        template.setSceneCode("MERCHANT_MFA");
        template.setLocale("zh-CN");
        template.setSubjectTemplate("Code ${otp}");
        template.setContentTemplate("Code ${otp}");
        template.setContentType("HTML");
        template.setSensitiveVariableNames("[\"otp\"]");
        return template;
    }

    private MerchantEmailAccountDO account() {
        MerchantEmailAccountDO account = new MerchantEmailAccountDO();
        account.setId(20L);
        account.setAccountCode("MERCHANT_DEFAULT");
        account.setProviderType("SMTP");
        account.setFromName("System");
        account.setFromEmail("masked@example.invalid");
        return account;
    }
}
