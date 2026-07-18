package com.scott.payment.admin.dto.channel;

import com.scott.payment.component.core.model.PageRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelAlertDTOs
 * @date : 2026-07-17 00:00
 * @email : scott_x@163.com
 * @description : 渠道预警管理 DTO 集合，位于 service-admin 接口传输层，仅承载后台规则配置、事件查询和邮件通知配置。
 * @status : create
 */
public final class ChannelAlertDTOs {

    private ChannelAlertDTOs() {
    }

    /**
     * 渠道预警规则分页查询条件。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class ChannelAlertRuleQuery extends PageRequest {
        /**
         * 渠道 ID，关联 channel_info.id。
         */
        private Long channelId;
        /**
         * 渠道编码，后台模糊筛选和事件排查使用。
         */
        private String channelCode;
        /**
         * 业务类型：ACQUIRING/PAYOUT。
         */
        private String businessType;
        /**
         * 支付方式，如 BANK_CARD、APPLE_PAY；ALL 表示全部支付方式。
         */
        private String paymentMethod;
        /**
         * 卡品牌，如 VISA、MASTERCARD；ALL 表示全部卡品牌。
         */
        private String cardBrand;
        /**
         * 规则类型，如 CONTINUOUS_FAILURE、SUCCESS_RATE_LOW。
         */
        private String ruleType;
        /**
         * 预警级别：L1_WARNING、L2_DEGRADED、L3_CIRCUIT_BREAK。
         */
        private String alertLevel;
        /**
         * 规则状态：0停用，1启用。
         */
        private Integer ruleStatus;
        /**
         * 规则名称模糊查询。
         */
        private String ruleName;
    }

    /**
     * 渠道预警规则保存请求。
     */
    @Data
    public static class ChannelAlertRuleSaveRequest {
        /**
         * 规则名称，用于后台识别和通知标题。
         */
        @NotBlank(message = "ruleName is required")
        private String ruleName;
        /**
         * 渠道 ID，关联 channel_info.id。
         */
        @NotNull(message = "channelId is required")
        private Long channelId;
        /**
         * 业务类型：ACQUIRING/PAYOUT。
         */
        @NotBlank(message = "businessType is required")
        private String businessType;
        /**
         * 支付方式，如 BANK_CARD；ALL 表示全部。
         */
        private String paymentMethod;
        /**
         * 卡品牌，如 VISA；ALL 表示全部。
         */
        private String cardBrand;
        /**
         * 规则类型。
         */
        @NotBlank(message = "ruleType is required")
        private String ruleType;
        /**
         * 时间窗口分钟数。
         */
        @NotNull(message = "windowMinutes is required")
        private Integer windowMinutes;
        /**
         * 连续失败类规则阈值笔数。
         */
        private Integer thresholdCount;
        /**
         * 成功率或异常比例类规则阈值，按百分比保存。
         */
        private BigDecimal thresholdRate;
        /**
         * 延迟类规则阈值，单位毫秒。
         */
        private Integer thresholdMillis;
        /**
         * 最小样本数，避免小样本触发比例类规则。
         */
        private Integer minimumSampleCount;
        /**
         * 预警级别。
         */
        @NotBlank(message = "alertLevel is required")
        private String alertLevel;
        /**
         * 规则说明，进入事件快照和邮件变量。
         */
        private String ruleDescription;
        /**
         * 是否自动降级：0否，1是；当前仅保存配置，不影响路由。
         */
        private Integer autoDegrade;
        /**
         * 是否自动熔断：0否，1是；当前仅保存配置，不影响路由。
         */
        private Integer autoCircuitBreak;
        /**
         * 规则状态：0停用，1启用。
         */
        private Integer ruleStatus;
        /**
         * 邮件收件人，多个邮箱逗号分隔。
         */
        @NotBlank(message = "emailRecipients is required")
        private String emailRecipients;
        /**
         * 邮件抄送人，多个邮箱逗号分隔。
         */
        private String emailCc;
        /**
         * 邮件模板编码，可为空时使用后续默认模板。
         */
        private String emailTemplateCode;
        /**
         * 邮件场景编码，可为空时使用 CHANNEL_ALERT。
         */
        private String emailSceneCode;
        /**
         * 备注。
         */
        private String remark;
    }

    /**
     * 渠道预警规则批量保存请求。
     */
    @Data
    public static class ChannelAlertRuleBatchSaveRequest {
        /**
         * 规则名称，同一批渠道预警规则共用。
         */
        @NotBlank(message = "ruleName is required")
        private String ruleName;
        /**
         * 渠道 ID，关联 channel_info.id。
         */
        @NotNull(message = "channelId is required")
        private Long channelId;
        /**
         * 业务类型：ACQUIRING/PAYOUT。
         */
        @NotBlank(message = "businessType is required")
        private String businessType;
        /**
         * 支付方式，如 BANK_CARD；ALL 表示全部。
         */
        @NotBlank(message = "paymentMethod is required")
        private String paymentMethod;
        /**
         * 卡品牌集合，卡支付可多选；非卡支付或全量使用 ALL。
         */
        private List<String> cardBrands = new ArrayList<>();
        /**
         * 规则配置集合，按规则类型展开为多条规则。
         */
        private List<ChannelAlertRuleItem> rules = new ArrayList<>();
        /**
         * 邮件通知配置。
         */
        @NotBlank(message = "notifyType is required")
        private String notifyType;
        /**
         * 邮件收件人，多个邮箱逗号分隔。
         */
        @NotBlank(message = "emailRecipients is required")
        private String emailRecipients;
        /**
         * 邮件抄送人，多个邮箱逗号分隔。
         */
        private String emailCc;
        /**
         * 邮件模板编码。
         */
        private String emailTemplateCode;
        /**
         * 邮件场景编码。
         */
        private String emailSceneCode;
        /**
         * 规则状态：0停用，1启用。
         */
        private Integer ruleStatus;
        /**
         * 备注。
         */
        private String remark;
    }

    /**
     * 渠道预警规则批量更新请求。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class ChannelAlertRuleDimensionSaveRequest extends ChannelAlertRuleBatchSaveRequest {
        /**
         * 原始维度内保留的规则 ID，用于编辑时删除未继续保留的旧规则。
         */
        private List<Long> retainedRuleIds = new ArrayList<>();
    }

    /**
     * 渠道预警单个规则配置项。
     */
    @Data
    public static class ChannelAlertRuleItem {
        /**
         * 已存在规则 ID，新增时为空。
         */
        private Long id;
        /**
         * 规则名称兼容字段，批量保存时以后外层公共规则名称为准。
         */
        private String ruleName;
        /**
         * 规则类型。
         */
        @NotBlank(message = "ruleType is required")
        private String ruleType;
        /**
         * 时间窗口分钟数，页面可按分钟、小时、天、周、月换算。
         */
        @NotNull(message = "windowMinutes is required")
        private Integer windowMinutes;
        /**
         * 连续失败类规则阈值笔数。
         */
        private Integer thresholdCount;
        /**
         * 成功率或异常比例类规则阈值，按百分比保存。
         */
        private BigDecimal thresholdRate;
        /**
         * 延迟类规则阈值，单位毫秒。
         */
        private Integer thresholdMillis;
        /**
         * 最小样本数，避免小样本触发比例类规则。
         */
        private Integer minimumSampleCount;
        /**
         * 预警级别。
         */
        @NotBlank(message = "alertLevel is required")
        private String alertLevel;
        /**
         * 规则说明，进入事件快照和邮件变量。
         */
        private String ruleDescription;
        /**
         * 是否自动降级：0否，1是；当前仅保存配置，不影响路由。
         */
        private Integer autoDegrade;
        /**
         * 是否自动熔断：0否，1是；当前仅保存配置，不影响路由。
         */
        private Integer autoCircuitBreak;
    }

    /**
     * 渠道预警规则维度详情。
     */
    @Data
    public static class ChannelAlertRuleDimensionResponse {
        private String ruleName;
        private Long channelId;
        private String channelCode;
        private String channelName;
        private String businessType;
        private String paymentMethod;
        private List<String> cardBrands = new ArrayList<>();
        private List<ChannelAlertRuleResponse> rules = new ArrayList<>();
        private String notifyType;
        private String emailRecipients;
        private String emailCc;
        private String emailTemplateCode;
        private String emailSceneCode;
        private Integer ruleStatus;
        private String remark;
    }

    /**
     * 渠道预警规则下拉选项响应。
     */
    @Data
    public static class ChannelAlertRuleOptionsResponse {
        private List<String> businessTypes = new ArrayList<>();
        private List<PaymentMethodOption> paymentMethods = new ArrayList<>();
        private List<String> cardBrands = new ArrayList<>();
        private List<UserEmailOption> userEmails = new ArrayList<>();
        private List<EmailTemplateOption> emailTemplates = new ArrayList<>();
        private List<String> emailSceneCodes = new ArrayList<>();
    }

    /**
     * 支付方式选项。
     */
    @Data
    public static class PaymentMethodOption {
        private String businessType;
        private String paymentMethod;
        private List<String> cardBrands = new ArrayList<>();
    }

    /**
     * 后台用户邮箱选项。
     */
    @Data
    public static class UserEmailOption {
        private Long accountId;
        private String loginAccount;
        private String realName;
        private String email;
    }

    /**
     * 邮件模板选项。
     */
    @Data
    public static class EmailTemplateOption {
        private Long id;
        private String templateCode;
        private String templateName;
        private String sceneCode;
        private String locale;
    }

    /**
     * 渠道预警规则响应。
     */
    @Data
    public static class ChannelAlertRuleResponse {
        private Long id;
        private String ruleCode;
        private String ruleGroupCode;
        private String ruleName;
        private Long channelId;
        private String channelCode;
        private String channelName;
        private String businessType;
        private String paymentMethod;
        private String cardBrand;
        private String ruleType;
        private Integer windowMinutes;
        private Integer thresholdCount;
        private BigDecimal thresholdRate;
        private Integer thresholdMillis;
        private Integer minimumSampleCount;
        private String alertLevel;
        private String ruleDescription;
        private Integer autoDegrade;
        private Integer autoCircuitBreak;
        private Integer ruleStatus;
        private String notifyType;
        private String emailRecipients;
        private String emailCc;
        private String emailTemplateCode;
        private String emailSceneCode;
        private String remark;
        private String createBy;
        private LocalDateTime createTime;
        private String updateBy;
        private LocalDateTime updateTime;
    }

    /**
     * 渠道预警事件分页查询条件。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class ChannelAlertEventQuery extends PageRequest {
        private String eventCode;
        private Long ruleId;
        private String ruleCode;
        private String ruleName;
        private Long channelId;
        private String channelCode;
        private String businessType;
        private String paymentMethod;
        private String cardBrand;
        private String ruleType;
        private String alertLevel;
        private String eventStatus;
        private LocalDateTime triggerStartTime;
        private LocalDateTime triggerEndTime;
    }

    /**
     * 渠道预警事件响应。
     */
    @Data
    public static class ChannelAlertEventResponse {
        private Long id;
        private String eventCode;
        private Long ruleId;
        private String ruleCode;
        private String ruleName;
        private Long channelId;
        private String channelCode;
        private String channelName;
        private String businessType;
        private String paymentMethod;
        private String cardBrand;
        private String ruleType;
        private String alertLevel;
        private Integer windowMinutes;
        private LocalDateTime windowStartTime;
        private LocalDateTime windowEndTime;
        private Integer sampleCount;
        private Integer failureCount;
        private Integer successCount;
        private BigDecimal successRate;
        private BigDecimal errorRate;
        private Integer maxContinuousFailureCount;
        private Integer averageLatencyMillis;
        private Integer triggerValueCount;
        private BigDecimal triggerValueRate;
        private Integer triggerValueMillis;
        private String thresholdSnapshot;
        private String eventStatus;
        private String notifyStatus;
        private LocalDateTime triggerTime;
        private LocalDateTime acknowledgedTime;
        private String acknowledgedBy;
        private String remark;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    /**
     * 渠道预警通知日志分页查询条件。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class ChannelAlertNotifyLogQuery extends PageRequest {
        private Long eventId;
        private String eventCode;
        private Long ruleId;
        private String ruleCode;
        private String notifyType;
        private String notifyStatus;
        private LocalDateTime createStartTime;
        private LocalDateTime createEndTime;
    }

    /**
     * 渠道预警通知日志响应。
     */
    @Data
    public static class ChannelAlertNotifyLogResponse {
        private Long id;
        private Long eventId;
        private String eventCode;
        private Long ruleId;
        private String ruleCode;
        private String notifyType;
        private String notifyStatus;
        private String emailRecipients;
        private String emailCc;
        private String emailTemplateCode;
        private String emailSceneCode;
        private LocalDateTime sendStartTime;
        private LocalDateTime sendEndTime;
        private String failReason;
        private String createBy;
        private LocalDateTime createTime;
        private String updateBy;
        private LocalDateTime updateTime;
    }

    /**
     * 通用状态请求。
     */
    @Data
    public static class AlertStatusRequest {
        /**
         * 状态值，0停用，1启用。
         */
        @NotNull(message = "status is required")
        private Integer status;
    }

    /**
     * 事件确认请求。
     */
    @Data
    public static class AlertEventAcknowledgeRequest {
        /**
         * 确认备注。
         */
        private String remark;
    }
}
