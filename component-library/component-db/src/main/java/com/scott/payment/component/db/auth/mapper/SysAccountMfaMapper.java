package com.scott.payment.component.db.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.component.db.auth.entity.SysAccountMfaDO;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysAccountMfaMapper
 * @date : 2026-07-19 00:00
 * @email : scott_x@163.com
 * @description : 系统账号 MFA 配置 Mapper，位于 component-db 数据访问层；只负责 OTP 策略和状态记录的持久化访问。
 * @status : create
 */
public interface SysAccountMfaMapper extends BaseMapper<SysAccountMfaDO> {
}
