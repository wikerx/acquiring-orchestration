package com.scott.payment.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.admin.entity.fund.FundAccountEntities.MerchantFundLedgerDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantFundLedgerMapper
 * @date : 2026-08-18 00:00
 * @email : scott_x@163.com
 * @description : 不可变资金流水查询访问，不提供更新或删除能力。
 * @status : create
 */
public interface MerchantFundLedgerMapper extends BaseMapper<MerchantFundLedgerDO> {

    /**
     * 查询账户当前最大流水序号，调用方必须已持有资金账户行锁。
     *
     * @param accountId 资金账户主键
     * @return 当前最大账户流水序号；没有流水时返回零
     */
    @Select("SELECT COALESCE(MAX(account_sequence), 0) FROM merchant_fund_ledger WHERE account_id = #{accountId}")
    long selectMaxAccountSequence(@Param("accountId") Long accountId);
}
