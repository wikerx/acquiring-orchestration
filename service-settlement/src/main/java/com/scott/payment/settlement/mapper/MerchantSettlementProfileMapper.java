package com.scott.payment.settlement.mapper;

import com.scott.payment.settlement.entity.MerchantSettlementProfileDO;
import com.scott.payment.settlement.entity.MerchantSettlementAccountDO;
import com.scott.payment.settlement.entity.MerchantSettlementProfileCommandAuditDO;
import com.scott.payment.settlement.entity.SettlementBatchGroupDO;
import com.scott.payment.settlement.entity.SettlementCandidateDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 商户结算档案 Mapper；命令写入只维护档案版本和不可变审计，候选与批次仍引用冻结档案 ID。
 */
public interface MerchantSettlementProfileMapper {

    /**
     * 按稳定档案号锁定结算档案，版本切换必须串行检查当前活动状态。
     */
    @Select("SELECT * FROM merchant_settlement_profile WHERE settlement_profile_no = #{profileNo} LIMIT 1 FOR UPDATE")
    MerchantSettlementProfileDO selectByProfileNoForUpdate(@Param("profileNo") String profileNo);

    /**
     * 按稳定档案号读取命令结果，用于幂等重放。
     */
    @Select("SELECT * FROM merchant_settlement_profile WHERE settlement_profile_no = #{profileNo} LIMIT 1")
    MerchantSettlementProfileDO selectByProfileNo(@Param("profileNo") String profileNo);

    /**
     * 锁定商户当前唯一活动档案，避免并发创建两个活动版本。
     */
    @Select("""
            SELECT *
            FROM merchant_settlement_profile
            WHERE merchant_id = #{merchantId}
              AND profile_status = 'ACTIVE'
              AND active_slot = 1
            LIMIT 1
            FOR UPDATE
            """)
    MerchantSettlementProfileDO selectActiveByMerchantForUpdate(@Param("merchantId") String merchantId);

    /**
     * 读取商户当前活动档案，供最终开通 readiness 查询使用。
     */
    @Select("""
            SELECT *
            FROM merchant_settlement_profile
            WHERE merchant_id = #{merchantId}
              AND profile_status = 'ACTIVE'
              AND active_slot = 1
            LIMIT 1
            """)
    MerchantSettlementProfileDO selectActiveByMerchant(@Param("merchantId") String merchantId);

    /**
     * 锁定档案目标资金账户；账户币种和状态由领域服务继续校验。
     */
    @Select("""
            SELECT id, account_no, merchant_id, settlement_currency, account_status
            FROM merchant_fund_account
            WHERE id = #{accountId}
              AND deleted = 0
            LIMIT 1
            FOR UPDATE
            """)
    MerchantSettlementAccountDO selectAccountForUpdate(@Param("accountId") Long accountId);

    /**
     * 读取活动档案关联账户，readiness 查询不获取写锁。
     */
    @Select("""
            SELECT id, account_no, merchant_id, settlement_currency, account_status
            FROM merchant_fund_account
            WHERE id = #{accountId}
              AND deleted = 0
            LIMIT 1
            """)
    MerchantSettlementAccountDO selectAccount(@Param("accountId") Long accountId);

    /**
     * 从系统 ISO 字典解析启用币种的权威 exponent，禁止请求方自行指定。
     */
    @Select("""
            SELECT fraction_digits
            FROM base_iso_currency
            WHERE alpha3_code = #{currency}
              AND status = 1
              AND deleted = 0
              AND fraction_digits BETWEEN 0 AND 8
            LIMIT 1
            """)
    Integer selectCurrencyExponent(@Param("currency") String currency);

    /**
     * 插入新的活动档案版本；商户活动位唯一约束是最终并发保护。
     */
    @Insert("""
            INSERT INTO merchant_settlement_profile
            (settlement_profile_no, merchant_id, settlement_account_id, target_currency,
             target_currency_exponent, business_time_zone, daily_cutoff_time, processing_mode,
             profile_status, active_slot, effective_date, expire_date, version,
             create_time, update_time)
            VALUES
            (#{row.settlementProfileNo}, #{row.merchantId}, #{row.settlementAccountId},
             #{row.targetCurrency}, #{row.targetCurrencyExponent}, #{row.businessTimeZone},
             #{row.dailyCutoffTime}, #{row.processingMode}, #{row.profileStatus}, #{row.activeSlot},
             #{row.effectiveDate}, #{row.expireDate}, #{row.version}, #{row.createTime}, #{row.updateTime})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "row.id")
    int insertProfile(@Param("row") MerchantSettlementProfileDO row);

    /**
     * 以档案号、活动状态和期望版本退役旧版本，历史候选仍可继续引用该行。
     */
    @Update("""
            UPDATE merchant_settlement_profile
            SET profile_status = 'RETIRED',
                active_slot = NULL,
                expire_date = #{expireDate},
                version = version + 1,
                update_time = #{now}
            WHERE settlement_profile_no = #{profileNo}
              AND profile_status = 'ACTIVE'
              AND active_slot = 1
              AND version = #{expectedVersion}
            """)
    int retireActiveProfile(@Param("profileNo") String profileNo,
                            @Param("expectedVersion") Long expectedVersion,
                            @Param("expireDate") java.time.LocalDate expireDate,
                            @Param("now") java.time.LocalDateTime now);

    /**
     * 先占用全局请求键；并发重复请求会等待首个事务后返回零行。
     */
    @Insert("""
            INSERT IGNORE INTO merchant_settlement_profile_command_audit
            (request_key, request_fingerprint, command_action, merchant_id, source_profile_no,
             result_profile_no, expected_version, operator_account_id, operator_account_name,
             operator_role_snapshot, client_ip, user_agent, reason, operation_time, create_time)
            VALUES
            (#{row.requestKey}, #{row.requestFingerprint}, #{row.commandAction}, #{row.merchantId},
             #{row.sourceProfileNo}, #{row.resultProfileNo}, #{row.expectedVersion},
             #{row.operatorAccountId}, #{row.operatorAccountName}, #{row.operatorRoleSnapshot},
             #{row.clientIp}, #{row.userAgent}, #{row.reason}, #{row.operationTime}, #{row.createTime})
            """)
    int insertCommandAuditIfAbsent(@Param("row") MerchantSettlementProfileCommandAuditDO row);

    /**
     * 锁定并读取既有请求结果，确保同键重放校验相同指纹后返回同一档案。
     */
    @Select("""
            SELECT *
            FROM merchant_settlement_profile_command_audit
            WHERE request_key = #{requestKey}
            LIMIT 1
            FOR UPDATE
            """)
    MerchantSettlementProfileCommandAuditDO selectCommandAuditForUpdate(
            @Param("requestKey") String requestKey);

    /**
     * 锁定人工预审引用的冻结档案，并同时验证目标账户仍为 NORMAL。
     */
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

    /**
     * 读取一个自动预审单可锁定的稳定候选页；最终独占仍由预审事务中的版本 CAS 完成。
     */
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
