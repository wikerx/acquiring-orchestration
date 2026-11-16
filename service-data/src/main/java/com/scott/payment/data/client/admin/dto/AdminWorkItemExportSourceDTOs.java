package com.scott.payment.data.client.admin.dto;

import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.WorkItemQuery;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Data 分页读取 Admin 统一待办导出源的内部契约，不包含处理轨迹或来源敏感载荷。
 */
public final class AdminWorkItemExportSourceDTOs {
    private AdminWorkItemExportSourceDTOs() {
    }

    /**
     * 单页读取请求，数据范围来自任务创建时冻结的可信快照。
     */
    @Data
    public static class PageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long creatorAccountId;
        private boolean allMerchants;
        private Set<String> merchantIds = new LinkedHashSet<>();
        private WorkItemQuery query;
        private Integer pageNo;
        private Integer pageSize;
    }

    /**
     * 一页导出源数据和总行数。
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
     * 统一待办导出行，只保留运营摘要字段。
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
