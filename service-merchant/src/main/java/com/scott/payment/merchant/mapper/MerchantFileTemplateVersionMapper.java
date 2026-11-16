package com.scott.payment.merchant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.merchant.entity.system.FileTemplateReadEntities.VersionDO;
import org.apache.ibatis.annotations.Mapper;

/** 商户端只读模板版本 Mapper。 */
@Mapper
public interface MerchantFileTemplateVersionMapper extends BaseMapper<VersionDO> {
}
