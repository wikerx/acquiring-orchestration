package com.scott.payment.admin.entity.exchange;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;


/**
 * Exchange Rate Entities 聚合类型，用于运营后台，集中定义同一业务域下的请求、响应、查询条件和持久化视图模型。
 */
public final class ExchangeRateEntities {

    /**
     * 阻止仅提供静态能力的类型被实例化。
     */
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

        /**
         * 数据来源类型，用于区分手工维护、文件导入、外部抓取或系统初始化数据。
         */
        private String sourceType;

        /**
         * 请求URL，表示当前内部调用、渠道调用或商户通知的目标地址。
         */
        private String requestUrl;

        /**
         * 持久化的默认来源，用于还原当前记录的业务事实。
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

        private Integer sourceStatus;

        /**
         * 持久化的{@code lastFetchTime}，用于还原当前记录的业务事实。
         */
        private LocalDateTime lastFetchTime;

        private String lastFetchStatus;
        /**
         * 汇率管理备注字段，用于记录人工说明，不参与核心状态流转。
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
         * 软删除标识，0 表示未删除，非 0 使用主键值避免唯一索引冲突。
         */

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

        /**
         * 现钞买入价，统一为 1 原始币种兑换目标币种，必须使用 BigDecimal。
         */

        private BigDecimal cashBuyRate;

        /**
         * 现钞卖出价，统一为 1 原始币种兑换目标币种，必须使用 BigDecimal。
         */

        private BigDecimal cashSellRate;

        /**
         * 现汇买入价，统一为 1 原始币种兑换目标币种，必须使用 BigDecimal。
         */

        private BigDecimal spotBuyRate;

        /**
         * 现汇卖出价，统一为 1 原始币种兑换目标币种，必须使用 BigDecimal。
         */

        private BigDecimal spotSellRate;

        /**
         * 中间折算价，统一为 1 原始币种兑换目标币种，必须使用 BigDecimal。
         */

        private BigDecimal middleRate;

        /**
         * 汇率源发布时间，数据库保留 DATETIME(3)。
         */

        private LocalDateTime publishTime;

        /**
         * 系统拉取或手工录入时间，数据库保留 DATETIME(3)。
         */

        private LocalDateTime fetchTime;

        /**
         * 原始汇率生效时间，数据库保留 DATETIME(3)。
         */

        private LocalDateTime effectiveTime;

        private String createMethod;

        /**
         * 批次号，用于关联同一次导入、抓取、清分或结算处理的记录。
         */
        private String batchNo;

        private String rateStatus;

        /**
         * 持久化的撤销原因，用于还原当前记录的业务事实。
         */
        private String voidReason;

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
         * 软删除标识，0 表示未删除，非 0 使用主键值避免唯一索引冲突。
         */

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

        /**
         * 汇率类型，用于区分基准汇率、业务汇率、买入价、卖出价或渠道报价。
         */
        private String rateType;

        private String sourceCode;

        private String baseCurrency;

        private String quoteCurrency;

        /**
         * 持久化的汇率字段，用于还原当前记录的业务事实。
         */
        private String rateField;

        /**
         * 持久化的{@code adjustDirection}，用于还原当前记录的业务事实。
         */
        private String adjustDirection;

        private String adjustMethod;

        /**
         * 调整值：BP 按基点解释，PERCENT 按百分比解释，必须使用 BigDecimal。
         */

        private BigDecimal adjustValue;

        /**
         * 最终业务汇率保留的小数位。
         */

        private Integer decimalScale;

        /**
         * 持久化的舍入模式，用于还原当前记录的业务事实。
         */
        private String roundingMode;

        /**
         * 匹配优先级，数值越小越优先。
         */
        private Integer priority;

        /**
         * 规则生效开始时间，数据库保留 DATETIME(3)。
         */

        private LocalDateTime effectiveStartTime;

        /**
         * 规则生效结束时间，数据库保留 DATETIME(3)。
         */

        private LocalDateTime effectiveEndTime;

        private Integer ruleStatus;
        /**
         * 汇率管理备注字段，用于记录人工说明，不参与核心状态流转。
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
         * 软删除标识，0 表示未删除，非 0 使用主键值避免唯一索引冲突。
         */

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

        /**
         * 汇率类型，用于区分基准汇率、业务汇率、买入价、卖出价或渠道报价。
         */
        private String rateType;

        private String sourceCode;

        private String baseCurrency;

        private String quoteCurrency;

        private Long rawRateId;

        private Long ruleId;

        /**
         * 规则选取的原始报价字段值，必须使用 BigDecimal。
         */

        private BigDecimal originalRate;

        /**
         * 最终业务汇率，已应用调整和舍入规则，必须使用 BigDecimal。
         */

        private BigDecimal finalRate;

        private String adjustDescription;

        /**
         * 业务汇率生效时间，数据库保留 DATETIME(3)。
         */

        private LocalDateTime effectiveTime;

        /**
         * 业务汇率失效时间，数据库保留 DATETIME(3)。
         */

        private LocalDateTime expireTime;

        private String generateMethod;

        private String rateStatus;
        /**
         * 汇率管理备注字段，用于记录人工说明，不参与核心状态流转。
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
         * 软删除标识，0 表示未删除，非 0 使用主键值避免唯一索引冲突。
         */

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

        /**
         * 汇率类型，用于区分基准汇率、业务汇率、买入价、卖出价或渠道报价。
         */
        private String rateType;

        /**
         * 持久化的{@code usageScene}，用于还原当前记录的业务事实。
         */
        private String usageScene;

        /**
         * 业务线类型，用于区分收单、代付等业务域，并隔离渠道配置、限额规则和后台查询口径。
         */
        private String businessType;

        /**
         * 持久化的{@code businessNo}，用于还原当前记录的业务事实。
         */
        private String businessNo;

        private String baseCurrency;

        private String quoteCurrency;

        /**
         * 业务实际使用汇率，必须与交易、清分或结算结果一起固化。
         */

        private BigDecimal usedRate;

        private Long businessRateId;

        private Long rawRateId;

        private Long ruleId;

        private String calculationDescription;

        /**
         * 汇率被业务链路实际应用的时间，数据库保留 DATETIME(3)。
         */

        private LocalDateTime appliedTime;

        /**
         * 记录创建人账号标识，用于操作审计。
         */
        private String createBy;

        /**
         * 记录创建时刻，持久化精度为毫秒。
         */
        private LocalDateTime createTime;

        /**
         * 软删除标识，0 表示未删除，非 0 使用主键值避免唯一索引冲突。
         */

        private Long deleted;
    }
}
