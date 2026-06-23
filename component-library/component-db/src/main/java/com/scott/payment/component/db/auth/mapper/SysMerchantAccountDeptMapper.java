package com.scott.payment.component.db.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.component.db.auth.entity.SysMerchantAccountDeptDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商户账号部门关联 Mapper。
 */
@Mapper
public interface SysMerchantAccountDeptMapper extends BaseMapper<SysMerchantAccountDeptDO> {
}
