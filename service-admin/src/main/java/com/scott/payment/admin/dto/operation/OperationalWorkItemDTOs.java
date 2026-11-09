package com.scott.payment.admin.dto.operation;

import com.scott.payment.component.core.model.PageResult;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 统一运营待办接口模型。
 *
 * <p>待办只返回运营元数据和经过数据范围校验的实时来源摘要，不复制来源业务状态。</p>
 */
public final class OperationalWorkItemDTOs {

    private OperationalWorkItemDTOs() {
    }

    /**
     * 待办列表筛选条件。
     */
    @Data
    public static class SearchRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private String workItemNo;
        private String keyword;
        private String sourceType;
        private String workStatus;
        private String priority;
        private Long ownerAccountId;
        private Boolean mineOnly;
        private Boolean unassignedOnly;
        private Boolean overdueOnly;
        private LocalDateTime detectedStartTime;
        private LocalDateTime detectedEndTime;
        private Integer pageNo;
        private Integer pageSize;
    }

    /**
     * 列表、导出和详情共用的待办摘要。
     */
    @Data
    public static class WorkItemSummary implements Serializable {
        private static final long serialVersionUID = 1L;
        private String workItemNo;
        private String sourceType;
        private String sourceKey;
        private String workStatus;
        private String priority;
        private Long ownerAccountId;
        private String ownerAccountName;
        private LocalDateTime slaDueAt;
        private Boolean overdue;
        private LocalDateTime firstDetectedAt;
        private LocalDateTime lastSyncedAt;
        private LocalDateTime closedAt;
        private String closeReason;
        private String latestRemark;
        private Long version;
        private String sourceStatus;
        private Boolean sourceAvailable;
        /**
         * 是否由来源业务状态机管理负责人和终态；为 true 时统一待办只允许查看和备注。
         */
        private Boolean sourceManaged;
        private String merchantId;
        private String merchantName;
        private String businessNo;
        private String relatedBusinessNo;
        private String transactionId;
        private LocalDateTime transactionDateTime;
        private String currency;
        private String amount;
        private Integer currencyExponent;
        private String rateType;
        private String baseCurrency;
        private String quoteCurrency;
        private String alertLevel;
        private Integer attemptCount;
        /**
         * 来源业务保存的稳定失败阶段，仅在来源具备该事实时返回。
         */
        private String failureStage;
        /**
         * 来源业务保存的稳定失败码，仅在来源具备该事实时返回。
         */
        private String failureCode;
        private String failureReason;
        private LocalDateTime sourceOccurredAt;
    }

    /**
     * 当前筛选条件下的运营汇总，不按当前页截断。
     */
    @Data
    public static class WorkItemMetrics implements Serializable {
        private static final long serialVersionUID = 1L;
        private long openCount;
        private long processingCount;
        private long overdueCount;
        private long unassignedCount;
        private long mineCount;
    }

    /**
     * 待办分页和汇总组合响应。
     */
    @Data
    public static class SearchResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private PageResult<WorkItemSummary> page;
        private WorkItemMetrics metrics;
    }

    /**
     * 单条待办的处理轨迹。
     */
    @Data
    public static class WorkItemHistory implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long id;
        private String actionType;
        private String beforeStatus;
        private String afterStatus;
        private Long beforeOwnerAccountId;
        private String beforeOwnerAccountName;
        private Long afterOwnerAccountId;
        private String afterOwnerAccountName;
        private Long operatorAccountId;
        private String operatorAccountName;
        private String remark;
        private LocalDateTime operatedAt;
    }

    /**
     * 待办详情，来源摘要和兼容历史窗口保持分层。
     *
     * <p>{@code history} 仅保留最新一页兼容窗口，完整轨迹通过独立分页接口读取。</p>
     */
    @Data
    public static class WorkItemDetail implements Serializable {
        private static final long serialVersionUID = 1L;
        private WorkItemSummary workItem;
        private List<WorkItemHistory> history = new ArrayList<>();
        private long historyTotal;
    }

    /**
     * 认领、分派、转交、完成和备注命令。
     */
    @Data
    public static class ActionRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank(message = "requestId must not be blank")
        @Size(max = 128, message = "requestId length must not exceed 128")
        private String requestId;
        @NotNull(message = "expectedVersion must not be null")
        private Long expectedVersion;
        private Long targetAccountId;
        @Size(max = 1000, message = "remark length must not exceed 1000")
        private String remark;
    }

    /**
     * 可分派的有效管理账号。
     */
    @Data
    public static class AssigneeOption implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long accountId;
        private String accountName;
        private String loginAccount;
    }

    /**
     * service-job 发起的幂等同步请求。
     */
    @Data
    public static class SyncRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank(message = "requestKey must not be blank")
        @Size(max = 128, message = "requestKey length must not exceed 128")
        private String requestKey;
        @NotNull(message = "syncTime must not be null")
        private LocalDateTime syncTime;
    }

    /**
     * 一轮同步的真实状态变更统计。
     */
    @Data
    public static class SyncResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private String requestKey;
        private Integer scannedSourceCount;
        private Integer createdCount;
        private Integer reopenedCount;
        private Integer closedCount;
        private Integer refreshedCount;
        private LocalDateTime syncTime;
    }
}
