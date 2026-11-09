package com.scott.payment.component.db.sharding;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * 在 riskAudit 逻辑数据源上下文中执行风控审计普通查询或主库强一致查询。
 */
@Component
public class RiskAuditLogicalReadExecutor {

    /**
     * 执行允许读写分离的普通风控审计查询。
     *
     * @param query 不产生业务写入的查询回调
     * @param <T>   查询结果类型
     * @return 查询结果
     */
    @DS(DataSourceName.RISK_AUDIT)
    public <T> T read(Supplier<T> query) {
        return Objects.requireNonNull(query, "query").get();
    }

    /**
     * 在风控审计主库路由范围内执行强一致查询。
     *
     * @param query 不产生业务写入的查询回调
     * @param <T>   查询结果类型
     * @return 查询结果
     */
    @DS(DataSourceName.RISK_AUDIT)
    public <T> T readPrimary(Supplier<T> query) {
        Objects.requireNonNull(query, "query");
        try (TransactionPrimaryRouteScope ignored = TransactionPrimaryRouteScope.open()) {
            return query.get();
        }
    }
}
