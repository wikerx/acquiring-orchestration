package com.scott.payment.settlement.mapper;

import com.scott.payment.settlement.entity.SettlementBatchGroupDO;
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

    /**
     * 批量读取具有真实 READY 候选且账户正常的结算维度；冻结后的 RETIRED 档案仍可完成存量候选。
     */
    @Select("""
            SELECT CASE WHEN candidate.source_type = 'RESERVE_RELEASE'
                        THEN 'RESERVE_RELEASE' ELSE 'REGULAR' END AS batch_type,
                   MIN(candidate.id) AS anchor_candidate_id,
                   profile.id AS settlement_profile_id,
                   profile.merchant_id,
                   profile.settlement_account_id,
                   profile.target_currency,
                   profile.target_currency_exponent,
                   profile.business_time_zone,
                   profile.daily_cutoff_time,
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
              AND candidate.source_type IN ('CLEARING_REVISION', 'RESERVE_RELEASE', 'ADJUSTMENT')
              AND candidate.target_currency = profile.target_currency
              AND candidate.target_currency_exponent = profile.target_currency_exponent
            GROUP BY CASE WHEN candidate.source_type = 'RESERVE_RELEASE'
                          THEN 'RESERVE_RELEASE' ELSE 'REGULAR' END,
                     profile.id, profile.merchant_id, profile.settlement_account_id,
                     profile.target_currency, profile.target_currency_exponent,
                     profile.business_time_zone, profile.daily_cutoff_time
            ORDER BY earliest_eligible_date ASC, profile.id ASC, batch_type ASC
            LIMIT #{limit}
            """)
    List<SettlementBatchGroupDO> selectReadyBatchGroups(@Param("limit") int limit);
}
