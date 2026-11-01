package com.scott.payment.merchant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.merchant.entity.email.MerchantEmailEntities.MerchantEmailTemplateDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商户邮件模板 Mapper，位于 service-merchant 数据访问层；复用管理系统邮件模板管理页面维护的商户模板。
 */
@Mapper
public interface MerchantEmailTemplateMapper extends BaseMapper<MerchantEmailTemplateDO> {
}
