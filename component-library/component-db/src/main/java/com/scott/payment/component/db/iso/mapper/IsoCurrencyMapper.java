package com.scott.payment.component.db.iso.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.component.db.iso.entity.IsoCurrencyDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : IsoCurrencyMapper
 * @date : 2026-06-03 14:26
 * @email : scott_x@163.com
 * @description : ISO 4217 币种基础字典 Mapper
 * @status : create
 */
@Mapper
public interface IsoCurrencyMapper extends BaseMapper<IsoCurrencyDO> {
}
