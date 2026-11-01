package com.scott.payment.component.db.iso.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.component.db.iso.entity.IsoCountryDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * ISO 3166 国家地区基础字典 Mapper
 */

@Mapper
public interface IsoCountryMapper extends BaseMapper<IsoCountryDO> {
}
