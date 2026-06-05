package com.scott.payment.admin.constant;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminOperationTypeConstants
 * @date : 2026-06-06 00:00
 * @email : scott_x@163.com
 * @description : 管理后台操作日志业务类型常量
 * @status : create
 */
public final class AdminOperationTypeConstants {

    /**
     * 新增操作。
     */
    public static final int CREATE = 1;

    /**
     * 修改操作。
     */
    public static final int UPDATE = 2;

    /**
     * 删除操作。
     */
    public static final int DELETE = 3;

    /**
     * 查询操作。
     */
    public static final int QUERY = 4;

    /**
     * 导出操作。
     */
    public static final int EXPORT = 5;

    /**
     * 审核操作。
     */
    public static final int AUDIT = 6;

    /**
     * 冻结操作。
     */
    public static final int FREEZE = 7;

    /**
     * 解冻操作。
     */
    public static final int UNFREEZE = 8;

    /**
     * 工具类不允许实例化。
     */
    private AdminOperationTypeConstants() {
    }
}
