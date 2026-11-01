package com.scott.payment.admin.dto.transaction;

import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionDetailResponse;
import com.scott.payment.component.core.model.PageRequest;
import com.scott.payment.component.core.model.PageResult;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 管理端勾兑异常接口模型，提供运营查询与受控处置字段，不包含人工目标交易状态。
 */
public final class AdminChannelMatchAbnormalDTOs {

    /**
     * 阻止仅提供静态能力的类型被实例化。
     */
    private AdminChannelMatchAbnormalDTOs() {
    }

    /**
     * 管理端案件分页查询。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class AbnormalQuery extends PageRequest {
        private static final long serialVersionUID = 1L;
        private String eventId;
        /**
         * 平台交易号，由支付核心生成，用于串联主单、动作单、渠道请求、回调和通知。
         */
        private String transactionId;
        /**
         * 商户号，用于限定商户配置、交易数据、风控规则和权限归属。
         */
        private String merchantId;
        /**
         * 商户订单号，由商户生成并在同一商户范围内用于交易幂等、查询和对账。
         */
        private String merchantOrderNo;
        private String abnormalType;
        private String abnormalLevel;
        private String eventStatus;
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
        private String assignedToId;
        private String detectSource;
        private Integer minimumOccurrenceCount;
        private LocalDateTime beginTime;
        /**
         * 请求中的结束时间，用于限定本次操作的输入和校验范围。
         */
        private LocalDateTime endTime;
        /**
         * 查询时间时区，使用 IANA 时区标识解释关联的本地日期时间。
         */
        private String queryTimeZone;
    }

    /**
     * 管理端案件记录。
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
        private String abnormalType;
        /**
         * 持久化的{@code abnormalLevel}，用于还原当前记录的业务事实。
         */
        private String abnormalLevel;
        private String eventStatus;
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
     * 管理端案件状态统计。
     */
    @Data
    public static class AbnormalSummary implements Serializable {
        private static final long serialVersionUID = 1L;
        private long totalCount;
        private long openCount;
        private long processingCount;
        private long resolvedCount;
        private long ignoredCount;
        private long highOrCriticalCount;
    }

    /**
     * 分页及统计响应。
     */
    @Data
    public static class AbnormalSearchResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private PageResult<AbnormalRecord> page;
        private AbnormalSummary summary;
    }

    /**
     * 案件聚合详情。
     */
    @Data
    public static class AbnormalDetailResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private AbnormalRecord abnormality;
        /**
         * 异常案件关联交易的完整管理端详情。
         */
        private TransactionDetailResponse transactionDetail;
    }

    /**
     * 页面领取或转派请求；目标账号为空时领取给当前用户。
     */
    @Data
    public static class AssignRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        /**
         * 交易受理时刻，按交易业务时区解释并保留毫秒精度。
         */
        private LocalDateTime transactionDateTime;
        private Integer expectedVersion;
        private String assigneeAccountId;
        private String assigneeName;
    }

    /**
     * Admin 调用 Payment 的领取命令。
     */
    @Data
    public static class AssignClientCommand implements Serializable {
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
     * 页面单笔重查请求。
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
     * 页面批量重查请求。
     */
    @Data
    public static class BatchRequeryCommand implements Serializable {
        private static final long serialVersionUID = 1L;
        private List<CaseReference> cases = new ArrayList<>();
    }

    /**
     * 批量案件引用。
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
     * 批量重查结果。
     */
    @Data
    public static class BatchRequeryResult implements Serializable {
        private static final long serialVersionUID = 1L;
        private int requestedCount;
        private int acceptedCount;
        private int failedCount;
        private List<String> failedEventIds = new ArrayList<>();
    }

    /**
     * 页面关闭或忽略请求。
     */
    @Data
    public static class ResolveCommand implements Serializable {
        private static final long serialVersionUID = 1L;
        /**
         * 交易受理时刻，按交易业务时区解释并保留毫秒精度。
         */
        private LocalDateTime transactionDateTime;
        private Integer expectedVersion;
        private String resolutionType;
        /**
         * 请求中的原因，用于限定本次操作的输入和校验范围。
         */
        private String reason;
        private String referenceId;
    }
}
