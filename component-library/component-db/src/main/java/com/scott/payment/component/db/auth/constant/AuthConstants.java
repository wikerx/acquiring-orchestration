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

    private AuthConstants() {
    }
}
