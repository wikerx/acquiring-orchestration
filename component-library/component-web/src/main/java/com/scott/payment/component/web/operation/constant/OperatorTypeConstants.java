package com.scott.payment.component.web.operation.constant;

/**
 * 管理类系统操作日志操作人类型常量
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
