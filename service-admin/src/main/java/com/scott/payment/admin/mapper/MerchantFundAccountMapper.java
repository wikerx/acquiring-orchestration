package com.scott.payment.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.admin.entity.fund.FundAccountEntities.MerchantFundAccountDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantFundAccountMapper
 * @date : 2026-08-18 00:00
 * @email : scott_x@163.com
 * @description : 商户资金账户开户、查询和余额变更锁数据访问。
 * @status : create
 */
public interface MerchantFundAccountMapper extends BaseMapper<MerchantFundAccountDO> {

    /** 按主键锁定资金账户，保证余额变更和流水入账串行执行。 */
    @Select("SELECT * FROM merchant_fund_account WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    MerchantFundAccountDO selectByIdForUpdate(@Param("id") Long id);

    /** 幂等创建零余额账户，唯一索引兜底商户重复开户。 */
    @Insert("""
            INSERT IGNORE INTO merchant_fund_account (
                account_no, merchant_id, settlement_currency, available_balance,
                account_status, account_version, create_by, create_time,
                update_by, update_time, deleted
            ) VALUES (
                #{account.accountNo}, #{account.merchantId}, #{account.settlementCurrency}, 0,
                'NORMAL', 0, #{account.createBy}, #{account.createTime},
                #{account.updateBy}, #{account.updateTime}, 0
            )
            """)
    int insertIfAbsent(@Param("account") MerchantFundAccountDO account);

    /**
     * 统计账户主库是否已产生余额或保证金记录，用于结算币种变更前的不可逆业务保护。
     *
     * @param accountId 资金账户主键
     * @param merchantId 商户号
     * @return 余额流水和保证金记录总数；交易活动由交易副本查询服务独立判断
     */
    @Select("""
            SELECT
                (SELECT COUNT(1) FROM merchant_fund_ledger
                 WHERE account_id = #{accountId} AND merchant_id = #{merchantId})
              + (SELECT COUNT(1) FROM merchant_reserve_item
                 WHERE account_id = #{accountId} AND merchant_id = #{merchantId})
            """)
    long countAccountRecords(@Param("accountId") Long accountId,
                             @Param("merchantId") String merchantId);
}
