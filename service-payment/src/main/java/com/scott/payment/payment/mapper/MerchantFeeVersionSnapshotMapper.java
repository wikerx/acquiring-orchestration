package com.scott.payment.payment.mapper;

import com.scott.payment.payment.entity.MerchantFeeVersionPointerDO;
import com.scott.payment.payment.entity.MerchantFeeVersionSnapshotRowDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantFeeVersionSnapshotMapper
 * @date : 2026-08-25 22:40
 * @email : scott_x@163.com
 * @description : Payment 对现有商户费用表的只读 Mapper，按商户读取 ACTIVE 指针并按明确版本一次聚合规则和阶梯，禁止执行费用写入。
 * @status : create
 */
public interface MerchantFeeVersionSnapshotMapper {

    /**
     * 查询商户当前已生效费用版本指针；必须从主库执行，避免审批后读取旧版本。
     *
     * @param merchantId 平台商户号
     * @return 当前 MERCHANT 方案版本身份；不存在时返回 null
     */
    @Select("""
            SELECT fp.id AS fee_plan_id,
                   fpv.id AS fee_plan_version_id,
                   fpv.version_no AS fee_plan_version_no
            FROM fee_plan fp
            INNER JOIN fee_plan_version fpv
                    ON fpv.id = fp.current_version_id
                   AND fpv.plan_id = fp.id
                   AND fp.current_version_no = fpv.version_no
                   AND fpv.version_status = 'ACTIVE'
                   AND fpv.deleted = 0
            WHERE fp.plan_type = 'MERCHANT'
              AND fp.merchant_id = #{merchantId}
              AND fp.status = 'ENABLED'
              AND fp.deleted = 0
            LIMIT 1
            """)
    MerchantFeeVersionPointerDO selectActivePointer(@Param("merchantId") String merchantId);

    /**
     * 一次读取明确不可变版本的版本、规则和阶梯行；调用方负责选择 Slave 或 Master 数据源。
     *
     * @param merchantId 版本所属平台商户号
     * @param feePlanId 快照已经锁定的 MERCHANT 方案主键
     * @param feePlanVersionId 快照已经锁定的版本主键
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
    List<MerchantFeeVersionSnapshotRowDO> selectVersionRows(
            @Param("merchantId") String merchantId,
            @Param("feePlanId") Long feePlanId,
            @Param("feePlanVersionId") Long feePlanVersionId);
}
