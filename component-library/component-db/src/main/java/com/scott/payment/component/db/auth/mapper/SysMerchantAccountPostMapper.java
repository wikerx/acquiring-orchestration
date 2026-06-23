package com.scott.payment.component.db.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.component.db.auth.entity.SysMerchantAccountPostDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商户账号岗位关联 Mapper。
 */
@Mapper
public interface SysMerchantAccountPostMapper extends BaseMapper<SysMerchantAccountPostDO> {
}
