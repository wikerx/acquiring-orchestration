package com.scott.payment.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.admin.entity.merchant.MerchantOnboardingEntities.MerchantReviewRecordDO;

/**
 * 商户开户审核不可变记录 Mapper，仅负责审核审计记录的数据访问
 */
public interface MerchantReviewRecordMapper extends BaseMapper<MerchantReviewRecordDO> {
}
