package com.scott.payment.settlement.mapper;

import com.scott.payment.settlement.entity.SettlementReviewDecisionTaskDO;
import com.scott.payment.settlement.entity.SettlementReviewDecisionRecoveryAuditDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 大批量预审异步决策的任务、租约和进度数据访问。
 */
public interface SettlementReviewDecisionTaskMapper {

    /**
     * 创建{@code insertIdempotent}，完成必要校验后写入或委托下游服务处理。
     * <p>
     * 写操作；实现必须沿用 结算服务 既有权限、幂等键、唯一约束和事务边界。
     * </p>
     * @param row 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @return 写入、更新或删除后的处理结果
     */
    @Insert("""
            INSERT INTO settlement_review_decision_task
            (task_no, request_key, review_order_no, expected_review_version,
             decision_action, decision_comment, operator_account_id, operator_account_name,
             operator_role_snapshot, client_ip, user_agent, operation_time,
             total_segment_count, processed_segment_count, result_batch_count,
             first_settlement_batch_no, task_status, retry_count, next_retry_time,
             version, create_time, update_time)
            VALUES
            (#{row.taskNo}, #{row.requestKey}, #{row.reviewOrderNo}, #{row.expectedReviewVersion},
             #{row.decisionAction}, #{row.decisionComment}, #{row.operatorAccountId},
             #{row.operatorAccountName}, #{row.operatorRoleSnapshot}, #{row.clientIp},
             #{row.userAgent}, #{row.operationTime}, #{row.totalSegmentCount}, 0, 0,
             NULL, 'QUEUED', 0, #{row.nextRetryTime}, 0, #{row.createTime}, #{row.updateTime})
            ON DUPLICATE KEY UPDATE id = id
            """)
    int insertIdempotent(@Param("row") SettlementReviewDecisionTaskDO row);

    /**
     * 查询{@code selectByRequestKeyForUpdate}；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 结算服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param requestKey 敏感或可识别输入，调用方必须按脱敏、加密或最小必要原则传递
     * @return 查询得到的业务对象、分页结果或空结果
     */
    @Select("SELECT * FROM settlement_review_decision_task WHERE request_key = #{requestKey} LIMIT 1 FOR UPDATE")
    SettlementReviewDecisionTaskDO selectByRequestKeyForUpdate(@Param("requestKey") String requestKey);

    /**
     * 查询{@code selectByTaskNo}；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 结算服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param taskNo 可恢复后台决策任务号
     * @return 查询得到的业务对象、分页结果或空结果
     */
    @Select("SELECT * FROM settlement_review_decision_task WHERE task_no = #{taskNo} LIMIT 1")
    SettlementReviewDecisionTaskDO selectByTaskNo(@Param("taskNo") String taskNo);

    /**
     * 查询{@code selectByTaskNoForUpdate}；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 结算服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param taskNo 待加锁读取的后台决策任务号
     * @return 查询得到的业务对象、分页结果或空结果
     */
    @Select("SELECT * FROM settlement_review_decision_task WHERE task_no = #{taskNo} LIMIT 1 FOR UPDATE")
    SettlementReviewDecisionTaskDO selectByTaskNoForUpdate(@Param("taskNo") String taskNo);

    /**
     * 查询{@code selectRecoveryAuditByRequestKey}；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 结算服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param requestKey 敏感或可识别输入，调用方必须按脱敏、加密或最小必要原则传递
     * @return 查询得到的业务对象、分页结果或空结果
     */
    @Select("""
            SELECT * FROM settlement_review_decision_recovery_audit
            WHERE request_key = #{requestKey} LIMIT 1
            """)
    SettlementReviewDecisionRecoveryAuditDO selectRecoveryAuditByRequestKey(
            @Param("requestKey") String requestKey);

    /**
     * 查询{@code selectRecoveryAuditByRequestKeyForUpdate}；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 结算服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param requestKey 敏感或可识别输入，调用方必须按脱敏、加密或最小必要原则传递
     * @return 查询得到的业务对象、分页结果或空结果
     */
    @Select("""
            SELECT * FROM settlement_review_decision_recovery_audit
            WHERE request_key = #{requestKey} LIMIT 1 FOR UPDATE
            """)
    SettlementReviewDecisionRecoveryAuditDO selectRecoveryAuditByRequestKeyForUpdate(
            @Param("requestKey") String requestKey);

    /**
     * 创建{@code insertRecoveryAuditIdempotent}，完成必要校验后写入或委托下游服务处理。
     * <p>
     * 写操作；实现必须沿用 结算服务 既有权限、幂等键、唯一约束和事务边界。
     * </p>
     * @param row 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @return 写入、更新或删除后的处理结果
     */
    @Insert("""
            INSERT INTO settlement_review_decision_recovery_audit
            (task_no, review_order_no, request_key, expected_version, task_status_before,
             processed_segment_count_before, result_batch_count_before, retry_count_before,
             failure_code_before, failure_message_before, started_time_before,
             completed_time_before, operator_account_id,
             operator_account_name, operator_role_snapshot, client_ip, user_agent, reason,
             operation_time, recovered_time, create_time)
            VALUES
            (#{row.taskNo}, #{row.reviewOrderNo}, #{row.requestKey}, #{row.expectedVersion},
             #{row.taskStatusBefore}, #{row.processedSegmentCountBefore},
             #{row.resultBatchCountBefore}, #{row.retryCountBefore}, #{row.failureCodeBefore},
             #{row.failureMessageBefore}, #{row.startedTimeBefore}, #{row.completedTimeBefore},
             #{row.operatorAccountId}, #{row.operatorAccountName},
             #{row.operatorRoleSnapshot}, #{row.clientIp}, #{row.userAgent}, #{row.reason},
             #{row.operationTime}, #{row.recoveredTime}, #{row.createTime})
            ON DUPLICATE KEY UPDATE id = id
            """)
    int insertRecoveryAuditIdempotent(
            @Param("row") SettlementReviewDecisionRecoveryAuditDO row);

    /**
     * 查询{@code selectNextDueForUpdate}；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 结算服务 既有权限、数据范围和空结果约定。
     * </p>
     * @param now 当前处理时刻，用于写入业务记录或审计记录的时间字段
     * @return 查询得到的业务对象、分页结果或空结果
     */
    @Select("""
            SELECT * FROM settlement_review_decision_task
            WHERE task_status IN ('QUEUED', 'PROCESSING')
              AND next_retry_time <= #{now}
              AND (processing_deadline IS NULL OR processing_deadline <= #{now})
            ORDER BY next_retry_time ASC, id ASC
            LIMIT 1 FOR UPDATE SKIP LOCKED
            """)
    SettlementReviewDecisionTaskDO selectNextDueForUpdate(@Param("now") LocalDateTime now);

    /**
     * 通过版本和过期租约条件领取到期任务，原子写入当前处理节点及租约截止时间。
     * @param taskNo 待领取的后台决策任务号
     * @param version 乐观锁预期版本，防止并发节点重复领取
     * @param owner 当前任务租约持有者
     * @param deadline 本次处理租约截止时间
     * @param now 当前处理时刻，用于写入业务记录或审计记录的时间字段
     * @return 当前方法计算的数量、版本或状态数值
     */
    @Update("""
            UPDATE settlement_review_decision_task
            SET task_status = 'PROCESSING', processing_owner = #{owner},
                processing_deadline = #{deadline}, started_time = COALESCE(started_time, #{now}),
                version = version + 1, update_time = #{now}
            WHERE task_no = #{taskNo} AND task_status IN ('QUEUED', 'PROCESSING')
              AND (processing_deadline IS NULL OR processing_deadline <= #{now})
              AND version = #{version}
            """)
    int markProcessing(@Param("taskNo") String taskNo,
                       @Param("version") long version,
                       @Param("owner") String owner,
                       @Param("deadline") LocalDateTime deadline,
                       @Param("now") LocalDateTime now);

    /**
     * 提交一个分段的处理进度并释放节点租约；仅当前 owner 和版本匹配时更新。
     * @param taskNo 正在处理的后台决策任务号
     * @param version 乐观锁预期版本
     * @param owner 当前任务租约持有者
     * @param resultBatchDelta 本段新生成的正式结算批次数，只允许零或一
     * @param settlementBatchNo 本段生成的正式批次号；未生成批次时为空
     * @param now 当前处理时刻，用于写入业务记录或审计记录的时间字段
     * @return 当前方法计算的数量、版本或状态数值
     */
    @Update("""
            UPDATE settlement_review_decision_task
            SET processed_segment_count = processed_segment_count + 1,
                result_batch_count = result_batch_count + #{resultBatchDelta},
                first_settlement_batch_no = COALESCE(first_settlement_batch_no, #{settlementBatchNo}),
                processing_owner = NULL, processing_deadline = NULL, next_retry_time = #{now},
                version = version + 1, update_time = #{now}
            WHERE task_no = #{taskNo} AND task_status = 'PROCESSING'
              AND processing_owner = #{owner} AND version = #{version}
              AND #{resultBatchDelta} IN (0, 1)
            """)
    int advance(@Param("taskNo") String taskNo,
                @Param("version") long version,
                @Param("owner") String owner,
                @Param("resultBatchDelta") int resultBatchDelta,
                @Param("settlementBatchNo") String settlementBatchNo,
                @Param("now") LocalDateTime now);

    /**
     * 在全部分段处理完成且 owner、版本匹配时将任务推进到完成终态。
     * @param taskNo 待完成的后台决策任务号
     * @param version 乐观锁预期版本
     * @param owner 当前任务租约持有者
     * @param now 当前处理时刻，用于写入业务记录或审计记录的时间字段
     * @return 当前方法计算的数量、版本或状态数值
     */
    @Update("""
            UPDATE settlement_review_decision_task
            SET task_status = 'COMPLETED', completed_time = #{now}, processing_owner = NULL,
                processing_deadline = NULL, version = version + 1, update_time = #{now}
            WHERE task_no = #{taskNo} AND task_status = 'PROCESSING'
              AND processing_owner = #{owner} AND version = #{version}
              AND processed_segment_count = total_segment_count
            """)
    int markCompleted(@Param("taskNo") String taskNo,
                      @Param("version") long version,
                      @Param("owner") String owner,
                      @Param("now") LocalDateTime now);

    /**
     * 记录可重试失败并释放处理租约，将任务重新排入下一次执行时间。
     * @param taskNo 待重新调度的后台决策任务号
     * @param version 乐观锁预期版本
     * @param failureCode 受控失败码或失败说明，用于状态机、商户文案映射和审计排障
     * @param failureMessage 受控失败码或失败说明，用于状态机、商户文案映射和审计排障
     * @param nextRetryTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @param now 当前处理时刻，用于写入业务记录或审计记录的时间字段
     * @return 当前方法计算的数量、版本或状态数值
     */
    @Update("""
            UPDATE settlement_review_decision_task
            SET task_status = 'QUEUED', retry_count = retry_count + 1,
                last_failure_code = #{failureCode}, last_failure_message = #{failureMessage},
                processing_owner = NULL, processing_deadline = NULL,
                next_retry_time = #{nextRetryTime}, version = version + 1, update_time = #{now}
            WHERE task_no = #{taskNo} AND task_status = 'PROCESSING' AND version = #{version}
            """)
    int reschedule(@Param("taskNo") String taskNo,
                   @Param("version") long version,
                   @Param("failureCode") String failureCode,
                   @Param("failureMessage") String failureMessage,
                   @Param("nextRetryTime") LocalDateTime nextRetryTime,
                   @Param("now") LocalDateTime now);

    /**
     * 记录不可重试失败并将任务推进到失败终态。
     * @param taskNo 待终止的后台决策任务号
     * @param version 乐观锁预期版本
     * @param failureCode 受控失败码或失败说明，用于状态机、商户文案映射和审计排障
     * @param failureMessage 受控失败码或失败说明，用于状态机、商户文案映射和审计排障
     * @param now 当前处理时刻，用于写入业务记录或审计记录的时间字段
     * @return 当前方法计算的数量、版本或状态数值
     */
    @Update("""
            UPDATE settlement_review_decision_task
            SET task_status = 'FAILED', retry_count = retry_count + 1,
                last_failure_code = #{failureCode}, last_failure_message = #{failureMessage},
                processing_owner = NULL, processing_deadline = NULL, completed_time = #{now},
                version = version + 1, update_time = #{now}
            WHERE task_no = #{taskNo} AND task_status = 'PROCESSING' AND version = #{version}
            """)
    int markFailed(@Param("taskNo") String taskNo,
                   @Param("version") long version,
                   @Param("failureCode") String failureCode,
                   @Param("failureMessage") String failureMessage,
                   @Param("now") LocalDateTime now);

    /**
     * 恢复失败处理，复用既有幂等身份并从已持久化阶段继续执行。
     * @param taskNo 待恢复的失败决策任务号
     * @param expectedVersion 调用方读取到的任务版本，防止并发恢复覆盖
     * @param now 当前处理时刻，用于写入业务记录或审计记录的时间字段
     * @return 当前方法计算的数量、版本或状态数值
     */
    @Update("""
            UPDATE settlement_review_decision_task
            SET task_status = 'QUEUED', processing_owner = NULL, processing_deadline = NULL,
                retry_count = 0, next_retry_time = #{now}, last_failure_code = NULL,
                last_failure_message = NULL, started_time = NULL, completed_time = NULL,
                version = version + 1, update_time = #{now}
            WHERE task_no = #{taskNo} AND task_status = 'FAILED' AND version = #{expectedVersion}
            """)
    int resumeFailed(@Param("taskNo") String taskNo,
                     @Param("expectedVersion") long expectedVersion,
                     @Param("now") LocalDateTime now);
}
