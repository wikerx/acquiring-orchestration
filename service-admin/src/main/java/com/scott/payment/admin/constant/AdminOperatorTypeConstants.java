package com.scott.payment.admin.constant;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminOperatorTypeConstants
 * @date : 2026-06-06 00:00
 * @email : scott_x@163.com
 * @description : 管理后台操作人类型常量
 * @status : create
 */
public final class AdminOperatorTypeConstants {

    /**
     * 后台管理用户。
     */
    public static final int ADMIN_USER = 1;

    /**
     * 商户用户，用于商户管理端或商户门户调用内部接口时标识操作来源。
     */
    public static final int MERCHANT_USER = 2;

    /**
     * 系统任务。
     */
    public static final int SYSTEM_JOB = 3;

    /**
     * 工具类不允许实例化。
     */
    private AdminOperatorTypeConstants() {
    }
}
