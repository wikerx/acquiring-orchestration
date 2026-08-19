package com.scott.payment.merchant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.merchant.entity.MerchantFinanceEntities.FeePlanVersionDO;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantFeePlanVersionMapper
 * @date : 2026-08-18 00:00
 * @email : scott_x@163.com
 * @description : 商户端生效费用版本只读数据访问，只允许读取当前商户方案指向的 ACTIVE 版本。
 * @status : create
 */
public interface MerchantFeePlanVersionMapper extends BaseMapper<FeePlanVersionDO> { }
