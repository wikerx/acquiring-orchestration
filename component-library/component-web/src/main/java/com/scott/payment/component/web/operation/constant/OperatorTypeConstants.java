package com.scott.payment.component.web.operation.constant;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OperatorTypeConstants
 * @date : 2026-06-06 00:00
 * @email : scott_x@163.com
 * @description : 管理类系统操作日志操作人类型常量
 * @status : create
 */
public final class OperatorTypeConstants {

    /**
     * 后台管理用户。
     */
    public static final int ADMIN_USER = 1;

    /**
     * 商户管理端用户。
     */
    public static final int MERCHANT_USER = 2;

    /**
     * 系统任务。
     */
    public static final int SYSTEM_JOB = 3;

    /**
     * 私有构造方法，禁止外部实例化该操作人类型常量集合。
     */
    private OperatorTypeConstants() {
    }
}
