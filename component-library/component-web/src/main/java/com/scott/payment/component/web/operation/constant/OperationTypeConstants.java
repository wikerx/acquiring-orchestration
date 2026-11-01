package com.scott.payment.component.web.operation.constant;

/**
 * 管理类系统操作日志业务类型常量
 */
public final class OperationTypeConstants {

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
     * 私有构造方法，禁止外部实例化该操作类型常量集合。
     */
    private OperationTypeConstants() {
    }
}
