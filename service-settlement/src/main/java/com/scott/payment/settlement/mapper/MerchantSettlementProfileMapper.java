package com.scott.payment.settlement.mapper;

import com.scott.payment.settlement.entity.MerchantSettlementProfileDO;
import com.scott.payment.settlement.entity.SettlementBatchGroupDO;
import com.scott.payment.settlement.entity.SettlementCandidateDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantSettlementProfileMapper
 * @date : 2026-08-26 22:10
 * @email : scott_x@163.com
 * @description : 商户结算档案只读 Mapper，按候选已冻结档案聚合待建日批维度，不承担档案维护或费用配置查询。
 * @status : create
 */
public interface MerchantSettlementProfileMapper {

    /** 锁定人工预审引用的冻结档案，并同时验证目标账户仍为 NORMAL。 */
    @Select("""
            SELECT profile.*
            FROM merchant_settlement_profile profile
            INNER JOIN merchant_fund_account account
                    ON account.id = profile.settlement_account_id
                   AND account.merchant_id = profile.merchant_id
                   AND account.settlement_currency = profile.target_currency
                   AND account.account_status = 'NORMAL'
                   AND account.deleted = 0
            WHERE profile.id = #{settlementProfileId}
              AND profile.profile_status IN ('ACTIVE', 'RETIRED')
              AND profile.effective_date <= #{businessDate}
              AND (profile.expire_date IS NULL OR profile.expire_date >= #{businessDate})
            LIMIT 1
            FOR UPDATE
            """)
    MerchantSettlementProfileDO selectReviewEligibleProfileForUpdate(
            @Param("settlementProfileId") Long settlementProfileId,
            @Param("businessDate") java.time.LocalDate businessDate);

    /**
     * 粗粒度发现具有 READY 候选且账户正常的结算维度；正式建批前仍须按成熟日切窗口锁定真实锚点。
     */
    @Select("""
            SELECT CASE candidate.source_type
                        WHEN 'RESERVE_RELEASE' THEN 'RESERVE_RELEASE'
                        WHEN 'ADJUSTMENT' THEN 'ADJUSTMENT'
                        ELSE 'REGULAR' END AS batch_type,
                   profile.id AS settlement_profile_id,
                   profile.merchant_id,
                   profile.settlement_account_id,
                   profile.target_currency,
                   profile.target_currency_exponent,
                   profile.business_time_zone,
                   profile.daily_cutoff_time,
                   profile.processing_mode,
                   MIN(candidate.settlement_eligible_date) AS earliest_eligible_date
            FROM settlement_candidate candidate
            INNER JOIN merchant_settlement_profile profile
                    ON profile.id = candidate.settlement_profile_id
                   AND profile.merchant_id = candidate.merchant_id
                   AND profile.profile_status IN ('ACTIVE', 'RETIRED')
            INNER JOIN merchant_fund_account account
                    ON account.id = profile.settlement_account_id
                   AND account.merchant_id = profile.merchant_id
                   AND account.settlement_currency = profile.target_currency
                   AND account.account_status = 'NORMAL'
                   AND account.deleted = 0
            WHERE candidate.shadow_mode = 0
              AND candidate.candidate_status = 'READY'
              AND candidate.settlement_batch_no IS NULL
              AND candidate.review_order_no IS NULL
              AND candidate.source_type IN ('CLEARING_REVISION', 'RESERVE_RELEASE', 'ADJUSTMENT')
              AND candidate.target_currency = profile.target_currency
              AND candidate.target_currency_exponent = profile.target_currency_exponent
              AND profile.processing_mode IN ('AUTO_POST', 'AUTO_REVIEW')
            GROUP BY CASE candidate.source_type
                         WHEN 'RESERVE_RELEASE' THEN 'RESERVE_RELEASE'
                         WHEN 'ADJUSTMENT' THEN 'ADJUSTMENT'
                         ELSE 'REGULAR' END,
                     profile.id, profile.merchant_id, profile.settlement_account_id,
                     profile.target_currency, profile.target_currency_exponent,
                     profile.business_time_zone, profile.daily_cutoff_time, profile.processing_mode
            ORDER BY earliest_eligible_date ASC, profile.id ASC, batch_type ASC
            LIMIT #{limit}
            """)
    List<SettlementBatchGroupDO> selectReadyBatchGroups(@Param("limit") int limit);

    /** 读取一个自动预审单可锁定的稳定候选页；最终独占仍由预审事务中的版本 CAS 完成。 */
    @Select("""
            SELECT candidate.*
            FROM settlement_candidate candidate
            WHERE candidate.settlement_profile_id = #{settlementProfileId}
              AND candidate.shadow_mode = 0
              AND candidate.candidate_status = 'READY'
              AND candidate.settlement_batch_no IS NULL
              AND candidate.review_order_no IS NULL
              AND candidate.settlement_eligible_date <= #{businessDate}
              AND candidate.create_time < #{cutoffEndTime}
              AND ((#{batchType} = 'REGULAR' AND candidate.source_type = 'CLEARING_REVISION')
                   OR (#{batchType} = 'RESERVE_RELEASE' AND candidate.source_type = 'RESERVE_RELEASE')
                   OR (#{batchType} = 'ADJUSTMENT' AND candidate.source_type = 'ADJUSTMENT'))
            ORDER BY candidate.id ASC
            LIMIT #{limit}
            """)
    List<SettlementCandidateDO> selectReadyReviewCandidates(
            @Param("settlementProfileId") Long settlementProfileId,
            @Param("batchType") String batchType,
            @Param("businessDate") java.time.LocalDate businessDate,
            @Param("cutoffEndTime") java.time.LocalDateTime cutoffEndTime,
            @Param("limit") int limit);
}
