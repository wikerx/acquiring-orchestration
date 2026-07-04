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
 * @author : scott
 * @version : v1.0.0
 * @classname : ExchangeRateDTOs
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 汇率管理Exchange Rate  DTO 集合，位于 service-admin 的接口传输层，用于说明职责边界、数据语义和关键业务约束。
 * @status : create
 */
public final class ExchangeRateDTOs {

    private ExchangeRateDTOs() {
    }

    /**
     * 管理端通用状态切换请求。
     */
    @Data
    public static class StatusRequest {
        /**
         * 汇率管理状态字段，取值需与数据字典或枚举约定保持一致。
         */
        @NotNull(message = "status is required")
        private Integer status;
    }

    /**
     * 原始汇率作废请求。
     */
    @Data
    public static class VoidRequest {
        /**
         * 汇率管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        @NotBlank(message = "voidReason is required")
        private String voidReason;
    }

    /**
     * 业务汇率生成请求。
     */
    @Data
    public static class GenerateBusinessRateRequest {

        /** 原始汇率记录 ID，用于选择汇率源报价。 */
        /**
         * 汇率管理金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
         */
        @NotNull(message = "rawRateId is required")
        private Long rawRateId;

        /** 汇率规则 ID，用于决定取值字段、调整方式和舍入规则。 */
        /**
         * 汇率管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
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
         * 汇率管理金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
         */
        @NotBlank(message = "rateType is required")
        private String rateType;
        /**
         * 汇率管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        @NotBlank(message = "sourceCode is required")
        private String sourceCode;
        /**
         * 汇率管理币种字段，通常使用 ISO 4217 三位字母代码，不能为空时由上层校验。
         */
        @NotBlank(message = "baseCurrency is required")
        private String baseCurrency;
        /**
         * 汇率管理币种字段，通常使用 ISO 4217 三位字母代码，不能为空时由上层校验。
         */
        @NotBlank(message = "quoteCurrency is required")
        private String quoteCurrency;

        /** 管理端手工录入的原始汇率，后端继续以 BigDecimal 解析，避免前端浮点数精度损失。 */
        /**
         * 汇率管理金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
         */
        @NotNull(message = "originalRate is required")
        private BigDecimal originalRate;

        /** 最终可用业务汇率，保存后可直接被交易或结算链路读取。 */
        /**
         * 汇率管理金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
         */
        @NotNull(message = "finalRate is required")
        private BigDecimal finalRate;
        /**
         * 汇率管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        @NotNull(message = "effectiveTime is required")
        private LocalDateTime effectiveTime;
        /**
         * 汇率管理金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
         */
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
        /**
         * 汇率管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
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
         * 汇率管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String sourceType;
        /**
         * 汇率管理状态字段，取值需与数据字典或枚举约定保持一致。
         */
        private Integer sourceStatus;
    }

    /**
     * 汇率源新增或修改请求。
     */
    @Data
    public static class SourceSaveRequest {
        /**
         * 汇率管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        @NotBlank(message = "sourceCode is required")
        private String sourceCode;
        /**
         * 汇率管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        @NotBlank(message = "sourceName is required")
        private String sourceName;
        /**
         * 汇率管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        @NotBlank(message = "sourceType is required")
        private String sourceType;
        /**
         * 汇率管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String requestUrl;
        /**
         * 汇率管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Integer defaultSource;
        /**
         * 汇率管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Integer priority;
        /**
         * 汇率管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private Integer timeoutSeconds;
        /**
         * 汇率管理状态字段，取值需与数据字典或枚举约定保持一致。
         */
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
        /**
         * 汇率管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private Long id;
        /**
         * 汇率管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String sourceCode;
        /**
         * 汇率管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String sourceName;
        /**
         * 汇率管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String sourceType;
        /**
         * 汇率管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String requestUrl;
        /**
         * 汇率管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Integer defaultSource;
        /**
         * 汇率管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Integer priority;
        /**
         * 汇率管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private Integer timeoutSeconds;
        /**
         * 汇率管理状态字段，取值需与数据字典或枚举约定保持一致。
         */
        private Integer sourceStatus;
        /**
         * 汇率管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime lastFetchTime;
        /**
         * 汇率管理状态字段，取值需与数据字典或枚举约定保持一致。
         */
        private String lastFetchStatus;
        /**
         * 汇率管理备注字段，用于记录人工说明，不参与核心状态流转。
         */
        private String remark;
        /**
         * 汇率管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime createTime;
        /**
         * 汇率管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime updateTime;
    }

    /**
     * 原始汇率分页查询条件。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class RawRateQuery extends PageRequest {
        /**
         * 汇率管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String sourceCode;
        /**
         * 汇率管理币种字段，通常使用 ISO 4217 三位字母代码，不能为空时由上层校验。
         */
        private String baseCurrency;
        /**
         * 汇率管理币种字段，通常使用 ISO 4217 三位字母代码，不能为空时由上层校验。
         */
        private String quoteCurrency;
        /**
         * 汇率管理金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
         */
        private String rateStatus;
        /**
         * 汇率管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String createMethod;
        /**
         * 汇率管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime publishStartTime;
        /**
         * 汇率管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime publishEndTime;
        /**
         * 汇率管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime fetchStartTime;
        /**
         * 汇率管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime fetchEndTime;
    }

    /**
     * 手工录入原始汇率请求。
     */
    @Data
    public static class RawRateSaveRequest {
        /**
         * 汇率管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        @NotBlank(message = "sourceCode is required")
        private String sourceCode;
        /**
         * 汇率管理币种字段，通常使用 ISO 4217 三位字母代码，不能为空时由上层校验。
         */
        @NotBlank(message = "baseCurrency is required")
        private String baseCurrency;
        /**
         * 汇率管理币种字段，通常使用 ISO 4217 三位字母代码，不能为空时由上层校验。
         */
        @NotBlank(message = "quoteCurrency is required")
        private String quoteCurrency;

        /** 现钞买入价，保留高精度 BigDecimal，禁止使用 double/float。 */
        /**
         * 汇率管理金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
         */
        private BigDecimal cashBuyRate;

        /** 现钞卖出价，保留高精度 BigDecimal，禁止使用 double/float。 */
        /**
         * 汇率管理金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
         */
        private BigDecimal cashSellRate;

        /** 现汇买入价，保留高精度 BigDecimal，禁止使用 double/float。 */
        /**
         * 汇率管理金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
         */
        private BigDecimal spotBuyRate;

        /** 现汇卖出价，保留高精度 BigDecimal，禁止使用 double/float。 */
        /**
         * 汇率管理金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
         */
        private BigDecimal spotSellRate;

        /** 中间折算价，保留高精度 BigDecimal，禁止使用 double/float。 */
        /**
         * 汇率管理金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
         */
        private BigDecimal middleRate;

        /** 外部汇率源发布时间，数据库使用 DATETIME(3) 保存。 */
        /**
         * 汇率管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        @NotNull(message = "publishTime is required")
        private LocalDateTime publishTime;

        /** 原始汇率生效时间；为空时使用 publishTime。 */
        /**
         * 汇率管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime effectiveTime;
        /**
         * 汇率管理编码或编号字段，用于业务识别、查询和幂等关联。
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
        /**
         * 汇率管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private Long id;
        /**
         * 汇率管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String sourceCode;
        /**
         * 汇率管理币种字段，通常使用 ISO 4217 三位字母代码，不能为空时由上层校验。
         */
        private String baseCurrency;
        /**
         * 汇率管理币种字段，通常使用 ISO 4217 三位字母代码，不能为空时由上层校验。
         */
        private String quoteCurrency;
        /**
         * 汇率管理金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
         */
        private BigDecimal cashBuyRate;
        /**
         * 汇率管理金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
         */
        private BigDecimal cashSellRate;
        /**
         * 汇率管理金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
         */
        private BigDecimal spotBuyRate;
        /**
         * 汇率管理金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
         */
        private BigDecimal spotSellRate;
        /**
         * 汇率管理金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
         */
        private BigDecimal middleRate;
        /**
         * 汇率管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime publishTime;
        /**
         * 汇率管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime fetchTime;
        /**
         * 汇率管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime effectiveTime;
        /**
         * 汇率管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String createMethod;
        /**
         * 汇率管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String batchNo;
        /**
         * 汇率管理金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
         */
        private String rateStatus;
        /**
         * 汇率管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private String voidReason;
        /**
         * 汇率管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime createTime;
        /**
         * 汇率管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
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
         * 汇率管理金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
         */
        private String rateType;
        /**
         * 汇率管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String sourceCode;
        /**
         * 汇率管理币种字段，通常使用 ISO 4217 三位字母代码，不能为空时由上层校验。
         */
        private String baseCurrency;
        /**
         * 汇率管理币种字段，通常使用 ISO 4217 三位字母代码，不能为空时由上层校验。
         */
        private String quoteCurrency;
        /**
         * 汇率管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String adjustDirection;
        /**
         * 汇率管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String adjustMethod;
        /**
         * 汇率管理状态字段，取值需与数据字典或枚举约定保持一致。
         */
        private Integer ruleStatus;
    }

    /**
     * 汇率规则新增或修改请求。
     */
    @Data
    public static class RuleSaveRequest {
        /**
         * 汇率管理金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
         */
        @NotBlank(message = "rateType is required")
        private String rateType;
        /**
         * 汇率管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        @NotBlank(message = "sourceCode is required")
        private String sourceCode;
        /**
         * 汇率管理币种字段，通常使用 ISO 4217 三位字母代码，不能为空时由上层校验。
         */
        @NotBlank(message = "baseCurrency is required")
        private String baseCurrency;
        /**
         * 汇率管理币种字段，通常使用 ISO 4217 三位字母代码，不能为空时由上层校验。
         */
        @NotBlank(message = "quoteCurrency is required")
        private String quoteCurrency;
        /**
         * 汇率管理金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
         */
        @NotBlank(message = "rateField is required")
        private String rateField;
        /**
         * 汇率管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        @NotBlank(message = "adjustDirection is required")
        private String adjustDirection;
        /**
         * 汇率管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        @NotBlank(message = "adjustMethod is required")
        private String adjustMethod;

        /** 调整值：BP 表示基点，PERCENT 表示百分比，统一使用 BigDecimal。 */
        /**
         * 汇率管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        @NotNull(message = "adjustValue is required")
        private BigDecimal adjustValue;

        /** 最终业务汇率小数位，当前限制为 2 到 12 位。 */
        /**
         * 汇率管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        @NotNull(message = "decimalScale is required")
        private Integer decimalScale;
        /**
         * 汇率管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        @NotBlank(message = "roundingMode is required")
        private String roundingMode;
        /**
         * 汇率管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Integer priority;
        /**
         * 汇率管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime effectiveStartTime;
        /**
         * 汇率管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime effectiveEndTime;
        /**
         * 汇率管理状态字段，取值需与数据字典或枚举约定保持一致。
         */
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
        /**
         * 汇率管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private Long id;
        /**
         * 汇率管理金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
         */
        private String rateType;
        /**
         * 汇率管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String sourceCode;
        /**
         * 汇率管理币种字段，通常使用 ISO 4217 三位字母代码，不能为空时由上层校验。
         */
        private String baseCurrency;
        /**
         * 汇率管理币种字段，通常使用 ISO 4217 三位字母代码，不能为空时由上层校验。
         */
        private String quoteCurrency;
        /**
         * 汇率管理金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
         */
        private String rateField;
        /**
         * 汇率管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String adjustDirection;
        /**
         * 汇率管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String adjustMethod;
        /**
         * 汇率管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private BigDecimal adjustValue;
        /**
         * 汇率管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Integer decimalScale;
        /**
         * 汇率管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String roundingMode;
        /**
         * 汇率管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Integer priority;
        /**
         * 汇率管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime effectiveStartTime;
        /**
         * 汇率管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime effectiveEndTime;
        /**
         * 汇率管理状态字段，取值需与数据字典或枚举约定保持一致。
         */
        private Integer ruleStatus;
        /**
         * 汇率管理备注字段，用于记录人工说明，不参与核心状态流转。
         */
        private String remark;
        /**
         * 汇率管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime createTime;
        /**
         * 汇率管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime updateTime;
    }

    /**
     * 业务汇率分页查询条件。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class BusinessRateQuery extends PageRequest {
        /**
         * 汇率管理金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
         */
        private String rateType;
        /**
         * 汇率管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String sourceCode;
        /**
         * 汇率管理币种字段，通常使用 ISO 4217 三位字母代码，不能为空时由上层校验。
         */
        private String baseCurrency;
        /**
         * 汇率管理币种字段，通常使用 ISO 4217 三位字母代码，不能为空时由上层校验。
         */
        private String quoteCurrency;
        /**
         * 汇率管理金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
         */
        private String rateStatus;
        /**
         * 汇率管理金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
         */
        private String generateMethod;
    }

    /**
     * 业务汇率页面响应。
     */
    @Data
    public static class BusinessRateResponse {
        /**
         * 汇率管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private Long id;
        /**
         * 汇率管理金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
         */
        private String rateType;
        /**
         * 汇率管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String sourceCode;
        /**
         * 汇率管理币种字段，通常使用 ISO 4217 三位字母代码，不能为空时由上层校验。
         */
        private String baseCurrency;
        /**
         * 汇率管理币种字段，通常使用 ISO 4217 三位字母代码，不能为空时由上层校验。
         */
        private String quoteCurrency;
        /**
         * 汇率管理金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
         */
        private Long rawRateId;
        /**
         * 汇率管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private Long ruleId;

        /** 规则选取的原始报价。 */
        /**
         * 汇率管理金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
         */
        private BigDecimal originalRate;

        /** 最终业务汇率，已按规则调整并舍入。 */
        /**
         * 汇率管理金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
         */
        private BigDecimal finalRate;
        /**
         * 汇率管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String adjustDescription;
        /**
         * 汇率管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime effectiveTime;
        /**
         * 汇率管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime expireTime;
        /**
         * 汇率管理金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
         */
        private String generateMethod;
        /**
         * 汇率管理金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
         */
        private String rateStatus;
        /**
         * 汇率管理备注字段，用于记录人工说明，不参与核心状态流转。
         */
        private String remark;
        /**
         * 汇率管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime createTime;
        /**
         * 汇率管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
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
         * 汇率管理金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
         */
        private String rateType;
        /**
         * 汇率管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String usageScene;
        /**
         * 汇率管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String businessType;
        /**
         * 汇率管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String businessNo;
        /**
         * 汇率管理币种字段，通常使用 ISO 4217 三位字母代码，不能为空时由上层校验。
         */
        private String baseCurrency;
        /**
         * 汇率管理币种字段，通常使用 ISO 4217 三位字母代码，不能为空时由上层校验。
         */
        private String quoteCurrency;
        /**
         * 汇率管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime appliedStartTime;
        /**
         * 汇率管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime appliedEndTime;
    }

    /**
     * 汇率使用快照页面响应。
     */
    @Data
    public static class UsageSnapshotResponse {
        /**
         * 汇率管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private Long id;
        /**
         * 汇率管理金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
         */
        private String rateType;
        /**
         * 汇率管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String usageScene;
        /**
         * 汇率管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String businessType;
        /**
         * 汇率管理编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String businessNo;
        /**
         * 汇率管理币种字段，通常使用 ISO 4217 三位字母代码，不能为空时由上层校验。
         */
        private String baseCurrency;
        /**
         * 汇率管理币种字段，通常使用 ISO 4217 三位字母代码，不能为空时由上层校验。
         */
        private String quoteCurrency;

        /** 业务链路实际使用的汇率，必须与交易、清分或结算快照一致。 */
        /**
         * 汇率管理金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
         */
        private BigDecimal usedRate;
        /**
         * 汇率管理金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
         */
        private Long businessRateId;
        /**
         * 汇率管理金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
         */
        private Long rawRateId;
        /**
         * 汇率管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private Long ruleId;
        /**
         * 汇率管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String calculationDescription;
        /**
         * 汇率管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime appliedTime;
        /**
         * 汇率管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime createTime;
    }
}
