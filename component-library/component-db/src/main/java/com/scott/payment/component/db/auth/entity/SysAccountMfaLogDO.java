package com.scott.payment.component.db.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysAccountMfaLogDO
 * @date : 2026-07-19 00:00
 * @email : scott_x@163.com
 * @description : 系统账号 MFA 操作日志实体，位于 component-db 认证数据层；记录 OTP 绑定、验证、重置、豁免和解锁等安全事件。
 * @status : create
 */
@Data
@TableName("sys_account_mfa_log")
public class SysAccountMfaLogDO {

    /**
     * 主键ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 系统应用ID。
     */
    private Long appId;

    /**
     * 登录账号ID。
     */
    private Long accountId;

    /**
     * 用户主体ID。
     */
    private Long userId;

    /**
     * 商户号，管理后台账号为空。
     */
    private String merchantId;

    /**
     * 操作类型。
     */
    private String actionType;

    /**
     * 操作结果：SUCCESS、FAILED。
     */
    private String result;

    /**
     * 操作原因或失败原因。
     */
    private String reason;

    /**
     * 操作前 MFA 策略。
     */
    private String beforePolicy;

    /**
     * 操作前 MFA 状态。
     */
    private String beforeStatus;

    /**
     * 操作后 MFA 策略。
     */
    private String afterPolicy;

    /**
     * 操作后 MFA 状态。
     */
    private String afterStatus;

    /**
     * 操作人账号ID，用户登录自助验证时为空。
     */
    private Long operatorAccountId;

    /**
     * 操作人登录账号。
     */
    private String operatorLoginAccount;

    /**
     * 客户端 IP。
     */
    private String clientIp;

    /**
     * User-Agent。
     */
    private String userAgent;

    /**
     * 事件时间。
     */
    private LocalDateTime eventTime;

    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;
}
