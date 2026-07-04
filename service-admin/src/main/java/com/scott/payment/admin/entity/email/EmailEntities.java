package com.scott.payment.admin.entity.email;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 邮件管理数据库实体集合。
 *
 * <p>承载管理后台发件账户、邮件模板和发送记录数据，不在实体层处理 SMTP 发送或模板渲染。</p>
 */
public final class EmailEntities {

    private EmailEntities() {
    }

    /**
     * 邮件发件账户配置实体。
     */
    @Data
    @TableName("msg_email_account")
    public static class EmailAccountDO {
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
        private LocalDateTime passwordUpdatedTime;
        private Integer connectTimeoutMs;
        private Integer readTimeoutMs;
        private Integer defaultFlag;
        private Integer status;
        private Integer verifyStatus;
        private LocalDateTime lastTestTime;
        private String lastErrorMessage;
        private Integer minuteLimit;
        private Integer dailyLimit;
        private String remark;
        private Integer sortOrder;
        private String createBy;
        private LocalDateTime createTime;
        private String updateBy;
        private LocalDateTime updateTime;
        private Long deleted;
    }

    /**
     * 邮件模板实体。
     */
    @Data
    @TableName("msg_email_template")
    public static class EmailTemplateDO {
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
        private String variableSchema;
        private String sensitiveVariableNames;
        private Integer status;
        private Integer systemBuiltin;
        private Integer versionNo;
        private String remark;
        private String createBy;
        private LocalDateTime createTime;
        private String updateBy;
        private LocalDateTime updateTime;
        private Long deleted;
    }

    /**
     * 邮件发送记录实体。
     */
    @Data
    @TableName("msg_email_send_record")
    public static class EmailSendRecordDO {
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
        private LocalDateTime nextRetryTime;
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
