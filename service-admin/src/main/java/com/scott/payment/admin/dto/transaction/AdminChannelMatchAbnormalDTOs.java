package com.scott.payment.admin.dto.transaction;

import com.scott.payment.component.core.model.PageRequest;
import com.scott.payment.component.core.model.PageResult;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminChannelMatchAbnormalDTOs
 * @date : 2026-08-06 00:00
 * @description : 管理端勾兑异常接口模型，提供运营查询与受控处置字段，不包含人工目标交易状态。
 * @status : create
 */
public final class AdminChannelMatchAbnormalDTOs {

    private AdminChannelMatchAbnormalDTOs() {
    }

    /** 管理端案件分页查询。 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class AbnormalQuery extends PageRequest {
        private static final long serialVersionUID = 1L;
        private String eventId;
        private String transactionId;
        private String merchantId;
        private String merchantOrderNo;
        private String abnormalType;
        private String abnormalLevel;
        private String eventStatus;
        private String transactionType;
        private String platformStatus;
        private String channelCode;
        private String channelOrderNo;
        private String assignedToId;
        private String detectSource;
        private Integer minimumOccurrenceCount;
        private LocalDateTime beginTime;
        private LocalDateTime endTime;
        private String queryTimeZone;
    }

    /** 管理端案件记录。 */
    @Data
    public static class AbnormalRecord implements Serializable {
        private static final long serialVersionUID = 1L;
        private String abnormalEventId;
        private String transactionId;
        private String operationId;
        private String abnormalType;
        private String abnormalLevel;
        private String eventStatus;
        private String sourceRecordType;
        private String sourceRecordId;
        private String abnormalDescription;
        private String rawReferenceJson;
        private LocalDateTime firstSeenTime;
        private LocalDateTime lastSeenTime;
        private LocalDateTime resolvedTime;
        private LocalDateTime transactionDateTime;
        private LocalDateTime sourceTransactionDateTime;
        private LocalDateTime rootTransactionDateTime;
        private String merchantId;
        private String merchantOrderNo;
        private String sourceTransactionId;
        private String transactionType;
        private String platformStatus;
        private String channelCode;
        private String channelOrderNo;
        private String channelTransactionId;
        private String channelStatus;
        private String channelMatchResult;
        private String detectSource;
        private String platformCurrency;
        private BigDecimal platformAmount;
        private String channelCurrency;
        private BigDecimal channelAmount;
        private BigDecimal amountDifference;
        private Integer currencyExponent;
        private Integer occurrenceCount;
        private String assignedToId;
        private String assignedToName;
        private LocalDateTime assignedTime;
        private String resolutionType;
        private String resolutionReferenceId;
        private Integer merchantNotifyRequired;
        private Integer version;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    /** 管理端案件状态统计。 */
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

    /** 分页及统计响应。 */
    @Data
    public static class AbnormalSearchResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private PageResult<AbnormalRecord> page;
        private AbnormalSummary summary;
    }

    /** 案件聚合详情。 */
    @Data
    public static class AbnormalDetailResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private AbnormalRecord abnormality;
        private Map<String, Object> transactionDetail;
    }

    /** 页面领取或转派请求；目标账号为空时领取给当前用户。 */
    @Data
    public static class AssignRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private LocalDateTime transactionDateTime;
        private Integer expectedVersion;
        private String assigneeAccountId;
        private String assigneeName;
    }

    /** Admin 调用 Payment 的领取命令。 */
    @Data
    public static class AssignClientCommand implements Serializable {
        private static final long serialVersionUID = 1L;
        private LocalDateTime transactionDateTime;
        private Integer expectedVersion;
        private String operatorId;
        private String operatorName;
    }

    /** 页面单笔重查请求。 */
    @Data
    public static class RequeryCommand implements Serializable {
        private static final long serialVersionUID = 1L;
        private LocalDateTime transactionDateTime;
        private Integer expectedVersion;
    }

    /** 页面批量重查请求。 */
    @Data
    public static class BatchRequeryCommand implements Serializable {
        private static final long serialVersionUID = 1L;
        private List<CaseReference> cases = new ArrayList<>();
    }

    /** 批量案件引用。 */
    @Data
    public static class CaseReference implements Serializable {
        private static final long serialVersionUID = 1L;
        private String eventId;
        private LocalDateTime transactionDateTime;
        private Integer expectedVersion;
    }

    /** 批量重查结果。 */
    @Data
    public static class BatchRequeryResult implements Serializable {
        private static final long serialVersionUID = 1L;
        private int requestedCount;
        private int acceptedCount;
        private int failedCount;
        private List<String> failedEventIds = new ArrayList<>();
    }

    /** 页面关闭或忽略请求。 */
    @Data
    public static class ResolveCommand implements Serializable {
        private static final long serialVersionUID = 1L;
        private LocalDateTime transactionDateTime;
        private Integer expectedVersion;
        private String resolutionType;
        private String reason;
        private String referenceId;
    }
}
