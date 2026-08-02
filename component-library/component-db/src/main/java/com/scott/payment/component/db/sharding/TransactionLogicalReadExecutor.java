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
 * @description : 在 transaction 逻辑数据源上下文中执行只读查询，并向兼容服务暴露当前逻辑表作用域。
 * @status : create
 */
@Component
public class TransactionLogicalReadExecutor {

    /** 当前线程嵌套逻辑查询深度；归零时必须 remove，防止线程池请求串扰。 */
    private final ThreadLocal<Integer> logicalRouteDepth = ThreadLocal.withInitial(() -> 0);

    /**
     * 在 ShardingSphere 逻辑数据源上执行允许读写分离的普通查询。
     *
     * @param query 不得产生业务写入的查询回调
     * @param <T> 查询结果类型
     * @return 查询结果
     */
    @DS(DataSourceName.TRANSACTION)
    public <T> T read(Supplier<T> query) {
        return execute(query, false);
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
        return execute(query, true);
    }

    /**
     * 判断当前线程是否正在逻辑表查询作用域内。
     *
     * @return true 表示表名解析必须返回固定逻辑表名
     */
    public boolean isLogicalRouteActive() {
        return logicalRouteDepth.get() > 0;
    }

    private <T> T execute(Supplier<T> query, boolean primaryOnly) {
        Objects.requireNonNull(query, "query");
        logicalRouteDepth.set(logicalRouteDepth.get() + 1);
        try {
            if (!primaryOnly) {
                return query.get();
            }
            try (TransactionPrimaryRouteScope ignored = TransactionPrimaryRouteScope.open()) {
                return query.get();
            }
        } finally {
            int remainingDepth = logicalRouteDepth.get() - 1;
            if (remainingDepth == 0) {
                logicalRouteDepth.remove();
            } else {
                logicalRouteDepth.set(remainingDepth);
            }
        }
    }

}
