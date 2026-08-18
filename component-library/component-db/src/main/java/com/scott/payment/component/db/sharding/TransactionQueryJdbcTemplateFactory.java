package com.scott.payment.component.db.sharding;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionQueryJdbcTemplateFactory
 * @date : 2026-08-02 00:00
 * @email : scott_x@163.com
 * @description : 为 Admin/Merchant 交易查询创建独立 JDBC 模板，以语句超时限制慢查询且不改变 ShardingSphere replica 路由语义。
 * @status : create
 */
@Component
public class TransactionQueryJdbcTemplateFactory {

    /**
     * 创建共享动态数据源但拥有独立语句配置的命名参数 JDBC 模板。
     *
     * @param dataSource dynamic-datasource 外层路由数据源
     * @param properties 交易查询预算配置
     * @return 每条 JDBC Statement 都应用向上取整秒级超时的查询模板
     */
    public NamedParameterJdbcTemplate create(DataSource dataSource,
                                             TransactionShardingProperties properties) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.setQueryTimeout(timeoutSeconds(properties));
        return new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    /** 将配置的毫秒超时向上取整为 JDBC 支持的正整数秒。 */
    private int timeoutSeconds(TransactionShardingProperties properties) {
        long timeoutMillis = properties.getQueryBudget().getSynchronousTimeoutMillis();
        long seconds = timeoutMillis / 1000L + (timeoutMillis % 1000L == 0L ? 0L : 1L);
        if (seconds <= 0L || seconds > Integer.MAX_VALUE) {
            throw new IllegalStateException("transaction query synchronous timeout is invalid");
        }
        return (int) seconds;
    }
}
