package com.scott.payment.component.db.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.component.db.auth.entity.SysUserPostDO;
import org.apache.ibatis.annotations.Mapper;


/**
 * Sys User Post Mapper 映射组件，位于 公共组件库，在数据库记录、领域模型、接口 DTO 或渠道协议对象之间转换字段。
 */
@Mapper
public interface SysUserPostMapper extends BaseMapper<SysUserPostDO> {
}
