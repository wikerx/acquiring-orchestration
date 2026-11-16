package com.scott.payment.merchant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.merchant.entity.system.FileTemplateReadEntities.AssetDO;
import org.apache.ibatis.annotations.Mapper;

/** 商户端只读模板资产 Mapper。 */
@Mapper
public interface MerchantFileTemplateAssetMapper extends BaseMapper<AssetDO> {
}
