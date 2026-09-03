package com.scott.payment.settlement.mapper;

import com.scott.payment.settlement.entity.SettlementCandidateDO;
import com.scott.payment.settlement.entity.SettlementCandidateActivationDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementCandidateMapper
 * @date : 2026-08-26 20:00
 * @email : scott_x@163.com
 * @description : 结算候选 Mapper；锁定清分交接行、核验依赖并以 READY、真实模式、配置和版本条件独占认领。
 * @status : create
 */
public interface SettlementCandidateMapper {

    /**
     * 锁定具有唯一活动档案和正常同币种资金账户的影子候选；异常、歧义或不完整配置不会进入结果集。
     */
    @Select("""
            SELECT candidate.id AS candidate_id,
                   candidate.version AS candidate_version,
                   candidate.merchant_id,
                   candidate.target_currency AS candidate_target_currency,
                   candidate.target_currency_exponent AS candidate_target_currency_exponent,
                   candidate.settlement_eligible_date,
                   profile.id AS settlement_profile_id,
                   profile.settlement_account_id,
                   profile.target_currency AS profile_target_currency,
                   profile.target_currency_exponent AS profile_target_currency_exponent,
                   profile.business_time_zone,
                   profile.daily_cutoff_time
            FROM settlement_candidate candidate
            INNER JOIN merchant_settlement_profile profile
                    ON profile.merchant_id = candidate.merchant_id
                   AND profile.profile_status = 'ACTIVE'
                   AND profile.active_slot = 1
                   AND profile.effective_date <= candidate.settlement_eligible_date
                   AND (profile.expire_date IS NULL
                        OR profile.expire_date >= candidate.settlement_eligible_date)
            INNER JOIN merchant_fund_account account
                    ON account.id = profile.settlement_account_id
                   AND account.merchant_id = profile.merchant_id
                   AND account.settlement_currency = profile.target_currency
                   AND account.account_status = 'NORMAL'
                   AND account.deleted = 0
            WHERE candidate.shadow_mode = 1
              AND candidate.candidate_status = 'READY'
              AND candidate.settlement_batch_no IS NULL
              AND candidate.source_type IN ('CLEARING_REVISION', 'RESERVE_RELEASE', 'ADJUSTMENT')
              AND candidate.settlement_profile_id IS NULL
              AND candidate.target_currency = profile.target_currency
              AND candidate.target_currency_exponent = profile.target_currency_exponent
            ORDER BY candidate.id ASC
            LIMIT #{limit}
            FOR UPDATE SKIP LOCKED
            """)
    List<SettlementCandidateActivationDO> selectActivatableForUpdate(@Param("limit") int limit);

    /**
     * 对已锁定投影执行一次批量 CAS；每个 OR 分支都携带候选主键、版本、商户和全部目标维度。
     */
    @Update("""
            <script>
            UPDATE settlement_candidate
            SET settlement_profile_id = CASE id
                <foreach collection="rows" item="row">
                    WHEN #{row.candidateId} THEN #{row.settlementProfileId}
                </foreach>
                END,
                shadow_mode = 0,
                version = version + 1,
                update_time = #{activatedTime}
            WHERE shadow_mode = 1
              AND candidate_status = 'READY'
              AND settlement_batch_no IS NULL
              AND settlement_profile_id IS NULL
              AND (
                <foreach collection="rows" item="row" separator=" OR ">
                    (id = #{row.candidateId}
                     AND version = #{row.candidateVersion}
                     AND merchant_id = #{row.merchantId}
                     AND target_currency = #{row.profileTargetCurrency}
                     AND target_currency_exponent = #{row.profileTargetCurrencyExponent})
                </foreach>
              )
            </script>
            """)
    int activateBatch(@Param("rows") List<SettlementCandidateActivationDO> rows,
                      @Param("activatedTime") LocalDateTime activatedTime);

    /**
     * 按批次冻结维度、日切窗口和依赖拓扑锁定下一页候选；每个候选仍携带自己的 version 进入批量 CAS。
     */
    @Select("""
            SELECT candidate.*
            FROM settlement_candidate candidate
            INNER JOIN settlement_batch batch
                    ON batch.settlement_batch_no = #{settlementBatchNo}
                   AND batch.merchant_id = candidate.merchant_id
                   AND batch.settlement_profile_id = candidate.settlement_profile_id
                   AND batch.target_currency = candidate.target_currency
                   AND batch.target_currency_exponent = candidate.target_currency_exponent
            WHERE candidate.candidate_status = 'READY'
              AND candidate.shadow_mode = 0
              AND candidate.settlement_batch_no IS NULL
              AND candidate.review_order_no IS NULL
              AND ((batch.batch_type = 'REGULAR'
                    AND candidate.source_type = 'CLEARING_REVISION')
                   OR (batch.batch_type = 'RESERVE_RELEASE'
                       AND candidate.source_type = 'RESERVE_RELEASE')
                   OR (batch.batch_type = 'ADJUSTMENT'
                       AND candidate.source_type = 'ADJUSTMENT'))
              AND candidate.settlement_eligible_date <= batch.business_date
              AND candidate.create_time < batch.cutoff_end_time
              AND NOT EXISTS (
                    SELECT 1
                    FROM settlement_candidate_dependency dependency
                    INNER JOIN settlement_candidate required_candidate
                            ON required_candidate.id = dependency.depends_on_candidate_id
                    WHERE dependency.candidate_id = candidate.id
                      AND NOT (
                            required_candidate.candidate_status = 'POSTED'
                            OR (required_candidate.candidate_status = 'CLAIMED'
                                AND required_candidate.settlement_batch_no = #{settlementBatchNo})
                      )
              )
            ORDER BY candidate.id ASC
            LIMIT #{limit}
            FOR UPDATE SKIP LOCKED
            """)
    List<SettlementCandidateDO> selectClaimableByBatchForUpdate(
            @Param("settlementBatchNo") String settlementBatchNo,
            @Param("limit") int limit);

    /**
     * 在自动正式建批事务内锁定第一条真实可认领候选；筛选条件必须与批次认领第一页保持一致。
     */
    @Select("""
            SELECT candidate.*
            FROM settlement_candidate candidate
            INNER JOIN merchant_settlement_profile profile
                    ON profile.id = candidate.settlement_profile_id
                   AND profile.merchant_id = candidate.merchant_id
                   AND profile.settlement_account_id = #{settlementAccountId}
                   AND profile.target_currency = candidate.target_currency
                   AND profile.target_currency_exponent = candidate.target_currency_exponent
                   AND profile.profile_status IN ('ACTIVE', 'RETIRED')
                   AND profile.processing_mode = 'AUTO_POST'
            INNER JOIN merchant_fund_account account
                    ON account.id = profile.settlement_account_id
                   AND account.merchant_id = profile.merchant_id
                   AND account.settlement_currency = profile.target_currency
                   AND account.account_status = 'NORMAL'
                   AND account.deleted = 0
            WHERE candidate.merchant_id = #{merchantId}
              AND candidate.settlement_profile_id = #{settlementProfileId}
              AND candidate.target_currency = #{targetCurrency}
              AND candidate.target_currency_exponent = #{targetCurrencyExponent}
              AND candidate.candidate_status = 'READY'
              AND candidate.shadow_mode = 0
              AND candidate.settlement_batch_no IS NULL
              AND candidate.review_order_no IS NULL
              AND ((#{batchType} = 'REGULAR'
                    AND candidate.source_type = 'CLEARING_REVISION')
                   OR (#{batchType} = 'RESERVE_RELEASE'
                       AND candidate.source_type = 'RESERVE_RELEASE')
                   OR (#{batchType} = 'ADJUSTMENT'
                       AND candidate.source_type = 'ADJUSTMENT'))
              AND candidate.settlement_eligible_date <= #{businessDate}
              AND candidate.create_time < #{cutoffEndTime}
              AND NOT EXISTS (
                    SELECT 1
                    FROM settlement_candidate_dependency dependency
                    INNER JOIN settlement_candidate required_candidate
                            ON required_candidate.id = dependency.depends_on_candidate_id
                    WHERE dependency.candidate_id = candidate.id
                      AND required_candidate.candidate_status != 'POSTED'
              )
            ORDER BY candidate.id ASC
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """)
    SettlementCandidateDO selectAutomaticPostAnchorForUpdate(
            @Param("merchantId") String merchantId,
            @Param("settlementProfileId") Long settlementProfileId,
            @Param("settlementAccountId") Long settlementAccountId,
            @Param("targetCurrency") String targetCurrency,
            @Param("targetCurrencyExponent") Integer targetCurrencyExponent,
            @Param("batchType") String batchType,
            @Param("businessDate") java.time.LocalDate businessDate,
            @Param("cutoffEndTime") LocalDateTime cutoffEndTime);

    /** 按主键升序锁定人工选择的候选，所有校验和 CAS 均在同一主库事务内完成。 */
    @Select("""
            <script>
            SELECT *
            FROM settlement_candidate
            WHERE id IN
            <foreach collection="candidateIds" item="candidateId" open="(" separator="," close=")">
                #{candidateId}
            </foreach>
            ORDER BY id ASC
            FOR UPDATE
            </script>
            """)
    List<SettlementCandidateDO> selectByIdsForUpdate(@Param("candidateIds") List<Long> candidateIds);

    /** 将页面携带版本的同维度 READY 候选原子锁入预审单。 */
    @Update("""
            <script>
            UPDATE settlement_candidate
            SET candidate_status = 'REVIEW_LOCKED',
                review_order_no = #{reviewOrderNo},
                review_locked_time = #{lockedTime},
                version = version + 1,
                update_time = #{lockedTime}
            WHERE candidate_status = 'READY'
              AND settlement_batch_no IS NULL
              AND review_order_no IS NULL
              AND shadow_mode = 0
              AND settlement_profile_id = #{settlementProfileId}
              AND (
                <foreach collection="rows" item="row" separator=" OR ">
                    (id = #{row.id}
                     AND version = #{row.version}
                     AND merchant_id = #{row.merchantId}
                     AND target_currency = #{row.targetCurrency}
                     AND target_currency_exponent = #{row.targetCurrencyExponent})
                </foreach>
              )
            </script>
            """)
    int lockForReview(@Param("rows") List<SettlementCandidateDO> rows,
                      @Param("reviewOrderNo") String reviewOrderNo,
                      @Param("settlementProfileId") Long settlementProfileId,
                      @Param("lockedTime") LocalDateTime lockedTime);

    /** 审批通过时将仍被该预审单独占的候选消费为正式批次认领。 */
    @Update("""
            <script>
            UPDATE settlement_candidate
            SET candidate_status = 'CLAIMED',
                settlement_batch_no = #{settlementBatchNo},
                claimed_time = #{claimedTime},
                version = version + 1,
                update_time = #{claimedTime}
            WHERE candidate_status = 'REVIEW_LOCKED'
              AND review_order_no = #{reviewOrderNo}
              AND settlement_batch_no IS NULL
              AND shadow_mode = 0
              AND (
                <foreach collection="rows" item="row" separator=" OR ">
                    (id = #{row.id}
                     AND version = #{row.version})
                </foreach>
              )
            </script>
            """)
    int consumeReviewLock(@Param("rows") List<SettlementCandidateDO> rows,
                          @Param("reviewOrderNo") String reviewOrderNo,
                          @Param("settlementBatchNo") String settlementBatchNo,
                          @Param("claimedTime") LocalDateTime claimedTime);

    /** 拒绝、取消或过期只释放仍属于该预审单且版本未变化的候选。 */
    @Update("""
            <script>
            UPDATE settlement_candidate
            SET candidate_status = 'READY',
                review_order_no = NULL,
                review_locked_time = NULL,
                version = version + 1,
                update_time = #{releasedTime}
            WHERE candidate_status = 'REVIEW_LOCKED'
              AND review_order_no = #{reviewOrderNo}
              AND settlement_batch_no IS NULL
              AND shadow_mode = 0
              AND (
                <foreach collection="rows" item="row" separator=" OR ">
                    (id = #{row.id}
                     AND version = #{row.version})
                </foreach>
              )
            </script>
            """)
    int releaseReviewLock(@Param("rows") List<SettlementCandidateDO> rows,
                          @Param("reviewOrderNo") String reviewOrderNo,
                          @Param("releasedTime") LocalDateTime releasedTime);

    /** 预审候选的依赖必须已入账，或与依赖方一起进入同一预审单。 */
    @Select("""
            <script>
            SELECT COUNT(1)
            FROM settlement_candidate_dependency dependency
            INNER JOIN settlement_candidate required_candidate
                    ON required_candidate.id = dependency.depends_on_candidate_id
            WHERE dependency.candidate_id IN
            <foreach collection="candidateIds" item="candidateId" open="(" separator="," close=")">
                #{candidateId}
            </foreach>
              AND required_candidate.candidate_status != 'POSTED'
              AND required_candidate.id NOT IN
            <foreach collection="candidateIds" item="candidateId" open="(" separator="," close=")">
                #{candidateId}
            </foreach>
            </script>
            """)
    long countUnresolvedReviewDependencies(@Param("candidateIds") List<Long> candidateIds);

    /** 批量认领已锁定候选；每个 OR 分支都校验主键、version、配置和真实 READY 状态。 */
    @Update("""
            <script>
            UPDATE settlement_candidate
            SET candidate_status = 'CLAIMED',
                settlement_batch_no = #{settlementBatchNo},
                claimed_time = #{claimedTime},
                version = version + 1,
                update_time = #{claimedTime}
            WHERE candidate_status = 'READY'
              AND settlement_batch_no IS NULL
              AND review_order_no IS NULL
              AND shadow_mode = 0
              AND settlement_profile_id = #{settlementProfileId}
              AND (
                <foreach collection="rows" item="row" separator=" OR ">
                    (id = #{row.id}
                     AND version = #{row.version}
                     AND merchant_id = #{row.merchantId}
                     AND target_currency = #{row.targetCurrency}
                     AND target_currency_exponent = #{row.targetCurrencyExponent})
                </foreach>
              )
            </script>
            """)
    int claimBatch(@Param("rows") List<SettlementCandidateDO> rows,
                   @Param("settlementBatchNo") String settlementBatchNo,
                   @Param("settlementProfileId") Long settlementProfileId,
                   @Param("claimedTime") LocalDateTime claimedTime);

    /** 按候选主键锁行，锁顺序固定在批次锁之后。 */
    @Select("""
            SELECT *
            FROM settlement_candidate
            WHERE id = #{candidateId}
            LIMIT 1
            FOR UPDATE
            """)
    SettlementCandidateDO selectByIdForUpdate(@Param("candidateId") Long candidateId);

    /**
     * 统计未满足依赖；依赖候选必须已经 POSTED，或已在当前批次先完成认领。
     */
    @Select("""
            SELECT COUNT(1)
            FROM settlement_candidate_dependency dependency
            INNER JOIN settlement_candidate required_candidate
                    ON required_candidate.id = dependency.depends_on_candidate_id
            WHERE dependency.candidate_id = #{candidateId}
              AND NOT (
                    required_candidate.candidate_status = 'POSTED'
                    OR (required_candidate.candidate_status = 'CLAIMED'
                        AND required_candidate.settlement_batch_no = #{settlementBatchNo})
                  )
            """)
    long countUnresolvedDependencies(@Param("candidateId") Long candidateId,
                                     @Param("settlementBatchNo") String settlementBatchNo);

    /**
     * 使用 READY、未归属、真实候选、冻结配置和 version 全条件 CAS 独占认领。
     */
    @Update("""
            UPDATE settlement_candidate
            SET candidate_status = 'CLAIMED',
                settlement_batch_no = #{settlementBatchNo},
                claimed_time = #{claimedTime},
                version = version + 1,
                update_time = CURRENT_TIMESTAMP(3)
            WHERE id = #{candidateId}
              AND candidate_status = 'READY'
              AND settlement_batch_no IS NULL
              AND review_order_no IS NULL
              AND shadow_mode = 0
              AND settlement_profile_id = #{settlementProfileId}
              AND version = #{expectedVersion}
            """)
    int claim(@Param("candidateId") Long candidateId,
              @Param("settlementBatchNo") String settlementBatchNo,
              @Param("settlementProfileId") Long settlementProfileId,
              @Param("expectedVersion") Long expectedVersion,
              @Param("claimedTime") LocalDateTime claimedTime);

    /** 批次进入人工复核时只迁移该批仍为 CLAIMED 的候选，保留批次归属和审计链。 */
    @Update("""
            UPDATE settlement_candidate
            SET candidate_status = 'MANUAL_REVIEW',
                version = version + 1,
                update_time = #{now}
            WHERE settlement_batch_no = #{settlementBatchNo}
              AND candidate_status = 'CLAIMED'
              AND shadow_mode = 0
            """)
    int markBatchManualReview(@Param("settlementBatchNo") String settlementBatchNo,
                              @Param("now") LocalDateTime now);

    /** 资金提交后将本批全部 CLAIMED 候选原子迁移到 POSTED。 */
    @Update("""
            UPDATE settlement_candidate
            SET candidate_status = 'POSTED',
                posted_time = #{now},
                version = version + 1,
                update_time = #{now}
            WHERE settlement_batch_no = #{settlementBatchNo}
              AND candidate_status = 'CLAIMED'
              AND shadow_mode = 0
            """)
    int markBatchPosted(@Param("settlementBatchNo") String settlementBatchNo,
                        @Param("now") LocalDateTime now);

    /** 入账前取消后将本批 CLAIMED 候选释放为 READY，保留清分修订不变。 */
    @Update("""
            UPDATE settlement_candidate
            SET candidate_status = 'READY',
                settlement_batch_no = NULL,
                claimed_time = NULL,
                version = version + 1,
                update_time = #{now}
            WHERE settlement_batch_no = #{settlementBatchNo}
              AND candidate_status = 'CLAIMED'
              AND shadow_mode = 0
            """)
    int releaseCancelledBatch(@Param("settlementBatchNo") String settlementBatchNo,
                              @Param("now") LocalDateTime now);
}
