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
 * Email DTOs 聚合类型，用于运营后台，集中定义同一业务域下的请求、响应、查询条件和持久化视图模型。
 */
public final class EmailDTOs {

    /**
     * 阻止仅提供静态能力的类型被实例化。
     */
    private EmailDTOs() {
    }

    /**
     * Email Account Query 查询条件模型，用于运营后台，承载筛选字段、时间范围、分页参数和列表查询边界。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class EmailAccountQuery extends PageRequest {
        private String accountName;
        private String appCode;
        /**
         * 作用域类型，用于限定模板、配置、通知或权限记录适用的平台、商户或业务范围。
         */
        private String scopeType;
        /**
         * 商户号，用于限定商户配置、交易数据、风控规则和权限归属。
         */
        private String merchantId;
        private String merchantName;
        private String fromEmail;
        private String sceneCode;
        private Integer status;
        private Integer verifyStatus;
        private LocalDateTime createStartTime;
        private LocalDateTime createEndTime;
    }

    /**
     * 邮件账号写操作请求模型，用于运营后台，承载新增或编辑字段；权限、状态和唯一性由应用服务校验。
     */
    @Data
    public static class EmailAccountSaveRequest {
        private String accountCode;
        @NotBlank(message = "accountName is required")
        private String accountName;
        @NotBlank(message = "appCode is required")
        private String appCode;
        /**
         * 作用域类型，用于限定模板、配置、通知或权限记录适用的平台、商户或业务范围。
         */
        @NotBlank(message = "scopeType is required")
        private String scopeType;
        /**
         * 商户号，用于限定商户配置、交易数据、风控规则和权限归属。
         */
        private String merchantId;
        /**
         * 请求中的商户编号，用于限定本次操作的输入和校验范围。
         */
        private String merchantNo;
        private String merchantName;
        private String sceneCode;
        /**
         * 服务提供方类型，用于区分邮箱、短信、汇率或其它外部服务供应商。
         */
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
        /**
         * 加密类型，用于声明密钥材料、邮件通道或开放接口报文采用的加密方式。
         */
        @NotBlank(message = "encryptionType is required")
        private String encryptionType;
        private Integer smtpAuthRequired;
        @NotBlank(message = "smtpUsername is required")
        private String smtpUsername;
        private String smtpPassword;
        private Integer connectTimeoutMs;
        private Integer readTimeoutMs;
        /**
         * 请求中的默认标识，用于限定本次操作的输入和校验范围。
         */
        private Integer defaultFlag;
        private Integer status;
        private Integer minuteLimit;
        private Integer dailyLimit;
        private String remark;
        private Integer sortOrder;
    }

    /**
     * 邮件账号响应模型，用于运营后台，向调用方展示处理结果和必要业务事实，不暴露持久化实体。
     */
    @Data
    public static class EmailAccountResponse {
        private Long id;
        private String accountCode;
        private String accountName;
        private String appCode;
        /**
         * 作用域类型，用于限定模板、配置、通知或权限记录适用的平台、商户或业务范围。
         */
        private String scopeType;
        /**
         * 商户号，用于限定商户配置、交易数据、风控规则和权限归属。
         */
        private String merchantId;
        private String merchantNo;
        private String merchantName;
        private String sceneCode;
        /**
         * 服务提供方类型，用于区分邮箱、短信、汇率或其它外部服务供应商。
         */
        private String providerType;
        private String fromName;
        private String fromEmail;
        private String replyToEmail;
        private String smtpHost;
        private Integer smtpPort;
        /**
         * 加密类型，用于声明密钥材料、邮件通道或开放接口报文采用的加密方式。
         */
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
        /**
         * 记录创建人账号标识，用于操作审计。
         */
        private String createBy;
        /**
         * 记录创建时刻，持久化精度为毫秒。
         */
        private LocalDateTime createTime;
        /**
         * 记录最后更新人账号标识，用于操作审计。
         */
        private String updateBy;
        /**
         * 记录最后更新时间，持久化精度为毫秒。
         */
        private LocalDateTime updateTime;
    }

    /**
     * 邮件账号请求模型，用于运营后台，定义调用方必须提供或可选提供的字段，不直接执行业务逻辑。
     */
    @Data
    public static class EmailAccountTestRequest {
        @NotBlank(message = "toEmail is required")
        @Email(message = "toEmail format is invalid")
        private String toEmail;
        /**
         * 请求中的主题，用于限定本次操作的输入和校验范围。
         */
        private String subject;
        /**
         * 请求中的内容，用于限定本次操作的输入和校验范围。
         */
        private String content;
    }

    /**
     * Email Status Request 状态变更请求模型，用于运营后台，承载启停、冻结、审核或处理状态更新所需字段。
     */
    @Data
    public static class EmailStatusRequest {
        @NotNull(message = "status is required")
        private Integer status;
    }

    /**
     * Email Template Query 查询条件模型，用于运营后台，承载筛选字段、时间范围、分页参数和列表查询边界。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class EmailTemplateQuery extends PageRequest {
        private String templateName;
        private String templateCode;
        private String appCode;
        private String sceneCode;
        /**
         * 语言区域标识，用于选择国际化文案和导出标题。
         */
        private String locale;
        private Integer status;
        private Integer systemBuiltin;
    }

    /**
     * 邮件模板写操作请求模型，用于运营后台，承载新增或编辑字段；权限、状态和唯一性由应用服务校验。
     */
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
        /**
         * 语言区域标识，用于选择国际化文案和导出标题。
         */
        private String locale;
        @NotBlank(message = "subjectTemplate is required")
        private String subjectTemplate;
        /**
         * 内容类型，用于区分文本、HTML、附件、JSON 或导出文件等载荷格式。
         */
        private String contentType;
        @NotBlank(message = "contentTemplate is required")
        private String contentTemplate;
        private String variableSchema;
        private String sensitiveVariableNames;
        private Integer status;
        private String remark;
    }

    /**
     * 邮件模板响应模型，用于运营后台，向调用方展示处理结果和必要业务事实，不暴露持久化实体。
     */
    @Data
    public static class EmailTemplateResponse {
        private Long id;
        private String templateCode;
        private String templateName;
        private String appCode;
        private String sceneCode;
        /**
         * 语言区域标识，用于选择国际化文案和导出标题。
         */
        private String locale;
        private String subjectTemplate;
        /**
         * 内容类型，用于区分文本、HTML、附件、JSON 或导出文件等载荷格式。
         */
        private String contentType;
        private String contentTemplate;
        private String variableSchema;
        private String sensitiveVariableNames;
        private Integer status;
        private Integer systemBuiltin;
        /**
         * 业务版本号，用于区分同一配置或方案的不可变版本。
         */
        private Integer versionNo;
        private String remark;
        /**
         * 记录创建人账号标识，用于操作审计。
         */
        private String createBy;
        /**
         * 记录创建时刻，持久化精度为毫秒。
         */
        private LocalDateTime createTime;
        /**
         * 记录最后更新人账号标识，用于操作审计。
         */
        private String updateBy;
        /**
         * 记录最后更新时间，持久化精度为毫秒。
         */
        private LocalDateTime updateTime;
    }

    /**
     * 邮件模板预览请求模型，用于运营后台，定义调用方必须提供或可选提供的字段，不直接执行业务逻辑。
     */
    @Data
    public static class EmailTemplatePreviewRequest {
        @NotBlank(message = "subjectTemplate is required")
        private String subjectTemplate;
        @NotBlank(message = "contentTemplate is required")
        private String contentTemplate;
        private String sensitiveVariableNames;
        /**
         * 请求中的变量，用于限定本次操作的输入和校验范围。
         */
        private Map<String, Object> variables = new LinkedHashMap<>();
    }

    /**
     * 邮件模板预览响应模型，用于运营后台，向调用方展示处理结果和必要业务事实，不暴露持久化实体。
     */
    @Data
    public static class EmailTemplatePreviewResponse {
        private String subject;
        private String content;
        private String maskedContent;
        private List<String> missingVariables = new ArrayList<>();
    }

    /**
     * 邮件发送请求，承载模板、收件人、业务标识和模板变量。
     */
    @Data
    public static class EmailSendRequest {
        @NotBlank(message = "appCode is required")
        private String appCode;
        /**
         * 商户号，用于限定商户配置、交易数据、风控规则和权限归属。
         */
        private String merchantId;
        /**
         * 请求中的商户编号，用于限定本次操作的输入和校验范围。
         */
        private String merchantNo;
        private String merchantName;
        @NotBlank(message = "templateCode is required")
        private String templateCode;
        private String sceneCode;
        /**
         * 语言区域标识，用于选择国际化文案和导出标题。
         */
        private String locale;
        @NotEmpty(message = "toEmails is required")
        private List<String> toEmails = new ArrayList<>();
        private List<String> ccEmails = new ArrayList<>();
        private List<String> bccEmails = new ArrayList<>();
        /**
         * 请求中的变量，用于限定本次操作的输入和校验范围。
         */
        private Map<String, Object> variables = new LinkedHashMap<>();
        private String bizType;
        private String bizNo;
        /**
         * 重试次数，用于记录 MQ、任务或商户通知当前已执行的重试轮次。
         */
        private Integer maxRetryCount;
    }

    /**
     * 邮件批量发送响应中的单条任务结果，返回收件人摘要、记录号和初始投递状态。
     */
    @Data
    public static class EmailSendResult {
        private Long recordId;
        private String emailNo;
        private Integer sendStatus;
        private String errorCode;
        /**
         * 内部错误摘要，用于运营排障；禁止包含密钥、卡数据和完整报文。
         */
        private String errorMessage;
    }

    /**
     * Email Record Query 查询条件模型，用于运营后台，承载筛选字段、时间范围、分页参数和列表查询边界。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class EmailRecordQuery extends PageRequest {
        private String emailNo;
        private String appCode;
        /**
         * 商户号，用于限定商户配置、交易数据、风控规则和权限归属。
         */
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

    /**
     * 邮件记录响应模型，用于运营后台，向调用方展示处理结果和必要业务事实，不暴露持久化实体。
     */
    @Data
    public static class EmailRecordResponse {
        private Long id;
        private String emailNo;
        private String appCode;
        /**
         * 商户号，用于限定商户配置、交易数据、风控规则和权限归属。
         */
        private String merchantId;
        private String merchantNo;
        private String merchantName;
        private String sceneCode;
        private String templateCode;
        private String templateName;
        /**
         * 语言区域标识，用于选择国际化文案和导出标题。
         */
        private String locale;
        private Long accountId;
        private String accountCode;
        /**
         * 服务提供方类型，用于区分邮箱、短信、汇率或其它外部服务供应商。
         */
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
        /**
         * 重试次数，用于记录 MQ、任务或商户通知当前已执行的重试轮次。
         */
        private Integer retryCount;
        /**
         * 重试次数，用于记录 MQ、任务或商户通知当前已执行的重试轮次。
         */
        private Integer maxRetryCount;
        private LocalDateTime nextRetryTime;
        private LocalDateTime sendStartTime;
        private LocalDateTime sendEndTime;
        private LocalDateTime sendSuccessTime;
        private Long costMs;
        private String errorCode;
        /**
         * 内部错误摘要，用于运营排障；禁止包含密钥、卡数据和完整报文。
         */
        private String errorMessage;
        /**
         * 执行本次管理操作的可信登录账号 ID，用于操作审计。
         */
        private Long operatorId;
        /**
         * 执行本次管理操作时的账号显示名称快照，用于操作审计。
         */
        private String operatorName;
        /**
         * 记录创建人账号标识，用于操作审计。
         */
        private String createBy;
        /**
         * 记录创建时刻，持久化精度为毫秒。
         */
        private LocalDateTime createTime;
        /**
         * 记录最后更新人账号标识，用于操作审计。
         */
        private String updateBy;
        /**
         * 记录最后更新时间，持久化精度为毫秒。
         */
        private LocalDateTime updateTime;
    }
}
