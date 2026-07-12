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
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : IsoCountryMapper
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Iso Country 数据访问 Mapper，位于 component-library/component-db 的数据访问层，用于定义调用契约和职责边界。
 * @status : create
 */
@Mapper
public interface IsoCountryMapper extends BaseMapper<IsoCountryDO> {
}
