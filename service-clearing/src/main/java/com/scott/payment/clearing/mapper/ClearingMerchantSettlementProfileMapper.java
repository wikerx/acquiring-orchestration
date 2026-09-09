package com.scott.payment.clearing.mapper;

import com.scott.payment.clearing.entity.ClearingMerchantSettlementProfileDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingMerchantSettlementProfileMapper
 * @date : 2026-08-26 18:36
 * @email : scott_x@163.com
 * @description : 只读活动结算档案的目标币种，供保证金释放生成候选；不读取汇率、不认领批次、不写账户。
 * @status : create
 */
public interface ClearingMerchantSettlementProfileMapper {

    /**
     * 按商户和释放业务日读取唯一活动档案。
     *
     * @param merchantId 平台商户号
     * @param businessDate 保证金释放业务日
     * @return 活动档案目标投影，不存在时返回空
     */
    @Select("""
            SELECT id, merchant_id, target_currency, target_currency_exponent
            FROM merchant_settlement_profile
            WHERE merchant_id = #{merchantId}
              AND profile_status = 'ACTIVE'
              AND active_slot = 1
              AND effective_date <= #{businessDate}
              AND (expire_date IS NULL OR expire_date >= #{businessDate})
            LIMIT 1
            """)
    ClearingMerchantSettlementProfileDO selectActiveProfile(
            @Param("merchantId") String merchantId,
            @Param("businessDate") LocalDate businessDate);
}
