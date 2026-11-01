package com.scott.payment.merchant.entity.email;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商户邮件发送实体集合，位于 service-merchant 数据层；复用管理系统邮件模板、发件账户和发送记录表支撑商户安全通知。
 */
public final class MerchantEmailEntities {

    /**
     * 禁止实例化商户邮件实体聚合类。
     */
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
        /**
         * 作用域类型，用于限定模板、配置、通知或权限记录适用的平台、商户或业务范围。
         */
        private String scopeType;
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
        private Integer smtpPort;
        /**
         * 加密类型，用于声明密钥材料、邮件通道或开放接口报文采用的加密方式。
         */
        private String encryptionType;
        private Integer smtpAuthRequired;
        private String smtpUsername;
        private String smtpPasswordCipher;
        private Integer connectTimeoutMs;
        private Integer readTimeoutMs;
        /**
         * 持久化的默认标识，用于还原当前记录的业务事实。
         */
        private Integer defaultFlag;
        private Integer status;
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
    public static class MerchantEmailTemplateDO {
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
        /**
         * 主题模板，用于定位邮件、通知或渠道参数模板。
         */
        private String subjectTemplate;
        /**
         * 内容类型，用于区分文本、HTML、附件、JSON 或导出文件等载荷格式。
         */
        private String contentType;
        /**
         * 内容模板，用于定位邮件、通知或渠道参数模板。
         */
        private String contentTemplate;
        private String sensitiveVariableNames;
        private Integer status;
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
    public static class MerchantEmailSendRecordDO {
        @TableId(type = IdType.AUTO)
        private Long id;
        /**
         * 邮件编号，表示业务联系人或付款人的邮箱地址，展示和日志输出必须脱敏。
         */
        private String emailNo;
        private String appCode;
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
         * 下一次允许重试的业务时间。
         */
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
        private LocalDateTime createTime;
        /**
         * 记录最后更新人账号标识，用于操作审计。
         */
        private String updateBy;
        private LocalDateTime updateTime;
        /**
         * 逻辑删除标识；0 表示有效，1 表示已删除，查询必须沿用统一软删除口径。
         */
        private Long deleted;
    }
}
