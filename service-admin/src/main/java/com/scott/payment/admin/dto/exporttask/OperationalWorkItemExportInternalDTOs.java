package com.scott.payment.admin.dto.exporttask;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 统一待办异步导出源内部契约，仅承载冻结查询、冻结商户范围和分页摘要。
 *
 * <p>该契约由通过 HMAC 鉴权的 service-data 调用，不能作为浏览器提交数据范围的入口。</p>
 */
public final class OperationalWorkItemExportInternalDTOs {

    private OperationalWorkItemExportInternalDTOs() {
    }

    /**
     * 创建任务时冻结的统一待办查询条件，不包含可被执行器篡改的分页参数。
     */
    @Data
    public static class WorkItemQuery implements Serializable {
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
    }

    /**
     * service-data 分页读取请求，商户集合必须与任务保存的可信快照一致。
     */
    @Data
    public static class PageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotNull
        private Long creatorAccountId;
        private boolean allMerchants;
        private Set<String> merchantIds = new LinkedHashSet<>();
        @Valid
        @NotNull
        private WorkItemQuery query;
        @NotNull
        @Min(1)
        private Integer pageNo;
        @NotNull
        @Min(1)
        @Max(100)
        private Integer pageSize;
    }

    /**
     * 一页权威待办摘要及总行数，Data 仅负责写入文件而不重写查询规则。
     */
    @Data
    public static class PageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private long pageNo;
        private long pageSize;
        private List<Row> records = new ArrayList<>();
    }

    /**
     * 导出源单行摘要，不包含来源敏感载荷和待办处理轨迹。
     */
    @Data
    public static class Row implements Serializable {
        private static final long serialVersionUID = 1L;
        private String workItemNo;
        private String sourceType;
        private String businessNo;
        private String merchantId;
        private String workStatus;
        private String sourceStatus;
        private String priority;
        private String ownerAccountName;
        private String currency;
        private String amount;
        private LocalDateTime firstDetectedAt;
        private LocalDateTime slaDueAt;
        private Boolean overdue;
        private String latestRemark;
    }
}
