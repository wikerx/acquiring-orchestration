package com.scott.payment.merchant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.merchant.entity.MerchantFinanceEntities.ReserveItemDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantPortalReserveFundMapper
 * @date : 2026-08-18 00:00
 * @email : scott_x@163.com
 * @description : 商户端保证金留存与释放明细只读数据访问，查询必须限定认证商户和账户归属。
 * @status : create
 */
public interface MerchantPortalReserveFundMapper extends BaseMapper<ReserveItemDO> {

    /**
     * 汇总认证商户指定账户仍被平台留存的保证金净额。
     *
     * @param accountId 资金账户主键
     * @param merchantId 认证商户号
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
