package com.scott.payment.admin.service.impl;

import com.scott.payment.admin.entity.email.EmailEntities.EmailAccountDO;
import com.scott.payment.admin.entity.email.EmailEntities.EmailSendRecordDO;
import com.scott.payment.admin.mapper.EmailAccountMapper;
import com.scott.payment.admin.mapper.EmailSendRecordMapper;
import com.scott.payment.component.mq.email.EmailDeliveryFailureSummary;
import com.scott.payment.component.mq.email.EmailPayloadCrypto;
import com.scott.payment.component.mq.constant.MqTag;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.component.mq.enums.EmailDeliveryStatus;
import com.scott.payment.component.mq.message.EmailDeliveryMessage;
import com.scott.payment.component.mq.properties.EmailDeliveryProperties;
import com.scott.payment.component.mq.publisher.ReliableMqPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminEmailDeliveryService
 * @date : 2026-08-02 23:40
 * @email : scott_x@163.com
 * @description : 通过数据库 CAS 驱动 Admin 邮件投递、失败退避和超时恢复，MQ 消息只携带记录定位键
 * @status : create
 */
@Service
public class AdminEmailDeliveryService {

    /** Admin 邮件记录固定应用边界。 */
    private static final String APP_CODE = "ADMIN";
    /** SMTP 或内容解密失败的稳定业务错误码。 */
    private static final String SEND_FAILED_CODE = "EMAIL_SEND_FAILED";

    /** 邮件发送记录 CAS Mapper。 */
    private final EmailSendRecordMapper recordMapper;
    /** 冻结 SMTP 配置的邮件账户 Mapper。 */
    private final EmailAccountMapper accountMapper;
    /** 不承担重试决策的 SMTP 适配器。 */
    private final AdminSmtpEmailSender smtpEmailSender;
    /** 投递正文解密组件。 */
    private final EmailPayloadCrypto payloadCrypto;
    /** 退避、超时和批量扫描配置。 */
    private final EmailDeliveryProperties properties;
    /** 与邮件记录同事务写入的可靠 Outbox 发布器。 */
    private final ReliableMqPublisher reliableMqPublisher;

    /** 创建 Admin 邮件投递状态机服务。 */
    public AdminEmailDeliveryService(EmailSendRecordMapper recordMapper,
                                     EmailAccountMapper accountMapper,
                                     AdminSmtpEmailSender smtpEmailSender,
                                     EmailPayloadCrypto payloadCrypto,
                                     EmailDeliveryProperties properties,
                                     ReliableMqPublisher reliableMqPublisher) {
        this.recordMapper = recordMapper;
        this.accountMapper = accountMapper;
        this.smtpEmailSender = smtpEmailSender;
        this.payloadCrypto = payloadCrypto;
        this.properties = properties;
        this.reliableMqPublisher = reliableMqPublisher;
    }

    /** 在当前业务事务中写入不含敏感内容的邮件投递 Outbox。 */
    public void enqueue(EmailSendRecordDO record) {
        reliableMqPublisher.publish(MqTopic.EMAIL_DELIVERY, MqTag.ADMIN_EMAIL_DELIVERY, toMessage(record));
    }

    /** 恢复超时记录并将到期重试与新 Outbox 放在同一事务提交。 */
    @Transactional(rollbackFor = Exception.class)
    public int recoverAndRequeue() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextRetryTime = now.plusSeconds(Math.max(properties.getRetryDelaySeconds(), 1L));
        recordMapper.recoverStaleDelivery(APP_CODE,
                now.minusSeconds(Math.max(properties.getProcessingTimeoutSeconds(), 1L)), nextRetryTime, now);
        List<EmailSendRecordDO> dueRecords = recordMapper.selectDueForRetry(
                APP_CODE, now, Math.max(properties.getBatchSize(), 1));
        int requeued = 0;
        for (EmailSendRecordDO record : dueRecords) {
            if (recordMapper.requeueForDelivery(record.getId(), record.getEmailNo(), record.getAppCode(), now) == 1) {
                enqueue(record);
                requeued++;
            }
        }
        return requeued;
    }

    /** 消费一条定位消息；重复消息由记录当前状态吸收。 */
    public boolean deliver(EmailDeliveryMessage message) {
        if (!isValid(message)) {
            return true;
        }
        EmailSendRecordDO record = recordMapper.selectByDeliveryKey(
                message.getRecordId(), message.getEmailNo(), message.getAppCode());
        if (record == null) {
            return true;
        }
        EmailDeliveryStatus status = EmailDeliveryStatus.fromCode(record.getSendStatus());
        if (status.isTerminal() || status != EmailDeliveryStatus.PENDING) {
            return true;
        }

        LocalDateTime start = LocalDateTime.now();
        if (recordMapper.claimForDelivery(record.getId(), record.getEmailNo(), record.getAppCode(), start) != 1) {
            return true;
        }
        try {
            EmailAccountDO account = accountMapper.selectById(record.getAccountId());
            if (account == null) {
                throw new IllegalStateException("email account not found");
            }
            properties.validateSmtpTimeoutBudget(account.getConnectTimeoutMs(), account.getReadTimeoutMs());
            String content = payloadCrypto.decrypt(record.getDeliveryContentCipher());
            smtpEmailSender.send(account, record, content, "HTML".equalsIgnoreCase(record.getContentType()));
        } catch (Exception exception) {
            LocalDateTime end = LocalDateTime.now();
            LocalDateTime nextRetryTime = end.plusSeconds(properties.calculateRetryDelaySeconds(record.getRetryCount()));
            recordMapper.markDeliveryFailure(record.getId(), record.getEmailNo(), record.getAppCode(), end,
                    nextRetryTime, elapsedMillis(start, end), SEND_FAILED_CODE,
                    EmailDeliveryFailureSummary.summarize(exception));
            return false;
        }
        LocalDateTime end = LocalDateTime.now();
        return recordMapper.markDeliverySuccess(record.getId(), record.getEmailNo(), record.getAppCode(),
                end, elapsedMillis(start, end)) == 1;
    }

    /** 仅接受带完整定位键的 Admin 消息，拒绝跨应用消费。 */
    private boolean isValid(EmailDeliveryMessage message) {
        return message != null
                && message.getRecordId() != null
                && StringUtils.hasText(message.getEmailNo())
                && APP_CODE.equalsIgnoreCase(message.getAppCode());
    }

    /** 将邮件记录转换为不含正文、地址和 SMTP 凭据的定位消息。 */
    private EmailDeliveryMessage toMessage(EmailSendRecordDO record) {
        EmailDeliveryMessage message = new EmailDeliveryMessage();
        message.setRecordId(record.getId());
        message.setEmailNo(record.getEmailNo());
        message.setAppCode(record.getAppCode());
        int retryCount = record.getRetryCount() == null ? 0 : Math.max(record.getRetryCount(), 0);
        message.setMessageId("email-admin-" + record.getId() + "-" + retryCount);
        return message;
    }

    /** 计算非负 SMTP 执行耗时。 */
    private long elapsedMillis(LocalDateTime start, LocalDateTime end) {
        return Math.max(Duration.between(start, end).toMillis(), 0L);
    }

}
