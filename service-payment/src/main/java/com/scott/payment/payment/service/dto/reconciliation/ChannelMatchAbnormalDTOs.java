package com.scott.payment.payment.service.dto.reconciliation;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Payment 勾兑异常内部 DTO 集合，承载案件记录、领取、重查和非资金终态处置命令；管理端查询模型由 service-admin 维护。
 */
public final class ChannelMatchAbnormalDTOs {

    /**
     * 阻止仅提供静态能力的类型被实例化。
     */
    private ChannelMatchAbnormalDTOs() {
    }

    /**
     * 勾兑异常列表与详情记录，金额使用币种主单位 BigDecimal。
     */
    @Data
    public static class AbnormalRecord implements Serializable {
        private static final long serialVersionUID = 1L;

        private String abnormalEventId;
        /**
         * 平台交易号，由支付核心生成，用于串联主单、动作单、渠道请求、回调和通知。
         */
        private String transactionId;
        /**
         * 平台操作号，由支付核心生成，用于定位一次授权、请款、退款、撤销或回调处理动作。
         */
        private String operationId;
        /**
         * {@code abnormalType}，用于区分 {@code AbnormalRecord} 记录的处理类别、配置维度或外部协议枚举。
         */
        private String abnormalType;
        /**
         * 持久化的{@code abnormalLevel}，用于还原当前记录的业务事实。
         */
        private String abnormalLevel;

        private String eventStatus;
        /**
         * 来源记录类型，用于区分 {@code AbnormalRecord} 记录的处理类别、配置维度或外部协议枚举。
         */
        private String sourceRecordType;

        private String sourceRecordId;

        private String abnormalDescription;
        /**
         * 持久化的{@code rawReferenceJson}，用于还原当前记录的业务事实。
         */
        private String rawReferenceJson;
        /**
         * 持久化的{@code firstSeenTime}，用于还原当前记录的业务事实。
         */
        private LocalDateTime firstSeenTime;
        /**
         * 持久化的{@code lastSeenTime}，用于还原当前记录的业务事实。
         */
        private LocalDateTime lastSeenTime;
        /**
         * 持久化的{@code resolvedTime}，用于还原当前记录的业务事实。
         */
        private LocalDateTime resolvedTime;
        /**
         * 交易受理时刻，按交易业务时区解释并保留毫秒精度。
         */
        private LocalDateTime transactionDateTime;
        /**
         * 持久化的来源交易日期时间，用于还原当前记录的业务事实。
         */
        private LocalDateTime sourceTransactionDateTime;
        /**
         * 持久化的{@code rootTransactionDateTime}，用于还原当前记录的业务事实。
         */
        private LocalDateTime rootTransactionDateTime;
        /**
         * 商户号，用于限定商户配置、交易数据、风控规则和权限归属。
         */
        private String merchantId;
        /**
         * 商户订单号，由商户生成并在同一商户范围内用于交易幂等、查询和对账。
         */
        private String merchantOrderNo;
        /**
         * 原平台交易号，用于将请款、退款、撤销、增量授权等后续动作关联到原始交易。
         */
        private String sourceTransactionId;
        /**
         * 交易类型，标识本次动作是支付、授权、请款、退款、撤销还是增量授权，用于选择状态机和渠道能力。
         */
        private String transactionType;

        private String platformStatus;
        /**
         * 渠道编码，用于定位 MPGS、WorldPay 等渠道适配实现和路由配置。
         */
        private String channelCode;
        /**
         * 渠道订单号，由渠道返回，用于渠道查询、回调匹配和对账。
         */
        private String channelOrderNo;
        /**
         * 平台交易号，由支付核心生成，用于串联主单、动作单、渠道请求、回调和通知。
         */
        private String channelTransactionId;

        private String channelStatus;
        /**
         * 持久化的渠道匹配结果，用于还原当前记录的业务事实。
         */
        private String channelMatchResult;
        /**
         * 持久化的{@code detectSource}，用于还原当前记录的业务事实。
         */
        private String detectSource;

        private String platformCurrency;

        private BigDecimal platformAmount;

        private String channelCurrency;

        private BigDecimal channelAmount;

        private BigDecimal amountDifference;
        /**
         * 交易币种的小数位数，用于主币种单位与最小货币单位之间的精确转换。
         */
        private Integer currencyExponent;

        private Integer occurrenceCount;

        private String assignedToId;

        private String assignedToName;
        /**
         * 持久化的{@code assignedTime}，用于还原当前记录的业务事实。
         */
        private LocalDateTime assignedTime;
        /**
         * {@code resolutionType}，用于区分 {@code AbnormalRecord} 记录的处理类别、配置维度或外部协议枚举。
         */
        private String resolutionType;

        private String resolutionReferenceId;
        /**
         * 持久化的商户通知必需标识，用于还原当前记录的业务事实。
         */
        private Integer merchantNotifyRequired;

        private Integer version;
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
     * 领取或转派命令。
     */
    @Data
    public static class AssignCommand implements Serializable {
        private static final long serialVersionUID = 1L;
        /**
         * 交易受理时刻，按交易业务时区解释并保留毫秒精度。
         */
        private LocalDateTime transactionDateTime;

        private Integer expectedVersion;
        /**
         * 执行本次管理操作的可信登录账号 ID，用于操作审计。
         */
        private String operatorId;
        /**
         * 执行本次管理操作时的账号显示名称快照，用于操作审计。
         */
        private String operatorName;
    }

    /**
     * 关闭或忽略命令；不允许携带目标交易状态。
     */
    @Data
    public static class ResolveCommand implements Serializable {
        private static final long serialVersionUID = 1L;
        /**
         * 交易受理时刻，按交易业务时区解释并保留毫秒精度。
         */
        private LocalDateTime transactionDateTime;

        private Integer expectedVersion;
        /**
         * {@code resolutionType}，用于区分 {@code ResolveCommand} 记录的处理类别、配置维度或外部协议枚举。
         */
        private String resolutionType;
        /**
         * 请求中的原因，用于限定本次操作的输入和校验范围。
         */
        private String reason;

        private String referenceId;
    }

    /**
     * 单笔重新勾兑命令。
     */
    @Data
    public static class RequeryCommand implements Serializable {
        private static final long serialVersionUID = 1L;
        /**
         * 交易受理时刻，按交易业务时区解释并保留毫秒精度。
         */
        private LocalDateTime transactionDateTime;

        private Integer expectedVersion;
    }

    /**
     * 批量重新勾兑案件引用，最多 100 条。
     */
    @Data
    public static class BatchRequeryCommand implements Serializable {
        private static final long serialVersionUID = 1L;
        /**
         * {@code cases}集合，承载 {@code BatchRequeryCommand} 当前请求或响应中的多值数据。
         */
        private List<CaseReference> cases = new ArrayList<>();
    }

    /**
     * 批量案件号及精确分片时间。
     */
    @Data
    public static class CaseReference implements Serializable {
        private static final long serialVersionUID = 1L;

        private String eventId;
        /**
         * 交易受理时刻，按交易业务时区解释并保留毫秒精度。
         */
        private LocalDateTime transactionDateTime;

        private Integer expectedVersion;
    }

    /**
     * 批量重查结果；单笔失败不阻断其余案件。
     */
    @Data
    public static class BatchRequeryResult implements Serializable {
        private static final long serialVersionUID = 1L;

        private int requestedCount;

        private int acceptedCount;

        private int failedCount;
        /**
         * {@code failedEventIds}集合，承载 {@code BatchRequeryResult} 当前请求或响应中的多值数据。
         */
        private List<String> failedEventIds = new ArrayList<>();
    }
}
