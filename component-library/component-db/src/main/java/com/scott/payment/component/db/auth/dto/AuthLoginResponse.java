package com.scott.payment.component.db.auth.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AuthLoginResponse
 * @date : 2026-06-06 00:00
 * @email : scott_x@163.com
 * @description : 管理类系统登录响应
 * @status : create
 */
@Data
public class AuthLoginResponse implements Serializable {

    /**
     * 序列化版本号。
     */
    private static final long serialVersionUID = 1L;

    /**
     * Bearer token 明文，前端后续请求使用该值。
     */
    private String accessToken;

    /**
     * token 类型。
     */
    private String tokenType = "Bearer";

    /**
     * 过期秒数。
     */
    private Long expiresIn;

    /**
     * token 过期时间。
     */
    private LocalDateTime expireAt;

    /**
     * 登录账号信息。
     */
    private AuthAccountDTO account;

    /**
     * 前端菜单树。
     */
    private List<AuthMenuDTO> menus = Collections.emptyList();

    /**
     * 当前账号角色编码集合。
     */
    private List<String> roles = Collections.emptyList();

    /**
     * 后端权限标识集合。
     */
    private List<String> permissions = Collections.emptyList();

    /**
     * 登录状态：SUCCESS 表示已签发会话，MFA_REQUIRED 表示需进入 OTP 二阶段。
     */
    private String loginStatus;

    /**
     * 是否需要 MFA 二阶段。
     */
    private Boolean mfaRequired;

    /**
     * MFA 挑战类型：BIND_REQUIRED、VERIFY_REQUIRED、RESET_BIND_REQUIRED、LOCKED。
     */
    private String mfaChallengeType;

    /**
     * 短期登录票据，仅 MFA_REQUIRED 时返回，不能作为真实登录 token 使用。
     */
    private String loginTicket;

    /**
     * 短期登录票据过期时间。
     */
    private LocalDateTime loginTicketExpireAt;

    /**
     * MFA 策略：OPTIONAL、REQUIRED、EXEMPT。
     */
    private String mfaPolicy;

    /**
     * MFA 状态：NOT_ENABLED、PENDING_BIND、ENABLED、RESET_REQUIRED、EXEMPT、LOCKED、DISABLED。
     */
    private String mfaStatus;

    /**
     * OTP 临时锁定截止时间。
     */
    private LocalDateTime mfaLockedUntil;
}
