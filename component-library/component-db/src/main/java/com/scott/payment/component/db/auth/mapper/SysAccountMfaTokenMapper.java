package com.scott.payment.component.db.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.component.db.auth.entity.SysAccountMfaTokenDO;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysAccountMfaTokenMapper
 * @date : 2026-07-19 00:00
 * @email : scott_x@163.com
 * @description : 系统账号 MFA 短期票据 Mapper，位于 component-db 数据访问层；只负责二阶段登录票据的哈希化持久化访问。
 * @status : create
 */
public interface SysAccountMfaTokenMapper extends BaseMapper<SysAccountMfaTokenDO> {
}
