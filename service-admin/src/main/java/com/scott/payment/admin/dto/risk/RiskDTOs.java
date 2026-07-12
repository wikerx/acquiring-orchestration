package com.scott.payment.admin.dto.risk;

import com.scott.payment.component.core.model.PageRequest;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RiskDTOs
 * @date : 2026-07-05 00:00
 * @email : scott_x@163.com
 * @description : 收单风控管理端请求和响应对象集合，位于 service-admin 传输层，仅服务管理系统配置、查询和审计页面。
 * @status : create
 */
public final class RiskDTOs {

    private RiskDTOs() {
    }

    /**
     * 通用下拉选项。
     */
    @Data
    public static class OptionItem {
        /**
         * 前端展示文案，不包含敏感信息。
         */
        private String label;

        /**
         * 业务值，用于提交给后端。
         */
        private String value;

        /**
         * 扩展字段，国家可承载 Alpha-3，字典可承载样式值。
         */
        private String extra;

        /**
         * 国家数字码，国家地区下拉使用。
         */
        private String numericCode;

        /**
         * 国家旗帜，国家地区下拉使用。
         */
        private String flagEmoji;

        /**
         * 大洲编码，国家地区批量选择时用于分组。
         */
        private String continentCode;

        /**
         * 大洲名称，国家地区批量选择时用于分组展示。
         */
        private String continentName;
    }

    /**
     * 风控页面下拉聚合。
     */
    @Data
    public static class RiskOptionsResponse {
        /**
         * 状态选项，值为 0 或 1。
         */
        private List<OptionItem> statusOptions;

        /**
         * 生效范围选项，支持 GLOBAL 和 MERCHANT。
         */
        private List<OptionItem> merchantScopeOptions;

        /**
         * 风险等级选项，管理端用于配置展示。
         */
        private List<OptionItem> riskLevelOptions;

        /**
         * 决策动作选项，后续风控服务可复用其语义。
         */
        private List<OptionItem> decisionActionOptions;

        /**
         * 卡品牌选项，优先复用 card_brand 字典。
         */
        private List<OptionItem> cardBrandOptions;

        /**
         * 国家地区选项，优先复用 base_iso_country。
         */
        private List<OptionItem> countryOptions;

        /**
         * 币种选项，优先复用 base_iso_currency。
         */
        private List<OptionItem> currencyOptions;

        /**
         * 限额类型选项，优先复用 channel_limit_type 字典。
         */
        private List<OptionItem> limitTypeOptions;

        /**
         * 名单有效期类型选项，管理端新增和编辑名单时使用。
         */
        private List<OptionItem> validityTypeOptions;

        /**
         * 名单来源类型选项，区分手工、导入和系统生成。
         */
        private List<OptionItem> sourceTypeOptions;
    }

    /**
     * 风控功能定义响应。
     */
    @Data
    public static class FunctionDefinitionResponse {
        /**
         * 模块类型：AML、BLACK、WHITE、RULE。
         */
        private String moduleType;

        /**
         * 功能编码，与管理端路由和后端表白名单一一对应。
         */
        private String functionCode;

        /**
         * 功能中文名称，用于页面标题。
         */
        private String functionName;

        /**
         * 前端路由路径。
         */
        private String routePath;

        /**
         * 权限前缀，用于前端按钮权限拼接。
         */
        private String permissionPrefix;

        /**
         * 是否为高风险区域功能。
         */
        private Boolean regionFunction;

        /**
         * 是否为内风控规则功能。
         */
        private Boolean ruleFunction;
    }

    /**
     * 通用名单分页请求。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class RiskListQueryRequest extends PageRequest {
        /**
         * 商户号，商户风控范围下可精确查询，允许为空。
         */
        private String merchantId;

        /**
         * 生效范围：GLOBAL 全局、MERCHANT 商户，允许为空。
         */
        private String merchantScope;

        /**
         * 规则名称，支持模糊查询，允许为空。
         */
        private String ruleName;

        /**
         * 匹配值脱敏展示，支持模糊查询；禁止传完整卡号、手机号、邮箱等敏感明文。
         */
        private String matchValue;

        /**
         * 国家 Alpha-2 编码，格式如 US、CN，允许为空。
         */
        private String countryAlpha2;

        /**
         * 状态：0 停用，1 启用，允许为空。
         */
        private Integer status;
    }

    /**
     * 通用名单保存请求。
     */
    @Data
    public static class RiskListSaveRequest {
        /**
         * 生效范围：GLOBAL 全局、MERCHANT 商户，允许为空，默认 GLOBAL。
         */
        private String merchantScope;

        /**
         * 商户号，仅商户范围生效时必填；全局范围应为空。
         */
        private String merchantId;

        /**
         * 历史兼容字段；名单类页面不再使用规则名称。
         */
        private String ruleName;

        /**
         * 匹配值明文，仅用于新增和编辑提交；后端负责脱敏、哈希和必要密文保存。
         */
        private String matchValuePlain;

        /**
         * 匹配值脱敏展示，兼容历史 CSV 导入；普通管理端页面不应手工提交该字段。
         */
        private String matchValueMasked;

        /**
         * 匹配值哈希，兼容历史 CSV 导入；普通管理端页面不应手工提交该字段。
         */
        private String matchValueHash;

        /**
         * 区间起始值，BIN、IP 等区间类功能使用，允许为空。
         */
        private String matchValueStart;

        /**
         * 区间结束值，BIN、IP 等区间类功能使用，允许为空。
         */
        private String matchValueEnd;

        /**
         * IP版本：IPV4、IPV6，仅 IP 区间名单由后端自动识别并保存。
         */
        private String ipVersion;

        /**
         * 卡品牌，复用 card_brand 字典，允许为空。
         */
        private String cardBrand;

        /**
         * 国家 Alpha-2 编码，格式如 US、CN，允许为空。
         */
        private String countryAlpha2;

        /**
         * 国家 Alpha-2 编码列表，国家类名单新增时用于一次录入多个国家或地区。
         */
        private List<String> countryAlpha2List;

        /**
         * 国家 Alpha-3 编码，格式如 USA、CHN，允许为空。
         */
        private String countryAlpha3;

        /**
         * 国家数字码，ISO 3166-1 numeric，允许为空。
         */
        private String countryNumeric;

        /**
         * 风险等级：LOW、MEDIUM、HIGH、CRITICAL，允许为空。
         */
        private String riskLevel;

        /**
         * 决策动作：PASS、REJECT、REVIEW，允许为空。
         */
        private String decisionAction;

        /**
         * 生效时间，具体时间点，允许为空。
         */
        private LocalDateTime effectiveTime;

        /**
         * 失效时间，具体时间点，允许为空。
         */
        private LocalDateTime expireTime;

        /**
         * 有效期类型：SUPER_LONG 超长期、LONG 长期、LIMITED 限定有效期，允许为空，默认 SUPER_LONG。
         */
        private String validityType;

        /**
         * 有效天数，LONG 和 LIMITED 时必填；LONG 至少 120 天。
         */
        private Integer validityDays;

        /**
         * 来源类型：MANUAL 手工、IMPORT 导入、SYSTEM 系统，允许为空，默认 MANUAL。
         */
        private String sourceType;

        /**
         * 状态：0 停用，1 启用，允许为空，默认启用。
         */
        private Integer status;

        /**
         * 备注，管理端说明文本，允许为空。
         */
        private String remark;
    }

    /**
     * 高风险区域保存请求。
     */
    @Data
    public static class RegionSaveRequest {
        /**
         * 生效范围：GLOBAL 全局、MERCHANT 商户，允许为空，默认 GLOBAL。
         */
        private String merchantScope;

        /**
         * 商户号，仅商户范围生效时必填；全局范围应为空。
         */
        private String merchantId;

        /**
         * 历史兼容字段；高风险区域黑名单不再使用规则名称。
         */
        private String ruleName;

        /**
         * 区域匹配级别：COUNTRY、STATE、CITY，必填。
         */
        @NotBlank(message = "请选择区域级别")
        private String regionMatchLevel;

        /**
         * 历史兼容字段；高风险区域固定由交易 IP 解析区域匹配，管理端不再填写。
         */
        private String matchSource;

        /**
         * 国家 Alpha-2 编码，格式如 US、CN，必填。
         */
        private String countryAlpha2;

        /**
         * 国家 Alpha-2 编码列表，仅国家级高风险区域新增时用于一次录入多条国家记录。
         */
        private List<String> countryAlpha2List;

        /**
         * 国家 Alpha-3 编码，后端根据 countryAlpha2 自动补齐并用于交易区域匹配。
         */
        private String countryAlpha3;

        /**
         * 历史兼容字段；国家名称由国家/地区下拉展示，管理端不再填写。
         */
        private String countryName;

        /**
         * 州省名称，匹配 STATE 或 CITY 时可填写。
         */
        private String stateProvinceName;

        /**
         * 城市名称，匹配 CITY 时可填写。
         */
        private String cityName;

        /**
         * 风险等级：LOW、MEDIUM、HIGH、CRITICAL，允许为空。
         */
        private String riskLevel;

        /**
         * 决策动作：PASS、REJECT、REVIEW，允许为空。
         */
        private String decisionAction;

        /**
         * 生效时间，具体时间点，允许为空。
         */
        private LocalDateTime effectiveTime;

        /**
         * 失效时间，具体时间点，允许为空。
         */
        private LocalDateTime expireTime;

        /**
         * 有效期类型：SUPER_LONG 超长期、LONG 长期、LIMITED 限定有效期，允许为空，默认 SUPER_LONG。
         */
        private String validityType;

        /**
         * 有效天数，LONG 和 LIMITED 时必填；LONG 至少 120 天。
         */
        private Integer validityDays;

        /**
         * 来源类型：MANUAL 手工、IMPORT 导入、SYSTEM 系统，允许为空，默认 MANUAL。
         */
        private String sourceType;

        /**
         * 状态：0 停用，1 启用，允许为空，默认启用。
         */
        private Integer status;

        /**
         * 备注，管理端说明文本，允许为空。
         */
        private String remark;
    }

    /**
     * 规则分页请求。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class RiskRuleQueryRequest extends PageRequest {
        /**
         * 生效范围：GLOBAL 全局、MERCHANT 商户，允许为空。
         */
        private String merchantScope;

        /**
         * 商户号，商户风控范围下可精确查询，允许为空。
         */
        private String merchantId;

        /**
         * 规则名称，支持模糊查询，允许为空。
         */
        private String ruleName;

        /**
         * 规则匹配值，来源网址、国家、BIN 等规则使用，允许为空并支持模糊查询。
         */
        private String matchValue;

        /**
         * 商户录入的来源网址，商户来源网址限定查询使用，允许为空并支持模糊查询。
         */
        private String sourceUrl;

        /**
         * 来源网址 host，商户来源网址限定查询使用，允许为空并支持模糊查询。
         */
        private String sourceHost;

        /**
         * 限额类型，商户交易限额规则查询使用，允许为空。
         */
        private String limitType;

        /**
         * 3DS 规则类型，区分风险策略、豁免策略和渠道默认策略，允许为空。
         */
        private String ruleType;

        /**
         * 收单渠道编码，3DS 规则查询使用；ALL 表示全部渠道。
         */
        private String channelCode;

        /**
         * 支付方式，3DS 规则查询使用；ALL 表示全部支付方式。
         */
        private String paymentMethod;

        /**
         * 卡品牌，3DS 规则查询使用；ALL 表示全部卡品牌。
         */
        private String cardBrand;

        /**
         * 交易币种，ISO 4217 Alpha-3，允许为空。
         */
        private String currency;

        /**
         * 3DS 触发动作：FORCE_3DS、SKIP_3DS、FOLLOW_DEFAULT，允许为空。
         */
        private String triggerAction;

        /**
         * 状态：0 停用，1 启用，允许为空。
         */
        private Integer status;
    }

    /**
     * 规则保存请求。
     */
    @Data
    public static class RiskRuleSaveRequest {
        /**
         * 生效范围：GLOBAL 全局、MERCHANT 商户，允许为空，默认 GLOBAL。
         */
        private String merchantScope;

        /**
         * 商户号，仅商户范围生效时必填；全局范围应为空。
         */
        private String merchantId;

        /**
         * 商户名称，3DS 等规则保存商户快照时使用，允许为空并由后端兜底查询。
         */
        private String merchantName;

        /**
         * 3DS 规则组编号，新增时允许为空并由后端生成。
         */
        private String ruleGroupNo;

        /**
         * 规则名称，普通内风控规则必填；商户来源网址限定不使用该字段。
         */
        private String ruleName;

        /**
         * 匹配方式：EXACT、DOMAIN、CONTAINS、REGEX，允许为空；商户来源网址限定固定按 host 匹配。
         */
        private String matchMode;

        /**
         * 规则匹配值，来源网址、国家、BIN 等规则使用；不得保存卡号、CVV 等敏感明文。
         */
        private String matchValue;

        /**
         * 商户录入的来源网址，商户来源网址限定使用，必须以 http:// 或 https:// 开头。
         */
        private String sourceUrl;

        /**
         * 商户录入的来源网址列表，商户来源网址限定批量新增时使用。
         */
        private List<String> sourceUrls;

        /**
         * 来源网址 host，后端从 sourceUrl 解析生成，管理端查询时允许精确过滤。
         */
        private String sourceHost;

        /**
         * 限额类型，优先复用 channel_limit_type 字典，允许为空。
         */
        private String limitType;

        /**
         * 最小金额，业务金额使用 BigDecimal，允许为空。
         */
        private BigDecimal amountMin;

        /**
         * 最大金额，业务金额使用 BigDecimal，允许为空。
         */
        private BigDecimal amountMax;

        /**
         * 交易币种，ISO 4217 Alpha-3，允许为空。
         */
        private String currency;

        /**
         * 3DS 规则类型：RISK_STRATEGY、EXEMPTION_STRATEGY、CHANNEL_POLICY。
         */
        private String ruleType;

        /**
         * 收单渠道编码；3DS 规则使用，ALL 表示全部渠道。
         */
        private String channelCode;

        /**
         * 支付方式；3DS 规则使用，默认 BANK_CARD，ALL 表示全部支付方式。
         */
        private String paymentMethod;

        /**
         * 卡品牌；3DS 规则使用，ALL 表示全部卡品牌。
         */
        private String cardBrand;

        /**
         * 卡品牌列表；3DS 新增时允许一次选择多个品牌，后端按单品牌拆分成多条规则保存。
         */
        private List<String> cardBrands;

        /**
         * 金额匹配类型：ALL、GE、LE、BETWEEN。
         */
        private String amountMatchType;

        /**
         * 3DS 风险条件：ANY、LOW_AND_ABOVE、MEDIUM_AND_ABOVE、HIGH_AND_ABOVE、CRITICAL_ONLY。
         */
        private String riskCondition;

        /**
         * 3DS 触发动作：FORCE_3DS、SKIP_3DS、FOLLOW_DEFAULT。
         */
        private String triggerAction;

        /**
         * 规则优先级，数字越小越优先。
         */
        private Integer priority;

        /**
         * 时间窗口秒数，频率类规则使用，允许为空。
         */
        private Integer timeWindowSeconds;

        /**
         * 阈值次数，频率类规则使用，允许为空。
         */
        private Integer thresholdCount;

        /**
         * 组合元素 JSON，频率类规则描述参与统计的元素，允许为空。
         */
        private String elementsJson;

        /**
         * 风险等级：LOW、MEDIUM、HIGH、CRITICAL，允许为空。
         */
        private String riskLevel;

        /**
         * 决策动作：PASS、REJECT、REVIEW，允许为空。
         */
        private String decisionAction;

        /**
         * 生效时间，具体时间点，允许为空。
         */
        private LocalDateTime effectiveTime;

        /**
         * 失效时间，具体时间点，允许为空。
         */
        private LocalDateTime expireTime;

        /**
         * 状态：0 停用，1 启用，允许为空，默认启用。
         */
        private Integer status;

        /**
         * 备注，管理端说明文本，允许为空。
         */
        private String remark;
    }

    /**
     * 商户来源网址批量保存请求。
     */
    @Data
    public static class RiskSourceUrlBatchSaveRequest {
        /**
         * 商户号，来源网址限定按商户号直接生效。
         */
        @NotBlank(message = "请输入商户号")
        private String merchantId;

        /**
         * 来源网址列表，每个值必须以 http:// 或 https:// 开头。
         */
        private List<String> sourceUrls;

        /**
         * 风险等级：LOW、MEDIUM、HIGH、CRITICAL，允许为空。
         */
        private String riskLevel;

        /**
         * 决策动作：PASS、REJECT、REVIEW，允许为空。
         */
        private String decisionAction;

        /**
         * 生效时间，具体时间点，允许为空。
         */
        private LocalDateTime effectiveTime;

        /**
         * 失效时间，具体时间点，允许为空。
         */
        private LocalDateTime expireTime;

        /**
         * 状态：0 停用，1 启用，允许为空，默认启用。
         */
        private Integer status;

        /**
         * 备注，管理端说明文本，允许为空。
         */
        private String remark;
    }

    /**
     * 状态更新请求。
     */
    @Data
    public static class StatusUpdateRequest {
        /**
         * 目标状态：0 停用，1 启用，必填。
         */
        private Integer status;
    }

    /**
     * 批量删除请求。
     */
    @Data
    public static class BatchRemoveRequest {
        /**
         * 待删除配置记录ID列表；仅允许删除当前功能物理表内未删除记录。
         */
        private List<Long> ids;
    }

    /**
     * 配置文件导入结果。
     */
    @Data
    public static class ImportResultResponse {
        /**
         * 成功导入条数。
         */
        private Integer successCount;

        /**
         * 失败条数；当前事务策略下失败会整体回滚。
         */
        private Integer failureCount;

        /**
         * 错误信息列表，不包含敏感明文。
         */
        private List<String> errors;
    }

    /**
     * 通用风险记录响应。
     */
    @Data
    public static class RiskRecordResponse {
        /**
         * 记录主键ID。
         */
        private Long id;

        /**
         * 生效范围：GLOBAL 全局、MERCHANT 商户。
         */
        private String merchantScope;

        /**
         * 商户号，允许为空。
         */
        private String merchantId;

        /**
         * 商户名称，允许为空。
         */
        private String merchantName;

        /**
         * 规则名称。
         */
        private String ruleName;

        /**
         * 3DS 规则组编号，供后续交易策略按组管理和排查使用。
         */
        private String ruleGroupNo;

        /**
         * 匹配值脱敏展示，响应中禁止出现完整敏感明文。
         */
        private String matchValueMasked;

        /**
         * 匹配值明文，仅编辑接口在授权后返回；列表、详情、导出不返回。
         */
        private String matchValuePlain;

        /**
         * 匹配值哈希，后续交易检索使用；管理端列表和详情默认不展示。
         */
        private String matchValueHash;

        /**
         * 区间起始值，允许为空。
         */
        private String matchValueStart;

        /**
         * 区间结束值，允许为空。
         */
        private String matchValueEnd;

        /**
         * IP版本：IPV4、IPV6，仅 IP 区间名单用于后续交易检索分流。
         */
        private String ipVersion;

        /**
         * 卡品牌，允许为空。
         */
        private String cardBrand;

        /**
         * 3DS 规则类型，允许为空。
         */
        private String ruleType;

        /**
         * 收单渠道编码，允许为空。
         */
        private String channelCode;

        /**
         * 支付方式，允许为空。
         */
        private String paymentMethod;

        /**
         * 金额匹配类型：ALL、GE、LE、BETWEEN。
         */
        private String amountMatchType;

        /**
         * 3DS 风险条件。
         */
        private String riskCondition;

        /**
         * 3DS 触发动作。
         */
        private String triggerAction;

        /**
         * 规则优先级，数字越小越优先。
         */
        private Integer priority;

        /**
         * 国家 Alpha-2 编码，允许为空。
         */
        private String countryAlpha2;

        /**
         * 国家 Alpha-3 编码，允许为空。
         */
        private String countryAlpha3;

        /**
         * 国家数字码，允许为空。
         */
        private String countryNumeric;

        /**
         * 风险等级。
         */
        private String riskLevel;

        /**
         * 决策动作。
         */
        private String decisionAction;

        /**
         * 生效时间。
         */
        private LocalDateTime effectiveTime;

        /**
         * 失效时间。
         */
        private LocalDateTime expireTime;

        /**
         * 有效期类型：SUPER_LONG、LONG、LIMITED。
         */
        private String validityType;

        /**
         * 有效天数，长期和限定有效期使用。
         */
        private Integer validityDays;

        /**
         * 来源类型：MANUAL、IMPORT、SYSTEM。
         */
        private String sourceType;

        /**
         * 状态：0 停用，1 启用。
         */
        private Integer status;

        /**
         * 备注。
         */
        private String remark;

        /**
         * 创建人。
         */
        private String createBy;

        /**
         * 更新人。
         */
        private String updateBy;

        /**
         * 创建时间。
         */
        private LocalDateTime createTime;

        /**
         * 更新时间。
         */
        private LocalDateTime updateTime;

        /**
         * 区域匹配级别：COUNTRY、STATE、CITY。
         */
        private String regionMatchLevel;

        /**
         * 历史兼容字段；高风险区域固定由交易 IP 解析区域匹配。
         */
        private String matchSource;

        /**
         * 国家或地区名称。
         */
        private String countryName;

        /**
         * 州省编码。
         */
        private String stateProvinceCode;

        /**
         * 州省名称。
         */
        private String stateProvinceName;

        /**
         * 城市编码。
         */
        private String cityCode;

        /**
         * 城市名称。
         */
        private String cityName;

        /**
         * 规则匹配方式。
         */
        private String matchMode;

        /**
         * 规则匹配值，不应包含敏感明文。
         */
        private String matchValue;

        /**
         * 商户录入的来源网址。
         */
        private String sourceUrl;

        /**
         * 来源网址 host，交易链路按商户号和该字段匹配。
         */
        private String sourceHost;

        /**
         * 限额类型。
         */
        private String limitType;

        /**
         * 最小金额。
         */
        private BigDecimal amountMin;

        /**
         * 最大金额。
         */
        private BigDecimal amountMax;

        /**
         * 交易币种，ISO 4217 Alpha-3。
         */
        private String currency;

        /**
         * 时间窗口秒数。
         */
        private Integer timeWindowSeconds;

        /**
         * 阈值次数。
         */
        private Integer thresholdCount;

        /**
         * 组合元素 JSON。
         */
        private String elementsJson;
    }

    /**
     * 系统交易加黑分页请求。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class TradeBlackQueryRequest extends PageRequest {
        /**
         * 商户号，允许为空。
         */
        private String merchantId;

        /**
         * 商户订单号，允许为空。
         */
        private String merchantOrderNo;

        /**
         * 平台支付订单号，允许为空。
         */
        private String paymentOrderNo;

        /**
         * 加黑对象类型：CARD、CARD_FINGERPRINT、EMAIL、PHONE、IP、DEVICE、CUSTOMER，允许为空。
         */
        private String blackTargetType;

        /**
         * 状态：0 已解除，1 已加黑，允许为空。
         */
        private Integer status;
    }

    /**
     * 系统交易加黑保存请求。
     */
    @Data
    public static class TradeBlackSaveRequest {
        /**
         * 商户号，允许为空。
         */
        private String merchantId;

        /**
         * 商户名称，允许为空。
         */
        private String merchantName;

        /**
         * 商户订单号，允许为空。
         */
        private String merchantOrderNo;

        /**
         * 平台支付订单号，允许为空。
         */
        private String paymentOrderNo;

        /**
         * 加黑对象类型：CARD、CARD_FINGERPRINT、EMAIL、PHONE、IP、DEVICE、CUSTOMER，必填。
         */
        @NotBlank(message = "请选择加黑对象")
        private String blackTargetType;

        /**
         * 加黑对象脱敏展示值，必填；禁止保存完整卡号、手机号、邮箱等敏感明文。
         */
        @NotBlank(message = "请输入对象脱敏值")
        private String blackTargetValueMasked;

        /**
         * 加黑对象哈希，允许为空。
         */
        private String blackTargetHash;

        /**
         * 来源类型：MANUAL、BATCH、SYSTEM，允许为空，默认 MANUAL。
         */
        private String sourceType;

        /**
         * 动作类型：ADD、RELEASE，允许为空，默认 ADD。
         */
        private String actionType;

        /**
         * 操作原因，允许为空。
         */
        private String actionReason;

        /**
         * 状态：0 已解除，1 已加黑，允许为空，默认已加黑。
         */
        private Integer status;
    }

    /**
     * 风控评估记录分页请求。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class EvaluationQueryRequest extends PageRequest {
        /**
         * 商户号，允许为空。
         */
        private String merchantId;

        /**
         * 商户订单号，允许为空。
         */
        private String merchantOrderNo;

        /**
         * 平台支付订单号，允许为空。
         */
        private String paymentOrderNo;

        /**
         * 决策结果：PASS、REJECT、REVIEW，允许为空。
         */
        private String decisionResult;
    }
}
