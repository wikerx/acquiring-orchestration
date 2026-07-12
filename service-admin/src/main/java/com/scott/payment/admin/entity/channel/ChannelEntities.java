package com.scott.payment.admin.entity.channel;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

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
         * 连接超时时间，单位秒，用于统一控制该渠道 HTTP 建连等待时间。
         */
        private Integer connectTimeoutSeconds;
        /**
         * 读取超时时间，单位秒，用于统一控制该渠道 HTTP 响应等待时间。
         */
        private Integer readTimeoutSeconds;
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
     * 渠道真实 MID 配置数据库实体。
     */
    @Data
    @TableName("channel_mid_config")
    public static class ChannelMidConfigDO {
        /**
         * 主键ID。
         */
        @TableId(type = IdType.AUTO)
        private Long id;
        /**
         * 渠道ID，关联 channel_info.id。
         */
        private Long channelId;
        /**
         * 渠道编码，用于路由查询和审计冗余。
         */
        private String channelCode;
        /**
         * 渠道侧真实 MID 或商户号。
         */
        private String channelMid;
        /**
         * MID 后台展示名称。
         */
        private String midName;
        /**
         * 渠道终端号，可为空。
         */
        private String terminalId;
        /**
         * 业务类型：ACQUIRING/PAYOUT。
         */
        private String businessType;
        /**
         * 支持支付方式，ALL 或逗号分隔。
         */
        private String paymentMethodScope;
        /**
         * 银行卡品牌范围，非银行卡支付方式为 NONE，银行卡为 ALL 或 card_brand 字典值逗号分隔。
         */
        private String cardBrandScope;
        /**
         * 支持交易类型，ALL 或 transaction_type 字典值逗号分隔。
         */
        private String transactionTypeScope;
        /**
         * 支持交易币种，ALL 或 ISO 4217 三位币种逗号分隔。
         */
        private String currencyScope;
        /**
         * 允许交易国家，ALL 或 ISO 国家码逗号分隔。
         */
        private String allowedCountryScope;
        /**
         * 默认结算币种。
         */
        private String defaultSettlementCurrency;
        /**
         * 结算周期，例如 T0/T1/T2。
         */
        private String settlementCycle;
        /**
         * 结算日切时间。
         */
        private LocalTime settlementCutoffTime;
        /**
         * 结算时区。
         */
        private String settlementTimeZone;
        /**
         * MID MCC。
         */
        private String mcc;
        /**
         * 账单描述。
         */
        private String statementDescriptor;
        /**
         * 根据 channel_metadata_schema 录入的 MID 元数据 JSON。
         */
        private String metadataValueJson;
        /**
         * MID 状态：0停用，1启用。
         */
        private Integer midStatus;
        /**
         * 生效时间。
         */
        private LocalDateTime effectiveTime;
        /**
         * 失效时间，空表示永不过期。
         */
        private LocalDateTime expireTime;
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
         * 删除标识：0未删除，大于0为删除记录ID。
         */
        private Long deleted;
    }

    /**
     * 商户与渠道 MID 绑定关系数据库实体。
     */
    @Data
    @TableName("merchant_channel_mid_binding")
    public static class MerchantChannelMidBindingDO {
        /**
         * 主键ID。
         */
        @TableId(type = IdType.AUTO)
        private Long id;
        /**
         * 平台商户号。
         */
        private String merchantId;
        /**
         * 渠道ID，关联 channel_info.id。
         */
        private Long channelId;
        /**
         * 渠道编码。
         */
        private String channelCode;
        /**
         * MID 配置ID，关联 channel_mid_config.id。
         */
        private Long midConfigId;
        /**
         * 渠道侧真实 MID，冗余用于展示和排障。
         */
        private String channelMid;
        /**
         * 绑定状态：0停用，1启用。
         */
        private Integer bindingStatus;
        /**
         * 生效时间。
         */
        private LocalDateTime effectiveTime;
        /**
         * 失效时间，空表示永不过期。
         */
        private LocalDateTime expireTime;
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
         * 删除标识：0未删除，大于0为删除记录ID。
         */
        private Long deleted;
    }

    /**
     * 渠道 MID 参数模板数据库实体。
     */
    @Data
    @TableName("channel_metadata_schema")
    public static class ChannelMetadataSchemaDO {
        /**
         * 主键ID。
         */
        @TableId(type = IdType.AUTO)
        private Long id;
        /**
         * 渠道ID。
         */
        private Long channelId;
        /**
         * 渠道编码，用于模板展示和审计。
         */
        private String channelCode;
        /**
         * MID 参数 key，例如 merchantId、username、privateKey。
         */
        private String fieldKey;
        /**
         * 页面展示名称。
         */
        private String fieldLabel;
        /**
         * 字段类型：TEXT、PASSWORD、URL、NUMBER、JSON、TEXTAREA、PRIVATE_KEY、PUBLIC_KEY、CERTIFICATE、SELECT。
         */
        private String fieldType;
        /**
         * 是否必填：0否，1是。
         */
        private Integer requiredFlag;
        /**
         * 是否敏感：0否，1是。
         */
        private Integer sensitiveFlag;
        /**
         * 格式校验正则。
         */
        private String validationRegex;
        /**
         * 页面输入占位说明。
         */
        private String placeholder;
        /**
         * 默认值，敏感字段不建议配置。
         */
        private String defaultValue;
        /**
         * 排序。
         */
        private Integer sortOrder;
        /**
         * 字段状态：0停用，1启用。
         */
        private Integer fieldStatus;
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
         * 删除标识：0未删除，大于0为删除记录ID。
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
