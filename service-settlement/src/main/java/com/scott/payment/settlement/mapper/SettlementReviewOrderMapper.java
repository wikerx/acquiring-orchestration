package com.scott.payment.settlement.mapper;

import com.scott.payment.settlement.entity.SettlementReviewOrderDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementReviewOrderMapper
 * @date : 2026-09-01 00:00
 * @email : scott_x@163.com
 * @description : 结算预审单数据访问接口；预审只追加，创建/决策请求键由唯一约束兜底，所有决策按待复核状态和版本 CAS 进入不可逆终态。
 * @status : create
 */
public interface SettlementReviewOrderMapper {

    /**
     * 追加待复核预审单；单号和创建请求键冲突时保持原行不变。
     * @param row 已冻结选择、来源、汇率、结果及 Maker 审计的预审单
     * @return 新插入行数，合法重放时为 0
     */
    @Insert("""
            INSERT INTO settlement_review_order
            (review_order_no, create_request_key, selection_fingerprint, review_type, create_mode,
             merchant_id, settlement_profile_id, settlement_account_id, target_currency,
             target_currency_exponent, business_date, business_time_zone, cutoff_begin_time,
             cutoff_end_time, candidate_count, projectable_candidate_count, source_fingerprint,
             rate_fingerprint, result_fingerprint, net_direction, net_amount, review_status,
             created_by_account_id, created_by_account_name, submitted_by_account_id,
             submitted_by_account_name, submitted_role_snapshot, submit_client_ip,
             submit_user_agent, submit_reason, submitted_time, version, create_time, update_time)
            VALUES
            (#{row.reviewOrderNo}, #{row.createRequestKey}, #{row.selectionFingerprint},
             #{row.reviewType}, #{row.createMode}, #{row.merchantId}, #{row.settlementProfileId},
             #{row.settlementAccountId}, #{row.targetCurrency}, #{row.targetCurrencyExponent},
             #{row.businessDate}, #{row.businessTimeZone}, #{row.cutoffBeginTime}, #{row.cutoffEndTime},
             #{row.candidateCount}, #{row.projectableCandidateCount}, #{row.sourceFingerprint},
             #{row.rateFingerprint}, #{row.resultFingerprint}, #{row.netDirection}, #{row.netAmount},
             #{row.reviewStatus}, #{row.createdByAccountId}, #{row.createdByAccountName},
             #{row.submittedByAccountId}, #{row.submittedByAccountName}, #{row.submittedRoleSnapshot},
             #{row.submitClientIp}, #{row.submitUserAgent}, #{row.submitReason}, #{row.submittedTime},
             #{row.version}, #{row.createTime}, #{row.updateTime})
            ON DUPLICATE KEY UPDATE id = id
            """)
    int insertIdempotent(@Param("row") SettlementReviewOrderDO row);

    /**
     * 按创建请求幂等键锁读预审单。
     * @param requestKey 创建请求幂等键
     * @return 匹配预审单，不存在时为空
     */
    @Select("""
            SELECT *
            FROM settlement_review_order
            WHERE create_request_key = #{requestKey}
            LIMIT 1
            FOR UPDATE
            """)
    SettlementReviewOrderDO selectByCreateRequestKeyForUpdate(@Param("requestKey") String requestKey);

    /**
     * 按预审单号锁读当前状态和版本。
     * @param reviewOrderNo 结算预审单号
     * @return 匹配预审单，不存在时为空
     */
    @Select("""
            SELECT *
            FROM settlement_review_order
            WHERE review_order_no = #{reviewOrderNo}
            LIMIT 1
            FOR UPDATE
            """)
    SettlementReviewOrderDO selectByReviewOrderNoForUpdate(@Param("reviewOrderNo") String reviewOrderNo);

    /**
     * 按决策请求幂等键锁读终态预审单。
     * @param requestKey 决策请求幂等键
     * @return 匹配预审单，不存在时为空
     */
    @Select("""
            SELECT *
            FROM settlement_review_order
            WHERE decision_request_key = #{requestKey}
            LIMIT 1
            FOR UPDATE
            """)
    SettlementReviewOrderDO selectByDecisionRequestKeyForUpdate(@Param("requestKey") String requestKey);

    /**
     * 从待复核状态按版本 CAS 批准并绑定正式结算批次。
     * @param row 已填充 Checker 审计和正式批次号的预审单
     * @param expectedVersion 决策前版本
     * @return 成功更新行数，必须为 1
     */
    @Update("""
            UPDATE settlement_review_order
            SET review_status = 'APPROVED',
                decided_by_account_id = #{row.decidedByAccountId},
                decided_by_account_name = #{row.decidedByAccountName},
                decided_role_snapshot = #{row.decidedRoleSnapshot},
                decision_client_ip = #{row.decisionClientIp},
                decision_user_agent = #{row.decisionUserAgent},
                decision_action = 'APPROVE',
                decision_request_key = #{row.decisionRequestKey},
                review_comment = #{row.reviewComment},
                decision_time = #{row.decisionTime},
                settlement_batch_no = #{row.settlementBatchNo},
                version = version + 1,
                update_time = #{row.decisionTime}
            WHERE review_order_no = #{row.reviewOrderNo}
              AND review_status = 'PENDING_APPROVAL'
              AND version = #{expectedVersion}
            """)
    int approve(@Param("row") SettlementReviewOrderDO row,
                @Param("expectedVersion") long expectedVersion);

    /**
     * 从待复核状态按版本 CAS 进入拒绝、取消或过期终态。
     * @param row 已填充 Checker 或系统终止审计的预审单
     * @param terminalStatus 允许的非批准终态
     * @param expectedVersion 决策前版本
     * @return 成功更新行数，必须为 1
     */
    @Update("""
            UPDATE settlement_review_order
            SET review_status = #{terminalStatus},
                decided_by_account_id = #{row.decidedByAccountId},
                decided_by_account_name = #{row.decidedByAccountName},
                decided_role_snapshot = #{row.decidedRoleSnapshot},
                decision_client_ip = #{row.decisionClientIp},
                decision_user_agent = #{row.decisionUserAgent},
                decision_action = #{row.decisionAction},
                decision_request_key = #{row.decisionRequestKey},
                review_comment = #{row.reviewComment},
                decision_time = #{row.decisionTime},
                version = version + 1,
                update_time = #{row.decisionTime}
            WHERE review_order_no = #{row.reviewOrderNo}
              AND review_status = 'PENDING_APPROVAL'
              AND version = #{expectedVersion}
            """)
    int terminate(@Param("row") SettlementReviewOrderDO row,
                  @Param("terminalStatus") String terminalStatus,
                  @Param("expectedVersion") long expectedVersion);
}
