package com.scott.payment.merchant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.merchant.entity.system.FileTemplateReadEntities.TemplateDO;
import org.apache.ibatis.annotations.Mapper;

/** 商户端只读模板定义 Mapper。 */
@Mapper
public interface MerchantFileTemplateMapper extends BaseMapper<TemplateDO> {
}
