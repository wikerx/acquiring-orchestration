package com.scott.payment.settlement.mapper;

import com.scott.payment.settlement.entity.MerchantReserveItemDO;
import com.scott.payment.settlement.entity.SettlementReserveHoldMigrationDO;
import com.scott.payment.settlement.entity.SettlementReserveHoldSnapshotDO;
import com.scott.payment.settlement.entity.SettlementReserveHoldStateDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 保证金来源修复证明的批量只读查询；分片事实必须携带原支付号和精确时间。
 */
public interface SettlementReserveSourceMapper {

    /** 读取所有命中审计，包括不完整证明，交由领域服务拒绝歧义和失效状态。 */
    @Select("""
            <script>
            SELECT a.repair_no, a.old_hold_detail_no, a.new_hold_detail_no, a.reserve_state_id,
                   a.old_state_version, a.new_state_version,
                   p.repair_status, p.merchant_id, p.transaction_id, p.transaction_date_time,
                   p.finance_state_id, p.expected_clearing_revision AS old_revision,
                   p.new_clearing_revision AS new_revision, p.reserve_fact_count,
                   p.reserve_state_count, p.dependency_count,
                   p.candidate_id AS old_candidate_id, p.new_candidate_id,
                   da.dependent_candidate_id, da.old_depends_on_candidate_id,
                   da.new_depends_on_candidate_id, da.dependency_type,
                   d.candidate_id AS current_dependent_candidate_id,
                   d.depends_on_candidate_id AS current_depends_on_candidate_id,
                   d.dependency_type AS current_dependency_type,
                   nc.candidate_status AS new_candidate_status,
                   nc.source_type AS new_candidate_source_type,
                   nc.source_business_id AS new_candidate_source_business_id,
                   nc.source_transaction_id AS new_candidate_transaction_id,
                   nc.source_transaction_date_time AS new_candidate_transaction_date_time,
                   nc.source_revision AS new_candidate_revision,
                   nc.merchant_id AS new_candidate_merchant_id,
                   nc.settlement_profile_id AS new_candidate_profile_id,
                   nc.target_currency AS new_candidate_target_currency,
                   nc.target_currency_exponent AS new_candidate_target_currency_exponent
            FROM clearing_label_principal_repair_reserve_state_audit a
            LEFT JOIN clearing_label_principal_repair p ON p.repair_no = a.repair_no
            LEFT JOIN clearing_label_principal_repair_dependency_audit da ON da.repair_no = a.repair_no
            LEFT JOIN settlement_candidate_dependency d ON d.id = da.dependency_id
            LEFT JOIN settlement_candidate nc ON nc.id = p.new_candidate_id
            WHERE a.old_hold_detail_no IN
              <foreach collection="holdDetailNos" item="no" open="(" close=")" separator=",">
                #{no}
              </foreach>
            ORDER BY a.id ASC, da.id ASC
            </script>
            """)
    List<SettlementReserveHoldMigrationDO> selectMigrations(
            @Param("holdDetailNos") List<String> holdDetailNos);

    /** 旧、新 HOLD 必须在同一原支付分片读取，不能按 ACTIVE 状态猜测替代来源。 */
    @Select("""
            <script>
            SELECT * FROM transaction_reserve_clearing_detail
            WHERE reserve_action_type = 'HOLD' AND (
              <foreach collection="migrations" item="row" separator=" OR ">
                (transaction_id = #{row.transactionId}
                 AND transaction_date_time = #{row.transactionDateTime}
                 AND reserve_clearing_detail_no IN (#{row.oldHoldDetailNo}, #{row.newHoldDetailNo}))
              </foreach>
            )
            ORDER BY transaction_date_time ASC, id ASC
            </script>
            """)
    List<SettlementReserveHoldSnapshotDO> selectHoldSnapshots(
            @Param("migrations") List<SettlementReserveHoldMigrationDO> migrations);

    /** 生命周期状态只读；解析不得重新生成释放或改变清分累计额。 */
    @Select("""
            <script>
            SELECT * FROM transaction_reserve_clearing_state WHERE
              <foreach collection="migrations" item="row" separator=" OR ">
                (reserve_state_id = #{row.reserveStateId}
                 AND original_transaction_id = #{row.transactionId}
                 AND transaction_date_time = #{row.transactionDateTime})
              </foreach>
            ORDER BY transaction_date_time ASC, id ASC
            </script>
            """)
    List<SettlementReserveHoldStateDO> selectStates(
            @Param("migrations") List<SettlementReserveHoldMigrationDO> migrations);

    /** 解析阶段批量读取责任；实际入账仍使用原 Mapper 的行锁及版本 CAS。 */
    @Select("""
            <script>
            SELECT * FROM merchant_reserve_item
            WHERE merchant_id = #{merchantId} AND source_business_no IN
              <foreach collection="holdDetailNos" item="no" open="(" close=")" separator=",">
                #{no}
              </foreach>
            ORDER BY id ASC
            </script>
            """)
    List<MerchantReserveItemDO> selectFundedItems(@Param("merchantId") String merchantId,
                                                @Param("holdDetailNos") List<String> holdDetailNos);
}
