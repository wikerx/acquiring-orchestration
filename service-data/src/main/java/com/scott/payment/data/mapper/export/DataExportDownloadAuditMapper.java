package com.scott.payment.data.mapper.export;

import com.scott.payment.data.entity.export.DataExportDownloadAuditDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 导出文件下载审计 Mapper，先固化请求事实，再条件更新最终结果。
 */
public interface DataExportDownloadAuditMapper {

    /**
     * 在输出文件前追加 REQUESTED 审计，避免响应发送后审计缺失。
     */
    @Insert("""
            INSERT INTO data_export_download_audit (
                audit_no, task_no, operator_account_id, operator_account_name, client_ip,
                result_status, failure_reason, file_sha256, requested_at, update_time
            ) VALUES (
                #{auditNo}, #{taskNo}, #{operatorAccountId}, #{operatorAccountName}, #{clientIp},
                #{resultStatus}, #{failureReason}, #{fileSha256}, #{requestedAt}, #{updateTime}
            )
            """)
    int insert(DataExportDownloadAuditDO audit);

    /**
     * 将指定 REQUESTED 审计条件更新为最终状态，重复完成不会覆盖首个结果。
     */
    @Update("""
            UPDATE data_export_download_audit
            SET result_status = #{resultStatus}, failure_reason = #{failureReason},
                downloaded_at = #{downloadedAt}, update_time = #{downloadedAt}
            WHERE audit_no = #{auditNo} AND result_status = 'REQUESTED'
            """)
    int complete(@Param("auditNo") String auditNo,
                 @Param("resultStatus") String resultStatus,
                 @Param("failureReason") String failureReason,
                 @Param("downloadedAt") LocalDateTime downloadedAt);
}
