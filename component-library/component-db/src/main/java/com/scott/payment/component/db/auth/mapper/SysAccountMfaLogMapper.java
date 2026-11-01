package com.scott.payment.component.db.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.component.db.auth.entity.SysAccountMfaLogDO;

/**
 * 系统账号 MFA 操作日志 Mapper，位于 component-db 数据访问层；只负责 OTP 安全事件审计记录的持久化访问。
 */
public interface SysAccountMfaLogMapper extends BaseMapper<SysAccountMfaLogDO> {
}
