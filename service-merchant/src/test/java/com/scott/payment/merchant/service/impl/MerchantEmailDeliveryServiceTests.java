package com.scott.payment.merchant.service.impl;

import com.scott.payment.component.mq.email.EmailPayloadCrypto;
import com.scott.payment.component.mq.enums.EmailDeliveryStatus;
import com.scott.payment.component.mq.message.EmailDeliveryMessage;
import com.scott.payment.component.mq.properties.EmailDeliveryProperties;
import com.scott.payment.component.mq.publisher.ReliableMqPublisher;
import com.scott.payment.merchant.entity.email.MerchantEmailEntities.MerchantEmailAccountDO;
import com.scott.payment.merchant.entity.email.MerchantEmailEntities.MerchantEmailSendRecordDO;
import com.scott.payment.merchant.mapper.MerchantEmailAccountMapper;
import com.scott.payment.merchant.mapper.MerchantEmailSendRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证 Merchant 邮件消费者的重复消息吸收、CAS 抢占和失败退避。 */
class MerchantEmailDeliveryServiceTests {

    /** 商户邮件记录 CAS Mapper。 */
    private MerchantEmailSendRecordMapper recordMapper;
    /** 商户 SMTP 账户 Mapper。 */
    private MerchantEmailAccountMapper accountMapper;
    /** SMTP 发送适配器。 */
    private MerchantSmtpEmailSender smtpEmailSender;
    /** 邮件正文解密组件。 */
    private EmailPayloadCrypto payloadCrypto;
    /** 被测 Merchant 邮件投递状态机。 */
    private MerchantEmailDeliveryService deliveryService;

    @BeforeEach
    void setUp() {
        recordMapper = mock(MerchantEmailSendRecordMapper.class);
        accountMapper = mock(MerchantEmailAccountMapper.class);
        smtpEmailSender = mock(MerchantSmtpEmailSender.class);
        payloadCrypto = mock(EmailPayloadCrypto.class);
        EmailDeliveryProperties properties = new EmailDeliveryProperties();
        properties.setRetryDelaySeconds(30);
        deliveryService = new MerchantEmailDeliveryService(recordMapper, accountMapper, smtpEmailSender,
                payloadCrypto, properties, mock(ReliableMqPublisher.class));
    }

    @Test
    void shouldIgnoreDuplicateMessageAfterSuccess() {
        when(recordMapper.selectByDeliveryKey(10L, "EMAIL-10", "MERCHANT"))
                .thenReturn(record(EmailDeliveryStatus.SUCCESS));

        assertThat(deliveryService.deliver(message())).isTrue();

        verify(recordMapper, never()).claimForDelivery(any(), any(), any(), any());
        verify(smtpEmailSender, never()).send(any(), any(), any(), any(Boolean.class));
    }

    @Test
    void shouldClaimPendingRecordAndMarkSuccess() {
        MerchantEmailSendRecordDO record = record(EmailDeliveryStatus.PENDING);
        MerchantEmailAccountDO account = new MerchantEmailAccountDO();
        account.setId(20L);
        account.setConnectTimeoutMs(10_000);
        account.setReadTimeoutMs(30_000);
        when(recordMapper.selectByDeliveryKey(10L, "EMAIL-10", "MERCHANT")).thenReturn(record);
        when(recordMapper.claimForDelivery(eq(10L), eq("EMAIL-10"), eq("MERCHANT"), any(LocalDateTime.class)))
                .thenReturn(1);
        when(accountMapper.selectById(20L)).thenReturn(account);
        when(payloadCrypto.decrypt("cipher-content")).thenReturn("rendered-content");
        when(recordMapper.markDeliverySuccess(eq(10L), eq("EMAIL-10"), eq("MERCHANT"),
                any(LocalDateTime.class), any(Long.class))).thenReturn(1);

        assertThat(deliveryService.deliver(message())).isTrue();

        verify(smtpEmailSender).send(account, record, "rendered-content", true);
        verify(recordMapper, never()).markDeliveryFailure(any(), any(), any(), any(), any(), any(), any(), any());
    }

    /** SMTP 已成功时，成功终态持久化异常不得误写为 SMTP 发送失败。 */
    @Test
    void shouldNotMarkDeliveryFailureWhenSuccessPersistenceFails() {
        MerchantEmailSendRecordDO record = record(EmailDeliveryStatus.PENDING);
        MerchantEmailAccountDO account = new MerchantEmailAccountDO();
        account.setId(20L);
        account.setConnectTimeoutMs(10_000);
        account.setReadTimeoutMs(30_000);
        when(recordMapper.selectByDeliveryKey(10L, "EMAIL-10", "MERCHANT")).thenReturn(record);
        when(recordMapper.claimForDelivery(eq(10L), eq("EMAIL-10"), eq("MERCHANT"), any(LocalDateTime.class)))
                .thenReturn(1);
        when(accountMapper.selectById(20L)).thenReturn(account);
        when(payloadCrypto.decrypt("cipher-content")).thenReturn("rendered-content");
        when(recordMapper.markDeliverySuccess(eq(10L), eq("EMAIL-10"), eq("MERCHANT"),
                any(LocalDateTime.class), any(Long.class))).thenThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(() -> deliveryService.deliver(message()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");

        verify(recordMapper, never()).markDeliveryFailure(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldPersistRetryAfterSmtpFailure() {
        MerchantEmailSendRecordDO record = record(EmailDeliveryStatus.PENDING);
        MerchantEmailAccountDO account = new MerchantEmailAccountDO();
        account.setId(20L);
        account.setConnectTimeoutMs(10_000);
        account.setReadTimeoutMs(30_000);
        when(recordMapper.selectByDeliveryKey(10L, "EMAIL-10", "MERCHANT")).thenReturn(record);
        when(recordMapper.claimForDelivery(eq(10L), eq("EMAIL-10"), eq("MERCHANT"), any(LocalDateTime.class)))
                .thenReturn(1);
        when(accountMapper.selectById(20L)).thenReturn(account);
        when(payloadCrypto.decrypt("cipher-content")).thenReturn("rendered-content");
        doThrow(new IllegalStateException("smtp unavailable"))
                .when(smtpEmailSender).send(account, record, "rendered-content", true);
        when(recordMapper.markDeliveryFailure(eq(10L), eq("EMAIL-10"), eq("MERCHANT"),
                any(LocalDateTime.class), any(LocalDateTime.class), any(Long.class),
                eq("EMAIL_SEND_FAILED"), eq("IllegalStateException"))).thenReturn(1);

        assertThat(deliveryService.deliver(message())).isFalse();

        verify(recordMapper).markDeliveryFailure(eq(10L), eq("EMAIL-10"), eq("MERCHANT"),
                any(LocalDateTime.class), any(LocalDateTime.class), any(Long.class),
                eq("EMAIL_SEND_FAILED"), eq("IllegalStateException"));
    }

    /** 超时预算超过恢复窗口时不得启动 SMTP，避免发送中记录被恢复任务重复抢占。 */
    @Test
    void shouldRejectSmtpTimeoutBudgetThatCanOverlapRecovery() {
        MerchantEmailSendRecordDO record = record(EmailDeliveryStatus.PENDING);
        MerchantEmailAccountDO account = new MerchantEmailAccountDO();
        account.setId(20L);
        account.setConnectTimeoutMs(100_000);
        account.setReadTimeoutMs(100_000);
        when(recordMapper.selectByDeliveryKey(10L, "EMAIL-10", "MERCHANT")).thenReturn(record);
        when(recordMapper.claimForDelivery(eq(10L), eq("EMAIL-10"), eq("MERCHANT"), any(LocalDateTime.class)))
                .thenReturn(1);
        when(accountMapper.selectById(20L)).thenReturn(account);
        when(recordMapper.markDeliveryFailure(eq(10L), eq("EMAIL-10"), eq("MERCHANT"),
                any(LocalDateTime.class), any(LocalDateTime.class), any(Long.class),
                eq("EMAIL_SEND_FAILED"), eq("IllegalStateException"))).thenReturn(1);

        assertThat(deliveryService.deliver(message())).isFalse();

        verify(smtpEmailSender, never()).send(any(), any(), any(), any(Boolean.class));
        verify(recordMapper).markDeliveryFailure(eq(10L), eq("EMAIL-10"), eq("MERCHANT"),
                any(LocalDateTime.class), any(LocalDateTime.class), any(Long.class),
                eq("EMAIL_SEND_FAILED"), eq("IllegalStateException"));
    }

    private EmailDeliveryMessage message() {
        EmailDeliveryMessage message = new EmailDeliveryMessage();
        message.setRecordId(10L);
        message.setEmailNo("EMAIL-10");
        message.setAppCode("MERCHANT");
        message.setMessageId("message-10");
        return message;
    }

    private MerchantEmailSendRecordDO record(EmailDeliveryStatus status) {
        MerchantEmailSendRecordDO record = new MerchantEmailSendRecordDO();
        record.setId(10L);
        record.setEmailNo("EMAIL-10");
        record.setAppCode("MERCHANT");
        record.setAccountId(20L);
        record.setSendStatus(status.getCode());
        record.setRetryCount(0);
        record.setMaxRetryCount(3);
        record.setDeliveryContentCipher("cipher-content");
        record.setContentType("HTML");
        return record;
    }
}
