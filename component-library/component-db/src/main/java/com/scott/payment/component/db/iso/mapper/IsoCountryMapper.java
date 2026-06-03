package com.scott.payment.component.db.iso.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.component.db.iso.entity.IsoCountryDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : IsoCountryMapper
 * @date : 2026-06-03 14:25
 * @email : scott_x@163.com
 * @description : ISO 3166 国家地区基础字典 Mapper
 * @status : create
 */
@Mapper
public interface IsoCountryMapper extends BaseMapper<IsoCountryDO> {
}
