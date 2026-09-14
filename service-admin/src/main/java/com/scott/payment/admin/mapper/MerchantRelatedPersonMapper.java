package com.scott.payment.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.admin.entity.merchant.MerchantOnboardingEntities.MerchantRelatedPersonDO;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantRelatedPersonMapper
 * @date : 2026-09-14 19:10
 * @email : scott_x@163.com
 * @description : 商户法定代表人、董事、UBO 与授权人 Mapper，仅负责相关人员数据访问
 * @status : create
 */
public interface MerchantRelatedPersonMapper extends BaseMapper<MerchantRelatedPersonDO> {
}
