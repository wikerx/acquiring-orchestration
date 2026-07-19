package com.scott.payment.merchant.entity.email;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantEmailEntities
 * @date : 2026-07-19 00:00
 * @email : scott_x@163.com
 * @description : 商户邮件发送实体集合，位于 service-merchant 数据层；复用管理系统邮件模板、发件账户和发送记录表支撑商户安全通知。
 * @status : create
 */
public final class MerchantEmailEntities {

    private MerchantEmailEntities() {
    }

    /**
     * 邮件发件账户配置实体。
     */
    @Data
    @TableName("msg_email_account")
    public static class MerchantEmailAccountDO {
        @TableId(type = IdType.AUTO)
        private Long id;
        private String accountCode;
        private String accountName;
        private String appCode;
        private String scopeType;
        private String merchantId;
        private String merchantNo;
        private String merchantName;
        private String sceneCode;
        private String providerType;
        private String fromName;
        private String fromEmail;
        private String replyToEmail;
        private String smtpHost;
        private Integer smtpPort;
        private String encryptionType;
        private Integer smtpAuthRequired;
        private String smtpUsername;
        private String smtpPasswordCipher;
        private Integer connectTimeoutMs;
        private Integer readTimeoutMs;
        private Integer defaultFlag;
        private Integer status;
        private Long deleted;
    }

    /**
     * 邮件模板实体。
     */
    @Data
    @TableName("msg_email_template")
    public static class MerchantEmailTemplateDO {
        @TableId(type = IdType.AUTO)
        private Long id;
        private String templateCode;
        private String templateName;
        private String appCode;
        private String sceneCode;
        private String locale;
        private String subjectTemplate;
        private String contentType;
        private String contentTemplate;
        private String sensitiveVariableNames;
        private Integer status;
        private Long deleted;
    }

    /**
     * 邮件发送记录实体。
     */
    @Data
    @TableName("msg_email_send_record")
    public static class MerchantEmailSendRecordDO {
        @TableId(type = IdType.AUTO)
        private Long id;
        private String emailNo;
        private String appCode;
        private String merchantId;
        private String merchantNo;
        private String merchantName;
        private String sceneCode;
        private String templateCode;
        private String templateName;
        private String locale;
        private Long accountId;
        private String accountCode;
        private String providerType;
        private String fromName;
        private String fromEmail;
        private String replyToEmail;
        private String toEmails;
        private String ccEmails;
        private String bccEmails;
        private String subject;
        private String contentSnapshot;
        private String variablesSnapshot;
        private String bizType;
        private String bizNo;
        private Integer sendStatus;
        private Integer retryCount;
        private Integer maxRetryCount;
        private LocalDateTime sendStartTime;
        private LocalDateTime sendEndTime;
        private LocalDateTime sendSuccessTime;
        private Long costMs;
        private String errorCode;
        private String errorMessage;
        private Long operatorId;
        private String operatorName;
        private String createBy;
        private LocalDateTime createTime;
        private String updateBy;
        private LocalDateTime updateTime;
        private Long deleted;
    }
}
