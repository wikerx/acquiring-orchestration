package com.scott.payment.admin.entity.channel;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 渠道预警管理实体集合，位于 service-admin 数据实体层，仅保存后台配置、预警事件和邮件通知日志。
 */
public final class ChannelAlertEntities {

    /**
     * 阻止仅提供静态能力的类型被实例化。
     */
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
        /**
         * 费用规则名称，用于运营识别同一费用版本内的原子匹配规则。
         */
        private String ruleName;
        private Long channelId;
        /**
         * 渠道编码，用于定位 MPGS、WorldPay 等渠道适配实现和路由配置。
         */
        private String channelCode;
        /**
         * 业务线类型，用于区分收单、代付等业务域，并隔离渠道配置、限额规则和后台查询口径。
         */
        private String businessType;
        private String paymentMethod;
        /**
         * 卡品牌编码，用于渠道能力匹配、路由和运营展示。
         */
        private String cardBrand;
        /**
         * 规则类型，用于区分风控、限额、预警或通知规则的匹配逻辑。
         */
        private String ruleType;
        /**
         * 持久化的告警等级，用于还原当前记录的业务事实。
         */
        private String alertLevel;
        /**
         * 持久化的{@code windowMinutes}，用于还原当前记录的业务事实。
         */
        private Integer windowMinutes;
        /**
         * 持久化的{@code windowStartTime}，用于还原当前记录的业务事实。
         */
        private LocalDateTime windowStartTime;
        /**
         * 持久化的{@code windowEndTime}，用于还原当前记录的业务事实。
         */
        private LocalDateTime windowEndTime;
        private Integer sampleCount;
        private Integer failureCount;
        private Integer successCount;
        /**
         * 持久化的成功汇率，用于还原当前记录的业务事实。
         */
        private BigDecimal successRate;
        /**
         * 持久化的错误汇率，用于还原当前记录的业务事实。
         */
        private BigDecimal errorRate;
        private Integer maxContinuousFailureCount;
        /**
         * 持久化的{@code averageLatencyMillis}，用于还原当前记录的业务事实。
         */
        private Integer averageLatencyMillis;
        private Integer triggerValueCount;
        /**
         * 持久化的触发值汇率，用于还原当前记录的业务事实。
         */
        private BigDecimal triggerValueRate;
        /**
         * 持久化的触发值毫秒数，用于还原当前记录的业务事实。
         */
        private Integer triggerValueMillis;
        /**
         * 持久化的{@code thresholdSnapshot}，用于还原当前记录的业务事实。
         */
        private String thresholdSnapshot;
        private String eventStatus;
        private String notifyStatus;
        /**
         * 持久化的触发时间，用于还原当前记录的业务事实。
         */
        private LocalDateTime triggerTime;
        /**
         * 持久化的{@code acknowledgedTime}，用于还原当前记录的业务事实。
         */
        private LocalDateTime acknowledgedTime;
        /**
         * 持久化的{@code acknowledgedBy}，用于还原当前记录的业务事实。
         */
        private String acknowledgedBy;
        private String remark;
        /**
         * 记录创建时刻，持久化精度为毫秒。
         */
        private LocalDateTime createTime;
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
        /**
         * 通知类型，用于区分交易成功、失败、处理中或补偿重试等商户通知场景。
         */
        private String notifyType;
        private String notifyStatus;
        private String emailRecipients;
        private String emailCc;
        private String emailTemplateCode;
        private String emailSceneCode;
        /**
         * 持久化的{@code sendStartTime}，用于还原当前记录的业务事实。
         */
        private LocalDateTime sendStartTime;
        /**
         * 持久化的{@code sendEndTime}，用于还原当前记录的业务事实。
         */
        private LocalDateTime sendEndTime;
        /**
         * 持久化的{@code failReason}，用于还原当前记录的业务事实。
         */
        private String failReason;
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
