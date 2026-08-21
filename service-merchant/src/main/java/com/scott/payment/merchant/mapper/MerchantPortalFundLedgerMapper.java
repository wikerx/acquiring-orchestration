package com.scott.payment.merchant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.merchant.entity.MerchantFinanceEntities.FundLedgerDO;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantPortalFundLedgerMapper
 * @date : 2026-08-18 00:00
 * @email : scott_x@163.com
 * @description : 商户端不可变余额流水只读数据访问，查询必须同时校验商户号和资金账户归属。
 * @status : create
 */
public interface MerchantPortalFundLedgerMapper extends BaseMapper<FundLedgerDO> { }
