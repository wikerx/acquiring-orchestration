package com.scott.payment.admin.dto.channel;

import com.scott.payment.component.core.model.PageRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelDTOs
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 渠道管理Channel  DTO 集合，位于 service-admin 的接口传输层，用于说明职责边界、数据语义和关键业务约束。
 * @status : create
 */
public final class ChannelDTOs {

    private ChannelDTOs() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class ChannelInfoQuery extends PageRequest {
        /**
         * 渠道管理敏感或密钥相关字段，日志和接口展示必须脱敏，必要时仅保存密文。
         */
        private String keyword;
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
        private Integer support3ds;
    }

    @Data
    public static class ChannelInfoSaveRequest {
        /**
         * 渠道管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        @NotBlank(message = "channelCode is required")
        private String channelCode;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        @NotBlank(message = "channelCnName is required")
        private String channelCnName;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        @NotBlank(message = "channelEnName is required")
        private String channelEnName;
        /**
         * 渠道管理状态字段，取值需与数据字典或枚举约定保持一致。
         */
        @NotNull(message = "channelStatus is required")
        private Integer channelStatus;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        @NotNull(message = "supportAcquiring is required")
        private Integer supportAcquiring;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        @NotNull(message = "supportPayout is required")
        private Integer supportPayout;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        @NotNull(message = "support3ds is required")
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
         * 渠道连接超时时间，单位秒。
         */
        private Integer connectTimeoutSeconds;
        /**
         * 渠道读取超时时间，单位秒。
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
         * 渠道 MID 参数模板，定义该渠道后续 MID 维护时必须填写的字段集合。
         */
        @Valid
        private List<ChannelMetadataSchemaItem> metadataSchemas = new ArrayList<>();
    }

    @Data
    public static class ChannelInfoResponse {
        /**
         * 渠道管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
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
         * 渠道连接超时时间，单位秒。
         */
        private Integer connectTimeoutSeconds;
        /**
         * 渠道读取超时时间，单位秒。
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
        private List<String> acquiringPaymentMethods = new ArrayList<>();
        private List<String> payoutPaymentMethods = new ArrayList<>();
        private List<ChannelMetadataSchemaItem> metadataSchemas = new ArrayList<>();
        /**
         * 渠道管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime createTime;
        /**
         * 渠道管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime updateTime;
    }

    @Data
    public static class ChannelMetadataSchemaItem {
        /**
         * 元数据模板主键，新增模板字段时为空。
         */
        private Long id;
        /**
         * MID 参数 key，例如 merchantId、username、privateKey。
         */
        private String fieldKey;
        /**
         * 页面展示名称，例如商户号、访问账户、商户私钥。
         */
        private String fieldLabel;
        /**
         * 字段类型，决定后续 MID 表单控件和基础校验。
         */
        private String fieldType;
        /**
         * 是否必填：0否，1是。
         */
        private Integer requiredFlag;
        /**
         * 是否敏感：0否，1是；敏感字段后续 MID 值必须加密存储并脱敏展示。
         */
        private Integer sensitiveFlag;
        /**
         * 可选正则表达式，用于后续 MID 值的格式校验。
         */
        private String validationRegex;
        /**
         * 页面输入占位说明。
         */
        private String placeholder;
        /**
         * 非敏感字段默认值；敏感字段不建议配置默认值。
         */
        private String defaultValue;
        /**
         * 字段排序，数字越小越靠前。
         */
        private Integer sortOrder;
        /**
         * 模板字段状态：0停用，1启用。
         */
        private Integer fieldStatus;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class ChannelMidConfigQuery extends PageRequest {
        /**
         * 渠道ID。
         */
        private Long channelId;
        /**
         * 渠道编码。
         */
        private String channelCode;
        /**
         * 平台商户输入的真实渠道 MID。
         */
        private String channelMid;
        /**
         * 业务类型：ACQUIRING/PAYOUT。
         */
        private String businessType;
        /**
         * MID 状态：0停用，1启用。
         */
        private Integer midStatus;
    }

    @Data
    public static class ChannelMidConfigSaveRequest {
        /**
         * 渠道ID，关联 channel_info.id。
         */
        @NotNull(message = "channelId is required")
        private Long channelId;
        /**
         * 渠道侧真实 MID 或商户号；优先由 MID 元数据中的商户号/MID 字段派生，兼容历史调用可显式传入。
         */
        private String channelMid;
        /**
         * MID 后台展示名称，可为空；为空时后端使用渠道 MID 作为落库兜底名称。
         */
        private String midName;
        /**
         * 渠道终端号，可为空。
         */
        private String terminalId;
        /**
         * 业务类型：ACQUIRING/PAYOUT。
         */
        @NotBlank(message = "businessType is required")
        private String businessType;
        /**
         * 支持支付方式，ALL 或逗号分隔。
         */
        @NotBlank(message = "paymentMethodScope is required")
        private String paymentMethodScope;
        /**
         * 银行卡品牌范围，非银行卡支付方式为 NONE，银行卡为 ALL 或 card_brand 字典值逗号分隔。
         */
        private String cardBrandScope;
        /**
         * 支持交易类型，由渠道能力按支付方式派生，前端不再要求人工维护。
         */
        private String transactionTypeScope;
        /**
         * 支持交易币种，ALL 或 ISO 4217 三位币种逗号分隔。
         */
        @NotBlank(message = "currencyScope is required")
        private String currencyScope;
        /**
         * 允许交易国家，ALL 或 ISO 国家码逗号分隔。
         */
        @NotBlank(message = "allowedCountryScope is required")
        private String allowedCountryScope;
        /**
         * 默认结算币种。
         */
        @NotBlank(message = "defaultSettlementCurrency is required")
        private String defaultSettlementCurrency;
        /**
         * 结算周期：T0/T1/T2。
         */
        @NotBlank(message = "settlementCycle is required")
        private String settlementCycle;
        /**
         * 结算日切时间，可为空。
         */
        private LocalTime settlementCutoffTime;
        /**
         * 结算时区。
         */
        @NotBlank(message = "settlementTimeZone is required")
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
         * 根据渠道元数据模板录入的 MID 元数据 JSON。
         */
        private String metadataValueJson;
        /**
         * MID 状态：0停用，1启用。
         */
        @NotNull(message = "midStatus is required")
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
    }

    @Data
    public static class ChannelMidConfigResponse {
        private Long id;
        private Long channelId;
        private String channelCode;
        private String channelName;
        private String channelMid;
        private String midName;
        private String terminalId;
        private String businessType;
        private String paymentMethodScope;
        private String cardBrandScope;
        private String transactionTypeScope;
        private String currencyScope;
        private String allowedCountryScope;
        private String defaultSettlementCurrency;
        private String settlementCycle;
        private LocalTime settlementCutoffTime;
        private String settlementTimeZone;
        private String mcc;
        private String statementDescriptor;
        private String metadataValueJson;
        private Integer midStatus;
        private LocalDateTime effectiveTime;
        private LocalDateTime expireTime;
        private String remark;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class MerchantChannelMidBindingQuery extends PageRequest {
        private String merchantId;
        private Long channelId;
        private String channelCode;
        private Long midConfigId;
        private Integer bindingStatus;
    }

    @Data
    public static class MerchantChannelMidBindingSaveRequest {
        @NotBlank(message = "merchantId is required")
        private String merchantId;
        @NotNull(message = "midConfigId is required")
        private Long midConfigId;
        @NotNull(message = "bindingStatus is required")
        private Integer bindingStatus;
        private LocalDateTime effectiveTime;
        private LocalDateTime expireTime;
        private String remark;
    }

    @Data
    public static class MerchantChannelMidBindingResponse {
        private Long id;
        private String merchantId;
        private Long channelId;
        private String channelCode;
        private String channelName;
        private Long midConfigId;
        private String channelMid;
        private String midName;
        private Integer bindingStatus;
        private LocalDateTime effectiveTime;
        private LocalDateTime expireTime;
        private String remark;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class CapabilityQuery extends PageRequest {
        /**
         * 渠道管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private Long channelId;
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
         * 渠道管理币种字段，通常使用 ISO 4217 三位字母代码，不能为空时由上层校验。
         */
        private String currencyCode;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String cardBrand;
        /**
         * 渠道管理状态字段，取值需与数据字典或枚举约定保持一致。
         */
        private Integer capabilityStatus;
    }

    @Data
    public static class CapabilitySaveRequest {
        /**
         * 渠道管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        @NotNull(message = "channelId is required")
        private Long channelId;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        @NotBlank(message = "businessType is required")
        private String businessType;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        @NotBlank(message = "paymentMethod is required")
        private String paymentMethod;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String transactionType;
        private List<String> transactionTypes = new ArrayList<>();
        private List<String> currencyCodes = new ArrayList<>();
        private List<String> cardBrands = new ArrayList<>();
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Integer support3ds;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Integer supportIncrementalAuthorization;
        /**
         * 渠道管理状态字段，取值需与数据字典或枚举约定保持一致。
         */
        @NotNull(message = "capabilityStatus is required")
        private Integer capabilityStatus;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Integer sortOrder;
        /**
         * 渠道管理备注字段，用于记录人工说明，不参与核心状态流转。
         */
        private String remark;
    }

    @Data
    public static class CapabilitySupportRequest {
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Integer support3ds;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Integer supportIncrementalAuthorization;
    }

    @Data
    public static class CapabilityResponse {
        /**
         * 渠道管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
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
        private String channelName;
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
        private List<String> transactionTypes = new ArrayList<>();
        private List<String> currencyCodes = new ArrayList<>();
        private List<String> cardBrands = new ArrayList<>();
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Integer support3ds;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
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
         * 渠道管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime createTime;
        /**
         * 渠道管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime updateTime;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class LimitQuery extends PageRequest {
        /**
         * 渠道管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private Long channelId;
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
         * 渠道管理状态字段，取值需与数据字典或枚举约定保持一致。
         */
        private Integer ruleStatus;
    }

    @Data
    public static class LimitSaveRequest {
        /**
         * 渠道管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        @NotNull(message = "channelId is required")
        private Long channelId;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        @NotBlank(message = "businessType is required")
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
        @NotBlank(message = "limitType is required")
        private String limitType;
        /**
         * 渠道管理金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
         */
        @NotNull(message = "limitAmount is required")
        private BigDecimal limitAmount;
        /**
         * 渠道管理状态字段，取值需与数据字典或枚举约定保持一致。
         */
        @NotNull(message = "ruleStatus is required")
        private Integer ruleStatus;
        /**
         * 渠道管理备注字段，用于记录人工说明，不参与核心状态流转。
         */
        private String remark;
    }

    @Data
    public static class LimitBatchSaveRequest {
        @Valid
        private List<LimitSaveRequest> items = new ArrayList<>();
    }

    @Data
    public static class LimitResponse {
        /**
         * 渠道管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
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
        private String channelName;
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
        private String updateBy;
        /**
         * 渠道管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime createTime;
        /**
         * 渠道管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime updateTime;
    }

    @Data
    public static class StatusRequest {
        /**
         * 渠道管理状态字段，取值需与数据字典或枚举约定保持一致。
         */
        @NotNull(message = "status is required")
        private Integer status;
    }

    @Data
    public static class ChannelOption {
        /**
         * 渠道管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private Long id;
        /**
         * 渠道管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String channelCode;
        /**
         * 渠道管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String channelName;
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
        private Integer support3ds;
    }
}
