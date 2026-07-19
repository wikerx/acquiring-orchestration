package com.scott.payment.component.db.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysAccountMfaDO
 * @date : 2026-07-19 00:00
 * @email : scott_x@163.com
 * @description : 系统账号 MFA 配置数据库实体，位于 component-db 认证数据层；按应用和账号独立保存 OTP 策略、绑定状态、加密密钥和锁定信息。
 * @status : create
 */
@Data
@TableName("sys_account_mfa")
public class SysAccountMfaDO {

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
     * 商户号，管理后台账号为空，商户端账号后续接入时使用。
     */
    private String merchantId;

    /**
     * MFA 策略：OPTIONAL、REQUIRED、EXEMPT。
     */
    private String mfaPolicy;

    /**
     * MFA 状态：NOT_ENABLED、PENDING_BIND、ENABLED、RESET_REQUIRED、EXEMPT、LOCKED、DISABLED。
     */
    private String mfaStatus;

    /**
     * MFA 类型，本期固定 TOTP。
     */
    private String mfaType;

    /**
     * 已绑定 TOTP 密钥密文，使用 AES-GCM 加密保存。
     */
    private String secretCipher;

    /**
     * 待绑定或待重新绑定 TOTP 密钥密文，用户确认绑定后才转为正式密钥。
     */
    private String pendingSecretCipher;

    /**
     * 验证器展示的发行方。
     */
    private String issuer;

    /**
     * 验证器展示的账号标签。
     */
    private String accountLabel;

    /**
     * 完成绑定时间。
     */
    private LocalDateTime bindTime;

    /**
     * 最近一次 OTP 验证成功时间。
     */
    private LocalDateTime lastVerifyTime;

    /**
     * 最近一次验证成功的 TOTP 时间步，用于阻止同一验证码重放。
     */
    private Long lastSuccessTimeStep;

    /**
     * 连续 OTP 验证失败次数。
     */
    private Integer failedVerifyCount;

    /**
     * OTP 连续失败后的锁定截止时间。
     */
    private LocalDateTime lockedUntil;

    /**
     * 最近一次重置时间。
     */
    private LocalDateTime resetTime;

    /**
     * 明确豁免原因，仅 EXEMPT 策略允许有值。
     */
    private String exemptReason;

    /**
     * 豁免截止时间，空表示长期豁免。
     */
    private LocalDateTime exemptUntil;

    /**
     * 备注。
     */
    private String remark;

    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;

    /**
     * 创建人ID。
     */
    private Long createdBy;

    /**
     * 修改时间。
     */
    private LocalDateTime updatedAt;

    /**
     * 修改人ID。
     */
    private Long updatedBy;

    /**
     * 删除标识：0未删除，大于0为删除记录ID。
     */
    private Long deleted;
}
