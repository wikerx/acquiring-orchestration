package com.scott.payment.clearing.mapper;

import com.scott.payment.clearing.entity.ClearingReserveAdjustmentDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingReserveAdjustmentMapper
 * @date : 2026-08-27 19:46
 * @email : scott_x@163.com
 * @description : 保证金调整审批 Mapper；申请只追加，批准或拒绝只允许从 PENDING_REVIEW 版本 CAS。
 * @status : update
 */
public interface ClearingReserveAdjustmentMapper {

    /**
     * 以请求键唯一约束幂等追加保证金调整申请，不覆盖既有审批事实。
     *
     * @param row 已冻结标签币种、金额、方向、Maker 和保证金状态版本的申请
     * @return 实际插入行数；唯一键重复时可能为 0
     */
    @Insert("""
            INSERT INTO clearing_reserve_adjustment
            (adjustment_no, request_key, reserve_state_id, original_transaction_id,
             original_transaction_date_time, merchant_id, reserve_currency,
             reserve_currency_exponent, direction, adjustment_amount, requested_release_date,
             expected_reserve_state_version, reason, submit_operator, adjustment_status,
             version, create_time, update_time)
            VALUES
            (#{row.adjustmentNo}, #{row.requestKey}, #{row.reserveStateId},
             #{row.originalTransactionId}, #{row.originalTransactionDateTime}, #{row.merchantId},
             #{row.reserveCurrency}, #{row.reserveCurrencyExponent}, #{row.direction},
             #{row.adjustmentAmount}, #{row.requestedReleaseDate},
             #{row.expectedReserveStateVersion}, #{row.reason}, #{row.submitOperator},
             #{row.adjustmentStatus}, #{row.version}, #{row.createTime}, #{row.updateTime})
            ON DUPLICATE KEY UPDATE id = id
            """)
    int insertIdempotent(@Param("row") ClearingReserveAdjustmentDO row);

    /**
     * 通过请求幂等键锁定既有调整申请。
     *
     * @param requestKey 调整申请全局幂等键
     * @return 已加行锁的申请，不存在时返回 null
     */
    @Select("""
            SELECT * FROM clearing_reserve_adjustment
            WHERE request_key = #{requestKey}
            LIMIT 1 FOR UPDATE
            """)
    ClearingReserveAdjustmentDO selectByRequestKeyForUpdate(@Param("requestKey") String requestKey);

    /**
     * 通过调整单号锁定申请，供 Maker-Checker 与资金状态原子校验。
     *
     * @param adjustmentNo 保证金调整单号
     * @return 已加行锁的申请，不存在时返回 null
     */
    @Select("""
            SELECT * FROM clearing_reserve_adjustment
            WHERE adjustment_no = #{adjustmentNo}
            LIMIT 1 FOR UPDATE
            """)
    ClearingReserveAdjustmentDO selectForUpdate(@Param("adjustmentNo") String adjustmentNo);

    /**
     * Checker 拒绝待复核申请，只允许 PENDING_REVIEW 版本 CAS。
     *
     * @param adjustmentNo 保证金调整单号
     * @param expectedVersion 待复核申请预期版本
     * @param reviewOperator 可信 Checker 身份快照
     * @param reviewComment 复核意见
     * @param reviewTime 复核时间
     * @return 成功更新行数，必须为 1
     */
    @Update("""
            UPDATE clearing_reserve_adjustment
            SET adjustment_status = 'REJECTED', review_operator = #{reviewOperator},
                review_comment = #{reviewComment}, review_time = #{reviewTime},
                version = version + 1, update_time = #{reviewTime}
            WHERE adjustment_no = #{adjustmentNo}
              AND adjustment_status = 'PENDING_REVIEW'
              AND version = #{expectedVersion}
            """)
    int markRejected(@Param("adjustmentNo") String adjustmentNo,
                     @Param("expectedVersion") long expectedVersion,
                     @Param("reviewOperator") String reviewOperator,
                     @Param("reviewComment") String reviewComment,
                     @Param("reviewTime") LocalDateTime reviewTime);

    /**
     * 保证金状态、不可变动作和结算候选提交后，将申请原子标记为 EXECUTED。
     *
     * @param adjustmentNo 保证金调整单号
     * @param expectedVersion 待复核申请预期版本
     * @param reviewOperator 可信 Checker 身份快照
     * @param reviewComment 复核意见
     * @param reviewTime 复核时间
     * @param executionTransactionId 独立保证金调整动作号
     * @param sourceRevision 保证金资金化修订号
     * @param executedTime 资金事实提交时间
     * @return 成功更新行数，必须为 1
     */
    @Update("""
            UPDATE clearing_reserve_adjustment
            SET adjustment_status = 'EXECUTED', review_operator = #{reviewOperator},
                review_comment = #{reviewComment}, review_time = #{reviewTime},
                execution_transaction_id = #{executionTransactionId},
                source_revision = #{sourceRevision}, executed_time = #{executedTime},
                version = version + 1, update_time = #{executedTime}
            WHERE adjustment_no = #{adjustmentNo}
              AND adjustment_status = 'PENDING_REVIEW'
              AND version = #{expectedVersion}
            """)
    int markExecuted(@Param("adjustmentNo") String adjustmentNo,
                     @Param("expectedVersion") long expectedVersion,
                     @Param("reviewOperator") String reviewOperator,
                     @Param("reviewComment") String reviewComment,
                     @Param("reviewTime") LocalDateTime reviewTime,
                     @Param("executionTransactionId") String executionTransactionId,
                     @Param("sourceRevision") int sourceRevision,
                     @Param("executedTime") LocalDateTime executedTime);
}
