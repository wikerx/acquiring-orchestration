package com.scott.payment.component.db.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysAccountMfaTokenDO
 * @date : 2026-07-19 00:00
 * @email : scott_x@163.com
 * @description : 系统账号 MFA 短期票据实体，位于 component-db 认证数据层；只保存票据哈希，用于账号密码通过后的 OTP 二阶段挑战。
 * @status : create
 */
@Data
@TableName("sys_account_mfa_token")
public class SysAccountMfaTokenDO {

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
     * 票据类型：LOGIN_MFA。
     */
    private String tokenType;

    /**
     * 票据 SHA-256 哈希，数据库不保存明文票据。
     */
    private String tokenHash;

    /**
     * 创建票据时的 MFA 挑战类型。
     */
    private String challengeType;

    /**
     * 票据过期时间。
     */
    private LocalDateTime expireAt;

    /**
     * 是否已使用：0否，1是。
     */
    private Integer used;

    /**
     * 票据使用时间。
     */
    private LocalDateTime usedAt;

    /**
     * 创建票据的客户端 IP。
     */
    private String clientIp;

    /**
     * 创建票据的 User-Agent。
     */
    private String userAgent;

    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;

    /**
     * 修改时间。
     */
    private LocalDateTime updatedAt;

    /**
     * 删除标识：0未删除，大于0为删除记录ID。
     */
    private Long deleted;
}
