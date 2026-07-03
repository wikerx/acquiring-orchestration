package com.scott.payment.admin.entity.exchange;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 汇率管理数据库实体集合。
 *
 * <p>仅承载管理后台汇率源、原始报价、规则、业务汇率和使用快照维护数据，不直接承载交易或结算状态机。</p>
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
        @TableId(type = IdType.AUTO)
        private Long id;
        private String sourceCode;
        private String sourceName;
        private String sourceType;
        private String requestUrl;
        private Integer defaultSource;
        private Integer priority;
        private Integer timeoutSeconds;
        private Integer sourceStatus;
        private LocalDateTime lastFetchTime;
        private String lastFetchStatus;
        private String remark;
        private String createBy;
        private LocalDateTime createTime;
        private String updateBy;
        private LocalDateTime updateTime;
        /** 软删除标识，0 表示未删除，非 0 使用主键值避免唯一索引冲突。 */
        private Long deleted;
    }

    /**
     * 汇率源原始报价数据库实体。
     */
    @Data
    @TableName("exchange_raw_rate")
    public static class ExchangeRawRateDO {
        @TableId(type = IdType.AUTO)
        private Long id;
        private String sourceCode;
        private String baseCurrency;
        private String quoteCurrency;
        /** 现钞买入价，统一为 1 原始币种兑换目标币种，必须使用 BigDecimal。 */
        private BigDecimal cashBuyRate;
        /** 现钞卖出价，统一为 1 原始币种兑换目标币种，必须使用 BigDecimal。 */
        private BigDecimal cashSellRate;
        /** 现汇买入价，统一为 1 原始币种兑换目标币种，必须使用 BigDecimal。 */
        private BigDecimal spotBuyRate;
        /** 现汇卖出价，统一为 1 原始币种兑换目标币种，必须使用 BigDecimal。 */
        private BigDecimal spotSellRate;
        /** 中间折算价，统一为 1 原始币种兑换目标币种，必须使用 BigDecimal。 */
        private BigDecimal middleRate;
        /** 汇率源发布时间，数据库保留 DATETIME(3)。 */
        private LocalDateTime publishTime;
        /** 系统拉取或手工录入时间，数据库保留 DATETIME(3)。 */
        private LocalDateTime fetchTime;
        /** 原始汇率生效时间，数据库保留 DATETIME(3)。 */
        private LocalDateTime effectiveTime;
        private String createMethod;
        private String batchNo;
        private String rateStatus;
        private String voidReason;
        private String createBy;
        private LocalDateTime createTime;
        private String updateBy;
        private LocalDateTime updateTime;
        /** 软删除标识，0 表示未删除，非 0 使用主键值避免唯一索引冲突。 */
        private Long deleted;
    }

    /**
     * 业务汇率生成规则数据库实体。
     */
    @Data
    @TableName("exchange_rate_rule")
    public static class ExchangeRateRuleDO {
        @TableId(type = IdType.AUTO)
        private Long id;
        private String rateType;
        private String sourceCode;
        private String baseCurrency;
        private String quoteCurrency;
        private String rateField;
        private String adjustDirection;
        private String adjustMethod;
        /** 调整值：BP 按基点解释，PERCENT 按百分比解释，必须使用 BigDecimal。 */
        private BigDecimal adjustValue;
        /** 最终业务汇率保留的小数位。 */
        private Integer decimalScale;
        private String roundingMode;
        private Integer priority;
        /** 规则生效开始时间，数据库保留 DATETIME(3)。 */
        private LocalDateTime effectiveStartTime;
        /** 规则生效结束时间，数据库保留 DATETIME(3)。 */
        private LocalDateTime effectiveEndTime;
        private Integer ruleStatus;
        private String remark;
        private String createBy;
        private LocalDateTime createTime;
        private String updateBy;
        private LocalDateTime updateTime;
        /** 软删除标识，0 表示未删除，非 0 使用主键值避免唯一索引冲突。 */
        private Long deleted;
    }

    /**
     * 业务最终可用汇率数据库实体。
     */
    @Data
    @TableName("exchange_business_rate")
    public static class ExchangeBusinessRateDO {
        @TableId(type = IdType.AUTO)
        private Long id;
        private String rateType;
        private String sourceCode;
        private String baseCurrency;
        private String quoteCurrency;
        private Long rawRateId;
        private Long ruleId;
        /** 规则选取的原始报价字段值，必须使用 BigDecimal。 */
        private BigDecimal originalRate;
        /** 最终业务汇率，已应用调整和舍入规则，必须使用 BigDecimal。 */
        private BigDecimal finalRate;
        private String adjustDescription;
        /** 业务汇率生效时间，数据库保留 DATETIME(3)。 */
        private LocalDateTime effectiveTime;
        /** 业务汇率失效时间，数据库保留 DATETIME(3)。 */
        private LocalDateTime expireTime;
        private String generateMethod;
        private String rateStatus;
        private String remark;
        private String createBy;
        private LocalDateTime createTime;
        private String updateBy;
        private LocalDateTime updateTime;
        /** 软删除标识，0 表示未删除，非 0 使用主键值避免唯一索引冲突。 */
        private Long deleted;
    }

    /**
     * 汇率业务使用快照数据库实体。
     */
    @Data
    @TableName("exchange_rate_usage_snapshot")
    public static class ExchangeRateUsageSnapshotDO {
        @TableId(type = IdType.AUTO)
        private Long id;
        private String rateType;
        private String usageScene;
        private String businessType;
        private String businessNo;
        private String baseCurrency;
        private String quoteCurrency;
        /** 业务实际使用汇率，必须与交易、清分或结算结果一起固化。 */
        private BigDecimal usedRate;
        private Long businessRateId;
        private Long rawRateId;
        private Long ruleId;
        private String calculationDescription;
        /** 汇率被业务链路实际应用的时间，数据库保留 DATETIME(3)。 */
        private LocalDateTime appliedTime;
        private String createBy;
        private LocalDateTime createTime;
        /** 软删除标识，0 表示未删除，非 0 使用主键值避免唯一索引冲突。 */
        private Long deleted;
    }
}
