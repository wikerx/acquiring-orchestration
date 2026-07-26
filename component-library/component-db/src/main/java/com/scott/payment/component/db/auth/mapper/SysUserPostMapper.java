package com.scott.payment.component.db.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.component.db.auth.entity.SysUserPostDO;
import org.apache.ibatis.annotations.Mapper;


@Mapper
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysUserPostMapper
 * @date : 2026-06-21 22:32
 * @email : scott_x@163.com
 * @description : Sys User Post Mapper 映射组件，位于 公共组件库，在数据库记录、领域模型、接口 DTO 或渠道协议对象之间转换字段。
 * @status : create
 */
public interface SysUserPostMapper extends BaseMapper<SysUserPostDO> {
}
