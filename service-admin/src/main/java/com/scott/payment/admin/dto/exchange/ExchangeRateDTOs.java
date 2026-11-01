package com.scott.payment.admin.dto.exchange;

import com.scott.payment.component.core.model.PageRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理端汇率业务接口模型集合，统一定义汇率源、原始汇率、业务汇率及使用快照的查询和响应契约。
 */
public final class ExchangeRateDTOs {

    /**
     * 阻止仅提供静态能力的类型被实例化。
     */
    private ExchangeRateDTOs() {
    }

    /**
     * 管理端通用状态切换请求。
     */
    @Data
    public static class StatusRequest {
        @NotNull(message = "status is required")
        private Integer status;
    }

    /**
     * 原始汇率作废请求。
     */
    @Data
    public static class VoidRequest {
        /**
         * 请求中的撤销原因，用于限定本次操作的输入和校验范围。
         */
        @NotBlank(message = "voidReason is required")
        private String voidReason;
    }

    /**
     * 业务汇率生成请求。
     */
    @Data
    public static class GenerateBusinessRateRequest {

        @NotNull(message = "rawRateId is required")
        private Long rawRateId;

        @NotNull(message = "ruleId is required")
        private Long ruleId;
        /**
         * 汇率管理备注字段，用于记录人工说明，不参与核心状态流转。
         */
        private String remark;
    }

    /**
     * 手工录入可直接使用的业务汇率请求。
     */
    @Data
    public static class BusinessRateSaveRequest {
        /**
         * 汇率类型，用于区分基准汇率、业务汇率、买入价、卖出价或渠道报价。
         */
        @NotBlank(message = "rateType is required")
        private String rateType;
        @NotBlank(message = "sourceCode is required")
        private String sourceCode;

        @NotBlank(message = "baseCurrency is required")
        private String baseCurrency;

        @NotBlank(message = "quoteCurrency is required")
        private String quoteCurrency;

        /**
         * 请求中的原始汇率，用于限定本次操作的输入和校验范围。
         */
        @NotNull(message = "originalRate is required")
        private BigDecimal originalRate;

        /**
         * 请求中的最终汇率，用于限定本次操作的输入和校验范围。
         */
        @NotNull(message = "finalRate is required")
        private BigDecimal finalRate;
        /**
         * 业务配置或汇率开始生效的具体时刻。
         */
        @NotNull(message = "effectiveTime is required")
        private LocalDateTime effectiveTime;
        private String rateStatus;
        /**
         * 汇率管理备注字段，用于记录人工说明，不参与核心状态流转。
         */
        private String remark;
    }

    /**
     * 批量手工录入业务汇率请求。
     */
    @Data
    public static class BusinessRateBatchSaveRequest {
        @Valid
        @Size(min = 1, max = 500, message = "items size must be between 1 and 500")
        private List<BusinessRateSaveRequest> items;
    }

    /**
     * 汇率源分页查询条件。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class SourceQuery extends PageRequest {
        /**
         * 汇率管理敏感或密钥相关字段，日志和接口展示必须脱敏，必要时仅保存密文。
         */
        private String keyword;
        /**
         * 数据来源类型，用于区分手工维护、文件导入、外部抓取或系统初始化数据。
         */
        private String sourceType;
        private Integer sourceStatus;
    }

    /**
     * 汇率源新增或修改请求。
     */
    @Data
    public static class SourceSaveRequest {
        @NotBlank(message = "sourceCode is required")
        private String sourceCode;
        @NotBlank(message = "sourceName is required")
        private String sourceName;
        /**
         * 数据来源类型，用于区分手工维护、文件导入、外部抓取或系统初始化数据。
         */
        @NotBlank(message = "sourceType is required")
        private String sourceType;

        private String requestUrl;
        /**
         * 请求中的默认来源，用于限定本次操作的输入和校验范围。
         */
        private Integer defaultSource;
        /**
         * 匹配优先级，数值越小越优先。
         */
        private Integer priority;
        /**
         * 远程调用超时时间，单位为秒。
         */
        private Integer timeoutSeconds;
        @NotNull(message = "sourceStatus is required")
        private Integer sourceStatus;
        /**
         * 汇率管理备注字段，用于记录人工说明，不参与核心状态流转。
         */
        private String remark;
    }

    /**
     * 汇率源管理页面响应。
     */
    @Data
    public static class SourceResponse {
        private Long id;
        private String sourceCode;
        private String sourceName;
        /**
         * 数据来源类型，用于区分手工维护、文件导入、外部抓取或系统初始化数据。
         */
        private String sourceType;

        private String requestUrl;
        private Integer defaultSource;
        /**
         * 匹配优先级，数值越小越优先。
         */
        private Integer priority;
        /**
         * 远程调用超时时间，单位为秒。
         */
        private Integer timeoutSeconds;
        private Integer sourceStatus;
        private LocalDateTime lastFetchTime;
        private String lastFetchStatus;
        /**
         * 汇率管理备注字段，用于记录人工说明，不参与核心状态流转。
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
     * 原始汇率分页查询条件。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class RawRateQuery extends PageRequest {
        private String sourceCode;

        private String baseCurrency;

        private String quoteCurrency;
        private String rateStatus;
        private String createMethod;
        /**
         * 发布时间查询下界，按系统业务时区解释，包含边界，允许为空，非敏感字段。
         */
        private LocalDateTime publishStartTime;
        /**
         * 发布时间查询上界，按系统业务时区解释，包含边界，允许为空，非敏感字段。
         */
        private LocalDateTime publishEndTime;
        /**
         * 拉取时间查询下界，按系统业务时区解释，包含边界，允许为空，非敏感字段。
         */
        private LocalDateTime fetchStartTime;
        /**
         * 拉取时间查询上界，按系统业务时区解释，包含边界，允许为空，非敏感字段。
         */
        private LocalDateTime fetchEndTime;

        /**
         * 原始汇率生效时间查询下界，按系统业务时区解释，包含边界，允许为空，非敏感字段。
         */
        private LocalDateTime effectiveStartTime;

        /**
         * 原始汇率生效时间查询上界，按系统业务时区解释，包含边界，允许为空，非敏感字段。
         */
        private LocalDateTime effectiveEndTime;
    }

    /**
     * 手工录入原始汇率请求。
     */
    @Data
    public static class RawRateSaveRequest {
        @NotBlank(message = "sourceCode is required")
        private String sourceCode;

        @NotBlank(message = "baseCurrency is required")
        private String baseCurrency;

        @NotBlank(message = "quoteCurrency is required")
        private String quoteCurrency;

        private BigDecimal cashBuyRate;

        private BigDecimal cashSellRate;

        private BigDecimal spotBuyRate;

        private BigDecimal spotSellRate;

        private BigDecimal middleRate;

        @NotNull(message = "publishTime is required")
        private LocalDateTime publishTime;

        /**
         * 业务配置或汇率开始生效的具体时刻。
         */
        private LocalDateTime effectiveTime;
        /**
         * 批次号，用于关联同一次导入、抓取、清分或结算处理的记录。
         */
        private String batchNo;
        /**
         * 汇率管理备注字段，用于记录人工说明，不参与核心状态流转。
         */
        private String remark;
    }

    /**
     * 原始汇率页面响应。
     */
    @Data
    public static class RawRateResponse {
        private Long id;
        private String sourceCode;

        private String baseCurrency;

        private String quoteCurrency;
        private BigDecimal cashBuyRate;
        private BigDecimal cashSellRate;
        private BigDecimal spotBuyRate;
        private BigDecimal spotSellRate;
        private BigDecimal middleRate;
        private LocalDateTime publishTime;
        private LocalDateTime fetchTime;
        /**
         * 业务配置或汇率开始生效的具体时刻。
         */
        private LocalDateTime effectiveTime;
        private String createMethod;
        /**
         * 批次号，用于关联同一次导入、抓取、清分或结算处理的记录。
         */
        private String batchNo;
        private String rateStatus;
        private String voidReason;
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
     * 汇率规则分页查询条件。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class RuleQuery extends PageRequest {
        /**
         * 汇率类型，用于区分基准汇率、业务汇率、买入价、卖出价或渠道报价。
         */
        private String rateType;
        private String sourceCode;

        private String baseCurrency;

        private String quoteCurrency;
        private String adjustDirection;
        private String adjustMethod;
        private Integer ruleStatus;
    }

    /**
     * 汇率规则新增或修改请求。
     */
    @Data
    public static class RuleSaveRequest {
        /**
         * 汇率类型，用于区分基准汇率、业务汇率、买入价、卖出价或渠道报价。
         */
        @NotBlank(message = "rateType is required")
        private String rateType;
        @NotBlank(message = "sourceCode is required")
        private String sourceCode;

        @NotBlank(message = "baseCurrency is required")
        private String baseCurrency;

        @NotBlank(message = "quoteCurrency is required")
        private String quoteCurrency;
        /**
         * 请求中的汇率字段，用于限定本次操作的输入和校验范围。
         */
        @NotBlank(message = "rateField is required")
        private String rateField;
        @NotBlank(message = "adjustDirection is required")
        private String adjustDirection;
        @NotBlank(message = "adjustMethod is required")
        private String adjustMethod;

        @NotNull(message = "adjustValue is required")
        private BigDecimal adjustValue;

        @NotNull(message = "decimalScale is required")
        private Integer decimalScale;
        /**
         * 请求中的舍入模式，用于限定本次操作的输入和校验范围。
         */
        @NotBlank(message = "roundingMode is required")
        private String roundingMode;
        /**
         * 匹配优先级，数值越小越优先。
         */
        private Integer priority;
        /**
         * 请求中的生效开始时间，用于限定本次操作的输入和校验范围。
         */
        private LocalDateTime effectiveStartTime;
        /**
         * 请求中的生效结束时间，用于限定本次操作的输入和校验范围。
         */
        private LocalDateTime effectiveEndTime;
        @NotNull(message = "ruleStatus is required")
        private Integer ruleStatus;
        /**
         * 汇率管理备注字段，用于记录人工说明，不参与核心状态流转。
         */
        private String remark;
    }

    /**
     * 汇率规则页面响应。
     */
    @Data
    public static class RuleResponse {
        private Long id;
        /**
         * 汇率类型，用于区分基准汇率、业务汇率、买入价、卖出价或渠道报价。
         */
        private String rateType;
        private String sourceCode;

        private String baseCurrency;

        private String quoteCurrency;
        private String rateField;
        private String adjustDirection;
        private String adjustMethod;
        private BigDecimal adjustValue;
        private Integer decimalScale;
        private String roundingMode;
        /**
         * 匹配优先级，数值越小越优先。
         */
        private Integer priority;
        private LocalDateTime effectiveStartTime;
        private LocalDateTime effectiveEndTime;
        private Integer ruleStatus;
        /**
         * 汇率管理备注字段，用于记录人工说明，不参与核心状态流转。
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
     * 业务汇率分页和导出查询条件，时间范围均按闭区间处理。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class BusinessRateQuery extends PageRequest {
        /**
         * 汇率类型，用于区分基准汇率、业务汇率、买入价、卖出价或渠道报价。
         */
        private String rateType;
        private String sourceCode;

        private String baseCurrency;

        private String quoteCurrency;
        private String rateStatus;
        /**
         * 生成方式，取值为 AUTO 或 MANUAL，允许为空，非敏感字段。
         */
        private String generateMethod;

        /**
         * 业务汇率生效时间查询下界，按系统业务时区解释，包含边界，允许为空，非敏感字段。
         */
        private LocalDateTime effectiveStartTime;

        /**
         * 业务汇率生效时间查询上界，按系统业务时区解释，包含边界，允许为空，非敏感字段。
         */
        private LocalDateTime effectiveEndTime;

        /**
         * 业务汇率创建时间查询下界，按系统业务时区解释，包含边界，允许为空，非敏感字段。
         */
        private LocalDateTime createStartTime;

        /**
         * 业务汇率创建时间查询上界，按系统业务时区解释，包含边界，允许为空，非敏感字段。
         */
        private LocalDateTime createEndTime;
    }

    /**
     * 业务汇率页面响应。
     */
    @Data
    public static class BusinessRateResponse {
        private Long id;
        /**
         * 汇率类型，用于区分基准汇率、业务汇率、买入价、卖出价或渠道报价。
         */
        private String rateType;
        private String sourceCode;

        private String baseCurrency;

        private String quoteCurrency;
        private Long rawRateId;
        private Long ruleId;

        private BigDecimal originalRate;

        private BigDecimal finalRate;
        private String adjustDescription;
        /**
         * 业务汇率开始生效的时间点，按系统业务时区返回，不允许为空，非敏感字段。
         */
        private LocalDateTime effectiveTime;
        /**
         * 业务汇率失效时间；为空表示永不过期，按系统业务时区返回，非敏感字段。
         */
        private LocalDateTime expireTime;
        /**
         * 生成方式，AUTO 表示按原始汇率和规则生成，MANUAL 表示人工录入，非敏感字段。
         */
        private String generateMethod;
        private String rateStatus;
        /**
         * 汇率管理备注字段，用于记录人工说明，不参与核心状态流转。
         */
        private String remark;
        /**
         * 记录创建时间，按系统业务时区返回，不允许为空，非敏感字段。
         */
        private LocalDateTime createTime;

        /**
         * 最近一次修改记录的管理员名称；历史数据可能为空，非敏感字段。
         */
        private String updateBy;

        /**
         * 最近更新时间，按系统业务时区返回，不允许为空，非敏感字段。
         */
        private LocalDateTime updateTime;
    }

    /**
     * 汇率使用快照分页查询条件。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class UsageSnapshotQuery extends PageRequest {
        /**
         * 汇率类型，用于区分基准汇率、业务汇率、买入价、卖出价或渠道报价。
         */
        private String rateType;
        private String usageScene;
        /**
         * 业务线类型，用于区分收单、代付等业务域，并隔离渠道配置、限额规则和后台查询口径。
         */
        private String businessType;
        private String businessNo;

        private String baseCurrency;

        private String quoteCurrency;
        private LocalDateTime appliedStartTime;
        private LocalDateTime appliedEndTime;
    }

    /**
     * 汇率使用快照页面响应。
     */
    @Data
    public static class UsageSnapshotResponse {
        private Long id;
        /**
         * 汇率类型，用于区分基准汇率、业务汇率、买入价、卖出价或渠道报价。
         */
        private String rateType;
        private String usageScene;
        /**
         * 业务线类型，用于区分收单、代付等业务域，并隔离渠道配置、限额规则和后台查询口径。
         */
        private String businessType;
        private String businessNo;

        private String baseCurrency;

        private String quoteCurrency;

        private BigDecimal usedRate;
        private Long businessRateId;
        private Long rawRateId;
        private Long ruleId;
        private String calculationDescription;
        private LocalDateTime appliedTime;
        /**
         * 记录创建时刻，持久化精度为毫秒。
         */
        private LocalDateTime createTime;
    }
}
