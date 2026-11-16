package com.scott.payment.admin.entity.channel;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付路由配置持久化模型集合。
 *
 * <p>本阶段仅由管理系统维护，不代表配置已经接入支付交易链路。</p>
 */
public final class PaymentRouteEntities {

    private PaymentRouteEntities() {
    }

    /** 支付路由规则主记录，保存匹配维度、生命周期和第一层规则优先级。 */
    @Data
    @TableName("payment_route_rule")
    public static class PaymentRouteRuleDO {
        @TableId(type = IdType.AUTO)
        private Long id;
        private String ruleCode;
        private String ruleName;
        private String ruleType;
        private String scopeType;
        private String merchantId;
        private String businessType;
        private String paymentMethod;
        private String transactionType;
        private String cardBrand;
        private String labelCurrency;
        private String issuerCountryScope;
        private String billingCountryScope;
        private BigDecimal amountMin;
        private BigDecimal amountMax;
        private Integer rulePriority;
        private String unavailablePolicy;
        private String ruleStatus;
        private Integer version;
        private String remark;
        private String publishedBy;
        private LocalDateTime publishedTime;
        private String disabledBy;
        private LocalDateTime disabledTime;
        private String createBy;
        private LocalDateTime createTime;
        private String updateBy;
        private LocalDateTime updateTime;
        private Long deleted;
    }

    /** 指定商户路由的商户关联；一条规则可同时适用于多个商户。 */
    @Data
    @TableName("payment_route_rule_merchant")
    public static class PaymentRouteRuleMerchantDO {
        @TableId(type = IdType.AUTO)
        private Long id;
        private Long ruleId;
        private String merchantId;
        private String createBy;
        private LocalDateTime createTime;
        private String updateBy;
        private LocalDateTime updateTime;
        private Long deleted;
    }

    /** 路由类型专属配置；strategyPriority 仅为首版表结构兼容字段。 */
    @Data
    @TableName("payment_route_strategy")
    public static class PaymentRouteStrategyDO {
        @TableId(type = IdType.AUTO)
        private Long id;
        private Long ruleId;
        private String strategyType;
        private Integer strategyPriority;
        private Integer historyLookbackMonths;
        private Integer strategyStatus;
        private String createBy;
        private LocalDateTime createTime;
        private String updateBy;
        private LocalDateTime updateTime;
        private Long deleted;
    }

    /** 路由候选 MID；优先级和权重只在对应路由类型中具有业务语义。 */
    @Data
    @TableName("payment_route_candidate")
    public static class PaymentRouteCandidateDO {
        @TableId(type = IdType.AUTO)
        private Long id;
        private Long ruleId;
        /** 候选所属支付方式；优先级和 MID 唯一性均在该分组内生效。 */
        private String paymentMethod;
        /** 银行卡品牌；非银行卡支付方式为 NONE。 */
        private String cardBrand;
        private Long channelId;
        private Long midConfigId;
        private Integer candidatePriority;
        private BigDecimal candidateWeight;
        private Integer candidateStatus;
        private String createBy;
        private LocalDateTime createTime;
        private String updateBy;
        private LocalDateTime updateTime;
        private Long deleted;
    }

    /** 规则适用支付方式，避免从候选 MID 反推匹配范围。 */
    @Data
    @TableName("payment_route_method")
    public static class PaymentRouteMethodDO {
        @TableId(type = IdType.AUTO)
        private Long id;
        private Long ruleId;
        private String paymentMethod;
        /** 银行卡品牌；非银行卡支付方式为 NONE。 */
        private String cardBrand;
        private String createBy;
        private LocalDateTime createTime;
        private String updateBy;
        private LocalDateTime updateTime;
        private Long deleted;
    }

    /** 规则适用交易类型；没有有效记录时表示全部交易类型。 */
    @Data
    @TableName("payment_route_transaction_type")
    public static class PaymentRouteTransactionTypeDO {
        @TableId(type = IdType.AUTO)
        private Long id;
        private Long ruleId;
        private String transactionType;
        private String createBy;
        private LocalDateTime createTime;
        private String updateBy;
        private LocalDateTime updateTime;
        private Long deleted;
    }

    /** 不可变的路由配置变更日志，仅保存非敏感配置快照和可信操作人。 */
    @Data
    @TableName("payment_route_change_log")
    public static class PaymentRouteChangeLogDO {
        @TableId(type = IdType.AUTO)
        private Long id;
        private Long ruleId;
        private String ruleCode;
        private String actionType;
        private Integer beforeVersion;
        private Integer afterVersion;
        private String beforeSnapshotJson;
        private String afterSnapshotJson;
        private Long operatorAccountId;
        private String operatorName;
        private String operationReason;
        private LocalDateTime createTime;
    }
}
