package com.scott.payment.component.db.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.component.db.auth.entity.MerchantIpWhitelistDO;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantIpWhitelistMapper
 * @date : 2026-07-18 00:00
 * @email : scott_x@163.com
 * @description : 商户 OpenAPI IP 白名单 Mapper，位于 component-db 数据访问层，仅负责精确 IP 白名单记录读写。
 * @status : create
 */
public interface MerchantIpWhitelistMapper extends BaseMapper<MerchantIpWhitelistDO> {
}
