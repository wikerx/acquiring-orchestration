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
 * @author : scott
 * @version : v1.0.0
 * @classname : EmailDTOs
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 邮件管理Email  DTO 集合，位于 service-admin 的接口传输层，用于说明职责边界、数据语义和关键业务约束。
 * @status : create
 */
public final class EmailDTOs {

    private EmailDTOs() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class EmailAccountQuery extends PageRequest {
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String accountName;
        /**
         * 邮件管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String appCode;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String scopeType;
        /**
         * 邮件管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private String merchantId;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String merchantName;
        /**
         * 邮件管理邮箱字段，需满足邮箱格式校验，日志展示时应按敏感信息处理。
         */
        private String fromEmail;
        /**
         * 邮件管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String sceneCode;
        /**
         * 邮件管理状态字段，取值需与数据字典或枚举约定保持一致。
         */
        private Integer status;
        /**
         * 邮件管理状态字段，取值需与数据字典或枚举约定保持一致。
         */
        private Integer verifyStatus;
        /**
         * 邮件管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime createStartTime;
        /**
         * 邮件管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime createEndTime;
    }

    @Data
    public static class EmailAccountSaveRequest {
        /**
         * 邮件管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String accountCode;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        @NotBlank(message = "accountName is required")
        private String accountName;
        /**
         * 邮件管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        @NotBlank(message = "appCode is required")
        private String appCode;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        @NotBlank(message = "scopeType is required")
        private String scopeType;
        /**
         * 邮件管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private String merchantId;
        /**
         * 邮件管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String merchantNo;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String merchantName;
        /**
         * 邮件管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String sceneCode;
        /**
         * 邮件管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private String providerType;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        @NotBlank(message = "fromName is required")
        private String fromName;
        /**
         * 邮件管理邮箱字段，需满足邮箱格式校验，日志展示时应按敏感信息处理。
         */
        @NotBlank(message = "fromEmail is required")
        @Email(message = "fromEmail format is invalid")
        private String fromEmail;
        /**
         * 邮件管理邮箱字段，需满足邮箱格式校验，日志展示时应按敏感信息处理。
         */
        @Email(message = "replyToEmail format is invalid")
        private String replyToEmail;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        @NotBlank(message = "smtpHost is required")
        private String smtpHost;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        @NotNull(message = "smtpPort is required")
        private Integer smtpPort;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        @NotBlank(message = "encryptionType is required")
        private String encryptionType;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Integer smtpAuthRequired;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        @NotBlank(message = "smtpUsername is required")
        private String smtpUsername;
        /**
         * 邮件管理敏感或密钥相关字段，日志和接口展示必须脱敏，必要时仅保存密文。
         */
        private String smtpPassword;
        /**
         * 邮件管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private Integer connectTimeoutMs;
        /**
         * 邮件管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private Integer readTimeoutMs;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Integer defaultFlag;
        /**
         * 邮件管理状态字段，取值需与数据字典或枚举约定保持一致。
         */
        private Integer status;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Integer minuteLimit;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Integer dailyLimit;
        /**
         * 邮件管理备注字段，用于记录人工说明，不参与核心状态流转。
         */
        private String remark;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Integer sortOrder;
    }

    @Data
    public static class EmailAccountResponse {
        /**
         * 邮件管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private Long id;
        /**
         * 邮件管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String accountCode;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String accountName;
        /**
         * 邮件管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String appCode;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String scopeType;
        /**
         * 邮件管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private String merchantId;
        /**
         * 邮件管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String merchantNo;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String merchantName;
        /**
         * 邮件管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String sceneCode;
        /**
         * 邮件管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private String providerType;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String fromName;
        /**
         * 邮件管理邮箱字段，需满足邮箱格式校验，日志展示时应按敏感信息处理。
         */
        private String fromEmail;
        /**
         * 邮件管理邮箱字段，需满足邮箱格式校验，日志展示时应按敏感信息处理。
         */
        private String replyToEmail;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String smtpHost;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Integer smtpPort;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String encryptionType;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Integer smtpAuthRequired;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String smtpUsername;
        /**
         * 邮件管理敏感或密钥相关字段，日志和接口展示必须脱敏，必要时仅保存密文。
         */
        private Integer passwordConfigured;
        /**
         * 邮件管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime passwordUpdatedTime;
        /**
         * 邮件管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private Integer connectTimeoutMs;
        /**
         * 邮件管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private Integer readTimeoutMs;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Integer defaultFlag;
        /**
         * 邮件管理状态字段，取值需与数据字典或枚举约定保持一致。
         */
        private Integer status;
        /**
         * 邮件管理状态字段，取值需与数据字典或枚举约定保持一致。
         */
        private Integer verifyStatus;
        /**
         * 邮件管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime lastTestTime;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String lastErrorMessage;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Integer minuteLimit;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Integer dailyLimit;
        /**
         * 邮件管理备注字段，用于记录人工说明，不参与核心状态流转。
         */
        private String remark;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Integer sortOrder;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String createBy;
        /**
         * 邮件管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime createTime;
        /**
         * 邮件管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private String updateBy;
        /**
         * 邮件管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime updateTime;
    }

    @Data
    public static class EmailAccountTestRequest {
        /**
         * 邮件管理邮箱字段，需满足邮箱格式校验，日志展示时应按敏感信息处理。
         */
        @NotBlank(message = "toEmail is required")
        @Email(message = "toEmail format is invalid")
        private String toEmail;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String subject;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String content;
    }

    @Data
    public static class EmailStatusRequest {
        /**
         * 邮件管理状态字段，取值需与数据字典或枚举约定保持一致。
         */
        @NotNull(message = "status is required")
        private Integer status;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class EmailTemplateQuery extends PageRequest {
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String templateName;
        /**
         * 邮件管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String templateCode;
        /**
         * 邮件管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String appCode;
        /**
         * 邮件管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String sceneCode;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String locale;
        /**
         * 邮件管理状态字段，取值需与数据字典或枚举约定保持一致。
         */
        private Integer status;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Integer systemBuiltin;
    }

    @Data
    public static class EmailTemplateSaveRequest {
        /**
         * 邮件管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        @NotBlank(message = "templateCode is required")
        private String templateCode;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        @NotBlank(message = "templateName is required")
        private String templateName;
        /**
         * 邮件管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        @NotBlank(message = "appCode is required")
        private String appCode;
        /**
         * 邮件管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        @NotBlank(message = "sceneCode is required")
        private String sceneCode;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String locale;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        @NotBlank(message = "subjectTemplate is required")
        private String subjectTemplate;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String contentType;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        @NotBlank(message = "contentTemplate is required")
        private String contentTemplate;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String variableSchema;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String sensitiveVariableNames;
        /**
         * 邮件管理状态字段，取值需与数据字典或枚举约定保持一致。
         */
        private Integer status;
        /**
         * 邮件管理备注字段，用于记录人工说明，不参与核心状态流转。
         */
        private String remark;
    }

    @Data
    public static class EmailTemplateResponse {
        /**
         * 邮件管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private Long id;
        /**
         * 邮件管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String templateCode;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String templateName;
        /**
         * 邮件管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String appCode;
        /**
         * 邮件管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String sceneCode;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String locale;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String subjectTemplate;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String contentType;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String contentTemplate;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String variableSchema;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String sensitiveVariableNames;
        /**
         * 邮件管理状态字段，取值需与数据字典或枚举约定保持一致。
         */
        private Integer status;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Integer systemBuiltin;
        /**
         * 邮件管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private Integer versionNo;
        /**
         * 邮件管理备注字段，用于记录人工说明，不参与核心状态流转。
         */
        private String remark;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String createBy;
        /**
         * 邮件管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime createTime;
        /**
         * 邮件管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private String updateBy;
        /**
         * 邮件管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime updateTime;
    }

    @Data
    public static class EmailTemplatePreviewRequest {
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        @NotBlank(message = "subjectTemplate is required")
        private String subjectTemplate;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        @NotBlank(message = "contentTemplate is required")
        private String contentTemplate;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String sensitiveVariableNames;
        private Map<String, Object> variables = new LinkedHashMap<>();
    }

    @Data
    public static class EmailTemplatePreviewResponse {
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String subject;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String content;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String maskedContent;
        private List<String> missingVariables = new ArrayList<>();
    }

    @Data
    public static class EmailSendRequest {
        /**
         * 邮件管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        @NotBlank(message = "appCode is required")
        private String appCode;
        /**
         * 邮件管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private String merchantId;
        /**
         * 邮件管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String merchantNo;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String merchantName;
        /**
         * 邮件管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        @NotBlank(message = "templateCode is required")
        private String templateCode;
        /**
         * 邮件管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String sceneCode;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String locale;
        @NotEmpty(message = "toEmails is required")
        private List<String> toEmails = new ArrayList<>();
        private List<String> ccEmails = new ArrayList<>();
        private List<String> bccEmails = new ArrayList<>();
        private Map<String, Object> variables = new LinkedHashMap<>();
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String bizType;
        /**
         * 邮件管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String bizNo;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Integer maxRetryCount;
    }

    @Data
    public static class EmailSendResult {
        /**
         * 邮件管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private Long recordId;
        /**
         * 邮件管理邮箱字段，需满足邮箱格式校验，日志展示时应按敏感信息处理。
         */
        private String emailNo;
        /**
         * 邮件管理状态字段，取值需与数据字典或枚举约定保持一致。
         */
        private Integer sendStatus;
        /**
         * 邮件管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String errorCode;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String errorMessage;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class EmailRecordQuery extends PageRequest {
        /**
         * 邮件管理邮箱字段，需满足邮箱格式校验，日志展示时应按敏感信息处理。
         */
        private String emailNo;
        /**
         * 邮件管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String appCode;
        /**
         * 邮件管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private String merchantId;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String merchantName;
        /**
         * 邮件管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String sceneCode;
        /**
         * 邮件管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String templateCode;
        /**
         * 邮件管理邮箱字段，需满足邮箱格式校验，日志展示时应按敏感信息处理。
         */
        private String toEmail;
        /**
         * 邮件管理状态字段，取值需与数据字典或枚举约定保持一致。
         */
        private Integer sendStatus;
        /**
         * 邮件管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String bizNo;
        /**
         * 邮件管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime createStartTime;
        /**
         * 邮件管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime createEndTime;
        /**
         * 邮件管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime sendStartTime;
        /**
         * 邮件管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime sendEndTime;
    }

    @Data
    public static class EmailRecordResponse {
        /**
         * 邮件管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private Long id;
        /**
         * 邮件管理邮箱字段，需满足邮箱格式校验，日志展示时应按敏感信息处理。
         */
        private String emailNo;
        /**
         * 邮件管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String appCode;
        /**
         * 邮件管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private String merchantId;
        /**
         * 邮件管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String merchantNo;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String merchantName;
        /**
         * 邮件管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String sceneCode;
        /**
         * 邮件管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String templateCode;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String templateName;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String locale;
        /**
         * 邮件管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private Long accountId;
        /**
         * 邮件管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String accountCode;
        /**
         * 邮件管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private String providerType;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String fromName;
        /**
         * 邮件管理邮箱字段，需满足邮箱格式校验，日志展示时应按敏感信息处理。
         */
        private String fromEmail;
        /**
         * 邮件管理邮箱字段，需满足邮箱格式校验，日志展示时应按敏感信息处理。
         */
        private String replyToEmail;
        /**
         * 邮件管理邮箱字段，需满足邮箱格式校验，日志展示时应按敏感信息处理。
         */
        private String toEmails;
        /**
         * 邮件管理邮箱字段，需满足邮箱格式校验，日志展示时应按敏感信息处理。
         */
        private String ccEmails;
        /**
         * 邮件管理邮箱字段，需满足邮箱格式校验，日志展示时应按敏感信息处理。
         */
        private String bccEmails;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String subject;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String contentSnapshot;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String variablesSnapshot;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String bizType;
        /**
         * 邮件管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String bizNo;
        /**
         * 邮件管理状态字段，取值需与数据字典或枚举约定保持一致。
         */
        private Integer sendStatus;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Integer retryCount;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Integer maxRetryCount;
        /**
         * 邮件管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime nextRetryTime;
        /**
         * 邮件管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime sendStartTime;
        /**
         * 邮件管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime sendEndTime;
        /**
         * 邮件管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime sendSuccessTime;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Long costMs;
        /**
         * 邮件管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String errorCode;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String errorMessage;
        /**
         * 邮件管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private Long operatorId;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String operatorName;
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String createBy;
        /**
         * 邮件管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime createTime;
        /**
         * 邮件管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private String updateBy;
        /**
         * 邮件管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime updateTime;
    }
}
