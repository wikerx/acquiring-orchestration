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
     * 唯一从库数据源名称，读请求通过 @DS(DataSourceName.SLAVE) 显式路由。
     */
    public static final String SLAVE = "slave";

    /**
     * 交易逻辑数据源名称。外层 dynamic-datasource 只选择该复合数据源，内部季度路由和读写分离由 ShardingSphere 负责。
     */
    public static final String TRANSACTION = "transaction";

    private DataSourceName() {
    }
}
