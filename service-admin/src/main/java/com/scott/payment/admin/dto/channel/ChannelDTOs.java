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
 * Channel DTOs 聚合类型，用于运营后台，集中定义同一业务域下的请求、响应、查询条件和持久化视图模型。
 */
public final class ChannelDTOs {

    /**
     * 阻止仅提供静态能力的类型被实例化。
     */
    private ChannelDTOs() {
    }

    /**
     * Channel Info Query 查询条件模型，用于运营后台，承载筛选字段、时间范围、分页参数和列表查询边界。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class ChannelInfoQuery extends PageRequest {
        /**
         * 渠道管理敏感或密钥相关字段，日志和接口展示必须脱敏，必要时仅保存密文。
         */
        private String keyword;
        private Integer channelStatus;
        private Integer supportAcquiring;
        private Integer supportPayout;
        private Integer support3ds;
    }

    /**
     * 渠道信息写操作请求模型，用于运营后台，承载新增或编辑字段；权限、状态和唯一性由应用服务校验。
     */
    @Data
    public static class ChannelInfoSaveRequest {
        /**
         * 渠道编码，用于定位 MPGS、WorldPay 等渠道适配实现和路由配置。
         */
        @NotBlank(message = "channelCode is required")
        private String channelCode;
        @NotBlank(message = "channelCnName is required")
        private String channelCnName;
        @NotBlank(message = "channelEnName is required")
        private String channelEnName;
        @NotNull(message = "channelStatus is required")
        private Integer channelStatus;
        @NotNull(message = "supportAcquiring is required")
        private Integer supportAcquiring;
        @NotNull(message = "supportPayout is required")
        private Integer supportPayout;
        @NotNull(message = "support3ds is required")
        private Integer support3ds;

        private String defaultRequestUrl;
        private String defaultInteractionMode;
        /**
         * 渠道连接超时时间，单位秒。
         */
        private Integer connectTimeoutSeconds;
        /**
         * 渠道读取超时时间，单位秒。
         */
        private Integer readTimeoutSeconds;
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

    /**
     * 渠道信息响应模型，用于运营后台，向调用方展示处理结果和必要业务事实，不暴露持久化实体。
     */
    @Data
    public static class ChannelInfoResponse {
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
        private Integer support3ds;

        private String defaultRequestUrl;
        private String defaultInteractionMode;
        /**
         * 渠道连接超时时间，单位秒。
         */
        private Integer connectTimeoutSeconds;
        /**
         * 渠道读取超时时间，单位秒。
         */
        private Integer readTimeoutSeconds;
        private Integer sortOrder;
        /**
         * 渠道管理备注字段，用于记录人工说明，不参与核心状态流转。
         */
        private String remark;
        private List<String> acquiringPaymentMethods = new ArrayList<>();
        private List<String> payoutPaymentMethods = new ArrayList<>();
        private List<ChannelMetadataSchemaItem> metadataSchemas = new ArrayList<>();
        /**
         * 记录创建时刻，持久化精度为毫秒。
         */
        private LocalDateTime createTime;
        /**
         * 记录最后更新时间，持久化精度为毫秒。
         */
        private LocalDateTime updateTime;
    }

    /**
     * 渠道元数据结构字段定义，描述配置项键名、类型、必填性、敏感性和校验规则。
     */
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

    /**
     * 渠道 MID 配置分页查询条件。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class ChannelMidConfigQuery extends PageRequest {
        /** 渠道 MID 或后台展示名称关键字。 */
        private String keyword;
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
        /** MID 必须支持的收单支付方式；按逗号分隔能力范围精确匹配。 */
        private String paymentMethod;
        /** 银行卡支付方式下 MID 必须支持的卡品牌；非银行卡支付方式不传。 */
        private String cardBrand;
        /**
         * MID 状态：0停用，1启用。
         */
        private Integer midStatus;
    }

    /**
     * 渠道 MID 配置保存请求，承载业务类型、能力范围和渠道接入参数。
     */
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
         * 支持交易类型，由渠道能力按支付方式派生；收单和代付分别使用对应交易类型字典。
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
         * 默认交易币种；必须属于 MID 允许交易币种并被所选渠道能力支持。
         */
        @NotBlank(message = "defaultTransactionCurrency is required")
        private String defaultTransactionCurrency;
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
         * 渠道 MID 对应的 MCC，用于渠道商户号能力和路由约束。
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

    /**
     * 渠道 MID 配置响应，展示业务范围、状态和脱敏后的接入参数。
     */
    @Data
    public static class ChannelMidConfigResponse {
        private Long id;
        private Long channelId;
        /**
         * 渠道编码，用于定位 MPGS、WorldPay 等渠道适配实现和路由配置。
         */
        private String channelCode;
        private String channelName;
        private String channelMid;
        private String midName;
        private String terminalId;
        /**
         * 业务线类型，用于区分收单、代付等业务域，并隔离渠道配置、限额规则和后台查询口径。
         */
        private String businessType;
        private String paymentMethodScope;
        private String cardBrandScope;
        /**
         * 交易类型，标识本次动作是支付、授权、请款、退款、撤销还是增量授权，用于选择状态机和渠道能力。
         */
        private String transactionTypeScope;
        private String currencyScope;
        private String allowedCountryScope;
        private String defaultTransactionCurrency;
        private String defaultSettlementCurrency;
        private String settlementCycle;
        private LocalTime settlementCutoffTime;
        /**
         * 结算时间时区，使用 IANA 时区标识解释关联的本地日期时间。
         */
        private String settlementTimeZone;
        private String mcc;
        private String statementDescriptor;
        private String metadataValueJson;
        private Integer midStatus;
        /**
         * 业务配置或汇率开始生效的具体时刻。
         */
        private LocalDateTime effectiveTime;
        /**
         * 业务配置、令牌或缓存条目的失效时刻。
         */
        private LocalDateTime expireTime;
        private String remark;
        /**
         * 记录创建时刻，持久化精度为毫秒。
         */
        private LocalDateTime createTime;
        /**
         * 记录最后更新时间，持久化精度为毫秒。
         */
        private LocalDateTime updateTime;
    }

    /**
     * Merchant Channel MID Binding Query 查询条件模型，用于运营后台，承载筛选字段、时间范围、分页参数和列表查询边界。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class MerchantChannelMidBindingQuery extends PageRequest {
        /**
         * 商户号，用于限定商户配置、交易数据、风控规则和权限归属。
         */
        private String merchantId;
        private Long channelId;
        /**
         * 渠道编码，用于定位 MPGS、WorldPay 等渠道适配实现和路由配置。
         */
        private String channelCode;
        /**
         * 业务类型：ACQUIRING 收单、PAYOUT 代付。
         */
        private String businessType;
        private Long midConfigId;
        private Integer bindingStatus;
    }

    /**
     * 商户渠道 MID 绑定保存请求。
     */
    @Data
    public static class MerchantChannelMidBindingSaveRequest {
        /**
         * 商户号，用于限定商户配置、交易数据、风控规则和权限归属。
         */
        @NotBlank(message = "merchantId is required")
        private String merchantId;
        @NotNull(message = "midConfigId is required")
        private Long midConfigId;
        @NotNull(message = "bindingStatus is required")
        private Integer bindingStatus;
        /**
         * 业务配置或汇率开始生效的具体时刻。
         */
        private LocalDateTime effectiveTime;
        /**
         * 业务配置、令牌或缓存条目的失效时刻。
         */
        private LocalDateTime expireTime;
        private String remark;
    }

    /**
     * 商户渠道 MID 绑定响应，展示商户、渠道、业务类型和绑定状态。
     */
    @Data
    public static class MerchantChannelMidBindingResponse {
        private Long id;
        /**
         * 商户号，用于限定商户配置、交易数据、风控规则和权限归属。
         */
        private String merchantId;
        private Long channelId;
        /**
         * 渠道编码，用于定位 MPGS、WorldPay 等渠道适配实现和路由配置。
         */
        private String channelCode;
        private String channelName;
        private Long midConfigId;
        private String channelMid;
        /**
         * 业务类型：ACQUIRING 收单、PAYOUT 代付，来源于所绑定的 MID 配置。
         */
        private String businessType;
        private String midName;
        private Integer bindingStatus;
        /**
         * 业务配置或汇率开始生效的具体时刻。
         */
        private LocalDateTime effectiveTime;
        /**
         * 业务配置、令牌或缓存条目的失效时刻。
         */
        private LocalDateTime expireTime;
        private String remark;
        /**
         * 记录创建时刻，持久化精度为毫秒。
         */
        private LocalDateTime createTime;
        /**
         * 记录最后更新时间，持久化精度为毫秒。
         */
        private LocalDateTime updateTime;
    }

    /**
     * Capability Query 查询条件模型，用于运营后台，承载筛选字段、时间范围、分页参数和列表查询边界。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class CapabilityQuery extends PageRequest {
        private Long channelId;
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
         * 渠道管理币种字段，通常使用 ISO 4217 三位字母代码，不能为空时由上层校验。
         */
        private String currencyCode;
        /**
         * 卡品牌编码，用于渠道能力匹配、路由和运营展示。
         */
        private String cardBrand;
        private Integer capabilityStatus;
    }

    /**
     * 渠道能力写操作请求模型，用于运营后台，承载新增或编辑字段；权限、状态和唯一性由应用服务校验。
     */
    @Data
    public static class CapabilitySaveRequest {
        @NotNull(message = "channelId is required")
        private Long channelId;
        /**
         * 业务线类型，用于区分收单、代付等业务域，并隔离渠道配置、限额规则和后台查询口径。
         */
        @NotBlank(message = "businessType is required")
        private String businessType;
        private String paymentMethod;
        /**
         * 新增支付能力时选择的多个支付方式；服务端会去重，并为每个支付方式创建独立能力记录。
         * 旧版调用方仍可只提交 {@link #paymentMethod}。
         */
        private List<String> paymentMethods = new ArrayList<>();
        /**
         * 交易类型，标识本次动作是支付、授权、请款、退款、撤销还是增量授权，用于选择状态机和渠道能力。
         */
        private String transactionType;
        /**
         * 交易类型，标识本次动作是支付、授权、请款、退款、撤销还是增量授权，用于选择状态机和渠道能力。
         */
        private List<String> transactionTypes = new ArrayList<>();
        private List<String> currencyCodes = new ArrayList<>();
        /**
         * 默认交易币种，必须且只能从允许币种中选择一个。
         */
        @NotBlank(message = "defaultTransactionCurrency is required")
        private String defaultTransactionCurrency;
        private List<String> cardBrands = new ArrayList<>();
        private Integer support3ds;
        private Integer supportIncrementalAuthorization;
        @NotNull(message = "capabilityStatus is required")
        private Integer capabilityStatus;
        private Integer sortOrder;
        /**
         * 渠道管理备注字段，用于记录人工说明，不参与核心状态流转。
         */
        private String remark;
    }

    /**
     * 渠道能力支持标识请求模型，用于运营后台，定义调用方必须提供或可选提供的字段，不直接执行业务逻辑。
     */
    @Data
    public static class CapabilitySupportRequest {
        private Integer support3ds;
        private Integer supportIncrementalAuthorization;
    }

    /**
     * 渠道能力响应模型，用于运营后台，向调用方展示处理结果和必要业务事实，不暴露持久化实体。
     */
    @Data
    public static class CapabilityResponse {
        private Long id;
        private Long channelId;
        /**
         * 渠道编码，用于定位 MPGS、WorldPay 等渠道适配实现和路由配置。
         */
        private String channelCode;
        private String channelName;
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
         * 交易类型，标识本次动作是支付、授权、请款、退款、撤销还是增量授权，用于选择状态机和渠道能力。
         */
        private List<String> transactionTypes = new ArrayList<>();
        private List<String> currencyCodes = new ArrayList<>();
        /**
         * 默认交易币种，渠道不直接支持请求币种时用于交易换汇路由。
         */
        private String defaultTransactionCurrency;
        private List<String> cardBrands = new ArrayList<>();
        private Integer support3ds;
        private Integer supportIncrementalAuthorization;
        private Integer capabilityStatus;
        private Integer sortOrder;
        /**
         * 渠道管理备注字段，用于记录人工说明，不参与核心状态流转。
         */
        private String remark;
        /**
         * 记录创建时刻，持久化精度为毫秒。
         */
        private LocalDateTime createTime;
        /**
         * 记录最后更新时间，持久化精度为毫秒。
         */
        private LocalDateTime updateTime;
    }

    /**
     * Limit Query 查询条件模型，用于运营后台，承载筛选字段、时间范围、分页参数和列表查询边界。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class LimitQuery extends PageRequest {
        private Long channelId;
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
        private Integer ruleStatus;
    }

    /**
     * 限额写操作请求模型，用于运营后台，承载新增或编辑字段；权限、状态和唯一性由应用服务校验。
     */
    @Data
    public static class LimitSaveRequest {
        @NotNull(message = "channelId is required")
        private Long channelId;
        /**
         * 业务线类型，用于区分收单、代付等业务域，并隔离渠道配置、限额规则和后台查询口径。
         */
        @NotBlank(message = "businessType is required")
        private String businessType;
        private String paymentMethod;
        /**
         * 卡品牌编码，用于渠道能力匹配、路由和运营展示。
         */
        private String cardBrand;
        @NotBlank(message = "limitType is required")
        private String limitType;
        @NotNull(message = "limitAmount is required")
        private BigDecimal limitAmount;
        @NotNull(message = "ruleStatus is required")
        private Integer ruleStatus;
        /**
         * 渠道管理备注字段，用于记录人工说明，不参与核心状态流转。
         */
        private String remark;
    }

    /**
     * limit批次写操作请求模型，用于运营后台，承载新增或编辑字段；权限、状态和唯一性由应用服务校验。
     */
    @Data
    public static class LimitBatchSaveRequest {
        @Valid
        private List<LimitSaveRequest> items = new ArrayList<>();
    }

    /**
     * 限额响应模型，用于运营后台，向调用方展示处理结果和必要业务事实，不暴露持久化实体。
     */
    @Data
    public static class LimitResponse {
        private Long id;
        private Long channelId;
        /**
         * 渠道编码，用于定位 MPGS、WorldPay 等渠道适配实现和路由配置。
         */
        private String channelCode;
        private String channelName;
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
         * 记录最后更新人账号标识，用于操作审计。
         */
        private String updateBy;
        /**
         * 记录创建时刻，持久化精度为毫秒。
         */
        private LocalDateTime createTime;
        /**
         * 记录最后更新时间，持久化精度为毫秒。
         */
        private LocalDateTime updateTime;
    }

    /**
     * Status Request 状态变更请求模型，用于运营后台，承载启停、冻结、审核或处理状态更新所需字段。
     */
    @Data
    public static class StatusRequest {
        @NotNull(message = "status is required")
        private Integer status;
    }

    /**
     * 渠道管理页面通用选项 DTO，返回受控代码、名称和可选状态。
     */
    @Data
    public static class ChannelOption {
        private Long id;
        /**
         * 渠道编码，用于定位 MPGS、WorldPay 等渠道适配实现和路由配置。
         */
        private String channelCode;
        private String channelName;
        private Integer channelStatus;
        private Integer supportAcquiring;
        private Integer supportPayout;
        private Integer support3ds;
    }
}
