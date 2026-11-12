package com.scott.payment.data.mapper.export;

import com.scott.payment.data.entity.export.DataExportTaskDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 异步导出任务 Mapper，只负责稳定分页、租约 CAS 和任务状态持久化。
 */
public interface DataExportTaskMapper {

    String COLUMNS = "id, task_no, request_id, task_type, task_name, source_service, "
            + "creator_app_code, creator_account_id, merchant_id, creator_account_name, locale, timezone, query_snapshot, "
            + "data_scope_snapshot, status, progress_percent, processed_rows, total_rows, "
            + "file_name, content_type, object_key, file_size_bytes, file_sha256, expires_at, "
            + "failure_code, failure_reason, retry_count, max_retry_count, next_retry_time, "
            + "lease_owner, lease_until, version, started_at, finished_at, create_time, update_time";

    /**
     * 插入一条初始 PENDING 任务；账号和请求键唯一约束提供重复提交幂等保护。
     */
    @Insert("""
            INSERT INTO data_export_task (
                task_no, request_id, task_type, task_name, source_service,
                creator_app_code, creator_account_id, merchant_id, creator_account_name, locale, timezone,
                query_snapshot, data_scope_snapshot, status, progress_percent,
                processed_rows, total_rows, retry_count, max_retry_count,
                next_retry_time, version, create_time, update_time
            ) VALUES (
                #{taskNo}, #{requestId}, #{taskType}, #{taskName}, #{sourceService},
                #{creatorAppCode}, #{creatorAccountId}, #{merchantId}, #{creatorAccountName}, #{locale}, #{timezone},
                #{querySnapshot}, #{dataScopeSnapshot}, #{status}, #{progressPercent},
                #{processedRows}, #{totalRows}, #{retryCount}, #{maxRetryCount},
                #{nextRetryTime}, #{version}, #{createTime}, #{updateTime}
            )
            """)
    int insert(DataExportTaskDO task);

    /**
     * 按创建账号和请求键查询幂等任务。
     */
    @Select("SELECT " + COLUMNS + " FROM data_export_task WHERE creator_app_code = #{appCode} "
            + "AND creator_account_id = #{accountId} AND (merchant_id <=> #{merchantId}) "
            + "AND request_id = #{requestId} LIMIT 1")
    DataExportTaskDO selectByRequestId(@Param("appCode") String appCode,
                                       @Param("accountId") Long accountId,
                                       @Param("merchantId") String merchantId,
                                       @Param("requestId") String requestId);

    /**
     * 按任务号和创建账号读取任务，禁止跨账号枚举下载记录。
     */
    @Select("SELECT " + COLUMNS + " FROM data_export_task WHERE task_no = #{taskNo} "
            + "AND creator_app_code = #{appCode} AND creator_account_id = #{accountId} "
            + "AND (merchant_id <=> #{merchantId}) LIMIT 1")
    DataExportTaskDO selectOwned(@Param("taskNo") String taskNo,
                                 @Param("appCode") String appCode,
                                 @Param("accountId") Long accountId,
                                 @Param("merchantId") String merchantId);

    /**
     * 按数据库主键读取领取后的任务快照。
     */
    @Select("SELECT " + COLUMNS + " FROM data_export_task WHERE id = #{id} LIMIT 1")
    DataExportTaskDO selectById(@Param("id") Long id);

    /**
     * 查询当前账号任务总数，筛选条件均使用受控枚举或半开时间范围。
     */
    @Select("""
            <script>
            SELECT COUNT(1) FROM data_export_task
            WHERE creator_app_code = #{appCode}
              AND creator_account_id = #{accountId}
              AND (merchant_id &lt;=&gt; #{merchantId})
            <if test="taskType != null and taskType != ''">AND task_type = #{taskType}</if>
            <if test="status != null and status != ''">AND status = #{status}</if>
            <if test="startTime != null">AND create_time &gt;= #{startTime}</if>
            <if test="endTime != null">AND create_time &lt;= #{endTime}</if>
            </script>
            """)
    long countOwned(@Param("appCode") String appCode,
                    @Param("accountId") Long accountId,
                    @Param("merchantId") String merchantId,
                    @Param("taskType") String taskType,
                    @Param("status") String status,
                    @Param("startTime") LocalDateTime startTime,
                    @Param("endTime") LocalDateTime endTime);

    /**
     * 稳定分页查询当前账号任务，按创建时间和主键倒序避免同毫秒记录漂移。
     */
    @Select("<script>SELECT " + COLUMNS + " FROM data_export_task " +
            """
            WHERE creator_app_code = #{appCode}
              AND creator_account_id = #{accountId}
              AND (merchant_id &lt;=&gt; #{merchantId})
            <if test="taskType != null and taskType != ''">AND task_type = #{taskType}</if>
            <if test="status != null and status != ''">AND status = #{status}</if>
            <if test="startTime != null">AND create_time &gt;= #{startTime}</if>
            <if test="endTime != null">AND create_time &lt;= #{endTime}</if>
            ORDER BY create_time DESC, id DESC
            LIMIT #{offset}, #{limit}
            </script>
            """)
    List<DataExportTaskDO> selectOwnedPage(@Param("appCode") String appCode,
                                           @Param("accountId") Long accountId,
                                           @Param("merchantId") String merchantId,
                                           @Param("taskType") String taskType,
                                           @Param("status") String status,
                                           @Param("startTime") LocalDateTime startTime,
                                           @Param("endTime") LocalDateTime endTime,
                                           @Param("offset") long offset,
                                           @Param("limit") int limit);

    /**
     * 查询到期且租约可抢占的任务候选；最终并发保护由后续版本 CAS 完成。
     */
    @Select("SELECT " + COLUMNS + " FROM data_export_task "
            + "WHERE status = 'PENDING' AND next_retry_time <= #{now} "
            + "AND (lease_until IS NULL OR lease_until < #{now}) "
            + "ORDER BY next_retry_time ASC, id ASC LIMIT #{limit}")
    List<DataExportTaskDO> selectDispatchCandidates(@Param("now") LocalDateTime now,
                                                    @Param("limit") int limit);

    /**
     * 查询租约已过期的 PROCESSING 任务，由调度服务先记录失败再按重试策略重新排队。
     */
    @Select("SELECT " + COLUMNS + " FROM data_export_task "
            + "WHERE status = 'PROCESSING' AND (lease_until IS NULL OR lease_until < #{now}) "
            + "ORDER BY COALESCE(lease_until, update_time) ASC, id ASC LIMIT #{limit}")
    List<DataExportTaskDO> selectStaleProcessing(@Param("now") LocalDateTime now,
                                                 @Param("limit") int limit);

    /**
     * 使用版本、状态和过期租约条件领取任务，保证多 Job 节点最多一个执行者。
     */
    @Update("""
            UPDATE data_export_task
            SET status = 'PROCESSING', lease_owner = #{leaseOwner}, lease_until = #{leaseUntil},
                started_at = COALESCE(started_at, #{now}), failure_code = NULL,
                failure_reason = NULL, progress_percent = 0, processed_rows = 0,
                version = version + 1, update_time = #{now}
            WHERE id = #{id} AND version = #{expectedVersion} AND status = 'PENDING'
              AND (lease_until IS NULL OR lease_until < #{now})
            """)
    int claim(@Param("id") Long id,
              @Param("expectedVersion") Long expectedVersion,
              @Param("leaseOwner") String leaseOwner,
              @Param("leaseUntil") LocalDateTime leaseUntil,
              @Param("now") LocalDateTime now);

    /**
     * 执行器容量不足时释放当前领取，不增加业务重试次数并延后再次领取。
     */
    @Update("""
            UPDATE data_export_task
            SET status = 'PENDING', next_retry_time = #{nextRetryTime},
                lease_owner = NULL, lease_until = NULL, version = version + 1, update_time = #{now}
            WHERE id = #{id} AND version = #{processingVersion}
              AND status = 'PROCESSING' AND lease_owner = #{leaseOwner}
            """)
    int deferClaim(@Param("id") Long id,
                   @Param("processingVersion") Long processingVersion,
                   @Param("leaseOwner") String leaseOwner,
                   @Param("nextRetryTime") LocalDateTime nextRetryTime,
                   @Param("now") LocalDateTime now);

    /**
     * 更新当前执行者的分页进度并延长租约，避免长导出被另一节点回收。
     */
    @Update("""
            UPDATE data_export_task
            SET processed_rows = #{processedRows}, total_rows = #{totalRows},
                progress_percent = #{progressPercent}, lease_until = #{leaseUntil},
                update_time = #{now}
            WHERE id = #{id} AND version = #{processingVersion}
              AND status = 'PROCESSING'
              AND (lease_owner = #{leaseOwner} OR (lease_owner IS NULL AND #{leaseOwner} IS NULL))
            """)
    int updateProgress(@Param("id") Long id,
                       @Param("processingVersion") Long processingVersion,
                       @Param("leaseOwner") String leaseOwner,
                       @Param("processedRows") long processedRows,
                       @Param("totalRows") long totalRows,
                       @Param("progressPercent") int progressPercent,
                       @Param("leaseUntil") LocalDateTime leaseUntil,
                       @Param("now") LocalDateTime now);

    /**
     * 将当前领取任务推进为 SUCCESS，并一次保存文件元数据和摘要。
     */
    @Update("""
            UPDATE data_export_task
            SET status = 'SUCCESS', progress_percent = 100, processed_rows = #{processedRows},
                total_rows = #{totalRows}, file_name = #{fileName}, content_type = #{contentType},
                object_key = #{objectKey}, file_size_bytes = #{fileSizeBytes},
                file_sha256 = #{fileSha256}, expires_at = #{expiresAt},
                finished_at = #{now}, lease_owner = NULL, lease_until = NULL,
                next_retry_time = NULL, version = version + 1, update_time = #{now}
            WHERE id = #{id} AND version = #{processingVersion}
              AND status = 'PROCESSING' AND lease_owner = #{leaseOwner}
            """)
    int markSuccess(@Param("id") Long id,
                    @Param("processingVersion") Long processingVersion,
                    @Param("leaseOwner") String leaseOwner,
                    @Param("processedRows") long processedRows,
                    @Param("totalRows") long totalRows,
                    @Param("fileName") String fileName,
                    @Param("contentType") String contentType,
                    @Param("objectKey") String objectKey,
                    @Param("fileSizeBytes") long fileSizeBytes,
                    @Param("fileSha256") String fileSha256,
                    @Param("expiresAt") LocalDateTime expiresAt,
                    @Param("now") LocalDateTime now);

    /**
     * 记录一次失败；未耗尽重试时恢复为 PENDING，否则保持 FAILED 终态等待人工重试。
     */
    @Update("""
            UPDATE data_export_task
            SET status = #{nextStatus}, failure_code = #{failureCode}, failure_reason = #{failureReason},
                retry_count = retry_count + 1, next_retry_time = #{nextRetryTime},
                finished_at = CASE WHEN #{nextStatus} = 'FAILED' THEN #{now} ELSE NULL END,
                lease_owner = NULL, lease_until = NULL, version = version + 1, update_time = #{now}
            WHERE id = #{id} AND version = #{processingVersion}
              AND status = 'PROCESSING'
              AND (lease_owner = #{leaseOwner} OR (lease_owner IS NULL AND #{leaseOwner} IS NULL))
            """)
    int markFailure(@Param("id") Long id,
                    @Param("processingVersion") Long processingVersion,
                    @Param("leaseOwner") String leaseOwner,
                    @Param("nextStatus") String nextStatus,
                    @Param("failureCode") String failureCode,
                    @Param("failureReason") String failureReason,
                    @Param("nextRetryTime") LocalDateTime nextRetryTime,
                    @Param("now") LocalDateTime now);

    /**
     * 由创建人将 FAILED 任务恢复为 PENDING，版本条件防止重复重试命令覆盖并发状态。
     */
    @Update("""
            UPDATE data_export_task
            SET status = 'PENDING', next_retry_time = #{now}, failure_code = NULL,
                failure_reason = NULL, finished_at = NULL, progress_percent = 0,
                processed_rows = 0, retry_count = 0, version = version + 1, update_time = #{now}
            WHERE task_no = #{taskNo} AND creator_app_code = #{appCode}
              AND creator_account_id = #{accountId} AND (merchant_id <=> #{merchantId})
              AND status = 'FAILED' AND version = #{expectedVersion}
            """)
    int retry(@Param("taskNo") String taskNo,
              @Param("appCode") String appCode,
              @Param("accountId") Long accountId,
              @Param("merchantId") String merchantId,
              @Param("expectedVersion") Long expectedVersion,
              @Param("now") LocalDateTime now);

    /**
     * 查询已到文件保留期的成功任务，清理动作逐条 CAS 执行。
     */
    @Select("SELECT " + COLUMNS + " FROM data_export_task WHERE status = 'SUCCESS' "
            + "AND expires_at IS NOT NULL AND expires_at <= #{now} ORDER BY expires_at ASC, id ASC LIMIT #{limit}")
    List<DataExportTaskDO> selectExpired(@Param("now") LocalDateTime now,
                                        @Param("limit") int limit);

    /**
     * 文件删除成功后将任务标记为 EXPIRED，并清空对象键避免后续误下载。
     */
    @Update("""
            UPDATE data_export_task
            SET status = 'EXPIRED', object_key = NULL, version = version + 1, update_time = #{now}
            WHERE id = #{id} AND version = #{expectedVersion} AND status = 'SUCCESS'
              AND expires_at IS NOT NULL AND expires_at <= #{now}
            """)
    int markExpired(@Param("id") Long id,
                    @Param("expectedVersion") Long expectedVersion,
                    @Param("now") LocalDateTime now);
}
