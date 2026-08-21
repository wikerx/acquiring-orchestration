package com.scott.payment.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.admin.entity.fund.FundAccountEntities.MerchantReserveItemDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantReserveItemMapper
 * @date : 2026-08-18 00:00
 * @email : scott_x@163.com
 * @description : 保证金留存与释放明细查询访问。
 * @status : create
 */
public interface MerchantReserveItemMapper extends BaseMapper<MerchantReserveItemDO> {

    /**
     * 实时汇总账户仍被平台留存的保证金净额。
     *
     * @param accountId 资金账户主键
     * @param merchantId 账户所属商户号
     * @return 未释放且未扣减的保证金金额，无明细时返回零
     */
    @Select("""
            SELECT COALESCE(SUM(GREATEST(retained_amount - released_amount, 0)), 0)
            FROM merchant_reserve_item
            WHERE account_id = #{accountId}
              AND merchant_id = #{merchantId}
              AND reserve_status IN ('HELD', 'RELEASABLE', 'FROZEN')
            """)
    BigDecimal sumHeldBalance(@Param("accountId") Long accountId,
                              @Param("merchantId") String merchantId);
}
