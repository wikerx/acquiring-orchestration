package com.scott.payment.admin.service.impl;

import com.scott.payment.admin.entity.email.EmailEntities.EmailAccountDO;
import com.scott.payment.admin.entity.email.EmailEntities.EmailSendRecordDO;
import com.scott.payment.admin.mapper.EmailAccountMapper;
import com.scott.payment.admin.mapper.EmailSendRecordMapper;
import com.scott.payment.component.mq.email.EmailPayloadCrypto;
import com.scott.payment.component.mq.enums.EmailDeliveryStatus;
import com.scott.payment.component.mq.message.EmailDeliveryMessage;
import com.scott.payment.component.mq.properties.EmailDeliveryProperties;
import com.scott.payment.component.mq.publisher.ReliableMqPublisher;
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

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminEmailDeliveryServiceTests
 * @date : 2026-08-02 23:50
 * @email : scott_x@163.com
 * @description : 验证 Admin 邮件消费者以数据库 CAS 吸收重复消息并按持久化状态推进成功或退避重试
 * @status : create
 */
class AdminEmailDeliveryServiceTests {

    /** 邮件记录 Mapper。 */
    private EmailSendRecordMapper recordMapper;
    /** 发件账户 Mapper。 */
    private EmailAccountMapper accountMapper;
    /** SMTP 发送适配器。 */
    private AdminSmtpEmailSender smtpEmailSender;
    /** 密文解密组件。 */
    private EmailPayloadCrypto payloadCrypto;
    /** 被测投递服务。 */
    private AdminEmailDeliveryService deliveryService;
    /** 可靠 Outbox 发布器。 */
    private ReliableMqPublisher reliableMqPublisher;

    /** 创建隔离的 Mock 依赖。 */
    @BeforeEach
    void setUp() {
        recordMapper = mock(EmailSendRecordMapper.class);
        accountMapper = mock(EmailAccountMapper.class);
        smtpEmailSender = mock(AdminSmtpEmailSender.class);
        payloadCrypto = mock(EmailPayloadCrypto.class);
        EmailDeliveryProperties properties = new EmailDeliveryProperties();
        properties.setRetryDelaySeconds(30);
        reliableMqPublisher = mock(ReliableMqPublisher.class);
        deliveryService = new AdminEmailDeliveryService(
                recordMapper,
                accountMapper,
                smtpEmailSender,
                payloadCrypto,
                properties,
                reliableMqPublisher);
    }

    /** 重复到达的成功终态消息不得再次调用 SMTP。 */
    @Test
    void shouldIgnoreDuplicateMessageAfterSuccess() {
        EmailSendRecordDO record = record(EmailDeliveryStatus.SUCCESS);
        when(recordMapper.selectByDeliveryKey(10L, "EMAIL-10", "ADMIN")).thenReturn(record);

        assertThat(deliveryService.deliver(message())).isTrue();

        verify(recordMapper, never()).claimForDelivery(any(), any(), any(), any());
        verify(smtpEmailSender, never()).send(any(), any(), any(), any(Boolean.class));
    }

    /** 其它应用的定位消息不得触发 Admin 记录查询或 SMTP 调用。 */
    @Test
    void shouldIgnoreForeignApplicationMessage() {
        EmailDeliveryMessage message = message();
        message.setAppCode("MERCHANT");

        assertThat(deliveryService.deliver(message)).isTrue();

        verify(recordMapper, never()).selectByDeliveryKey(any(), any(), any());
        verify(smtpEmailSender, never()).send(any(), any(), any(), any(Boolean.class));
    }

    /** 待发送记录只有 CAS 抢占成功后才允许调用 SMTP 并进入成功终态。 */
    @Test
    void shouldClaimPendingRecordAndMarkSuccess() {
        EmailSendRecordDO record = record(EmailDeliveryStatus.PENDING);
        EmailAccountDO account = new EmailAccountDO();
        account.setId(20L);
        account.setConnectTimeoutMs(10_000);
        account.setReadTimeoutMs(30_000);
        when(recordMapper.selectByDeliveryKey(10L, "EMAIL-10", "ADMIN")).thenReturn(record);
        when(recordMapper.claimForDelivery(eq(10L), eq("EMAIL-10"), eq("ADMIN"), any(LocalDateTime.class))).thenReturn(1);
        when(accountMapper.selectById(20L)).thenReturn(account);
        when(payloadCrypto.decrypt("cipher-content")).thenReturn("rendered-content");
        when(recordMapper.markDeliverySuccess(eq(10L), eq("EMAIL-10"), eq("ADMIN"),
                any(LocalDateTime.class), any(Long.class))).thenReturn(1);

        assertThat(deliveryService.deliver(message())).isTrue();

        verify(smtpEmailSender).send(account, record, "rendered-content", true);
        verify(recordMapper).markDeliverySuccess(eq(10L), eq("EMAIL-10"), eq("ADMIN"),
                any(LocalDateTime.class), any(Long.class));
        verify(recordMapper, never()).markDeliveryFailure(any(), any(), any(), any(), any(), any(), any(), any());
    }

    /** SMTP 已成功时，成功终态持久化异常不得误写为 SMTP 发送失败。 */
    @Test
    void shouldNotMarkDeliveryFailureWhenSuccessPersistenceFails() {
        EmailSendRecordDO record = record(EmailDeliveryStatus.PENDING);
        EmailAccountDO account = new EmailAccountDO();
        account.setId(20L);
        account.setConnectTimeoutMs(10_000);
        account.setReadTimeoutMs(30_000);
        when(recordMapper.selectByDeliveryKey(10L, "EMAIL-10", "ADMIN")).thenReturn(record);
        when(recordMapper.claimForDelivery(eq(10L), eq("EMAIL-10"), eq("ADMIN"), any(LocalDateTime.class))).thenReturn(1);
        when(accountMapper.selectById(20L)).thenReturn(account);
        when(payloadCrypto.decrypt("cipher-content")).thenReturn("rendered-content");
        when(recordMapper.markDeliverySuccess(eq(10L), eq("EMAIL-10"), eq("ADMIN"),
                any(LocalDateTime.class), any(Long.class))).thenThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(() -> deliveryService.deliver(message()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");

        verify(recordMapper, never()).markDeliveryFailure(any(), any(), any(), any(), any(), any(), any(), any());
    }

    /** SMTP 异常只推进 RETRY_WAIT/CLOSED，不允许无条件覆盖其它消费者结果。 */
    @Test
    void shouldPersistRetryAfterSmtpFailure() {
        EmailSendRecordDO record = record(EmailDeliveryStatus.PENDING);
        EmailAccountDO account = new EmailAccountDO();
        account.setId(20L);
        account.setConnectTimeoutMs(10_000);
        account.setReadTimeoutMs(30_000);
        when(recordMapper.selectByDeliveryKey(10L, "EMAIL-10", "ADMIN")).thenReturn(record);
        when(recordMapper.claimForDelivery(eq(10L), eq("EMAIL-10"), eq("ADMIN"), any(LocalDateTime.class))).thenReturn(1);
        when(accountMapper.selectById(20L)).thenReturn(account);
        when(payloadCrypto.decrypt("cipher-content")).thenReturn("rendered-content");
        doThrow(new IllegalStateException("smtp unavailable"))
                .when(smtpEmailSender).send(account, record, "rendered-content", true);
        when(recordMapper.markDeliveryFailure(eq(10L), eq("EMAIL-10"), eq("ADMIN"),
                any(LocalDateTime.class), any(LocalDateTime.class), any(Long.class),
                eq("EMAIL_SEND_FAILED"), eq("IllegalStateException"))).thenReturn(1);

        assertThat(deliveryService.deliver(message())).isFalse();

        verify(recordMapper).markDeliveryFailure(eq(10L), eq("EMAIL-10"), eq("ADMIN"),
                any(LocalDateTime.class), any(LocalDateTime.class), any(Long.class),
                eq("EMAIL_SEND_FAILED"), eq("IllegalStateException"));
        verify(recordMapper, never()).markDeliverySuccess(any(), any(), any(), any(), any());
    }

    /** 超时预算超过恢复窗口时不得启动 SMTP，避免发送中记录被恢复任务重复抢占。 */
    @Test
    void shouldRejectSmtpTimeoutBudgetThatCanOverlapRecovery() {
        EmailSendRecordDO record = record(EmailDeliveryStatus.PENDING);
        EmailAccountDO account = new EmailAccountDO();
        account.setId(20L);
        account.setConnectTimeoutMs(100_000);
        account.setReadTimeoutMs(100_000);
        when(recordMapper.selectByDeliveryKey(10L, "EMAIL-10", "ADMIN")).thenReturn(record);
        when(recordMapper.claimForDelivery(eq(10L), eq("EMAIL-10"), eq("ADMIN"), any(LocalDateTime.class))).thenReturn(1);
        when(accountMapper.selectById(20L)).thenReturn(account);
        when(recordMapper.markDeliveryFailure(eq(10L), eq("EMAIL-10"), eq("ADMIN"),
                any(LocalDateTime.class), any(LocalDateTime.class), any(Long.class),
                eq("EMAIL_SEND_FAILED"), eq("IllegalStateException"))).thenReturn(1);

        assertThat(deliveryService.deliver(message())).isFalse();

        verify(smtpEmailSender, never()).send(any(), any(), any(), any(Boolean.class));
        verify(recordMapper).markDeliveryFailure(eq(10L), eq("EMAIL-10"), eq("ADMIN"),
                any(LocalDateTime.class), any(LocalDateTime.class), any(Long.class),
                eq("EMAIL_SEND_FAILED"), eq("IllegalStateException"));
    }

    /** 到期记录必须先 CAS 回待发送，再写入只含定位字段的新 Outbox。 */
    @Test
    void shouldRequeueDueRecordAndPublishLocatorOnly() {
        EmailSendRecordDO record = record(EmailDeliveryStatus.RETRY_WAIT);
        record.setRetryCount(1);
        when(recordMapper.selectDueForRetry(eq("ADMIN"), any(LocalDateTime.class), eq(100)))
                .thenReturn(java.util.List.of(record));
        when(recordMapper.requeueForDelivery(eq(10L), eq("EMAIL-10"), eq("ADMIN"), any(LocalDateTime.class)))
                .thenReturn(1);

        assertThat(deliveryService.recoverAndRequeue()).isOne();

        org.mockito.ArgumentCaptor<EmailDeliveryMessage> messageCaptor =
                org.mockito.ArgumentCaptor.forClass(EmailDeliveryMessage.class);
        verify(reliableMqPublisher).publish(eq(com.scott.payment.component.mq.constant.MqTopic.EMAIL_DELIVERY),
                eq(com.scott.payment.component.mq.constant.MqTag.ADMIN_EMAIL_DELIVERY), messageCaptor.capture());
        assertThat(messageCaptor.getValue())
                .extracting(EmailDeliveryMessage::getRecordId,
                        EmailDeliveryMessage::getEmailNo,
                        EmailDeliveryMessage::getAppCode)
                .containsExactly(10L, "EMAIL-10", "ADMIN");
    }

    /** 构造最小投递消息。 */
    private EmailDeliveryMessage message() {
        EmailDeliveryMessage message = new EmailDeliveryMessage();
        message.setRecordId(10L);
        message.setEmailNo("EMAIL-10");
        message.setAppCode("ADMIN");
        message.setMessageId("message-10");
        return message;
    }

    /** 构造最小邮件发送记录。 */
    private EmailSendRecordDO record(EmailDeliveryStatus status) {
        EmailSendRecordDO record = new EmailSendRecordDO();
        record.setId(10L);
        record.setEmailNo("EMAIL-10");
        record.setAppCode("ADMIN");
        record.setAccountId(20L);
        record.setSendStatus(status.getCode());
        record.setRetryCount(0);
        record.setMaxRetryCount(3);
        record.setDeliveryContentCipher("cipher-content");
        record.setContentType("HTML");
        return record;
    }
}
