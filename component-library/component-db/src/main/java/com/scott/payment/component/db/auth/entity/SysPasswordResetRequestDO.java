package com.scott.payment.component.db.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysPasswordResetRequestDO
 * @date : 2026-10-15 00:00
 * @email : scott_x@163.com
 * @description : 系统账号密码重置请求实体；仅保存验证码和令牌哈希，并支持虚拟请求防止账号枚举。
 * @status : create
 */
@Data
@TableName("sys_password_reset_request")
public class SysPasswordResetRequestDO {
    /** 数据库主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 高熵公开请求编号，不承载账号信息。 */
    private String requestNo;
    /** 应用 ID。 */
    private Long appId;
    /** 商户号；管理系统请求可为空。 */
    private String merchantId;
    /** 合格账号 ID；虚拟请求为空。 */
    private Long accountId;
    /** 规范化身份的 SHA-256 指纹，仅用于限流。 */
    private String accountFingerprint;
    /** 邮件验证码 PBKDF2 哈希。 */
    private String emailCodeHash;
    /** 邮件验证码随机盐。 */
    private String emailCodeSalt;
    /** 邮件验证码到期时间。 */
    private LocalDateTime emailExpireAt;
    /** 邮件验证码累计失败次数。 */
    private Integer emailVerifyCount;
    /** 邮箱验证成功时间。 */
    private LocalDateTime emailVerifiedAt;
    /** MFA 验证成功时间。 */
    private LocalDateTime mfaVerifiedAt;
    /** 邮箱验证后阶段令牌的 SHA-256 哈希。 */
    private String stageTokenHash;
    /** 阶段令牌到期时间。 */
    private LocalDateTime stageTokenExpireAt;
    /** 最终重置令牌的 SHA-256 哈希。 */
    private String resetTokenHash;
    /** 最终重置令牌到期时间。 */
    private LocalDateTime resetTokenExpireAt;
    /** 请求状态。 */
    private String state;
    /** 请求 IP，用于限流和安全审计。 */
    private String requestIp;
    /** 截断后的 User-Agent，用于安全审计。 */
    private String userAgent;
    /** 邮件语言：zh-CN 或 en-US。 */
    private String locale;
    /** 密码重置完成时间。 */
    private LocalDateTime completedAt;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;
}
