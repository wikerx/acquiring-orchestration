package com.scott.payment.component.db.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysEmailVerificationRequestDO
 * @date : 2026-10-15 00:00
 * @email : scott_x@163.com
 * @description : 系统账号激活和邮箱验证请求实体；只保存邮箱指纹、验证码哈希与动作令牌哈希。
 * @status : create
 */
@Data
@TableName("sys_email_verification_request")
public class SysEmailVerificationRequestDO {
    /** 数据库主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 高熵公开请求编号，不包含账号或邮箱信息。 */
    private String requestNo;
    /** 系统应用主键。 */
    private Long appId;
    /** 商户号；管理系统请求可为空。 */
    private String merchantId;
    /** 待验证账号主键。 */
    private Long accountId;
    /** 用途：ACCOUNT_ACTIVATION、EMAIL_VERIFICATION、EMAIL_CHANGE。 */
    private String purpose;
    /** 目标邮箱规范化后的 SHA-256 指纹。 */
    private String emailFingerprint;
    /** 邮件验证码 PBKDF2 哈希。 */
    private String codeHash;
    /** 邮件验证码随机盐。 */
    private String codeSalt;
    /** 邮件验证码到期时间。 */
    private LocalDateTime expireAt;
    /** 邮件验证码累计失败次数。 */
    private Integer verifyCount;
    /** 验证码通过时间。 */
    private LocalDateTime verifiedAt;
    /** 激活阶段一次性动作令牌 SHA-256 哈希。 */
    private String actionTokenHash;
    /** 激活阶段一次性动作令牌到期时间。 */
    private LocalDateTime actionTokenExpireAt;
    /** 持久化状态，用于阻止验证码或令牌重放。 */
    private String state;
    /** 发起请求的客户端 IP；系统内部发起时保存 internal。 */
    private String requestIp;
    /** 截断后的 User-Agent，用于安全审计。 */
    private String userAgent;
    /** 邮件语言：zh-CN 或 en-US。 */
    private String locale;
    /** 验证或激活完成时间。 */
    private LocalDateTime completedAt;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;
}
