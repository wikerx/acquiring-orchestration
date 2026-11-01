package com.scott.payment.admin.entity.email;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Email Entities 聚合类型，用于运营后台，集中定义同一业务域下的请求、响应、查询条件和持久化视图模型。
 */
public final class EmailEntities {

    /**
     * 阻止仅提供静态能力的类型被实例化。
     */
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
        /**
         * 作用域类型，用于限定模板、配置、通知或权限记录适用的平台、商户或业务范围。
         */
        private String scopeType;
        /**
         * 商户号，用于限定商户配置、交易数据、风控规则和权限归属。
         */
        private String merchantId;
        /**
         * 持久化的商户编号，用于还原当前记录的业务事实。
         */
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
        /**
         * 持久化的{@code smtpPort}，用于还原当前记录的业务事实。
         */
        private Integer smtpPort;
        /**
         * 加密类型，用于声明密钥材料、邮件通道或开放接口报文采用的加密方式。
         */
        private String encryptionType;
        /**
         * 持久化的{@code smtpAuthRequired}，用于还原当前记录的业务事实。
         */
        private Integer smtpAuthRequired;
        private String smtpUsername;
        private String smtpPasswordCipher;
        /**
         * 持久化的{@code passwordUpdatedTime}，用于还原当前记录的业务事实。
         */
        private LocalDateTime passwordUpdatedTime;
        /**
         * 持久化的{@code connectTimeoutMs}，用于还原当前记录的业务事实。
         */
        private Integer connectTimeoutMs;
        /**
         * 持久化的{@code readTimeoutMs}，用于还原当前记录的业务事实。
         */
        private Integer readTimeoutMs;
        /**
         * 持久化的默认标识，用于还原当前记录的业务事实。
         */
        private Integer defaultFlag;
        private Integer status;
        private Integer verifyStatus;
        /**
         * 持久化的{@code lastTestTime}，用于还原当前记录的业务事实。
         */
        private LocalDateTime lastTestTime;
        /**
         * 持久化的{@code lastErrorMessage}，用于还原当前记录的业务事实。
         */
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
        /**
         * 逻辑删除标识；0 表示有效，1 表示已删除，查询必须沿用统一软删除口径。
         */
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
        /**
         * 持久化的{@code variableSchema}，用于还原当前记录的业务事实。
         */
        private String variableSchema;
        private String sensitiveVariableNames;
        private Integer status;
        /**
         * 持久化的{@code systemBuiltin}，用于还原当前记录的业务事实。
         */
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
        /**
         * 逻辑删除标识；0 表示有效，1 表示已删除，查询必须沿用统一软删除口径。
         */
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
        /**
         * 商户号，用于限定商户配置、交易数据、风控规则和权限归属。
         */
        private String merchantId;
        /**
         * 持久化的商户编号，用于还原当前记录的业务事实。
         */
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
        /**
         * 持久化的主题，用于还原当前记录的业务事实。
         */
        private String subject;
        /**
         * 持久化的内容快照，用于还原当前记录的业务事实。
         */
        private String contentSnapshot;
        /**
         * 加密保存的实际投递正文，仅在消费者发送成功前保留。
         */
        private String deliveryContentCipher;
        /**
         * 实际投递正文类型，取值为 HTML 或 TEXT。
         */
        private String contentType;
        /**
         * 持久化的变量快照，用于还原当前记录的业务事实。
         */
        private String variablesSnapshot;
        private String bizType;
        /**
         * 持久化的{@code bizNo}，用于还原当前记录的业务事实。
         */
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
        /**
         * 持久化的{@code nextRetryTime}，用于还原当前记录的业务事实。
         */
        private LocalDateTime nextRetryTime;
        /**
         * 持久化的{@code sendStartTime}，用于还原当前记录的业务事实。
         */
        private LocalDateTime sendStartTime;
        /**
         * 持久化的{@code sendEndTime}，用于还原当前记录的业务事实。
         */
        private LocalDateTime sendEndTime;
        /**
         * 持久化的{@code sendSuccessTime}，用于还原当前记录的业务事实。
         */
        private LocalDateTime sendSuccessTime;
        /**
         * 持久化的{@code costMs}，用于还原当前记录的业务事实。
         */
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
        /**
         * 逻辑删除标识；0 表示有效，1 表示已删除，查询必须沿用统一软删除口径。
         */
        private Long deleted;
    }
}
