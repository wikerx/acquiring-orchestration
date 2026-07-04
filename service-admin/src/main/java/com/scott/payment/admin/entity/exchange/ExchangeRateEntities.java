package com.scott.payment.admin.entity.exchange;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ExchangeRateEntities
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 汇率管理Exchange Rate 实体集合，位于 service-admin 的数据实体层，用于说明职责边界、数据语义和关键业务约束。
 * @status : create
 */
public final class ExchangeRateEntities {

    private ExchangeRateEntities() {
    }

    /**
     * 汇率源配置数据库实体。
     */
    @Data
    @TableName("exchange_rate_source")
    public static class ExchangeRateSourceDO {
        /**
         * 汇率管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        @TableId(type = IdType.AUTO)
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
         * 汇率管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String createBy;
        /**
         * 汇率管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime createTime;
        /**
         * 汇率管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private String updateBy;
        /**
         * 汇率管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime updateTime;

        /** 软删除标识，0 表示未删除，非 0 使用主键值避免唯一索引冲突。 */
        /**
         * 汇率管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Long deleted;
    }

    /**
     * 汇率源原始报价数据库实体。
     */
    @Data
    @TableName("exchange_raw_rate")
    public static class ExchangeRawRateDO {
        /**
         * 汇率管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        @TableId(type = IdType.AUTO)
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

        /** 现钞买入价，统一为 1 原始币种兑换目标币种，必须使用 BigDecimal。 */
        /**
         * 汇率管理金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
         */
        private BigDecimal cashBuyRate;

        /** 现钞卖出价，统一为 1 原始币种兑换目标币种，必须使用 BigDecimal。 */
        /**
         * 汇率管理金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
         */
        private BigDecimal cashSellRate;

        /** 现汇买入价，统一为 1 原始币种兑换目标币种，必须使用 BigDecimal。 */
        /**
         * 汇率管理金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
         */
        private BigDecimal spotBuyRate;

        /** 现汇卖出价，统一为 1 原始币种兑换目标币种，必须使用 BigDecimal。 */
        /**
         * 汇率管理金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
         */
        private BigDecimal spotSellRate;

        /** 中间折算价，统一为 1 原始币种兑换目标币种，必须使用 BigDecimal。 */
        /**
         * 汇率管理金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
         */
        private BigDecimal middleRate;

        /** 汇率源发布时间，数据库保留 DATETIME(3)。 */
        /**
         * 汇率管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime publishTime;

        /** 系统拉取或手工录入时间，数据库保留 DATETIME(3)。 */
        /**
         * 汇率管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime fetchTime;

        /** 原始汇率生效时间，数据库保留 DATETIME(3)。 */
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
         * 汇率管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String createBy;
        /**
         * 汇率管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime createTime;
        /**
         * 汇率管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private String updateBy;
        /**
         * 汇率管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime updateTime;

        /** 软删除标识，0 表示未删除，非 0 使用主键值避免唯一索引冲突。 */
        /**
         * 汇率管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Long deleted;
    }

    /**
     * 业务汇率生成规则数据库实体。
     */
    @Data
    @TableName("exchange_rate_rule")
    public static class ExchangeRateRuleDO {
        /**
         * 汇率管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        @TableId(type = IdType.AUTO)
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

        /** 调整值：BP 按基点解释，PERCENT 按百分比解释，必须使用 BigDecimal。 */
        /**
         * 汇率管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private BigDecimal adjustValue;

        /** 最终业务汇率保留的小数位。 */
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

        /** 规则生效开始时间，数据库保留 DATETIME(3)。 */
        /**
         * 汇率管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime effectiveStartTime;

        /** 规则生效结束时间，数据库保留 DATETIME(3)。 */
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
         * 汇率管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String createBy;
        /**
         * 汇率管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime createTime;
        /**
         * 汇率管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private String updateBy;
        /**
         * 汇率管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime updateTime;

        /** 软删除标识，0 表示未删除，非 0 使用主键值避免唯一索引冲突。 */
        /**
         * 汇率管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Long deleted;
    }

    /**
     * 业务最终可用汇率数据库实体。
     */
    @Data
    @TableName("exchange_business_rate")
    public static class ExchangeBusinessRateDO {
        /**
         * 汇率管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        @TableId(type = IdType.AUTO)
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

        /** 规则选取的原始报价字段值，必须使用 BigDecimal。 */
        /**
         * 汇率管理金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
         */
        private BigDecimal originalRate;

        /** 最终业务汇率，已应用调整和舍入规则，必须使用 BigDecimal。 */
        /**
         * 汇率管理金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
         */
        private BigDecimal finalRate;
        /**
         * 汇率管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String adjustDescription;

        /** 业务汇率生效时间，数据库保留 DATETIME(3)。 */
        /**
         * 汇率管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime effectiveTime;

        /** 业务汇率失效时间，数据库保留 DATETIME(3)。 */
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
         * 汇率管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String createBy;
        /**
         * 汇率管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime createTime;
        /**
         * 汇率管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private String updateBy;
        /**
         * 汇率管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime updateTime;

        /** 软删除标识，0 表示未删除，非 0 使用主键值避免唯一索引冲突。 */
        /**
         * 汇率管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Long deleted;
    }

    /**
     * 汇率业务使用快照数据库实体。
     */
    @Data
    @TableName("exchange_rate_usage_snapshot")
    public static class ExchangeRateUsageSnapshotDO {
        /**
         * 汇率管理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        @TableId(type = IdType.AUTO)
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

        /** 业务实际使用汇率，必须与交易、清分或结算结果一起固化。 */
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

        /** 汇率被业务链路实际应用的时间，数据库保留 DATETIME(3)。 */
        /**
         * 汇率管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime appliedTime;
        /**
         * 汇率管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String createBy;
        /**
         * 汇率管理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime createTime;

        /** 软删除标识，0 表示未删除，非 0 使用主键值避免唯一索引冲突。 */
        /**
         * 汇率管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Long deleted;
    }
}
