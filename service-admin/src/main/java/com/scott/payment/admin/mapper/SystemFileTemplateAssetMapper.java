package com.scott.payment.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.admin.entity.system.FileTemplateEntities.FileTemplateAssetDO;
import org.apache.ibatis.annotations.Mapper;

/** 系统文件模板本地化资产数据访问组件。 */
@Mapper
public interface SystemFileTemplateAssetMapper extends BaseMapper<FileTemplateAssetDO> {
}
