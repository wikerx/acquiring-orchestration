package com.scott.payment.merchant.service.impl;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.mq.email.EmailDeliveryFailureSummary;
import com.scott.payment.component.mq.email.EmailPayloadCrypto;
import com.scott.payment.merchant.entity.email.MerchantEmailEntities.MerchantEmailAccountDO;
import com.scott.payment.merchant.entity.email.MerchantEmailEntities.MerchantEmailSendRecordDO;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantSmtpEmailSender
 * @date : 2026-08-02 23:40
 * @email : scott_x@163.com
 * @description : Merchant SMTP 外部系统适配器，只发送冻结记录，不负责投递状态流转或重试决策
 * @status : create
 */
@Component
public class MerchantSmtpEmailSender {

    /** SMTP 密码解密组件，运行时密钥缺失时拒绝发送。 */
    private final EmailPayloadCrypto payloadCrypto;

    /** 创建 Merchant SMTP 适配器。 */
    public MerchantSmtpEmailSender(EmailPayloadCrypto payloadCrypto) {
        this.payloadCrypto = payloadCrypto;
    }

    /** 将已解密正文发送到记录冻结的商户收件地址。 */
    public void send(MerchantEmailAccountDO account,
                     MerchantEmailSendRecordDO record,
                     String content,
                     boolean html) {
        try {
            JavaMailSenderImpl sender = buildMailSender(account);
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(account.getFromEmail(), account.getFromName());
            if (StringUtils.hasText(account.getReplyToEmail())) {
                helper.setReplyTo(account.getReplyToEmail());
            }
            helper.setTo(parseEmailArray(record.getToEmails()));
            helper.setSubject(record.getSubject());
            helper.setText(content, html);
            sender.send(message);
        } catch (Exception exception) {
            throw new IllegalStateException(EmailDeliveryFailureSummary.summarize(exception), exception);
        }
    }

    /** 根据冻结账户配置创建单次使用的 SMTP 客户端。 */
    private JavaMailSenderImpl buildMailSender(MerchantEmailAccountDO account) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(account.getSmtpHost());
        sender.setPort(account.getSmtpPort());
        sender.setUsername(account.getSmtpUsername());
        if (Integer.valueOf(1).equals(account.getSmtpAuthRequired())) {
            sender.setPassword(payloadCrypto.decrypt(account.getSmtpPasswordCipher()));
        }
        Properties properties = sender.getJavaMailProperties();
        properties.put("mail.smtp.auth", String.valueOf(Integer.valueOf(1).equals(account.getSmtpAuthRequired())));
        properties.put("mail.smtp.connectiontimeout", String.valueOf(account.getConnectTimeoutMs()));
        properties.put("mail.smtp.timeout", String.valueOf(account.getReadTimeoutMs()));
        properties.put("mail.smtp.writetimeout", String.valueOf(account.getReadTimeoutMs()));
        if ("SSL".equals(account.getEncryptionType()) || "TLS".equals(account.getEncryptionType())) {
            properties.put("mail.smtp.ssl.enable", "true");
        } else if ("STARTTLS".equals(account.getEncryptionType())) {
            properties.put("mail.smtp.starttls.enable", "true");
            properties.put("mail.smtp.starttls.required", "true");
        }
        return sender;
    }

    /** 将数据库 JSON 地址数组解析为 JavaMail 接收的字符串数组。 */
    private String[] parseEmailArray(String value) {
        if (!StringUtils.hasText(value)) {
            return new String[0];
        }
        List<String> emails = JsonUtils.parseArray(value, String.class);
        return emails.stream().filter(StringUtils::hasText).map(String::trim).toArray(String[]::new);
    }

}
