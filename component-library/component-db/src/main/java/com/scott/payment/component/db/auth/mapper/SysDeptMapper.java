package com.scott.payment.component.db.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.component.db.auth.entity.SysDeptDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysDeptMapper
 * @date : 2026-06-12 20:00
 * @email : scott_x@163.com
 * @description : 部门 MyBatis Plus Mapper 接口
 * @status : create
 */
@Mapper
public interface SysDeptMapper extends BaseMapper<SysDeptDO> {
}
