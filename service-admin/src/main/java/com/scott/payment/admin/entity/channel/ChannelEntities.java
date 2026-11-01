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
 * Channel Entities 聚合类型，用于运营后台，集中定义同一业务域下的请求、响应、查询条件和持久化视图模型。
 */
public final class ChannelEntities {

    /**
     * 阻止仅提供静态能力的类型被实例化。
     */
    private ChannelEntities() {
    }

    /**
     * 渠道基础信息数据库实体。
     */
    @Data
    @TableName("channel_info")
    public static class ChannelInfoDO {
        @TableId(type = IdType.AUTO)
        private Long id;
        /**
         * 渠道编码，用于定位 MPGS、WorldPay 等渠道适配实现和路由配置。
         */
        private String channelCode;
        private String channelCnName;
        private String channelEnName;
        private Integer channelStatus;
        private Integer supportAcquiring;
        private Integer supportPayout;
        @TableField("support_3ds")
        private Integer support3ds;
        /**
         * 默认请求URL，表示当前内部调用、渠道调用或商户通知的目标地址。
         */
        private String defaultRequestUrl;
        /**
         * 持久化的{@code defaultInteractionMode}，用于还原当前记录的业务事实。
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
        private Integer sortOrder;
        /**
         * 渠道管理备注字段，用于记录人工说明，不参与核心状态流转。
         */
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
         * 支持交易类型，ALL 或按业务类型取 transaction_type / payout_transaction_type 字典值逗号分隔。
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
         * 渠道 MID 对应的 MCC，用于渠道商户号能力和路由约束。
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
        @TableId(type = IdType.AUTO)
        private Long id;
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
         * 交易类型，标识本次动作是支付、授权、请款、退款、撤销还是增量授权，用于选择状态机和渠道能力。
         */
        private String transactionType;
        /**
         * 渠道不直接支持请求币种时使用的默认交易币种，必须属于该能力的允许币种。
         */
        @TableField("default_transaction_currency")
        private String defaultTransactionCurrency;
        @TableField("support_3ds")
        private Integer support3ds;
        @TableField("support_incremental_authorization")
        private Integer supportIncrementalAuthorization;
        private Integer capabilityStatus;
        private Integer sortOrder;
        /**
         * 渠道管理备注字段，用于记录人工说明，不参与核心状态流转。
         */
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
     * 渠道支付能力币种数据库实体。
     */
    @Data
    @TableName("channel_capability_currency")
    public static class ChannelCapabilityCurrencyDO {
        @TableId(type = IdType.AUTO)
        private Long id;
        private Long capabilityId;
        private Long channelId;
        /**
         * 渠道编码，用于定位 MPGS、WorldPay 等渠道适配实现和路由配置。
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
     * 渠道卡品牌绑定数据库实体。
     */
    @Data
    @TableName("channel_capability_card_brand")
    public static class ChannelCapabilityCardBrandDO {
        @TableId(type = IdType.AUTO)
        private Long id;
        private Long capabilityId;
        private Long channelId;
        /**
         * 渠道编码，用于定位 MPGS、WorldPay 等渠道适配实现和路由配置。
         */
        private String channelCode;
        /**
         * 卡品牌编码，用于渠道能力匹配、路由和运营展示。
         */
        private String cardBrand;
        private Integer brandStatus;
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
     * 渠道限额规则数据库实体。
     */
    @Data
    @TableName("channel_limit_rule")
    public static class ChannelLimitRuleDO {
        @TableId(type = IdType.AUTO)
        private Long id;
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
        private String limitType;
        /**
         * 渠道管理币种字段，通常使用 ISO 4217 三位字母代码，不能为空时由上层校验。
         */
        private String limitCurrency;
        private BigDecimal limitAmount;
        private Integer ruleStatus;
        /**
         * 渠道管理备注字段，用于记录人工说明，不参与核心状态流转。
         */
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

}
