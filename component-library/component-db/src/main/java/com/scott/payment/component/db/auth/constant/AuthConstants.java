package com.scott.payment.component.db.auth.constant;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AuthConstants
 * @date : 2026-06-06 00:00
 * @email : scott_x@163.com
 * @description : 管理类系统登录权限常量
 * @status : create
 */
public final class AuthConstants {

    /**
     * 管理后台应用编码。
     */
    public static final String APP_ADMIN = "ADMIN";

    /**
     * 商户管理系统应用编码。
     */
    public static final String APP_MERCHANT = "MERCHANT";

    /**
     * 平台用户类型。
     */
    public static final String USER_TYPE_PLATFORM = "PLATFORM";

    /**
     * 商户用户类型。
     */
    public static final String USER_TYPE_MERCHANT = "MERCHANT";

    /**
     * 默认平台角色。
     */
    public static final String DEFAULT_ADMIN_ROLE = "ADMIN_OPERATOR";

    /**
     * 默认商户管理员角色。
     */
    public static final String DEFAULT_MERCHANT_ROLE = "MERCHANT_ADMIN";

    /**
     * 启用状态。
     */
    public static final int ENABLED = 1;

    /**
     * 禁用状态。
     */
    public static final int DISABLED = 0;

    /**
     * 未删除标识。
     */
    public static final long NOT_DELETED = 0L;

    /**
     * 默认登录有效期，单位秒。
     */
    public static final long DEFAULT_TOKEN_TTL_SECONDS = 7_200L;

    /**
     * MFA 策略：上线前已有账号默认不启用 OTP。
     */
    public static final String MFA_POLICY_OPTIONAL = "OPTIONAL";

    /**
     * MFA 策略：账号登录必须完成 OTP 绑定和验证。
     */
    public static final String MFA_POLICY_REQUIRED = "REQUIRED";

    /**
     * MFA 策略：经管理员明确批准后豁免 OTP。
     */
    public static final String MFA_POLICY_EXEMPT = "EXEMPT";

    /**
     * MFA 状态：未启用。
     */
    public static final String MFA_STATUS_NOT_ENABLED = "NOT_ENABLED";

    /**
     * MFA 状态：待首次绑定。
     */
    public static final String MFA_STATUS_PENDING_BIND = "PENDING_BIND";

    /**
     * MFA 状态：已启用。
     */
    public static final String MFA_STATUS_ENABLED = "ENABLED";

    /**
     * MFA 状态：已重置，必须重新绑定。
     */
    public static final String MFA_STATUS_RESET_REQUIRED = "RESET_REQUIRED";

    /**
     * MFA 状态：已豁免。
     */
    public static final String MFA_STATUS_EXEMPT = "EXEMPT";

    /**
     * MFA 状态：因连续失败临时锁定。
     */
    public static final String MFA_STATUS_LOCKED = "LOCKED";

    /**
     * MFA 状态：账号停用后 MFA 不可用。
     */
    public static final String MFA_STATUS_DISABLED = "DISABLED";

    /**
     * MFA 类型：基于时间的一次性密码。
     */
    public static final String MFA_TYPE_TOTP = "TOTP";

    /**
     * 登录响应状态：已完成登录。
     */
    public static final String LOGIN_STATUS_SUCCESS = "SUCCESS";

    /**
     * 登录响应状态：需要进入 MFA 二阶段。
     */
    public static final String LOGIN_STATUS_MFA_REQUIRED = "MFA_REQUIRED";

    /**
     * MFA 挑战类型：需要首次绑定。
     */
    public static final String MFA_CHALLENGE_BIND_REQUIRED = "BIND_REQUIRED";

    /**
     * MFA 挑战类型：需要输入动态验证码。
     */
    public static final String MFA_CHALLENGE_VERIFY_REQUIRED = "VERIFY_REQUIRED";

    /**
     * MFA 挑战类型：重置后需要重新绑定。
     */
    public static final String MFA_CHALLENGE_RESET_BIND_REQUIRED = "RESET_BIND_REQUIRED";

    /**
     * MFA 挑战类型：连续失败后临时锁定。
     */
    public static final String MFA_CHALLENGE_LOCKED = "LOCKED";

    private AuthConstants() {
    }
}
