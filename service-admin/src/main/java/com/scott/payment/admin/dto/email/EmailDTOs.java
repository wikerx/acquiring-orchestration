package com.scott.payment.admin.dto.email;

import com.scott.payment.component.core.model.PageRequest;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 邮件管理请求和响应 DTO 集合。
 *
 * <p>用于管理端邮件账户、模板和发送记录接口，避免数据库实体直接暴露给前端。</p>
 */
public final class EmailDTOs {

    private EmailDTOs() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class EmailAccountQuery extends PageRequest {
        private String accountName;
        private String appCode;
        private String scopeType;
        private String merchantId;
        private String merchantName;
        private String fromEmail;
        private String sceneCode;
        private Integer status;
        private Integer verifyStatus;
        private LocalDateTime createStartTime;
        private LocalDateTime createEndTime;
    }

    @Data
    public static class EmailAccountSaveRequest {
        private String accountCode;
        @NotBlank(message = "accountName is required")
        private String accountName;
        @NotBlank(message = "appCode is required")
        private String appCode;
        @NotBlank(message = "scopeType is required")
        private String scopeType;
        private String merchantId;
        private String merchantNo;
        private String merchantName;
        private String sceneCode;
        private String providerType;
        @NotBlank(message = "fromName is required")
        private String fromName;
        @NotBlank(message = "fromEmail is required")
        @Email(message = "fromEmail format is invalid")
        private String fromEmail;
        @Email(message = "replyToEmail format is invalid")
        private String replyToEmail;
        @NotBlank(message = "smtpHost is required")
        private String smtpHost;
        @NotNull(message = "smtpPort is required")
        private Integer smtpPort;
        @NotBlank(message = "encryptionType is required")
        private String encryptionType;
        private Integer smtpAuthRequired;
        @NotBlank(message = "smtpUsername is required")
        private String smtpUsername;
        private String smtpPassword;
        private Integer connectTimeoutMs;
        private Integer readTimeoutMs;
        private Integer defaultFlag;
        private Integer status;
        private Integer minuteLimit;
        private Integer dailyLimit;
        private String remark;
        private Integer sortOrder;
    }

    @Data
    public static class EmailAccountResponse {
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
        private Integer passwordConfigured;
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
    }

    @Data
    public static class EmailAccountTestRequest {
        @NotBlank(message = "toEmail is required")
        @Email(message = "toEmail format is invalid")
        private String toEmail;
        private String subject;
        private String content;
    }

    @Data
    public static class EmailStatusRequest {
        @NotNull(message = "status is required")
        private Integer status;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class EmailTemplateQuery extends PageRequest {
        private String templateName;
        private String templateCode;
        private String appCode;
        private String sceneCode;
        private String locale;
        private Integer status;
        private Integer systemBuiltin;
    }

    @Data
    public static class EmailTemplateSaveRequest {
        @NotBlank(message = "templateCode is required")
        private String templateCode;
        @NotBlank(message = "templateName is required")
        private String templateName;
        @NotBlank(message = "appCode is required")
        private String appCode;
        @NotBlank(message = "sceneCode is required")
        private String sceneCode;
        private String locale;
        @NotBlank(message = "subjectTemplate is required")
        private String subjectTemplate;
        private String contentType;
        @NotBlank(message = "contentTemplate is required")
        private String contentTemplate;
        private String variableSchema;
        private String sensitiveVariableNames;
        private Integer status;
        private String remark;
    }

    @Data
    public static class EmailTemplateResponse {
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
    }

    @Data
    public static class EmailTemplatePreviewRequest {
        @NotBlank(message = "subjectTemplate is required")
        private String subjectTemplate;
        @NotBlank(message = "contentTemplate is required")
        private String contentTemplate;
        private String sensitiveVariableNames;
        private Map<String, Object> variables = new LinkedHashMap<>();
    }

    @Data
    public static class EmailTemplatePreviewResponse {
        private String subject;
        private String content;
        private String maskedContent;
        private List<String> missingVariables = new ArrayList<>();
    }

    @Data
    public static class EmailSendRequest {
        @NotBlank(message = "appCode is required")
        private String appCode;
        private String merchantId;
        private String merchantNo;
        private String merchantName;
        @NotBlank(message = "templateCode is required")
        private String templateCode;
        private String sceneCode;
        private String locale;
        @NotEmpty(message = "toEmails is required")
        private List<String> toEmails = new ArrayList<>();
        private List<String> ccEmails = new ArrayList<>();
        private List<String> bccEmails = new ArrayList<>();
        private Map<String, Object> variables = new LinkedHashMap<>();
        private String bizType;
        private String bizNo;
        private Integer maxRetryCount;
    }

    @Data
    public static class EmailSendResult {
        private Long recordId;
        private String emailNo;
        private Integer sendStatus;
        private String errorCode;
        private String errorMessage;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class EmailRecordQuery extends PageRequest {
        private String emailNo;
        private String appCode;
        private String merchantId;
        private String merchantName;
        private String sceneCode;
        private String templateCode;
        private String toEmail;
        private Integer sendStatus;
        private String bizNo;
        private LocalDateTime createStartTime;
        private LocalDateTime createEndTime;
        private LocalDateTime sendStartTime;
        private LocalDateTime sendEndTime;
    }

    @Data
    public static class EmailRecordResponse {
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
    }
}
