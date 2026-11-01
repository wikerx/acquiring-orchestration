package com.scott.payment.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.admin.entity.merchant.MerchantOnboardingEntities.MerchantRelatedPersonDO;

/**
 * 商户法定代表人、董事、UBO 与授权人 Mapper，仅负责相关人员数据访问
 */
public interface MerchantRelatedPersonMapper extends BaseMapper<MerchantRelatedPersonDO> {
}
