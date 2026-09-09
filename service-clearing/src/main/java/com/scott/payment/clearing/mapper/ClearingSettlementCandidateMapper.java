package com.scott.payment.clearing.mapper;

import com.scott.payment.clearing.entity.ClearingSettlementCandidateDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

import com.scott.payment.clearing.entity.ClearingTierPeriodReplayItemFactsDO;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingSettlementCandidateMapper
 * @date : 2026-08-27 19:46
 * @email : scott_x@163.com
 * @description : 清分结算候选 Mapper；只创建或替换 READY 候选，不执行结算认领和入账。
 * @status : update
 */
public interface ClearingSettlementCandidateMapper {

    /**
     * 依赖来源类型、业务身份和修订唯一键幂等写入候选，不覆盖既有候选状态。
     *
     * @param row 待写入的清分或保证金候选快照
     * @return 实际插入行数；唯一键重复时可能为 0
     */
    @Insert("""
            INSERT INTO settlement_candidate
            (candidate_no, source_type, source_business_id, source_revision,
             source_transaction_id, source_transaction_date_time, merchant_id,
             settlement_profile_id, target_currency, target_currency_exponent,
             settlement_eligible_date, candidate_status, shadow_mode, settlement_batch_no,
             version, create_time, update_time)
            VALUES
            (#{row.candidateNo}, #{row.sourceType}, #{row.sourceBusinessId}, #{row.sourceRevision},
             #{row.sourceTransactionId}, #{row.sourceTransactionDateTime}, #{row.merchantId},
             #{row.settlementProfileId}, #{row.targetCurrency}, #{row.targetCurrencyExponent},
             #{row.settlementEligibleDate}, #{row.candidateStatus}, #{row.shadowMode},
             #{row.settlementBatchNo}, #{row.version}, #{row.createTime}, #{row.updateTime})
            ON DUPLICATE KEY UPDATE id = id
            """)
    int insertIdempotent(@Param("row") ClearingSettlementCandidateDO row);

    /**
     * 按清分财务状态和修订锁定真实交易候选。
     *
     * @param financeStateId 清分财务状态业务号
     * @param revision 清分修订号
     * @return 已加行锁的候选，不存在时返回 null
     */
    @Select("""
            SELECT *
            FROM settlement_candidate
            WHERE source_type = 'CLEARING_REVISION'
              AND source_business_id = #{financeStateId}
              AND source_revision = #{revision}
            LIMIT 1
            FOR UPDATE
            """)
    ClearingSettlementCandidateDO selectForUpdate(@Param("financeStateId") String financeStateId,
                                                   @Param("revision") int revision);

    /** 按非交易清分来源类型和业务身份锁定独立财务候选。 */
    @Select("""
            SELECT *
            FROM settlement_candidate
            WHERE source_type = #{sourceType}
              AND source_business_id = #{sourceBusinessId}
              AND source_revision = #{revision}
            LIMIT 1
            FOR UPDATE
            """)
    ClearingSettlementCandidateDO selectSourceForUpdate(
            @Param("sourceType") String sourceType,
            @Param("sourceBusinessId") String sourceBusinessId,
            @Param("revision") int revision);

    /** 重算只能淘汰尚未被任何结算批次认领的 READY 候选。 */
    @Update("""
            UPDATE settlement_candidate
            SET candidate_status = 'SUPERSEDED',
                version = version + 1,
                update_time = #{now}
            WHERE source_type = 'CLEARING_REVISION'
              AND source_business_id = #{financeStateId}
              AND source_revision = #{revision}
              AND candidate_status = 'READY'
              AND settlement_batch_no IS NULL
              AND version = #{expectedVersion}
            """)
    int supersedeReady(@Param("financeStateId") String financeStateId,
                       @Param("revision") int revision,
                       @Param("expectedVersion") long expectedVersion,
                       @Param("now") LocalDateTime now);

    /** 按冻结动作身份锁定当前清分修订候选，任何缺失、非 READY 或已认领状态都由服务层拒绝。 */
    @Select("""
            <script>
            SELECT * FROM settlement_candidate
            WHERE source_type = 'CLEARING_REVISION' AND (
              <foreach collection="items" item="item" separator=" OR ">
                (source_business_id = #{item.financeStateId}
                 AND source_revision = #{item.clearingRevision})
              </foreach>
            )
            ORDER BY source_business_id ASC, source_revision ASC
            FOR UPDATE
            </script>
            """)
    List<ClearingSettlementCandidateDO> selectForTierReplay(
            @Param("items") List<ClearingTierPeriodReplayItemFactsDO> items);

    /** 将完整冻结集合从 READY 原子切换到 REPLAY_HOLD，结算认领仍只允许 READY。 */
    @Update("""
            <script>
            UPDATE settlement_candidate
            SET candidate_status = 'REPLAY_HOLD', version = version + 1, update_time = #{now}
            WHERE source_type = 'CLEARING_REVISION'
              AND candidate_status = 'READY' AND settlement_batch_no IS NULL AND (
                <foreach collection="candidates" item="candidate" separator=" OR ">
                  (source_business_id = #{candidate.sourceBusinessId}
                   AND source_revision = #{candidate.sourceRevision}
                   AND version = #{candidate.version})
                </foreach>
              )
            </script>
            """)
    int holdForTierReplay(@Param("candidates") List<ClearingSettlementCandidateDO> candidates,
                          @Param("now") LocalDateTime now);

    /** 单项重放完成后只淘汰本次预先冻结的旧候选。 */
    @Update("""
            UPDATE settlement_candidate
            SET candidate_status = 'SUPERSEDED', version = version + 1, update_time = #{now}
            WHERE source_type = 'CLEARING_REVISION'
              AND source_business_id = #{financeStateId}
              AND source_revision = #{revision}
              AND candidate_status = 'REPLAY_HOLD'
              AND settlement_batch_no IS NULL
              AND version = #{expectedVersion}
            """)
    int supersedeReplayHeld(@Param("financeStateId") String financeStateId,
                            @Param("revision") int revision,
                            @Param("expectedVersion") long expectedVersion,
                            @Param("now") LocalDateTime now);
}
