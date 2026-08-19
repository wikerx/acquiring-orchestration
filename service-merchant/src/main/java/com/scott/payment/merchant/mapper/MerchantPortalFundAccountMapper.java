package com.scott.payment.merchant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.merchant.entity.MerchantFinanceEntities.FundAccountDO;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantPortalFundAccountMapper
 * @date : 2026-08-18 00:00
 * @email : scott_x@163.com
 * @description : 商户端资金账户只读数据访问，所有查询必须限定认证商户号且不提供余额修改入口。
 * @status : create
 */
public interface MerchantPortalFundAccountMapper extends BaseMapper<FundAccountDO> { }
