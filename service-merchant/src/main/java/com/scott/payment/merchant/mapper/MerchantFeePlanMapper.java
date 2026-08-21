package com.scott.payment.merchant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.merchant.entity.MerchantFinanceEntities.FeePlanDO;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantFeePlanMapper
 * @date : 2026-08-18 00:00
 * @email : scott_x@163.com
 * @description : 商户端费用方案只读数据访问，业务查询必须同时限定认证商户号和商户方案类型。
 * @status : create
 */
public interface MerchantFeePlanMapper extends BaseMapper<FeePlanDO> { }
