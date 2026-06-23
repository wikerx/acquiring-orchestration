package com.scott.payment.component.db.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.component.db.auth.entity.SysMerchantUserRoleDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商户端用户角色关联 Mapper。
 */
@Mapper
public interface SysMerchantUserRoleMapper extends BaseMapper<SysMerchantUserRoleDO> {
}
