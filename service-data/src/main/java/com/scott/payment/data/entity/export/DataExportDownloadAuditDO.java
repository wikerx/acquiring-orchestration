package com.scott.payment.data.entity.export;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 导出文件下载审计实体，记录操作人、来源 IP、结果和文件摘要，不保存文件正文或查询条件。
 */
@Data
public class DataExportDownloadAuditDO {
    private Long id;
    private String auditNo;
    private String taskNo;
    private Long operatorAccountId;
    private String operatorAccountName;
    private String clientIp;
    private String resultStatus;
    private String failureReason;
    private String fileSha256;
    private LocalDateTime requestedAt;
    private LocalDateTime downloadedAt;
    private LocalDateTime updateTime;
}
