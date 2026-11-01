package com.scott.payment.component.db.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.component.db.auth.entity.SysAccountMfaTokenDO;

/**
 * 系统账号 MFA 短期票据 Mapper，位于 component-db 数据访问层；只负责二阶段登录票据的哈希化持久化访问。
 */
public interface SysAccountMfaTokenMapper extends BaseMapper<SysAccountMfaTokenDO> {
}
