package com.scott.payment.component.db.iso.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.component.db.iso.entity.IsoCurrencyDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * ISO 4217 币种基础字典 Mapper
 */

@Mapper
public interface IsoCurrencyMapper extends BaseMapper<IsoCurrencyDO> {
}
