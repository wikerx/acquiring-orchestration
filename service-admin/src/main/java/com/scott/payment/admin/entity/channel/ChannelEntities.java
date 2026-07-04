package com.scott.payment.admin.entity.channel;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelEntities
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 渠道管理Channel 实体集合，位于 service-admin 的数据实体层，用于说明职责边界、数据语义和关键业务约束。
 * @status : create
 */
public final class ChannelEntities {

    private ChannelEntities() {
    }

    /**
     * 渠道基础信息数据库实体。
     */
    @Data
    @TableName("channel_info")
    public static class ChannelInfoDO {
        /**
         * 渠道管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        @TableId(type = IdType.AUTO)
        private Long id;
        /**
         * 渠道管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String channelCode;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String channelCnName;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String channelEnName;
        /**
         * 渠道管理状态字段，取值需与数据字典或枚举约定保持一致。
         */
        private Integer channelStatus;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Integer supportAcquiring;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Integer supportPayout;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        @TableField("support_3ds")
        private Integer support3ds;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String defaultRequestUrl;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String defaultInteractionMode;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Integer sortOrder;
        /**
         * 渠道管理备注字段，用于记录人工说明，不参与核心状态流转。
         */
        private String remark;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String createBy;
        /**
         * 渠道管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime createTime;
        /**
         * 渠道管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private String updateBy;
        /**
         * 渠道管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime updateTime;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Long deleted;
    }

    /**
     * 渠道支付能力数据库实体。
     */
    @Data
    @TableName("channel_payment_capability")
    public static class ChannelPaymentCapabilityDO {
        /**
         * 渠道管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        @TableId(type = IdType.AUTO)
        private Long id;
        /**
         * 渠道管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private Long channelId;
        /**
         * 渠道管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String channelCode;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String businessType;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String paymentMethod;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String transactionType;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        @TableField("support_3ds")
        private Integer support3ds;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        @TableField("support_incremental_authorization")
        private Integer supportIncrementalAuthorization;
        /**
         * 渠道管理状态字段，取值需与数据字典或枚举约定保持一致。
         */
        private Integer capabilityStatus;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Integer sortOrder;
        /**
         * 渠道管理备注字段，用于记录人工说明，不参与核心状态流转。
         */
        private String remark;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String createBy;
        /**
         * 渠道管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime createTime;
        /**
         * 渠道管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private String updateBy;
        /**
         * 渠道管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime updateTime;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Long deleted;
    }

    /**
     * 渠道支付能力币种数据库实体。
     */
    @Data
    @TableName("channel_capability_currency")
    public static class ChannelCapabilityCurrencyDO {
        /**
         * 渠道管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        @TableId(type = IdType.AUTO)
        private Long id;
        /**
         * 渠道管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private Long capabilityId;
        /**
         * 渠道管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private Long channelId;
        /**
         * 渠道管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String channelCode;
        /**
         * 渠道管理币种字段，通常使用 ISO 4217 三位字母代码，不能为空时由上层校验。
         */
        private String currencyCode;
        /**
         * 渠道管理币种字段，通常使用 ISO 4217 三位字母代码，不能为空时由上层校验。
         */
        private Integer currencyStatus;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String createBy;
        /**
         * 渠道管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime createTime;
        /**
         * 渠道管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private String updateBy;
        /**
         * 渠道管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime updateTime;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Long deleted;
    }

    /**
     * 渠道卡品牌绑定数据库实体。
     */
    @Data
    @TableName("channel_capability_card_brand")
    public static class ChannelCapabilityCardBrandDO {
        /**
         * 渠道管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        @TableId(type = IdType.AUTO)
        private Long id;
        /**
         * 渠道管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private Long capabilityId;
        /**
         * 渠道管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private Long channelId;
        /**
         * 渠道管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String channelCode;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String cardBrand;
        /**
         * 渠道管理状态字段，取值需与数据字典或枚举约定保持一致。
         */
        private Integer brandStatus;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Integer sortOrder;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String createBy;
        /**
         * 渠道管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime createTime;
        /**
         * 渠道管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private String updateBy;
        /**
         * 渠道管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime updateTime;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Long deleted;
    }

    /**
     * 渠道限额规则数据库实体。
     */
    @Data
    @TableName("channel_limit_rule")
    public static class ChannelLimitRuleDO {
        /**
         * 渠道管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        @TableId(type = IdType.AUTO)
        private Long id;
        /**
         * 渠道管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private Long channelId;
        /**
         * 渠道管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String channelCode;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String businessType;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String paymentMethod;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String cardBrand;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String limitType;
        /**
         * 渠道管理币种字段，通常使用 ISO 4217 三位字母代码，不能为空时由上层校验。
         */
        private String limitCurrency;
        /**
         * 渠道管理金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
         */
        private BigDecimal limitAmount;
        /**
         * 渠道管理状态字段，取值需与数据字典或枚举约定保持一致。
         */
        private Integer ruleStatus;
        /**
         * 渠道管理备注字段，用于记录人工说明，不参与核心状态流转。
         */
        private String remark;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String createBy;
        /**
         * 渠道管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime createTime;
        /**
         * 渠道管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private String updateBy;
        /**
         * 渠道管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime updateTime;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Long deleted;
    }

}
