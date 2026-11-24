package com.scott.payment.admin.service.impl;

import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.BatchSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.BatchSummary;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.OperationalState;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ResultSummaryLine;
import com.scott.payment.admin.service.AdminMerchantDataScope;
import com.scott.payment.component.db.sharding.TransactionLogicalReadExecutor;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import com.scott.payment.component.core.exception.ServiceException;
import org.apache.shardingsphere.driver.api.ShardingSphereDataSourceFactory;
import org.apache.shardingsphere.driver.jdbc.core.datasource.ShardingSphereDataSource;
import org.apache.shardingsphere.single.config.SingleRuleConfiguration;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 验证 Admin 结算本地查询的标准分页、稳定排序、数据范围和普通读路由
 */
class JdbcAdminSettlementQueryServiceTests {

    @Test
    @SuppressWarnings("unchecked")
    void searchShouldUseStandardPaginationAndNewestBusinessBatchOrder() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        TransactionLogicalReadExecutor readExecutor = executingReadExecutor();
        when(jdbcTemplate.queryForObject(
                anyString(), any(MapSqlParameterSource.class), eq(Long.class))).thenReturn(11L);
        BatchSummary row = new BatchSummary();
        row.setId(1L);
        row.setSettlementBatchNo("SB20260826-00000001");
        row.setBusinessDate(LocalDate.of(2026, 8, 26));
        row.setDailySequence(1);
        when(jdbcTemplate.query(
                anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(row));
        JdbcAdminSettlementQueryService service = new JdbcAdminSettlementQueryService(
                jdbcTemplate, readExecutor, new TransactionShardingProperties());
        BatchSearchRequest request = new BatchSearchRequest();
        request.setBeginBusinessDate(LocalDate.of(2026, 8, 1));
        request.setEndBusinessDate(LocalDate.of(2026, 8, 31));
        request.setPageNo(2);
        request.setPageSize(10);

        var page = service.search(request, AdminMerchantDataScope.all());

        assertThat(page.getTotal()).isEqualTo(11L);
        assertThat(page.getPageNo()).isEqualTo(2L);
        assertThat(page.getRecords()).singleElement()
                .extracting(BatchSummary::getDisplayBatchNo)
                .isEqualTo("2026-08-26 00000001");
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> parameters =
                ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).query(sql.capture(), parameters.capture(), any(RowMapper.class));
        assertThat(sql.getValue()).contains(
                "FROM settlement_batch",
                "AS merchant_name",
                "AS settlement_account_no",
                "COUNT(DISTINCT item.source_transaction_id)",
                "AS transaction_count",
                "item.result_role = 'LEDGER_POSTING'",
                "AS net_direction",
                "AS net_amount",
                "ORDER BY business_date DESC, id DESC",
                "LIMIT :offset, :limit");
        assertThat(parameters.getValue().getValue("offset")).isEqualTo(10L);
        verify(readExecutor).read(any());
        verify(readExecutor, never()).readPrimary(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchShouldApplyCustomMerchantScopeInCountAndPageQueries() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForObject(
                anyString(), any(MapSqlParameterSource.class), eq(Long.class))).thenReturn(1L);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());
        JdbcAdminSettlementQueryService service = new JdbcAdminSettlementQueryService(
                jdbcTemplate, executingReadExecutor(), new TransactionShardingProperties());
        BatchSearchRequest request = request();

        service.search(request, AdminMerchantDataScope.limited(java.util.Set.of("M1001", "M1002")));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> parameters =
                ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).queryForObject(sql.capture(), parameters.capture(), eq(Long.class));
        assertThat(sql.getValue()).contains("merchant_id IN (:permittedMerchantIds)");
        assertThat(parameters.getValue().getValue("permittedMerchantIds"))
                .isEqualTo(java.util.Set.of("M1001", "M1002"));
    }

    @Test
    void transactionDomainShouldUseServerControlledRegularPredicate() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForObject(
                anyString(), any(MapSqlParameterSource.class), eq(Long.class))).thenReturn(0L);
        JdbcAdminSettlementQueryService service = new JdbcAdminSettlementQueryService(
                jdbcTemplate, executingReadExecutor(), new TransactionShardingProperties());
        BatchSearchRequest request = request();
        request.setBatchDomain("transaction");

        service.search(request, AdminMerchantDataScope.all());

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> parameters =
                ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).queryForObject(sql.capture(), parameters.capture(), eq(Long.class));
        assertThat(sql.getValue()).contains("batch_type = :transactionBatchType");
        assertThat(parameters.getValue().getValue("transactionBatchType")).isEqualTo("REGULAR");
    }

    @Test
    void reserveDomainShouldUseReleaseAdjustmentPredicateAndOptionalExactType() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForObject(
                anyString(), any(MapSqlParameterSource.class), eq(Long.class))).thenReturn(0L);
        JdbcAdminSettlementQueryService service = new JdbcAdminSettlementQueryService(
                jdbcTemplate, executingReadExecutor(), new TransactionShardingProperties());
        BatchSearchRequest request = request();
        request.setBatchDomain("RESERVE");
        request.setBatchType("adjustment");

        service.search(request, AdminMerchantDataScope.all());

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> parameters =
                ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).queryForObject(sql.capture(), parameters.capture(), eq(Long.class));
        assertThat(sql.getValue()).contains(
                "batch_type IN (:reserveBatchTypes)",
                "batch_type = :batchType");
        assertThat(parameters.getValue().getValue("reserveBatchTypes"))
                .isEqualTo(List.of("RESERVE_RELEASE", "ADJUSTMENT"));
        assertThat(parameters.getValue().getValue("batchType")).isEqualTo("ADJUSTMENT");
    }

    @Test
    void batchDomainShouldRejectIncompatibleBatchTypeBeforeDatabaseQuery() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        JdbcAdminSettlementQueryService service = new JdbcAdminSettlementQueryService(
                jdbcTemplate, executingReadExecutor(), new TransactionShardingProperties());
        BatchSearchRequest request = request();
        request.setBatchDomain("TRANSACTION");
        request.setBatchType("RESERVE_RELEASE");

        assertThatThrownBy(() -> service.search(request, AdminMerchantDataScope.all()))
                .isInstanceOf(ServiceException.class);
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void emptyScopeShouldReturnEmptyPageWithoutDatabaseQuery() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        JdbcAdminSettlementQueryService service = new JdbcAdminSettlementQueryService(
                jdbcTemplate, executingReadExecutor(), new TransactionShardingProperties());

        var page = service.search(request(), AdminMerchantDataScope.limited(java.util.Set.of()));

        assertThat(page.getTotal()).isZero();
        assertThat(page.getRecords()).isEmpty();
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void detailShouldNotLoadAllResultSummaries() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        BatchSummary batch = new BatchSummary();
        batch.setSettlementBatchNo("SB20260831-00000001");
        batch.setBusinessDate(LocalDate.of(2026, 8, 31));
        batch.setDailySequence(1);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> invocation.<String>getArgument(0).contains("FROM settlement_batch\n")
                        ? List.of(batch) : List.of());
        when(jdbcTemplate.queryForObject(
                anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(new OperationalState());

        new JdbcAdminSettlementQueryService(jdbcTemplate, executingReadExecutor(),
                new TransactionShardingProperties())
                .detail("SB20260831-00000001", AdminMerchantDataScope.all());

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(3)).query(
                sql.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));
        assertThat(sql.getAllValues()).noneSatisfy(detailSql -> assertThat(detailSql)
                .contains("FROM settlement_result_summary summary"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void voucherShouldLoadCompleteResultSummaryAfterApplyingMerchantScope() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        BatchSummary batch = new BatchSummary();
        batch.setSettlementBatchNo("SB20260831-00000001");
        batch.setMerchantId("M1001");
        batch.setBusinessDate(LocalDate.of(2026, 8, 31));
        batch.setDailySequence(1);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0);
                    if (sql.contains("FROM settlement_batch\n")) return List.of(batch);
                    if (sql.contains("FROM settlement_result_summary summary")) {
                        return List.of(new ResultSummaryLine());
                    }
                    return List.of();
                });
        when(jdbcTemplate.queryForObject(
                anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(new OperationalState());

        var detail = new JdbcAdminSettlementQueryService(jdbcTemplate, executingReadExecutor(),
                new TransactionShardingProperties())
                .voucherDetail("SB20260831-00000001",
                        AdminMerchantDataScope.limited(java.util.Set.of("M1001")));

        assertThat(detail.getResultSummaries()).hasSize(1);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> parameters =
                ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate, times(4)).query(
                sql.capture(), parameters.capture(), any(RowMapper.class));
        assertThat(sql.getAllValues()).anySatisfy(summarySql -> assertThat(summarySql).contains(
                "FROM settlement_result_summary summary",
                "batch.merchant_id IN (:permittedMerchantIds)",
                "ORDER BY summary.merchant_id, summary.payment_type"));
        assertThat(parameters.getAllValues()).anySatisfy(value -> assertThat(
                value.getValue("permittedMerchantIds")).isEqualTo(java.util.Set.of("M1001")));
    }

    @Test
    @SuppressWarnings("unchecked")
    void reserveVoucherAndSummaryPageShouldSplitConservedAmountsByTransactionType() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:reserve_voucher;MODE=MySQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate database = new JdbcTemplate(dataSource);
        database.execute("""
                CREATE TABLE settlement_batch (settlement_batch_no VARCHAR, merchant_id VARCHAR,
                    target_currency_exponent INT, batch_type VARCHAR);
                CREATE TABLE base_iso_currency (alpha3_code VARCHAR, fraction_digits INT, deleted INT);
                CREATE TABLE settlement_result_summary (id BIGINT, settlement_batch_no VARCHAR, merchant_id VARCHAR,
                    payment_type VARCHAR, payment_method VARCHAR, transaction_type VARCHAR, result_item_type VARCHAR,
                    fee_category VARCHAR, direction VARCHAR, source_currency VARCHAR, target_currency VARCHAR,
                    transaction_count BIGINT, source_amount DECIMAL, target_amount DECIMAL);
                CREATE TABLE settlement_result_item (settlement_batch_no VARCHAR, merchant_id VARCHAR, candidate_id BIGINT,
                    source_detail_type VARCHAR, source_detail_no VARCHAR, result_role VARCHAR, payment_type VARCHAR,
                    payment_method VARCHAR, transaction_type VARCHAR, result_item_type VARCHAR, fee_category VARCHAR,
                    direction VARCHAR, source_currency VARCHAR, target_currency VARCHAR, source_amount DECIMAL, target_amount DECIMAL);
                CREATE TABLE merchant_reserve_action (settlement_batch_no VARCHAR, candidate_id BIGINT,
                    source_reserve_detail_no VARCHAR, action_type VARCHAR, reserve_item_id BIGINT, reserve_no VARCHAR);
                CREATE TABLE merchant_reserve_item (id BIGINT, merchant_id VARCHAR, source_transaction_id VARCHAR, reserve_no VARCHAR);
                CREATE TABLE transaction_locator (transaction_id VARCHAR, merchant_id VARCHAR, transaction_type VARCHAR);
                INSERT INTO settlement_batch VALUES
                    ('SB20260831-00000001', 'M1001', 2, 'RESERVE_RELEASE'),
                    ('SB20260831-00000002', 'M1001', 2, 'REGULAR'),
                    ('SB20260831-00000003', 'M1001', 2, 'ADJUSTMENT');
                INSERT INTO base_iso_currency VALUES ('USD', 2, 0);
                INSERT INTO settlement_result_summary VALUES
                    (1,'SB20260831-00000001','M1001','BANK_CARD','VISA','RESERVE_RELEASE','RESERVE_RELEASE','NONE','CREDIT','USD','USD',2,42,42),
                    (2,'SB20260831-00000001','M1001','BANK_CARD','JCB','RESERVE_RELEASE','RESERVE_RELEASE','NONE','CREDIT','USD','USD',2,19,19),
                    (3,'SB20260831-00000002','M1001','BANK_CARD','JCB','PAYMENT','PRINCIPAL','NONE','CREDIT','USD','USD',1,60,60),
                    (4,'SB20260831-00000001','M1001','BANK_CARD','AMEX','RESERVE_RELEASE','RESERVE_RELEASE','NONE','CREDIT','USD','USD',1,5,5),
                    (5,'SB20260831-00000003','M1001','BANK_CARD','VISA','ADJUSTMENT','ADJUSTMENT','NONE','DEBIT','USD','USD',1,6,6);
                INSERT INTO settlement_result_item VALUES
                    ('SB20260831-00000001','M1001',11,'RESERVE_CLEARING','RD11','FINANCIAL_COMPONENT','BANK_CARD','VISA','RESERVE_RELEASE','RESERVE_RELEASE',NULL,'CREDIT','USD','USD',22,22),
                    ('SB20260831-00000001','M1001',12,'RESERVE_CLEARING','RD12','FINANCIAL_COMPONENT','BANK_CARD','VISA','RESERVE_RELEASE','RESERVE_RELEASE',NULL,'CREDIT','USD','USD',20,20),
                    ('SB20260831-00000001','M1001',21,'RESERVE_CLEARING','RD21','FINANCIAL_COMPONENT','BANK_CARD','JCB','RESERVE_RELEASE','RESERVE_RELEASE',NULL,'CREDIT','USD','USD',10,10),
                    ('SB20260831-00000001','M1001',22,'RESERVE_CLEARING','RD22','FINANCIAL_COMPONENT','BANK_CARD','JCB','RESERVE_RELEASE','RESERVE_RELEASE',NULL,'CREDIT','USD','USD',9,9),
                    ('SB20260831-00000001','M1001',31,'RESERVE_CLEARING','RD31','FINANCIAL_COMPONENT','BANK_CARD','AMEX','RESERVE_RELEASE','RESERVE_RELEASE',NULL,'CREDIT','USD','USD',4,4),
                    ('SB20260831-00000003','M1001',41,'RESERVE_CLEARING','RD41','FINANCIAL_COMPONENT','BANK_CARD','VISA','ADJUSTMENT','ADJUSTMENT',NULL,'DEBIT','USD','USD',6,6);
                INSERT INTO merchant_reserve_action VALUES
                    ('SB20260831-00000001',11,'RD11','RELEASE',11,'R11'), ('SB20260831-00000001',12,'RD12','RELEASE',12,'R12'),
                    ('SB20260831-00000001',21,'RD21','RELEASE',21,'R21'), ('SB20260831-00000001',22,'RD22','RELEASE',22,'R22'),
                    ('SB20260831-00000001',31,'RD31','RELEASE',31,'R31'),
                    ('SB20260831-00000003',41,'RD41','ADJUSTMENT',11,'R11');
                INSERT INTO merchant_reserve_item VALUES (11,'M1001','TX11','R11'), (12,'M1001','TX12','R12'),
                    (21,'M1001','TX21','R21'), (22,'M1001','TX22','R22'), (31,'M1001','TX31','R31');
                INSERT INTO transaction_locator VALUES ('TX11','M1001','CAPTURE'), ('TX12','M1001','PAYMENT'),
                    ('TX21','M1001','PAYMENT'), ('TX22','M1002','PAYMENT'), ('TX31','M1001','PAYMENT');
                """);
        try (var logicalDataSource = (ShardingSphereDataSource) ShardingSphereDataSourceFactory.createDataSource(
                Map.of("master", dataSource), List.of(new SingleRuleConfiguration(List.of("master.*"), "master")),
                new Properties())) {
            var realJdbc = new NamedParameterJdbcTemplate(logicalDataSource);
            NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
            BatchSummary batch = new BatchSummary();
            batch.setSettlementBatchNo("SB20260831-00000001");
            batch.setMerchantId("M1001");
            batch.setBatchType("RESERVE_RELEASE");
            batch.setBusinessDate(LocalDate.of(2026, 8, 31));
            batch.setDailySequence(1);
            when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                    .thenAnswer(invocation -> {
                        String sql = invocation.getArgument(0);
                        if (sql.contains("FROM settlement_batch\n")) return List.of(batch);
                        if (sql.contains("FROM settlement_result_summary summary")) {
                            return realJdbc.query(sql, invocation.<MapSqlParameterSource>getArgument(1),
                                    invocation.<RowMapper<ResultSummaryLine>>getArgument(2));
                        }
                        return List.of();
                    });
            when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                    .thenAnswer(invocation -> realJdbc.queryForObject(invocation.<String>getArgument(0),
                            invocation.<MapSqlParameterSource>getArgument(1), Long.class));

            var service = new JdbcAdminSettlementQueryService(jdbcTemplate, executingReadExecutor(),
                    new TransactionShardingProperties());
            var scope = AdminMerchantDataScope.limited(Set.of(batch.getMerchantId()));
            var detail = service.voucherDetail(batch.getSettlementBatchNo(), scope);

            assertThat(detail.getResultSummaries()).satisfiesExactly(drift -> {
                assertThat(drift.getPaymentMethod()).isEqualTo("AMEX");
                assertThat(drift.getTransactionType()).isNull();
                assertThat(drift.getTargetAmount()).isEqualByComparingTo("5");
            }, unknown -> {
                assertThat(unknown.getPaymentMethod()).isEqualTo("JCB");
                assertThat(unknown.getOriginalTransactionTypes()).isEmpty();
                assertThat(unknown.getTransactionType()).isNull();
                assertThat(unknown.getTargetAmount()).isEqualByComparingTo("9");
            }, known -> {
                assertThat(known.getPaymentMethod()).isEqualTo("JCB");
                assertThat(known.getTransactionType()).isEqualTo("PAYMENT");
                assertThat(known.getTargetAmount()).isEqualByComparingTo("10");
            }, capture -> {
                assertThat(capture.getPaymentMethod()).isEqualTo("VISA");
                assertThat(capture.getOriginalTransactionTypes()).containsExactly("CAPTURE");
                assertThat(capture.getTransactionType()).isEqualTo("CAPTURE");
                assertThat(capture.getResultItemType()).isEqualTo("RESERVE_RELEASE");
                assertThat(capture.getTransactionCount()).isEqualTo(1L);
                assertThat(capture.getSourceAmount()).isEqualByComparingTo("22");
                assertThat(capture.getTargetAmount()).isEqualByComparingTo("22");
            }, payment -> {
                assertThat(payment.getPaymentMethod()).isEqualTo("VISA");
                assertThat(payment.getTransactionType()).isEqualTo("PAYMENT");
                assertThat(payment.getTransactionCount()).isEqualTo(1L);
                assertThat(payment.getTargetAmount()).isEqualByComparingTo("20");
            });
            var releasePage = service.searchResultSummaries(batch.getSettlementBatchNo(), 2, 2, scope);
            assertThat(releasePage.getTotal()).isEqualTo(5L);
            assertThat(releasePage.getRecords()).usingRecursiveFieldByFieldElementComparator()
                    .containsExactlyElementsOf(detail.getResultSummaries().subList(2, 4));
            var regularPage = service.searchResultSummaries("SB20260831-00000002", 1, 10, scope);
            assertThat(regularPage.getRecords()).singleElement().satisfies(row -> {
                assertThat(row.getTransactionType()).isEqualTo("PAYMENT");
                assertThat(row.getOriginalTransactionTypes()).isNull();
                assertThat(row.getTargetAmount()).isEqualByComparingTo("60");
            });
            var adjustmentPage = service.searchResultSummaries("SB20260831-00000003", 1, 10, scope);
            assertThat(adjustmentPage.getRecords()).singleElement().satisfies(row -> {
                assertThat(row.getTransactionType()).isEqualTo("CAPTURE");
                assertThat(row.getResultItemType()).isEqualTo("ADJUSTMENT");
                assertThat(row.getDirection()).isEqualTo("DEBIT");
                assertThat(row.getTargetAmount()).isEqualByComparingTo("6");
            });
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void resultSummaryShouldUseStablePaginationAndAdminMerchantScope() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForObject(
                anyString(), any(MapSqlParameterSource.class), eq(Long.class))).thenReturn(25L);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(new ResultSummaryLine()));
        JdbcAdminSettlementQueryService service = new JdbcAdminSettlementQueryService(
                jdbcTemplate, executingReadExecutor(), new TransactionShardingProperties());

        var page = service.searchResultSummaries(
                "SB20260831-00000001", 2, 20,
                AdminMerchantDataScope.limited(java.util.Set.of("M1001", "M1002")));

        assertThat(page.getTotal()).isEqualTo(25L);
        assertThat(page.getPageNo()).isEqualTo(2L);
        assertThat(page.getPageSize()).isEqualTo(20L);
        assertThat(page.getRecords()).hasSize(1);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> parameters =
                ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).query(sql.capture(), parameters.capture(), any(RowMapper.class));
        assertThat(sql.getValue()).contains(
                "FROM settlement_result_summary summary",
                "batch.merchant_id IN (:permittedMerchantIds)",
                "AS source_currency_exponent",
                "summary.target_currency_exponent",
                "ORDER BY summary.merchant_id, summary.payment_type, summary.payment_method",
                "summary.target_currency, summary.id",
                "LIMIT 20, 20");
        assertThat(parameters.getValue().getValue("permittedMerchantIds"))
                .isEqualTo(java.util.Set.of("M1001", "M1002"));
    }

    @Test
    void accessibleBatchWithoutSummariesShouldReturnEmptyPage() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForObject(
                anyString(), any(MapSqlParameterSource.class), eq(Long.class))).thenReturn(0L);
        when(jdbcTemplate.queryForList(
                anyString(), any(MapSqlParameterSource.class), eq(String.class)))
                .thenReturn(List.of("M1001"));
        JdbcAdminSettlementQueryService service = new JdbcAdminSettlementQueryService(
                jdbcTemplate, executingReadExecutor(), new TransactionShardingProperties());

        var page = service.searchResultSummaries(
                "SB20260831-00000001", 1, 20,
                AdminMerchantDataScope.limited(java.util.Set.of("M1001")));

        assertThat(page.getTotal()).isZero();
        assertThat(page.getRecords()).isEmpty();
        verify(jdbcTemplate).queryForList(
                anyString(), any(MapSqlParameterSource.class), eq(String.class));
    }

    private BatchSearchRequest request() {
        BatchSearchRequest request = new BatchSearchRequest();
        request.setBeginBusinessDate(LocalDate.of(2026, 8, 1));
        request.setEndBusinessDate(LocalDate.of(2026, 8, 31));
        return request;
    }

    private TransactionLogicalReadExecutor executingReadExecutor() {
        TransactionLogicalReadExecutor executor = mock(TransactionLogicalReadExecutor.class);
        when(executor.read(any())).thenAnswer(invocation ->
                invocation.<Supplier<?>>getArgument(0).get());
        return executor;
    }
}
