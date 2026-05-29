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
     * 从库读分组名称，读请求可通过 @DS(DataSourceName.SLAVE) 显式路由到从库组。
     */
    public static final String SLAVE = "slave";

    /**
     * 一号从库数据源名称，当前 dev 环境默认与主库指向同一个 MySQL。
     */
    public static final String SLAVE_1 = "slave_1";

    /**
     * 二号从库数据源名称，当前 dev 环境默认与主库指向同一个 MySQL。
     */
    public static final String SLAVE_2 = "slave_2";

    private DataSourceName() {
    }
}
