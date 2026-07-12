package com.scott.payment.admin.entity.email;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : EmailEntities
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 邮件管理Email 实体集合，位于 service-admin 的数据实体层，用于说明职责边界、数据语义和关键业务约束。
 * @status : create
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
        /**
         * 邮件管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        @TableId(type = IdType.AUTO)
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
        private String smtpPasswordCipher;
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
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Long deleted;
    }

    /**
     * 邮件模板实体。
     */
    @Data
    @TableName("msg_email_template")
    public static class EmailTemplateDO {
        /**
         * 邮件管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        @TableId(type = IdType.AUTO)
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
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Long deleted;
    }

    /**
     * 邮件发送记录实体。
     */
    @Data
    @TableName("msg_email_send_record")
    public static class EmailSendRecordDO {
        /**
         * 邮件管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        @TableId(type = IdType.AUTO)
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
        /**
         * 邮件管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Long deleted;
    }
}
