package com.scott.payment.data.service.export;

import java.time.LocalDateTime;

/**
 * 已成功上传的导出文件元数据，供任务状态提交和失败清理使用。
 */
public record ExportArtifact(String fileName,
                             String contentType,
                             String objectKey,
                             long fileSizeBytes,
                             String fileSha256,
                             long processedRows,
                             long totalRows,
                             LocalDateTime expiresAt) {
}
