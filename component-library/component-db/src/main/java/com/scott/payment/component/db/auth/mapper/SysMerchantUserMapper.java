package com.scott.payment.component.db.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.component.db.auth.entity.SysMerchantUserDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商户端登录用户 Mapper。
 */
@Mapper
public interface SysMerchantUserMapper extends BaseMapper<SysMerchantUserDO> {
}
