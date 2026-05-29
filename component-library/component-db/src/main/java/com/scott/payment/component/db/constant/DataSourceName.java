package com.scott.payment.component.db.constant;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DataSourceName
 * @date : 2026-05-29 00:00
 * @email : scott_x@163.com
 * @description : 动态数据源名称常量
 * @status : create
 */
public final class DataSourceName {

    /**
     * 主库数据源名称，默认承接写请求和未显式标记的数据访问。
     */
    public static final String MASTER = "master";

    /**
     * 从库数据源名称，读请求可通过 @DS(DataSourceName.SLAVE) 显式路由到该数据源。
     */
    public static final String SLAVE = "slave";

    private DataSourceName() {
    }
}
