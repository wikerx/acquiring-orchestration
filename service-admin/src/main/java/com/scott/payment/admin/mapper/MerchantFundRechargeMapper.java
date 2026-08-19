package com.scott.payment.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.admin.entity.fund.FundAccountEntities.MerchantFundRechargeDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantFundRechargeMapper
 * @date : 2026-08-18 00:00
 * @email : scott_x@163.com
 * @description : 商户资金账户充值申请数据访问组件。
 * @status : create
 */
@Mapper
public interface MerchantFundRechargeMapper extends BaseMapper<MerchantFundRechargeDO> {

    /**
     * 按主键加排他锁读取充值申请，串行化审核、复核、驳回和最终入账。
     *
     * @param id 充值申请主键
     * @return 未删除的充值申请；不存在时返回 null
     */
    @Select("SELECT * FROM merchant_fund_recharge WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    MerchantFundRechargeDO selectByIdForUpdate(@Param("id") Long id);

    /**
     * 按客户端请求号执行当前读，用于并发唯一键冲突后的稳定幂等回查。
     *
     * @param requestId 客户端充值唯一请求号
     * @return 已占用请求号的充值申请；不存在时返回 null
     */
    @Select("SELECT * FROM merchant_fund_recharge WHERE request_id = #{requestId} AND deleted = 0 FOR UPDATE")
    MerchantFundRechargeDO selectByRequestIdForUpdate(@Param("requestId") String requestId);
}
