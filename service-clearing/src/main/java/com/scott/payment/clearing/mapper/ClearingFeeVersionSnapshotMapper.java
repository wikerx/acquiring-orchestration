package com.scott.payment.clearing.mapper;

import com.scott.payment.clearing.entity.ClearingFeeVersionSnapshotRowDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingFeeVersionSnapshotMapper
 * @date : 2026-08-26 09:55
 * @email : scott_x@163.com
 * @description : 清分费用版本只读 Mapper，只允许按动作已锁定的商户、方案和版本 ID 查询 ACTIVE 或 SUPERSEDED 版本。
 * @status : create
 */
public interface ClearingFeeVersionSnapshotMapper {

    /**
     * 一次聚合确切不可变版本的规则和阶梯，禁止改查商户当前版本。
     *
     * @param merchantId 动作所属商户
     * @param feePlanId 动作锁定费用方案 ID
     * @param feePlanVersionId 动作锁定不可变版本 ID
     * @return 按规则和阶梯稳定排序的扁平行
     */
    @Select("""
            SELECT fp.merchant_id AS merchant_id,
                   fp.id AS fee_plan_id,
                   fpv.id AS fee_plan_version_id,
                   fpv.version_no AS fee_plan_version_no,
                   fpv.settlement_currency AS settlement_currency,
                   fpv.reserve_rate AS reserve_rate,
                   fpv.reserve_delay_unit AS reserve_delay_unit,
                   fpv.reserve_delay_days AS reserve_delay_days,
                   fr.id AS fee_rule_id,
                   fr.fee_category AS fee_category,
                   fr.transaction_type AS transaction_type,
                   fr.payment_type AS payment_type,
                   fr.payment_method AS payment_method,
                   fr.risk_service_type AS risk_service_type,
                   fr.charge_trigger AS charge_trigger,
                   fr.fee_mode AS fee_mode,
                   fr.percentage_rate AS percentage_rate,
                   fr.fixed_amount_usd AS fixed_amount_usd,
                   fr.minimum_amount_usd AS minimum_amount_usd,
                   fr.maximum_amount_usd AS maximum_amount_usd,
                   fr.tier_metric AS tier_metric,
                   frt.id AS fee_tier_id,
                   frt.lower_bound AS tier_lower_bound,
                   frt.upper_bound AS tier_upper_bound,
                   frt.percentage_rate AS tier_percentage_rate,
                   frt.fixed_amount_usd AS tier_fixed_amount_usd,
                   frt.minimum_amount_usd AS tier_minimum_amount_usd,
                   frt.maximum_amount_usd AS tier_maximum_amount_usd
            FROM fee_plan fp
            INNER JOIN fee_plan_version fpv
                    ON fpv.plan_id = fp.id
                   AND fpv.id = #{feePlanVersionId}
                   AND fpv.version_status IN ('ACTIVE', 'SUPERSEDED')
                   AND fpv.deleted = 0
            INNER JOIN fee_rule fr
                    ON fr.plan_version_id = fpv.id
                   AND fr.deleted = 0
            LEFT JOIN fee_rule_tier frt
                   ON frt.fee_rule_id = fr.id
                  AND frt.deleted = 0
            WHERE fp.id = #{feePlanId}
              AND fp.plan_type = 'MERCHANT'
              AND fp.merchant_id = #{merchantId}
              AND fp.deleted = 0
            ORDER BY fr.sort_no ASC, fr.id ASC, frt.sort_no ASC, frt.id ASC
            """)
    List<ClearingFeeVersionSnapshotRowDO> selectVersionRows(
            @Param("merchantId") String merchantId,
            @Param("feePlanId") Long feePlanId,
            @Param("feePlanVersionId") Long feePlanVersionId);
}
