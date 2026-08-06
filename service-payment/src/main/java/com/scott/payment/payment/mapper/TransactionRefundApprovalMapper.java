package com.scott.payment.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.payment.entity.TransactionRefundApprovalDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionRefundApprovalMapper
 * @date : 2026-08-06 00:00
 * @description : 退款审批普通表 Mapper，仅负责审批工作队列查询、插入和带版本条件的状态更新。
 * @status : create
 */
public interface TransactionRefundApprovalMapper extends BaseMapper<TransactionRefundApprovalDO> {

    /**
     * 插入退款审批任务。
     *
     * @param row 审批任务
     * @return 影响行数
     */
    @Insert("""
            INSERT INTO transaction_refund_approval
            (approval_id, refund_transaction_id, refund_transaction_date_time,
             source_transaction_id, source_transaction_date_time, root_transaction_date_time,
             merchant_id, approval_status, approval_policy_code, approval_policy_snapshot,
             current_approval_level, total_approval_levels, applicant_type, applicant_id,
             applicant_name, expire_time, version, create_time, update_time)
            VALUES
            (#{row.approvalId}, #{row.refundTransactionId}, #{row.refundTransactionDateTime},
             #{row.sourceTransactionId}, #{row.sourceTransactionDateTime}, #{row.rootTransactionDateTime},
             #{row.merchantId}, #{row.approvalStatus}, #{row.approvalPolicyCode}, #{row.approvalPolicySnapshot},
             #{row.currentApprovalLevel}, #{row.totalApprovalLevels}, #{row.applicantType}, #{row.applicantId},
             #{row.applicantName}, #{row.expireTime}, #{row.version}, #{row.createTime}, #{row.updateTime})
            """)
    int insertApproval(@Param("row") TransactionRefundApprovalDO row);

    /**
     * 在主库事务中锁定审批任务，路由时间只能从该记录读取。
     *
     * @param approvalId 审批单号
     * @return 审批任务
     */
    @Select("""
            SELECT *
            FROM transaction_refund_approval
            WHERE approval_id = #{approvalId}
            LIMIT 1
            FOR UPDATE
            """)
    TransactionRefundApprovalDO selectByApprovalIdForUpdate(@Param("approvalId") String approvalId);

    /**
     * 按退款交易号查询唯一审批任务。
     *
     * @param refundTransactionId 退款交易号
     * @return 审批任务
     */
    @Select("""
            SELECT *
            FROM transaction_refund_approval
            WHERE refund_transaction_id = #{refundTransactionId}
            LIMIT 1
            """)
    TransactionRefundApprovalDO selectByRefundTransactionId(
            @Param("refundTransactionId") String refundTransactionId);

    /**
     * 使用当前状态和版本完成审批决策。
     *
     * @return 影响行数
     */
    @Update("""
            UPDATE transaction_refund_approval
            SET approval_status = #{targetStatus},
                approval_operator_id = #{operatorId},
                approval_operator_name = #{operatorName},
                approval_time = #{decisionTime},
                approval_reason = #{reason},
                decision_request_id = #{decisionRequestId},
                execution_event_id = #{executionEventId},
                version = version + 1,
                update_time = #{decisionTime}
            WHERE approval_id = #{approvalId}
              AND approval_status = 'PENDING'
              AND version = #{expectedVersion}
            """)
    int decide(@Param("approvalId") String approvalId,
               @Param("expectedVersion") Integer expectedVersion,
               @Param("targetStatus") String targetStatus,
               @Param("operatorId") String operatorId,
               @Param("operatorName") String operatorName,
               @Param("reason") String reason,
               @Param("decisionRequestId") String decisionRequestId,
               @Param("executionEventId") String executionEventId,
               @Param("decisionTime") LocalDateTime decisionTime);

    /**
     * 查询到期且仍待处理的审批任务。
     *
     * @param now 当前时间
     * @param limit 最大数量
     * @return 到期任务
     */
    @Select("""
            SELECT *
            FROM transaction_refund_approval
            WHERE approval_status = 'PENDING'
              AND expire_time <= #{now}
            ORDER BY expire_time ASC, id ASC
            LIMIT #{limit}
            """)
    List<TransactionRefundApprovalDO> selectExpired(@Param("now") LocalDateTime now,
                                                    @Param("limit") int limit);

    /**
     * 查询超过恢复阈值且仍处于批准状态的退款审批任务。
     *
     * @param staleBefore 最晚更新时间阈值
     * @param limit 最大数量
     * @return 待核对执行事件的审批任务
     */
    @Select("""
            SELECT *
            FROM transaction_refund_approval
            WHERE approval_status = 'APPROVED'
              AND execution_event_id IS NOT NULL
              AND update_time <= #{staleBefore}
            ORDER BY update_time ASC, id ASC
            LIMIT #{limit}
            """)
    List<TransactionRefundApprovalDO> selectApprovedForRecovery(
            @Param("staleBefore") LocalDateTime staleBefore,
            @Param("limit") int limit);
}
