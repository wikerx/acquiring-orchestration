package com.scott.payment.admin.service.impl;

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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JdbcAdminTransactionFundQueryServiceTests
 * @date : 2026-08-19 00:00
 * @email : scott_x@163.com
 * @description : 管理端交易资金查询测试，验证副本读作用域、资金动作范围、结算状态和标签币种隔离。
 * @status : create
 */
class JdbcAdminTransactionFundQueryServiceTests {

    /** 成功且未结算的正向和逆向动作应按标签币种计算净额，其他动作和商户必须排除。 */
    @Test
    void shouldAggregateOnlySuccessfulUnsettledFundActionsByLabelCurrency() {
        JdbcDataSource dataSource = dataSource("admin-pending-balance");
        JdbcTemplate jdbc = prepareTransactionOperationTable(dataSource);
        insert(jdbc, "merchant-a", "PAYMENT", "SUCCESS", "NOT_SETTLED", "USD", "100.00", 0);
        insert(jdbc, "merchant-a", "REFUND", "SUCCESS", "NOT_SETTLED", "USD", "20.00", 0);
        insert(jdbc, "merchant-a", "CAPTURE", "SUCCESS", "NOT_SETTLED", "EUR", "50.00", 0);
        insert(jdbc, "merchant-a", "AUTHORIZATION", "SUCCESS", "NOT_SETTLED", "USD", "999.00", 0);
        insert(jdbc, "merchant-a", "VOID", "SUCCESS", "NOT_SETTLED", "USD", "999.00", 0);
        insert(jdbc, "merchant-a", "PAYMENT", "FAILED", "NOT_SETTLED", "USD", "999.00", 0);
        insert(jdbc, "merchant-a", "PAYMENT", "SUCCESS", "SETTLED", "USD", "999.00", 0);
        insert(jdbc, "merchant-a", "PAYMENT", "SUCCESS", "NOT_SETTLED", "USD", "999.00", 1);
        insert(jdbc, "merchant-b", "PAYMENT", "SUCCESS", "NOT_SETTLED", "USD", "999.00", 0);
        TransactionLogicalReadExecutor readExecutor = executingReadExecutor();
        JdbcAdminTransactionFundQueryService service = service(dataSource, readExecutor);

        var balances = service.sumPendingBalances("merchant-a");

        assertThat(balances).extracting("currency", "amount").containsExactly(
                org.assertj.core.groups.Tuple.tuple("EUR", new BigDecimal("50.000000")),
                org.assertj.core.groups.Tuple.tuple("USD", new BigDecimal("80.000000")));
        verify(readExecutor).read(any());
        verify(readExecutor, never()).readPrimary(any());
    }

    /** 历史成功资金动作即使已经结算，也必须阻止结算币种被直接修改。 */
    @Test
    void shouldDetectHistoricalSuccessfulFundActivity() {
        JdbcDataSource dataSource = dataSource("admin-fund-activity");
        JdbcTemplate jdbc = prepareTransactionOperationTable(dataSource);
        insert(jdbc, "merchant-a", "PAYMENT", "SUCCESS", "SETTLED", "USD", "100.00", 0);
        insert(jdbc, "merchant-b", "AUTHORIZATION", "SUCCESS", "NOT_SETTLED", "USD", "100.00", 0);
        JdbcAdminTransactionFundQueryService service = service(dataSource, executingReadExecutor());

        assertThat(service.hasSuccessfulFundTransaction("merchant-a")).isTrue();
        assertThat(service.hasSuccessfulFundTransaction("merchant-b")).isFalse();
    }

    private JdbcAdminTransactionFundQueryService service(
            JdbcDataSource dataSource, TransactionLogicalReadExecutor readExecutor) {
        TransactionShardingProperties properties = new TransactionShardingProperties();
        properties.setPhysicalNodes(List.of("202603", "202604"));
        return new JdbcAdminTransactionFundQueryService(
                new NamedParameterJdbcTemplate(dataSource), readExecutor, properties,
                Clock.fixed(Instant.parse("2026-08-20T00:00:00Z"), ZoneId.of("Asia/Shanghai")));
    }

    private JdbcDataSource dataSource(String databaseName) {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + databaseName + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
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
                    deleted TINYINT NOT NULL
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
                        String amount,
                        int deleted) {
        jdbc.update("""
                        INSERT INTO transaction_operation (
                            merchant_id, transaction_date_time, transaction_status, settlement_status,
                            transaction_type, label_currency, label_amount, deleted
                        ) VALUES (?, TIMESTAMP '2026-08-18 10:00:00', ?, ?, ?, ?, ?, ?)
                        """,
                merchantId, transactionStatus, settlementStatus,
                transactionType, currency, new BigDecimal(amount), deleted);
    }

    private TransactionLogicalReadExecutor executingReadExecutor() {
        TransactionLogicalReadExecutor executor = mock(TransactionLogicalReadExecutor.class);
        when(executor.read(any())).thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(0).get());
        return executor;
    }
}
