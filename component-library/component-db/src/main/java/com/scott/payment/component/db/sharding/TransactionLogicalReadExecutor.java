package com.scott.payment.component.db.sharding;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionLogicalReadExecutor
 * @date : 2026-08-02 00:00
 * @email : scott_x@163.com
 * @description : 在 transaction 逻辑数据源上下文中执行普通或主库强一致只读查询。
 * @status : create
 */
@Component
public class TransactionLogicalReadExecutor {

    /**
     * 在 ShardingSphere 逻辑数据源上执行允许读写分离的普通查询。
     *
     * @param query 不得产生业务写入的查询回调
     * @param <T> 查询结果类型
     * @return 查询结果
     */
    @DS(DataSourceName.TRANSACTION)
    public <T> T read(Supplier<T> query) {
        return Objects.requireNonNull(query, "query").get();
    }

    /**
     * 在 ShardingSphere 逻辑数据源的 primary 节点执行强一致查询。
     *
     * @param query 不得产生业务写入的查询回调
     * @param <T> 查询结果类型
     * @return 查询结果
     */
    @DS(DataSourceName.TRANSACTION)
    public <T> T readPrimary(Supplier<T> query) {
        Objects.requireNonNull(query, "query");
        try (TransactionPrimaryRouteScope ignored = TransactionPrimaryRouteScope.open()) {
            return query.get();
        }
    }
}
