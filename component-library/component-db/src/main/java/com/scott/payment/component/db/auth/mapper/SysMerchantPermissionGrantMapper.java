package com.scott.payment.component.db.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.component.db.auth.entity.SysMerchantPermissionGrantDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商户资源权限授权 Mapper。
 */
@Mapper
public interface SysMerchantPermissionGrantMapper extends BaseMapper<SysMerchantPermissionGrantDO> {
}
