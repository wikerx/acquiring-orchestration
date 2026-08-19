package com.scott.payment.merchant.service.impl;

import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.db.sharding.TransactionLogicalReadExecutor;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JdbcMerchantPendingBalanceQueryServiceTests
 * @date : 2026-08-19 00:00
 * @email : scott_x@163.com
 * @description : 商户端在途余额查询测试，验证认证商户隔离、标签币种净额和交易副本普通读路由。
 * @status : create
 */
class JdbcMerchantPendingBalanceQueryServiceTests {

    /** 商户端在途余额只允许返回认证商户成功且未结算的资金动作。 */
    @Test
    void shouldAggregatePendingBalanceWithinAuthenticatedMerchantBoundary() {
        JdbcDataSource dataSource = dataSource();
        JdbcTemplate jdbc = prepareTransactionOperationTable(dataSource);
        insert(jdbc, "merchant-a", "PAYMENT", "SUCCESS", "NOT_SETTLED", "USD", "125.00");
        insert(jdbc, "merchant-a", "CHARGEBACK", "SUCCESS", "NOT_SETTLED", "USD", "25.00");
        insert(jdbc, "merchant-a", "PRE_AUTH_COMPLETION", "SUCCESS", "NOT_SETTLED", "EUR", "30.00");
        insert(jdbc, "merchant-b", "PAYMENT", "SUCCESS", "NOT_SETTLED", "USD", "900.00");
        TransactionLogicalReadExecutor readExecutor = executingReadExecutor();
        JdbcMerchantPendingBalanceQueryService service = service(dataSource, readExecutor);

        var balances = service.sumPendingBalances("merchant-a");

        assertThat(balances).extracting("currency", "amount").containsExactly(
                org.assertj.core.groups.Tuple.tuple("EUR", new BigDecimal("30.000000")),
                org.assertj.core.groups.Tuple.tuple("USD", new BigDecimal("100.000000")));
        verify(readExecutor).read(any());
        verify(readExecutor, never()).readPrimary(any());
    }

    /** 缺少认证商户号时必须在访问交易副本前失败。 */
    @Test
    void shouldRejectMissingAuthenticatedMerchantBeforeQuery() {
        JdbcDataSource dataSource = dataSource();
        prepareTransactionOperationTable(dataSource);
        TransactionLogicalReadExecutor readExecutor = mock(TransactionLogicalReadExecutor.class);
        JdbcMerchantPendingBalanceQueryService service = service(dataSource, readExecutor);

        assertThatThrownBy(() -> service.sumPendingBalances(" ")).isInstanceOf(ApiException.class);

        verify(readExecutor, never()).read(any());
    }

    private JdbcMerchantPendingBalanceQueryService service(
            JdbcDataSource dataSource, TransactionLogicalReadExecutor readExecutor) {
        TransactionShardingProperties properties = new TransactionShardingProperties();
        properties.setPhysicalNodes(List.of("202603", "202604"));
        return new JdbcMerchantPendingBalanceQueryService(
                new NamedParameterJdbcTemplate(dataSource), readExecutor, properties,
                Clock.fixed(Instant.parse("2026-08-20T00:00:00Z"), ZoneId.of("Asia/Shanghai")));
    }

    private JdbcDataSource dataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:merchant-pending-" + System.nanoTime()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        return dataSource;
    }

    private JdbcTemplate prepareTransactionOperationTable(JdbcDataSource dataSource) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE transaction_operation (
                    merchant_id VARCHAR(64) NOT NULL,
                    transaction_date_time TIMESTAMP NOT NULL,
                    transaction_status VARCHAR(32) NOT NULL,
                    settlement_status VARCHAR(32) NOT NULL,
                    transaction_type VARCHAR(32) NOT NULL,
                    label_currency CHAR(3) NOT NULL,
                    label_amount DECIMAL(20,6) NOT NULL,
                    deleted TINYINT NOT NULL DEFAULT 0
                )
                """);
        return jdbc;
    }

    private void insert(JdbcTemplate jdbc,
                        String merchantId,
                        String transactionType,
                        String transactionStatus,
                        String settlementStatus,
                        String currency,
                        String amount) {
        jdbc.update("""
                        INSERT INTO transaction_operation (
                            merchant_id, transaction_date_time, transaction_status, settlement_status,
                            transaction_type, label_currency, label_amount, deleted
                        ) VALUES (?, TIMESTAMP '2026-08-18 10:00:00', ?, ?, ?, ?, ?, 0)
                        """,
                merchantId, transactionStatus, settlementStatus,
                transactionType, currency, new BigDecimal(amount));
    }

    private TransactionLogicalReadExecutor executingReadExecutor() {
        TransactionLogicalReadExecutor executor = mock(TransactionLogicalReadExecutor.class);
        when(executor.read(any())).thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(0).get());
        return executor;
    }
}
