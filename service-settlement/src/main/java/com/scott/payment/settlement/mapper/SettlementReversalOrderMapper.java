package com.scott.payment.settlement.mapper;

import com.scott.payment.settlement.entity.SettlementReversalOrderDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementReversalOrderMapper
 * @date : 2026-09-01 00:00
 * @email : scott_x@163.com
 * @description : 冲正申请数据访问接口；申请只追加，创建/决策请求键由唯一约束兜底，批准或拒绝仅允许从待复核状态按版本 CAS 进入终态。
 * @status : create
 */
public interface SettlementReversalOrderMapper {

    /**
     * 追加待复核冲正单；单号和创建请求键冲突时保持原行不变。
     * @param row 已冻结原批次资金事实和 Maker 审计的冲正单
     * @return 新插入行数，合法重放时为 0
     */
    @Insert("""
            INSERT INTO settlement_reversal_order
            (reversal_order_no, create_request_key, original_batch_no, merchant_id,
             settlement_account_id, target_currency, target_currency_exponent,
             original_batch_version, original_net_result_item_id, original_fund_ledger_id,
             net_direction, net_amount, source_fingerprint, reversal_status,
             submitted_by_account_id, submitted_by_account_name, submitted_role_snapshot,
             submit_client_ip, submit_user_agent, submit_reason, submitted_time,
             version, create_time, update_time)
            VALUES
            (#{row.reversalOrderNo}, #{row.createRequestKey}, #{row.originalBatchNo}, #{row.merchantId},
             #{row.settlementAccountId}, #{row.targetCurrency}, #{row.targetCurrencyExponent},
             #{row.originalBatchVersion}, #{row.originalNetResultItemId}, #{row.originalFundLedgerId},
             #{row.netDirection}, #{row.netAmount}, #{row.sourceFingerprint}, #{row.reversalStatus},
             #{row.submittedByAccountId}, #{row.submittedByAccountName}, #{row.submittedRoleSnapshot},
             #{row.submitClientIp}, #{row.submitUserAgent}, #{row.submitReason}, #{row.submittedTime},
             #{row.version}, #{row.createTime}, #{row.updateTime})
            ON DUPLICATE KEY UPDATE id = id
            """)
    int insertIdempotent(@Param("row") SettlementReversalOrderDO row);

    /**
     * 按创建请求幂等键锁读冲正单。
     * @param requestKey 创建请求幂等键
     * @return 匹配冲正单，不存在时为空
     */
    @Select("SELECT * FROM settlement_reversal_order WHERE create_request_key = #{requestKey} LIMIT 1 FOR UPDATE")
    SettlementReversalOrderDO selectByCreateRequestKeyForUpdate(@Param("requestKey") String requestKey);

    /**
     * 按决策请求幂等键锁读终态冲正单。
     * @param requestKey 决策请求幂等键
     * @return 匹配冲正单，不存在时为空
     */
    @Select("SELECT * FROM settlement_reversal_order WHERE decision_request_key = #{requestKey} LIMIT 1 FOR UPDATE")
    SettlementReversalOrderDO selectByDecisionRequestKeyForUpdate(@Param("requestKey") String requestKey);

    /**
     * 按冲正单号锁读当前状态和版本。
     * @param orderNo 冲正申请单号
     * @return 匹配冲正单，不存在时为空
     */
    @Select("SELECT * FROM settlement_reversal_order WHERE reversal_order_no = #{orderNo} LIMIT 1 FOR UPDATE")
    SettlementReversalOrderDO selectByReversalOrderNoForUpdate(@Param("orderNo") String orderNo);

    /**
     * 锁读原批次最近一笔待复核或已批准冲正，防止并发重复冲正。
     * @param originalBatchNo 原正式结算批次号
     * @return 有效冲正单，不存在时为空
     */
    @Select("""
            SELECT * FROM settlement_reversal_order
            WHERE original_batch_no = #{originalBatchNo}
              AND reversal_status IN ('PENDING_APPROVAL', 'APPROVED')
            ORDER BY id DESC LIMIT 1 FOR UPDATE
            """)
    SettlementReversalOrderDO selectActiveByOriginalBatchForUpdate(
            @Param("originalBatchNo") String originalBatchNo);

    /**
     * 从待复核状态按版本 CAS 批准并绑定独立反向批次。
     * @param row 已填充 Checker 审计和反向批次号的冲正单
     * @param expectedVersion 决策前版本
     * @return 成功更新行数，必须为 1
     */
    @Update("""
            UPDATE settlement_reversal_order
            SET reversal_status = 'APPROVED', reversal_batch_no = #{row.reversalBatchNo},
                decided_by_account_id = #{row.decidedByAccountId},
                decided_by_account_name = #{row.decidedByAccountName},
                decided_role_snapshot = #{row.decidedRoleSnapshot},
                decision_client_ip = #{row.decisionClientIp},
                decision_user_agent = #{row.decisionUserAgent},
                decision_action = 'APPROVE', decision_request_key = #{row.decisionRequestKey},
                decision_comment = #{row.decisionComment}, decision_time = #{row.decisionTime},
                version = version + 1, update_time = #{row.decisionTime}
            WHERE reversal_order_no = #{row.reversalOrderNo}
              AND reversal_status = 'PENDING_APPROVAL' AND version = #{expectedVersion}
            """)
    int approve(@Param("row") SettlementReversalOrderDO row,
                @Param("expectedVersion") long expectedVersion);

    /**
     * 从待复核状态按版本 CAS 拒绝并冻结 Checker 审计。
     * @param row 已填充 Checker 审计的冲正单
     * @param expectedVersion 决策前版本
     * @return 成功更新行数，必须为 1
     */
    @Update("""
            UPDATE settlement_reversal_order
            SET reversal_status = 'REJECTED',
                decided_by_account_id = #{row.decidedByAccountId},
                decided_by_account_name = #{row.decidedByAccountName},
                decided_role_snapshot = #{row.decidedRoleSnapshot},
                decision_client_ip = #{row.decisionClientIp},
                decision_user_agent = #{row.decisionUserAgent},
                decision_action = 'REJECT', decision_request_key = #{row.decisionRequestKey},
                decision_comment = #{row.decisionComment}, decision_time = #{row.decisionTime},
                version = version + 1, update_time = #{row.decisionTime}
            WHERE reversal_order_no = #{row.reversalOrderNo}
              AND reversal_status = 'PENDING_APPROVAL' AND version = #{expectedVersion}
            """)
    int reject(@Param("row") SettlementReversalOrderDO row,
               @Param("expectedVersion") long expectedVersion);
}
