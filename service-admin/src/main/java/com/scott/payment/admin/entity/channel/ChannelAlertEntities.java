package com.scott.payment.admin.entity.channel;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelAlertEntities
 * @date : 2026-07-17 00:00
 * @email : scott_x@163.com
 * @description : 渠道预警管理实体集合，位于 service-admin 数据实体层，仅保存后台配置、预警事件和邮件通知日志。
 * @status : create
 */
public final class ChannelAlertEntities {

    private ChannelAlertEntities() {
    }

    /**
     * 渠道预警规则数据库实体。
     */
    @Data
    @TableName("channel_alert_rule")
    public static class ChannelAlertRuleDO {
        /**
         * 主键 ID。
         */
        @TableId(type = IdType.AUTO)
        private Long id;
        /**
         * 规则编码，用于事件和通知日志关联。
         */
        private String ruleCode;
        /**
         * 规则分组编码，同一次批量配置的卡品牌和规则类型共用该编码。
         */
        private String ruleGroupCode;
        /**
         * 规则名称，用于后台展示和邮件标题。
         */
        private String ruleName;
        /**
         * 渠道 ID，关联 channel_info.id。
         */
        private Long channelId;
        /**
         * 渠道编码，冗余用于事件查询和审计。
         */
        private String channelCode;
        /**
         * 业务类型：ACQUIRING/PAYOUT。
         */
        private String businessType;
        /**
         * 支付方式，ALL 表示全部。
         */
        private String paymentMethod;
        /**
         * 卡品牌，ALL 表示全部。
         */
        private String cardBrand;
        /**
         * 规则类型。
         */
        private String ruleType;
        /**
         * 时间窗口分钟数。
         */
        private Integer windowMinutes;
        /**
         * 笔数阈值，用于连续失败类规则。
         */
        private Integer thresholdCount;
        /**
         * 比例阈值，按百分比保存。
         */
        private BigDecimal thresholdRate;
        /**
         * 延迟阈值，单位毫秒。
         */
        private Integer thresholdMillis;
        /**
         * 最小样本数。
         */
        private Integer minimumSampleCount;
        /**
         * 预警级别。
         */
        private String alertLevel;
        /**
         * 规则说明。
         */
        private String ruleDescription;
        /**
         * 是否自动降级：0否，1是；当前仅保存配置。
         */
        private Integer autoDegrade;
        /**
         * 是否自动熔断：0否，1是；当前仅保存配置。
         */
        private Integer autoCircuitBreak;
        /**
         * 规则状态：0停用，1启用。
         */
        private Integer ruleStatus;
        /**
         * 通知方式，当前仅支持 EMAIL。
         */
        private String notifyType;
        /**
         * 邮件收件人，多个地址逗号分隔。
         */
        private String emailRecipients;
        /**
         * 邮件抄送人，多个地址逗号分隔。
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
         * 备注。
         */
        private String remark;
        /**
         * 创建人。
         */
        private String createBy;
        /**
         * 创建时间。
         */
        private LocalDateTime createTime;
        /**
         * 更新人。
         */
        private String updateBy;
        /**
         * 更新时间。
         */
        private LocalDateTime updateTime;
        /**
         * 删除标识：0未删除，大于0表示删除记录 ID。
         */
        private Long deleted;
    }

    /**
     * 渠道预警触发事件数据库实体。
     */
    @Data
    @TableName("channel_alert_event")
    public static class ChannelAlertEventDO {
        @TableId(type = IdType.AUTO)
        private Long id;
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
        private Long deleted;
    }

    /**
     * 渠道预警通知执行日志数据库实体。
     */
    @Data
    @TableName("channel_alert_notify_log")
    public static class ChannelAlertNotifyLogDO {
        @TableId(type = IdType.AUTO)
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
        private Long deleted;
    }
}
