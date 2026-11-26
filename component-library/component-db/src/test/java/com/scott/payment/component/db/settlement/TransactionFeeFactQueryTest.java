package com.scott.payment.component.db.settlement;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionFeeFactQueryTest {
    @Test
    void readsOnlyTheAuthorizedActionAndKeepsItsStoredDecimalPrecision() {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:fee_facts_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE transaction_clearing_detail (
                    id INT, line_no INT, transaction_id VARCHAR(32), transaction_date_time TIMESTAMP,
                    merchant_id VARCHAR(32), clearing_revision INT, record_status VARCHAR(16),
                    fee_group_no VARCHAR(32), clearing_detail_no VARCHAR(32), item_type VARCHAR(32),
                    fee_category VARCHAR(32), risk_service_type VARCHAR(32), component_type VARCHAR(32),
                    direction VARCHAR(8), currency VARCHAR(3), currency_exponent INT, amount DECIMAL(24,8),
                    basis_currency VARCHAR(3), basis_amount DECIMAL(24,8), percentage_rate DECIMAL(12,8),
                    minimum_amount_usd DECIMAL(24,8), maximum_amount_usd DECIMAL(24,8),
                    limit_evaluation_status VARCHAR(32), applied_limit VARCHAR(16))
                """);
        jdbc.execute("""
                CREATE TABLE settlement_candidate (
                    id INT, source_type VARCHAR(32), source_revision INT, source_transaction_id VARCHAR(32),
                    source_transaction_date_time TIMESTAMP, merchant_id VARCHAR(32), settlement_batch_no VARCHAR(32))
                """);
        jdbc.execute("""
                CREATE TABLE settlement_batch (
                    settlement_batch_no VARCHAR(32), merchant_id VARCHAR(32), batch_type VARCHAR(16), batch_status VARCHAR(16))
                """);
        jdbc.execute("CREATE TABLE settlement_batch_rate (id INT, direct_rate DECIMAL(24,12))");
        jdbc.execute("""
                CREATE TABLE settlement_result_item (
                    id INT, result_line_no INT, candidate_id INT, settlement_batch_no VARCHAR(32), merchant_id VARCHAR(32),
                    source_transaction_id VARCHAR(32), source_transaction_date_time TIMESTAMP, source_detail_type VARCHAR(32),
                    settlement_batch_rate_id INT, result_item_type VARCHAR(32), fee_group_no VARCHAR(32),
                    source_detail_no VARCHAR(32), result_role VARCHAR(32), fee_category VARCHAR(32), direction VARCHAR(8),
                    source_currency VARCHAR(3), source_amount DECIMAL(24,8), target_currency VARCHAR(3),
                    target_currency_exponent INT, unrounded_target_amount DECIMAL(48,20), target_amount DECIMAL(24,8),
                    applied_limit VARCHAR(16))
                """);
        jdbc.execute("""
                CREATE TABLE transaction_locator (
                    transaction_id VARCHAR(32), merchant_id VARCHAR(32), transaction_date_time TIMESTAMP, transaction_type VARCHAR(32))
                """);
        LocalDateTime time = LocalDateTime.of(2026, 11, 24, 16, 9, 38, 40_000_000);
        jdbc.update("""
                INSERT INTO transaction_clearing_detail
                (id, line_no, transaction_id, transaction_date_time, merchant_id, clearing_revision, record_status,
                 fee_group_no, clearing_detail_no, item_type, fee_category, component_type, direction,
                 currency, currency_exponent, amount)
                VALUES (1,1,'finish',?,'merchant-a',1,'ACTIVE','fee1','line1','PLATFORM_FEE','SETTLEMENT_FX_FEE',
                 'PERCENTAGE','DEBIT','CAD',2,4.85000000),
                       (2,1,'finish',?,'merchant-a',2,'ACTIVE','fee2','line2','PLATFORM_FEE','SETTLEMENT_FX_FEE',
                 'PERCENTAGE','DEBIT','CAD',2,9.99000000)
                """, time, time);
        jdbc.update("INSERT INTO settlement_candidate VALUES (1,'CLEARING_REVISION',1,'finish',?,'merchant-a','batch1')", time);
        jdbc.update("INSERT INTO settlement_batch VALUES ('batch1','merchant-a','REGULAR','POSTED')");
        jdbc.update("INSERT INTO settlement_batch_rate VALUES (1,0.702728520000)");
        jdbc.update("""
                INSERT INTO settlement_result_item VALUES
                (1,1,1,'batch1','merchant-a','finish',?,'TRANSACTION_CLEARING',1,'FEE_GROUP_FINAL','fee1','line1',
                 'FINANCIAL_COMPONENT','SETTLEMENT_FX_FEE','DEBIT','USD',3.61000000,'USD',2,3.60823332212345678901,
                 3.61000000,'NONE')
                """, time);
        jdbc.update("INSERT INTO transaction_locator VALUES ('source','merchant-a',?,'PRE_AUTHORIZATION')", time.minusSeconds(1));
        var named = new NamedParameterJdbcTemplate(dataSource);

        var facts = TransactionFeeFactQuery.read(named, "merchant-a", "finish", time, 1, "source", 100);
        assertEquals("4.85000000", facts.clearing().get(0).amount());
        assertEquals(1, facts.clearing().size());
        assertEquals("3.60823332212345678901", facts.settlement().get(0).unroundedAmount());
        assertEquals("3.61000000", facts.settlement().get(0).amount());
        assertEquals(time.minusSeconds(1), facts.sourceAction().transactionDateTime());
        assertTrue(facts.complete());

        var outsideScope = TransactionFeeFactQuery.read(named, "merchant-b", "finish", time, 1, "source", 100);
        assertTrue(outsideScope.clearing().isEmpty());
        assertTrue(outsideScope.settlement().isEmpty());
        assertEquals(null, outsideScope.sourceAction());
    }
}
