package com.scott.payment.data.entity.export;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 异步导出任务持久化实体，保存可信条件快照、执行租约、文件摘要和可恢复状态。
 */
@Data
public class DataExportTaskDO {
    private Long id;
    private String taskNo;
    private String requestId;
    private String taskType;
    private String taskName;
    private String sourceService;
    private Long creatorAccountId;
    private String creatorAccountName;
    private String locale;
    private String timezone;
    private String querySnapshot;
    private String dataScopeSnapshot;
    private String status;
    private Integer progressPercent;
    private Long processedRows;
    private Long totalRows;
    private String fileName;
    private String contentType;
    private String objectKey;
    private Long fileSizeBytes;
    private String fileSha256;
    private LocalDateTime expiresAt;
    private String failureCode;
    private String failureReason;
    private Integer retryCount;
    private Integer maxRetryCount;
    private LocalDateTime nextRetryTime;
    private String leaseOwner;
    private LocalDateTime leaseUntil;
    private Long version;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
