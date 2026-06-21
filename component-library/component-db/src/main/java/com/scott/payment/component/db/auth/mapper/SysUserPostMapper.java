package com.scott.payment.component.db.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.component.db.auth.entity.SysUserPostDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户岗位关联数据访问接口。
 *
 * <p>只负责 {@code sys_user_post} 关系表的基础增删查改，岗位有效性由业务服务校验。</p>
 */
@Mapper
public interface SysUserPostMapper extends BaseMapper<SysUserPostDO> {
}
