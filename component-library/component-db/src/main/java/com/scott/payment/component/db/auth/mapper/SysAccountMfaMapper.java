package com.scott.payment.component.db.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scott.payment.component.db.auth.entity.SysAccountMfaDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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

    /** 锁定指定账号的有效 MFA 配置，保证同一 TOTP 时间步不能被并发敏感操作重复消费。 */
    @Select("""
            SELECT *
            FROM sys_account_mfa
            WHERE app_id = #{appId}
              AND account_id = #{accountId}
              AND deleted = 0
            LIMIT 1
            FOR UPDATE
            """)
    SysAccountMfaDO selectByAccountForUpdate(@Param("appId") Long appId,
                                             @Param("accountId") Long accountId);
}
