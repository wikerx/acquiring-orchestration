package com.scott.payment.admin.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.ChannelInteractionLogResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.MerchantNotificationDeliveryLogResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.MerchantNotificationDetailResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.MerchantNotificationQuery;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.MerchantNotificationResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionOperationResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionAmountChangeResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionDetailResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionOperationSummaryResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionOrderSearchResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionOrderResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionPageQuery;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionStatusHistoryResponse;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.db.sharding.TransactionLogicalReadExecutor;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import com.scott.payment.component.core.exception.TransactionDataUnavailableException;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.core.openapi.result.OpenApiResultCode;
import com.scott.payment.admin.service.AdminRiskTimelineQueryService;
import com.scott.payment.admin.service.AdminMerchantDataScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 管理后台交易 JDBC 查询服务单元测试，验证交易详情聚合日志查询按表结构生成 SQL，避免无软删字段日志表拼接 deleted 条件。
 */
class JdbcAdminTransactionQueryServiceTest {

    /**
     * 异步交易主单导出必须把有限商户集合落实为 SQL IN 条件。
     */
    @Test
    void orderExportShouldApplyFrozenMerchantScopeInSql() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(0L);
        JdbcAdminTransactionQueryService service = buildService(jdbcTemplate);
        TransactionPageQuery query = new TransactionPageQuery();
        query.setBeginTime(LocalDateTime.now().minusHours(1));
        query.setEndTime(LocalDateTime.now());

        service.pageOrdersForExport(query, AdminMerchantDataScope.limited(Set.of("M1001", "M1002")));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).queryForObject(sql.capture(), params.capture(), eq(Long.class));
        assertThat(sql.getValue()).contains("merchant_id IN (:scopeMerchantIds)");
        assertThat(params.getValue().getValue("scopeMerchantIds"))
                .isEqualTo(Set.of("M1001", "M1002"));
    }

    /**
     * 显式商户号不在冻结范围时必须返回空页且不访问数据库。
     */
    @Test
    void orderExportShouldRejectMerchantOutsideFrozenScopeBeforeSql() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        JdbcAdminTransactionQueryService service = buildService(jdbcTemplate);
        TransactionPageQuery query = new TransactionPageQuery();
        query.setMerchantId("M-OUTSIDE");
        query.setBeginTime(LocalDateTime.now().minusHours(1));
        query.setEndTime(LocalDateTime.now());

        PageResult<TransactionOrderResponse> result = service.pageOrdersForExport(
                query, AdminMerchantDataScope.limited(Set.of("M1001")));

        assertThat(result.getTotal()).isZero();
        verify(jdbcTemplate, never()).queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class));
    }

    /**
     * 异步交易操作导出必须把有限商户集合落实为带操作表别名的 SQL IN 条件。
     */
    @Test
    void operationExportShouldApplyFrozenMerchantScopeInSql() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(0L);
        JdbcAdminTransactionQueryService service = buildService(jdbcTemplate);
        TransactionPageQuery query = new TransactionPageQuery();
        query.setBeginTime(LocalDateTime.now().minusHours(1));
        query.setEndTime(LocalDateTime.now());

        service.pageOperationsForExport(query, AdminMerchantDataScope.limited(Set.of("M2001", "M2002")));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).queryForObject(sql.capture(), params.capture(), eq(Long.class));
        assertThat(sql.getValue()).contains("o.merchant_id IN (:scopeMerchantIds)");
        assertThat(params.getValue().getValue("scopeMerchantIds"))
                .isEqualTo(Set.of("M2001", "M2002"));
    }

    /**
     * 异步商户通知导出必须在通知逻辑表查询中落实冻结商户集合。
     */
    @Test
    void merchantNotificationExportShouldApplyFrozenMerchantScopeInSql() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(0L);
        JdbcAdminTransactionQueryService service = buildService(jdbcTemplate);
        MerchantNotificationQuery query = new MerchantNotificationQuery();
        query.setBeginTime(LocalDateTime.now().minusHours(1));
        query.setEndTime(LocalDateTime.now());

        service.pageMerchantNotificationsForExport(
                query, AdminMerchantDataScope.limited(Set.of("M3001")));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).queryForObject(sql.capture(), params.capture(), eq(Long.class));
        assertThat(sql.getValue()).contains("merchant_id IN (:scopeMerchantIds)");
        assertThat(params.getValue().getValue("scopeMerchantIds")).isEqualTo(Set.of("M3001"));
    }

    /**
     * 商户通知显式商户号不在冻结范围时必须返回空页且不访问数据库。
     */
    @Test
    void merchantNotificationExportShouldRejectMerchantOutsideFrozenScopeBeforeSql() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        JdbcAdminTransactionQueryService service = buildService(jdbcTemplate);
        MerchantNotificationQuery query = new MerchantNotificationQuery();
        query.setMerchantId("M-OUTSIDE");
        query.setBeginTime(LocalDateTime.now().minusHours(1));
        query.setEndTime(LocalDateTime.now());

        PageResult<MerchantNotificationResponse> result = service.pageMerchantNotificationsForExport(
                query, AdminMerchantDataScope.limited(Set.of("M3001")));

        assertThat(result.getTotal()).isZero();
        verify(jdbcTemplate, never()).queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class));
    }

    /**
     * F 类描述故障来源而非交易终态，后台筛选不得据此将 PROCESSING/PENDING 误判为 FAILED。
     */
    @Test
    void merchantResponseCodeFilterShouldNotInferStatusFromFailureOriginCode() {
        JdbcAdminTransactionQueryService service = buildService(mock(NamedParameterJdbcTemplate.class));

        assertThat((String) ReflectionTestUtils.invokeMethod(
                service, "resolveStatusByMerchantResponseCode", "F1000")).isNull();
        assertThat((String) ReflectionTestUtils.invokeMethod(
                service, "resolveStatusByMerchantResponseCode", "F2000")).isNull();
        assertThat((String) ReflectionTestUtils.invokeMethod(
                service, "resolveStatusByMerchantResponseCode", "F3000")).isNull();
        assertThat((String) ReflectionTestUtils.invokeMethod(
                service, "resolveStatusByMerchantResponseCode", "T0003")).isEqualTo("FAILED");
    }

    @Test
    void historicalPaymentShouldShowAuthorizationAndCaptureWithoutDuplicatingPersistedLog() {
        TransactionOrderResponse order = order("operation-a", LocalDateTime.of(2026, 9, 27, 10, 0));
        order.setRootTransactionId("transaction-a");
        TransactionOperationResponse operation = operation("operation-a", "transaction-a", order.getTransactionDateTime());
        operation.setTransactionType("PAYMENT");
        operation.setTransactionStatus("SUCCESS");
        operation.setLabelCurrency("EUR");
        operation.setLabelAmount(new BigDecimal("28.20"));
        operation.setTransactionCurrency("HKD");
        operation.setTransactionAmount(new BigDecimal("31.02"));
        operation.setOperationTime(order.getTransactionDateTime());
        LocalDateTime successTime = order.getTransactionDateTime().plusSeconds(2);
        TransactionStatusHistoryResponse successHistory = new TransactionStatusHistoryResponse();
        successHistory.setTransactionId("transaction-a");
        successHistory.setStatusObject("OPERATION");
        successHistory.setToStatus("SUCCESS");
        successHistory.setStatusTime(successTime);

        List<TransactionAmountChangeResponse> changes = JdbcAdminTransactionQueryService.withInitialAmountChange(
                order, List.of(operation), List.of(successHistory), List.of());

        assertThat(changes).hasSize(1);
        assertThat(changes.get(0).getChangeSource()).isEqualTo("INITIAL_OPERATION");
        assertThat(changes.get(0).getChangeTime()).isEqualTo(successTime);
        assertThat(changes.get(0).getLabelAmountCurrency()).isEqualTo("EUR");
        assertThat(changes.get(0).getLabelChangeAmount()).isEqualByComparingTo("28.20");
        assertThat(changes.get(0).getLabelAuthorizedAfter()).isEqualByComparingTo("28.20");
        assertThat(changes.get(0).getLabelCapturedAfter()).isEqualByComparingTo("28.20");
        assertThat(changes.get(0).getLabelAvailableCaptureAfter()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(changes.get(0).getLabelAvailableRefundAfter()).isEqualByComparingTo("28.20");
        assertThat(changes.get(0).getAuthorizedAfter()).isEqualByComparingTo("31.02");
        assertThat(changes.get(0).getCapturedAfter()).isEqualByComparingTo("31.02");
        assertThat(changes.get(0).getAvailableCaptureAfter()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(changes.get(0).getAvailableRefundAfter()).isEqualByComparingTo("31.02");
        assertThat(JdbcAdminTransactionQueryService.withInitialAmountChange(
                order, List.of(operation), List.of(successHistory), changes)).hasSize(1);
        TransactionAmountChangeResponse refund = new TransactionAmountChangeResponse();
        refund.setTransactionId("refund-a");
        refund.setChangeType("REFUND");
        assertThat(JdbcAdminTransactionQueryService.withInitialAmountChange(
                order, List.of(operation), List.of(successHistory), List.of(refund)))
                .extracting(TransactionAmountChangeResponse::getChangeType)
                .containsExactly("PAYMENT", "REFUND");
    }

    @Test
    void historicalAuthorizationShouldRemainCapturableAndFailedInitialActionShouldNotAppear() {
        TransactionOrderResponse order = order("operation-a", LocalDateTime.of(2026, 9, 27, 10, 0));
        order.setRootTransactionId("transaction-a");
        TransactionOperationResponse operation = operation("operation-a", "transaction-a", order.getTransactionDateTime());
        operation.setTransactionType("AUTHORIZATION");
        operation.setTransactionStatus("SUCCESS");
        operation.setTransactionAmount(new BigDecimal("16.56"));

        List<TransactionAmountChangeResponse> changes = JdbcAdminTransactionQueryService.withInitialAmountChange(
                order, List.of(operation), List.of(), List.of());

        assertThat(changes.get(0).getAuthorizedAfter()).isEqualByComparingTo("16.56");
        assertThat(changes.get(0).getCapturedAfter()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(changes.get(0).getAvailableCaptureAfter()).isEqualByComparingTo("16.56");
        assertThat(changes.get(0).getAvailableRefundAfter()).isEqualByComparingTo(BigDecimal.ZERO);
        operation.setTransactionStatus("FAILED");
        assertThat(JdbcAdminTransactionQueryService.withInitialAmountChange(
                order, List.of(operation), List.of(), List.of())).isEmpty();
    }

    /**
     * 3DS 展示应优先读取支付方式快照，并用 Hosted Checkout 尝试补齐历史记录。
     */
    @Test
    @SuppressWarnings("unchecked")
    void threeDsEnrichmentShouldUseSnapshotAndCheckoutFallbackInBatches() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class), eq(String.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, String.class)
                        .contains("transaction_payment_method_info")
                        ? List.of("operation-snapshot")
                        : List.of("operation-checkout"));
        JdbcAdminTransactionQueryService service = buildService(jdbcTemplate);

        Set<String> operationIds = (Set<String>) ReflectionTestUtils.invokeMethod(
                service,
                "findThreeDsOperationIds",
                List.of("operation-snapshot", "operation-checkout"));

        assertThat(operationIds).containsExactlyInAnyOrder("operation-snapshot", "operation-checkout");
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, atLeast(2)).queryForList(
                sqlCaptor.capture(), any(MapSqlParameterSource.class), eq(String.class));
        assertThat(sqlCaptor.getAllValues()).anySatisfy(sql -> assertThat(sql)
                .contains("three_ds_indicator", "transaction_date_time >= :registeredNodeBegin"));
        assertThat(sqlCaptor.getAllValues()).anySatisfy(sql -> assertThat(sql)
                .contains("payment_checkout_attempt", "three_ds_required = 1"));
    }

    @Test
    void pageOrdersShouldClampRequestedRangeToRegisteredNodesAndCurrentTime() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        TransactionShardingProperties properties = new TransactionShardingProperties();
        properties.setPhysicalNodes(List.of("202603", "202604"));
        JdbcAdminTransactionQueryService service = new JdbcAdminTransactionQueryService(
                jdbcTemplate,
                mock(AdminRiskTimelineQueryService.class),
                new TransactionLogicalReadExecutor(),
                properties);
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(0L);
        TransactionPageQuery query = new TransactionPageQuery();
        query.setBeginTime(LocalDateTime.of(2026, 4, 1, 0, 0));
        query.setEndTime(LocalDateTime.of(2099, 12, 31, 23, 59, 59));

        LocalDateTime beforeQuery = LocalDateTime.now();
        service.pageOrders(query);
        LocalDateTime afterQuery = LocalDateTime.now();

        assertThat(query.getBeginTime()).isEqualTo(LocalDateTime.of(2026, 7, 1, 0, 0));
        assertThat(query.getEndTime()).isBetween(beforeQuery, afterQuery);
    }

    @Test
    void pageOrdersShouldReturnEmptyWithoutSqlWhenRangeEndsBeforeFirstRegisteredNode() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        TransactionShardingProperties properties = new TransactionShardingProperties();
        properties.setPhysicalNodes(List.of("202603", "202604"));
        JdbcAdminTransactionQueryService service = new JdbcAdminTransactionQueryService(
                jdbcTemplate,
                mock(AdminRiskTimelineQueryService.class),
                new TransactionLogicalReadExecutor(),
                properties);
        TransactionPageQuery query = new TransactionPageQuery();
        query.setBeginTime(LocalDateTime.of(2026, 4, 1, 0, 0));
        query.setEndTime(LocalDateTime.of(2026, 6, 30, 23, 59, 59));

        assertThat(service.pageOrders(query).getRecords()).isEmpty();

        verify(jdbcTemplate, never()).queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class));
    }

    /**
     * ShardingSphere 模式必须通过 transaction 逻辑数据源执行固定逻辑表 SQL。
     */
    @Test
    void pageOrdersShouldUseTransactionLogicalTableWithoutPhysicalResolution() throws NoSuchMethodException {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        JdbcAdminTransactionQueryService service = new JdbcAdminTransactionQueryService(
                jdbcTemplate,
                mock(AdminRiskTimelineQueryService.class));
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(0L);
        TransactionPageQuery query = new TransactionPageQuery();
        query.setBeginTime(LocalDateTime.of(2026, 4, 1, 0, 0));
        query.setEndTime(LocalDateTime.of(2026, 7, 31, 23, 59));

        service.pageOrders(query);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(
                sqlCaptor.capture(), any(MapSqlParameterSource.class), eq(Long.class));
        assertThat(sqlCaptor.getValue()).contains("FROM transaction_order");
        assertThat(sqlCaptor.getValue()).doesNotContain("transaction_order_2026");
        assertThat(sqlCaptor.getValue()).contains("transaction_date_time >= :beginTime");
        assertThat(JdbcAdminTransactionQueryService.class.getAnnotation(DS.class)).isNull();
        DS dataSource = TransactionLogicalReadExecutor.class
                .getMethod("read", java.util.function.Supplier.class)
                .getAnnotation(DS.class);
        assertThat(dataSource.value()).isEqualTo(DataSourceName.TRANSACTION);
    }

    @Test
    void pageOrdersShouldApplyConfiguredResultRowBudget() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        TransactionShardingProperties properties = new TransactionShardingProperties();
        properties.getQueryBudget().setMaxResultRows(7);
        JdbcAdminTransactionQueryService service = new JdbcAdminTransactionQueryService(
                jdbcTemplate,
                mock(AdminRiskTimelineQueryService.class),
                new TransactionLogicalReadExecutor(),
                properties);
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(0L);
        TransactionPageQuery query = new TransactionPageQuery();
        query.setPageSize(500);

        service.pageOrders(query);

        assertThat(query.getPageSize()).isEqualTo(7);
    }

    /**
     * 支付方式汇总必须使用标签金额，并按标签币种解析 ISO 4217 辅币位。
     */
    @Test
    @SuppressWarnings("unchecked")
    void operationSummaryShouldAggregateLabelAmountsWithLabelCurrencyExponent() throws Exception {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        ResultSet jpyAmount = operationSummaryResultSet("SUCCESS", null, null, "JPY", 2L, "1200");
        ResultSet kwdAmount = operationSummaryResultSet("FAILED", null, null, "KWD", 1L, "12.345");
        ResultSet jpyPayment = operationSummaryResultSet(null, "BANK_CARD", "VISA", "JPY", 2L, "1200");
        ResultSet kwdPayment = operationSummaryResultSet(null, "BANK_CARD", "VISA", "KWD", 1L, "12.345");
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0, String.class);
                    RowMapper<Object> mapper = invocation.getArgument(2);
                    if (sql.contains("AS payment_method")) {
                        return List.of(mapper.mapRow(jpyPayment, 0), mapper.mapRow(kwdPayment, 1));
                    }
                    return List.of(mapper.mapRow(jpyAmount, 0), mapper.mapRow(kwdAmount, 1));
                });
        JdbcAdminTransactionQueryService service = buildService(jdbcTemplate);
        TransactionPageQuery query = new TransactionPageQuery();
        query.setBeginTime(LocalDateTime.of(2026, 10, 17, 0, 0));
        query.setEndTime(LocalDateTime.of(2026, 10, 18, 0, 0));

        TransactionOperationSummaryResponse response = ReflectionTestUtils.invokeMethod(service, "operationSummary", query);

        assertThat(response).isNotNull();
        assertThat(response.getTotalCount()).isEqualTo(3L);
        assertThat(response.getAmountSummaries()).extracting("currency", "currencyExponent", "amount")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("JPY", 0, new BigDecimal("1200")),
                        org.assertj.core.groups.Tuple.tuple("KWD", 3, new BigDecimal("12.345")));
        assertThat(response.getPaymentMethodSummaries()).singleElement().satisfies(payment -> {
            assertThat(payment.getCount()).isEqualTo(3L);
            assertThat(payment.getPaymentMethod()).isEqualTo("BANK_CARD");
            assertThat(payment.getPaymentBrand()).isEqualTo("VISA");
            assertThat(payment.getAmountSummaries()).extracting("currency", "currencyExponent", "amount")
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple("JPY", 0, new BigDecimal("1200")),
                            org.assertj.core.groups.Tuple.tuple("KWD", 3, new BigDecimal("12.345")));
        });
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, atLeast(2)).query(
                sqlCaptor.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));
        assertThat(sqlCaptor.getAllValues()).allSatisfy(sql -> assertThat(sql)
                .contains("o.label_currency", "o.label_amount")
                .doesNotContain("o.transaction_currency", "o.transaction_amount", "o.currency_exponent"));
    }

    private ResultSet operationSummaryResultSet(String transactionStatus,
                                                String paymentMethod,
                                                String paymentBrand,
                                                String currency,
                                                long count,
                                                String amount) throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getString("transaction_status")).thenReturn(transactionStatus);
        when(resultSet.getString("payment_method")).thenReturn(paymentMethod);
        when(resultSet.getString("payment_brand")).thenReturn(paymentBrand);
        when(resultSet.getString("currency")).thenReturn(currency);
        when(resultSet.getLong("count")).thenReturn(count);
        when(resultSet.getBigDecimal("amount")).thenReturn(new BigDecimal(amount));
        return resultSet;
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchOrdersShouldReturnFullQuerySummaryIncludingCapturedAndProcessing() throws Exception {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        ResultSet hkdSummary = orderSummaryResultSet(
                "HKD", 2, 7L, 5L, 1L, 1L, 4L,
                "1000.10", "800.10", "700.10", "100.00");
        ResultSet usdSummary = orderSummaryResultSet(
                "USD", 2, 3L, 1L, 1L, 1L, 0L,
                "200.00", "100.00", "0.00", "50.00");
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0, String.class);
                    if (!sql.contains("GROUP BY COALESCE(transaction_currency")) {
                        return Collections.emptyList();
                    }
                    RowMapper<Object> mapper = invocation.getArgument(2);
                    return List.of(mapper.mapRow(hkdSummary, 0), mapper.mapRow(usdSummary, 1));
                });
        JdbcAdminTransactionQueryService service = buildService(jdbcTemplate);
        TransactionPageQuery query = new TransactionPageQuery();
        query.setBeginTime(LocalDateTime.of(2026, 10, 17, 0, 0));
        query.setEndTime(LocalDateTime.of(2026, 10, 18, 0, 0));

        TransactionOrderSearchResponse response = service.searchOrders(query);

        assertThat(response.getPage().getTotal()).isEqualTo(10L);
        assertThat(response.getSummary().getTotalCount()).isEqualTo(10L);
        assertThat(response.getSummary().getSuccessCount()).isEqualTo(6L);
        assertThat(response.getSummary().getFailedCount()).isEqualTo(2L);
        assertThat(response.getSummary().getProcessingCount()).isEqualTo(2L);
        assertThat(response.getSummary().getCapturedCount()).isEqualTo(4L);
        assertThat(response.getSummary().getAmountSummaries()).satisfiesExactly(
                amount -> assertThat(amount).extracting(
                        "currency", "currencyExponent", "amount")
                        .containsExactly("HKD", 2, new BigDecimal("1000.10")),
                amount -> assertThat(amount).extracting(
                        "currency", "currencyExponent", "amount")
                        .containsExactly("USD", 2, new BigDecimal("200.00")));
        assertThat(response.getSummary().getSuccessAmountSummaries()).extracting("currency", "amount")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("HKD", new BigDecimal("800.10")),
                        org.assertj.core.groups.Tuple.tuple("USD", new BigDecimal("100.00")));
        assertThat(response.getSummary().getCapturedAmountSummaries()).extracting("currency", "amount")
                .containsExactly(org.assertj.core.groups.Tuple.tuple("HKD", new BigDecimal("700.10")));
        assertThat(response.getSummary().getFailedAmountSummaries()).extracting("currency", "amount")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("HKD", new BigDecimal("100.00")),
                        org.assertj.core.groups.Tuple.tuple("USD", new BigDecimal("50.00")));
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, atLeast(1)).query(
                sqlCaptor.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));
        String summarySql = sqlCaptor.getAllValues().stream()
                .filter(sql -> sql.contains("GROUP BY COALESCE(transaction_currency"))
                .findFirst()
                .orElseThrow();
        assertThat(summarySql)
                .contains("SUM(CASE WHEN transaction_status = 'SUCCESS' THEN 1 ELSE 0 END)")
                .contains("SUM(CASE WHEN transaction_status = 'FAILED' THEN 1 ELSE 0 END)")
                .contains("SUM(CASE WHEN transaction_status IN ('PENDING', 'PROCESSING') THEN 1 ELSE 0 END)")
                .contains("SUM(CASE WHEN captured_amount > 0 THEN 1 ELSE 0 END)")
                .contains("SUM(CASE WHEN captured_amount > 0 THEN captured_amount ELSE 0 END)")
                .contains("GROUP BY COALESCE(transaction_currency, 'UNKNOWN'), currency_exponent")
                .contains("FROM transaction_order")
                .doesNotContain("transaction_order_2026");
    }

    private ResultSet orderSummaryResultSet(String currency,
                                            int currencyExponent,
                                            long totalCount,
                                            long successCount,
                                            long failedCount,
                                            long processingCount,
                                            long capturedCount,
                                            String totalAmount,
                                            String successAmount,
                                            String capturedAmount,
                                            String failedAmount) throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getString("currency")).thenReturn(currency);
        when(resultSet.getInt("currency_exponent")).thenReturn(currencyExponent);
        when(resultSet.wasNull()).thenReturn(false);
        when(resultSet.getLong("total_count")).thenReturn(totalCount);
        when(resultSet.getLong("success_count")).thenReturn(successCount);
        when(resultSet.getLong("failed_count")).thenReturn(failedCount);
        when(resultSet.getLong("processing_count")).thenReturn(processingCount);
        when(resultSet.getLong("captured_count")).thenReturn(capturedCount);
        when(resultSet.getBigDecimal("total_amount")).thenReturn(new BigDecimal(totalAmount));
        when(resultSet.getBigDecimal("success_amount")).thenReturn(new BigDecimal(successAmount));
        when(resultSet.getBigDecimal("captured_amount")).thenReturn(new BigDecimal(capturedAmount));
        when(resultSet.getBigDecimal("failed_amount")).thenReturn(new BigDecimal(failedAmount));
        return resultSet;
    }

    /**
     * 详情首查必须使用列表返回的毫秒分片时间精确定位动作单。
     */
    @Test
    @SuppressWarnings("unchecked")
    void detailShouldBindExactShardingTimeInFirstQuery() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        LocalDateTime transactionTime = LocalDateTime.of(2026, 7, 21, 19, 53, 50, 233_000_000);
        LocalDateTime rootTransactionTime = LocalDateTime.of(2026, 4, 10, 9, 15, 30);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(Collections.emptyList());
        JdbcAdminTransactionQueryService service = new JdbcAdminTransactionQueryService(
                jdbcTemplate, mock(AdminRiskTimelineQueryService.class));

        assertThatThrownBy(() -> service.detail("transaction-a", transactionTime, rootTransactionTime))
                .isInstanceOf(RuntimeException.class);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), paramsCaptor.capture(), any(RowMapper.class));
        assertThat(sqlCaptor.getValue()).contains("FROM transaction_operation");
        assertThat(sqlCaptor.getValue()).contains("transaction_date_time = :transactionDateTime");
        assertThat(sqlCaptor.getValue()).doesNotContain("transactionDateTimeEnd");
        assertThat(paramsCaptor.getValue().getValue("transactionDateTime")).isEqualTo(transactionTime);
        assertThat(paramsCaptor.getValue().hasValue("transactionDateTimeEnd")).isFalse();
    }

    /**
     * 交易详情应按当前交易号和精确分片时间返回已有的账单、付款人和收货信息。
     */
    @Test
    @SuppressWarnings("unchecked")
    void detailShouldReturnPersistedPartyInformationFromExactShard() throws Exception {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        LocalDateTime transactionTime = LocalDateTime.of(2026, 8, 17, 14, 8, 30, 123_000_000);
        TransactionOperationResponse operation = operation("operation-a", "transaction-a", transactionTime);
        TransactionOrderResponse order = order("operation-a", transactionTime);
        ResultSet billingResultSet = mock(ResultSet.class);
        when(billingResultSet.getString("first_name")).thenReturn("John");
        when(billingResultSet.getString("last_name")).thenReturn("Smith");
        when(billingResultSet.getString("email")).thenReturn("john.smith@example.com");
        when(billingResultSet.getString("phone")).thenReturn("+12025550124");
        when(billingResultSet.getString("country")).thenReturn("USA");
        when(billingResultSet.getString("state")).thenReturn("NY");
        when(billingResultSet.getString("city")).thenReturn("New York");
        when(billingResultSet.getString("street")).thenReturn("100 Main Street");
        when(billingResultSet.getString("postal")).thenReturn("10001");
        ResultSet payerResultSet = mock(ResultSet.class);
        when(payerResultSet.getString("payer_id")).thenReturn("CUSTOMER-10002");
        when(payerResultSet.getString("first_name")).thenReturn("John");
        when(payerResultSet.getString("last_name")).thenReturn("Smith");
        when(payerResultSet.getString("ip_address")).thenReturn("203.0.113.10");
        when(payerResultSet.getString("session_id")).thenReturn("SESSION-10002");
        when(payerResultSet.getString("browser_info_json"))
                .thenReturn("{\"browser\":{\"name\":\"Chrome\",\"version\":\"128.0.0.0\"}}");
        when(payerResultSet.getString("user_agent")).thenReturn("Mozilla/5.0");
        ResultSet shippingResultSet = mock(ResultSet.class);
        when(shippingResultSet.getString("first_name")).thenReturn("Jane");
        when(shippingResultSet.getString("last_name")).thenReturn("Smith");
        when(shippingResultSet.getString("country")).thenReturn("USA");
        when(shippingResultSet.getString("street")).thenReturn("200 Shipping Street");
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0, String.class);
                    RowMapper<?> mapper = invocation.getArgument(2, RowMapper.class);
                    if (sql.contains("FROM transaction_operation")
                            && sql.contains("transaction_id = :transactionId")) {
                        return List.of(operation);
                    }
                    if (sql.contains("FROM transaction_order")) {
                        return List.of(order);
                    }
                    if (sql.contains("FROM transaction_operation")) {
                        return List.of(operation);
                    }
                    if (sql.contains("FROM transaction_billing_info")) {
                        return List.of(mapper.mapRow(billingResultSet, 0));
                    }
                    if (sql.contains("FROM transaction_payer_info")) {
                        return List.of(mapper.mapRow(payerResultSet, 0));
                    }
                    if (sql.contains("FROM transaction_shipping_info")) {
                        return List.of(mapper.mapRow(shippingResultSet, 0));
                    }
                    return Collections.emptyList();
                });
        when(jdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class)))
                .thenReturn(Collections.emptyList());
        when(jdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class), eq(String.class)))
                .thenReturn(Collections.emptyList());
        AdminRiskTimelineQueryService riskTimelineQueryService = mock(AdminRiskTimelineQueryService.class);
        when(riskTimelineQueryService.findRiskEvents(anyString())).thenReturn(Collections.emptyList());
        JdbcAdminTransactionQueryService service = new JdbcAdminTransactionQueryService(
                jdbcTemplate, riskTimelineQueryService);

        TransactionDetailResponse detail = service.detail(
                "transaction-a", transactionTime, transactionTime);

        assertThat(detail.getBillingCardHolderInfo().getFirstName()).isEqualTo("John");
        assertThat(detail.getBillingCardHolderInfo().getCountry()).isEqualTo("USA");
        assertThat(detail.getPayerInfo().getPayerId()).isEqualTo("CUSTOMER-10002");
        assertThat(detail.getPayerInfo().getIpAddress()).isEqualTo("203.0.113.10");
        assertThat(detail.getPayerInfo().getBrowserInfo())
                .extractingByKey("browser")
                .isEqualTo(Map.of("name", "Chrome", "version", "128.0.0.0"));
        assertThat(detail.getShippingInfo().getFirstName()).isEqualTo("Jane");
        assertThat(detail.getShippingInfo().getStreet()).isEqualTo("200 Shipping Street");
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate, atLeast(3)).query(
                sqlCaptor.capture(), paramsCaptor.capture(), any(RowMapper.class));
        List<Integer> partyQueryIndexes = java.util.stream.IntStream
                .range(0, sqlCaptor.getAllValues().size())
                .filter(index -> sqlCaptor.getAllValues().get(index).contains("transaction_billing_info")
                        || sqlCaptor.getAllValues().get(index).contains("transaction_payer_info")
                        || sqlCaptor.getAllValues().get(index).contains("transaction_shipping_info"))
                .boxed()
                .toList();
        assertThat(partyQueryIndexes).hasSize(3);
        partyQueryIndexes.forEach(index -> {
            assertThat(sqlCaptor.getAllValues().get(index))
                    .contains("transaction_id = :transactionId")
                    .contains("transaction_date_time = :transactionDateTime")
                    .doesNotContain("transaction_billing_info_2026")
                    .doesNotContain("transaction_payer_info_2026")
                    .doesNotContain("transaction_shipping_info_2026");
            assertThat(paramsCaptor.getAllValues().get(index).getValue("transactionId"))
                    .isEqualTo("transaction-a");
            assertThat(paramsCaptor.getAllValues().get(index).getValue("transactionDateTime"))
                    .isEqualTo(transactionTime);
        });
    }

    /**
     * 当前交易没有附属快照时，详情字段应保持为空且不伪造占位对象。
     */
    @Test
    void partyInformationQueriesShouldReturnNullWhenSnapshotsDoNotExist() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(Collections.emptyList());
        JdbcAdminTransactionQueryService service = buildService(jdbcTemplate);
        LocalDateTime transactionTime = LocalDateTime.of(2026, 8, 17, 14, 8, 30, 123_000_000);

        Object billingInfo = ReflectionTestUtils.invokeMethod(
                service, "selectBillingCardHolderInfo", "transaction-a", transactionTime);
        Object payerInfo = ReflectionTestUtils.invokeMethod(
                service, "selectPayerInfo", "transaction-a", transactionTime);
        Object shippingInfo = ReflectionTestUtils.invokeMethod(
                service, "selectShippingInfo", "transaction-a", transactionTime);

        assertThat(billingInfo).isNull();
        assertThat(payerInfo).isNull();
        assertThat(shippingInfo).isNull();
    }

    /**
     * 人工重发资格查询必须同时限定真实分片时间、交易终态和通知可重发状态。
     */
    @Test
    void callbackRetryEligibilityShouldUseExactShardAndTerminalStatus() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        LocalDateTime transactionTime = LocalDateTime.of(2026, 8, 4, 12, 30, 0, 125_000_000);
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Boolean.class)))
                .thenReturn(Boolean.TRUE);
        JdbcAdminTransactionQueryService service = new JdbcAdminTransactionQueryService(
                jdbcTemplate, mock(AdminRiskTimelineQueryService.class));

        assertThat(service.existsRetryableTerminalMerchantNotification("transaction-a", transactionTime)).isTrue();

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).queryForObject(
                sqlCaptor.capture(), paramsCaptor.capture(), eq(Boolean.class));
        assertThat(sqlCaptor.getValue())
                .contains("FROM transaction_operation operation_record")
                .contains("JOIN transaction_merchant_notification notification")
                .contains("operation_record.transaction_date_time = :transactionDateTime")
                .contains("notification.transaction_date_time = operation_record.transaction_date_time")
                .contains("operation_record.transaction_status IN ('SUCCESS', 'FAILED')")
                .contains("notification.notify_status IN ('SUCCESS', 'CLOSED')")
                .contains("notification.notify_status = 'FAILED'")
                .contains("notification.next_retry_time IS NULL")
                .doesNotContain("notification.notify_status = 'INIT'")
                .doesNotContain("${");
        assertThat(paramsCaptor.getValue().getValue("transactionDateTime")).isEqualTo(transactionTime);
    }

    /**
     * 商户回调主详情只读取任务快照，投递记录由独立分页接口加载。
     */
    @Test
    void merchantNotificationDetailShouldUseExactShardAndLeaveAttemptLogsEmpty() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        LocalDateTime transactionTime = LocalDateTime.of(2026, 8, 4, 18, 29, 44, 988_000_000);
        MerchantNotificationResponse notification = new MerchantNotificationResponse();
        notification.setNotifyId("notification-a");
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(notification));
        JdbcAdminTransactionQueryService service = new JdbcAdminTransactionQueryService(
                jdbcTemplate, mock(AdminRiskTimelineQueryService.class));

        MerchantNotificationDetailResponse detail =
                service.merchantNotificationDetail("notification-a", transactionTime);

        assertThat(detail.getNotification()).isEqualTo(notification);
        assertThat(detail.getDeliveryLogs()).isEmpty();
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate)
                .query(sqlCaptor.capture(), paramsCaptor.capture(), any(RowMapper.class));
        assertThat(sqlCaptor.getValue())
                .contains("notify_id = :notifyId", "transaction_date_time = :transactionDateTime")
                .doesNotContain("transaction_merchant_notification_log", "${");
        assertThat(paramsCaptor.getValue().getValue("notifyId")).isEqualTo("notification-a");
        assertThat(paramsCaptor.getValue().getValue("transactionDateTime")).isEqualTo(transactionTime);
    }

    /**
     * 投递日志必须在校验通知存在后按最新尝试倒序分页。
     */
    @Test
    void merchantNotificationDeliveryLogsShouldPageNewestAttemptsFirst() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        LocalDateTime transactionTime = LocalDateTime.of(2026, 8, 4, 18, 29, 44, 988_000_000);
        MerchantNotificationDeliveryLogResponse latestAttempt = new MerchantNotificationDeliveryLogResponse();
        latestAttempt.setAttemptNo(12);
        when(jdbcTemplate.queryForObject(
                anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(1L, 27L);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(latestAttempt));
        JdbcAdminTransactionQueryService service = new JdbcAdminTransactionQueryService(
                jdbcTemplate, mock(AdminRiskTimelineQueryService.class));

        PageResult<MerchantNotificationDeliveryLogResponse> page =
                service.pageMerchantNotificationDeliveryLogs(
                        "notification-a", transactionTime, 2L, 10L);

        assertThat(page.getTotal()).isEqualTo(27L);
        assertThat(page.getPageNo()).isEqualTo(2L);
        assertThat(page.getRecords()).containsExactly(latestAttempt);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).query(
                sqlCaptor.capture(), paramsCaptor.capture(), any(RowMapper.class));
        assertThat(sqlCaptor.getValue())
                .contains("ORDER BY attempt_no DESC, id DESC", "LIMIT :offset, :limit");
        assertThat(paramsCaptor.getValue().getValue("offset")).isEqualTo(10L);
        assertThat(paramsCaptor.getValue().getValue("limit")).isEqualTo(10L);
    }

    /**
     * 详情附属日志触及未登记季度时必须明确失败，禁止伪装为空日志列表。
     */
    @Test
    @SuppressWarnings("unchecked")
    void detailShouldPropagateMissingQuarterFromOptionalInteractionLog() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        LocalDateTime transactionTime = LocalDateTime.of(2026, 7, 21, 19, 53, 50, 233_000_000);
        TransactionOperationResponse operation = operation("operation-a", "transaction-a", transactionTime);
        TransactionOrderResponse order = order("operation-a", transactionTime);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0, String.class);
                    if (sql.contains("FROM transaction_operation") && sql.contains("transaction_id = :transactionId")) {
                        return List.of(operation);
                    }
                    if (sql.contains("FROM transaction_order")) {
                        return List.of(order);
                    }
                    if (sql.contains("FROM transaction_operation")) {
                        return List.of(operation);
                    }
                    if (sql.contains("FROM transaction_merchant_api_interaction_log")) {
                        throw new TransactionDataUnavailableException(
                                "transaction_merchant_api_interaction_log", "2026-Q2", "test-001");
                    }
                    return Collections.emptyList();
                });
        AdminRiskTimelineQueryService riskTimelineQueryService = mock(AdminRiskTimelineQueryService.class);
        when(riskTimelineQueryService.findRiskEvents(anyString())).thenReturn(Collections.emptyList());
        JdbcAdminTransactionQueryService service = new JdbcAdminTransactionQueryService(
                jdbcTemplate, riskTimelineQueryService);

        assertThatThrownBy(() -> service.detail("transaction-a", transactionTime, transactionTime))
                .isInstanceOf(TransactionDataUnavailableException.class)
                .hasMessageContaining("transaction_merchant_api_interaction_log");
    }

    @Test
    @SuppressWarnings("unchecked")
    void operationPageShouldBatchLoadLifecycleWithoutUnshardedOrderJoin() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        LocalDateTime actionTime = LocalDateTime.of(2026, 7, 21, 19, 53, 50);
        LocalDateTime rootTime = LocalDateTime.of(2026, 4, 10, 9, 15, 30);
        TransactionOperationResponse firstOperation = operation("operation-a", "transaction-a", actionTime);
        TransactionOperationResponse secondOperation = operation("operation-b", "transaction-b", actionTime.plusSeconds(1));
        firstOperation.setAccessType("DIRECT_API");
        secondOperation.setAccessType("DIRECT_API");
        TransactionOrderResponse firstOrder = order("operation-a", rootTime);
        TransactionOrderResponse secondOrder = order("operation-b", rootTime.plusSeconds(1));
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(2L);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0, String.class);
                    if (sql.contains("FROM transaction_operation o") && sql.contains("LIMIT :offset, :limit")) {
                        assertThat(sql).doesNotContain("SELECT o.*");
                        assertThat(sql).contains(
                                "o.transaction_rate", "o.settlement_currency", "o.settlement_amount",
                                "o.settlement_rate", "o.settlement_date", "o.settlement_batch_no",
                                "o.settlement_status");
                        return List.of(firstOperation, secondOperation);
                    }
                    if (sql.contains("FROM transaction_order")) {
                        return List.of(firstOrder, secondOrder);
                    }
                    return Collections.emptyList();
                });
        when(jdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class), eq(String.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, String.class).contains("payment_checkout_attempt")
                        ? List.of("transaction-a")
                        : Collections.emptyList());
        JdbcAdminTransactionQueryService service = buildService(jdbcTemplate);
        TransactionPageQuery query = new TransactionPageQuery();
        query.setBeginTime(LocalDateTime.of(2026, 7, 1, 0, 0));
        query.setEndTime(LocalDateTime.of(2026, 7, 31, 23, 59));

        List<TransactionOperationResponse> rows = service.pageOperations(query).getRecords();

        assertThat(rows)
                .extracting(TransactionOperationResponse::getRootTransactionDateTime)
                .containsExactly(rootTime, rootTime.plusSeconds(1));
        assertThat(rows)
                .extracting(TransactionOperationResponse::getAccessType)
                .containsExactly("HOSTED_CHECKOUT", "DIRECT_API");
        assertThat(rows).allSatisfy(row ->
                assertThat(row.getMerchantNotificationStatus()).isNull());
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, atLeast(2)).query(
                sqlCaptor.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));
        assertThat(sqlCaptor.getAllValues()).noneMatch(sql -> sql.contains("LEFT JOIN transaction_order"));
        assertThat(sqlCaptor.getAllValues().stream().filter(sql -> sql.contains("FROM transaction_order")))
                .singleElement()
                .satisfies(sql -> {
                    assertThat(sql).doesNotContain("SELECT *");
                    assertThat(sql).contains(
                            "settlement_currency", "settlement_amount", "settlement_rate",
                            "settlement_date", "settlement_batch_no",
                            "settlement_transaction_id", "settlement_transaction_date_time");
                    assertThat(sql).contains("operation_id IN (:operationIds)");
                    assertThat(sql).contains("transaction_date_time >= :registeredNodeBegin");
                    assertThat(sql).contains("transaction_date_time < :registeredNodeEnd");
                });
    }

    /**
     * 操作单列表必须按当前页交易号和分片时间批量返回商户通知任务的最新状态。
     */
    @ParameterizedTest
    @ValueSource(strings = {"SUCCESS", "FAILED", "CLOSED"})
    @SuppressWarnings("unchecked")
    void operationPageShouldExposeCurrentMerchantNotificationStatus(String currentStatus) throws Exception {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        LocalDateTime transactionTime = LocalDateTime.of(2026, 8, 17, 11, 51, 32, 567_000_000);
        TransactionOperationResponse operation = operation("operation-a", "transaction-a", transactionTime);
        TransactionOrderResponse order = order("operation-a", transactionTime);
        ResultSet previousNotificationResultSet = mock(ResultSet.class);
        when(previousNotificationResultSet.getString("transaction_id")).thenReturn("transaction-a");
        when(previousNotificationResultSet.getString("notify_status")).thenReturn("PROCESSING");
        ResultSet currentNotificationResultSet = mock(ResultSet.class);
        when(currentNotificationResultSet.getString("transaction_id")).thenReturn("transaction-a");
        when(currentNotificationResultSet.getString("notify_status")).thenReturn(currentStatus);
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(1L);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    String sql = invocation.getArgument(0, String.class);
                    if (sql.contains("FROM transaction_operation o") && sql.contains("LIMIT :offset, :limit")) {
                        return List.of(operation);
                    }
                    if (sql.contains("FROM transaction_merchant_notification")) {
                        RowMapper<?> mapper = invocation.getArgument(2, RowMapper.class);
                        return List.of(
                                mapper.mapRow(previousNotificationResultSet, 0),
                                mapper.mapRow(currentNotificationResultSet, 1));
                    }
                    if (sql.contains("FROM transaction_order")) {
                        return List.of(order);
                    }
                    return Collections.emptyList();
                });
        when(jdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class), eq(String.class)))
                .thenReturn(Collections.emptyList());
        JdbcAdminTransactionQueryService service = buildService(jdbcTemplate);
        TransactionPageQuery query = new TransactionPageQuery();
        query.setBeginTime(LocalDateTime.of(2026, 8, 17, 0, 0));
        query.setEndTime(LocalDateTime.of(2026, 8, 17, 23, 59, 59));

        List<TransactionOperationResponse> rows = service.pageOperations(query).getRecords();

        assertThat(rows)
                .extracting(TransactionOperationResponse::getMerchantNotificationStatus)
                .containsExactly(currentStatus);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate, atLeast(2)).query(
                sqlCaptor.capture(), paramsCaptor.capture(), any(RowMapper.class));
        int notificationQueryIndex = java.util.stream.IntStream.range(0, sqlCaptor.getAllValues().size())
                .filter(index -> sqlCaptor.getAllValues().get(index).contains("FROM transaction_merchant_notification"))
                .findFirst()
                .orElseThrow();
        assertThat(sqlCaptor.getAllValues().get(notificationQueryIndex))
                .contains("transaction_id IN (:transactionIds)")
                .contains("transaction_date_time >= :notificationBeginTime")
                .contains("transaction_date_time < :notificationEndTime")
                .contains("ORDER BY update_time ASC, id ASC")
                .doesNotContain("transaction_merchant_notification_2026");
        assertThat(paramsCaptor.getAllValues().get(notificationQueryIndex).getValue("transactionIds"))
                .isEqualTo(List.of("transaction-a"));
    }

    /**
     * 新收银台交易已落库来源时，应直接映射为 Hosted Checkout。
     */
    @Test
    @SuppressWarnings("unchecked")
    void operationMapperShouldUsePersistedHostedCheckoutSource() throws Exception {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        JdbcAdminTransactionQueryService service = buildService(jdbcTemplate);
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getString("request_source")).thenReturn("HOSTED_CHECKOUT");
        RowMapper<TransactionOperationResponse> mapper =
                (RowMapper<TransactionOperationResponse>) ReflectionTestUtils.invokeMethod(
                        service, "operationMapper", false);

        TransactionOperationResponse row = mapper.mapRow(resultSet, 0);

        assertThat(row.getAccessType()).isEqualTo("HOSTED_CHECKOUT");
    }

    /**
     * 管理端可查看受控渠道摘要，但商户与付款人预览必须使用各自的安全文案。
     */
    @Test
    @SuppressWarnings("unchecked")
    void operationMapperShouldSeparateInternalMerchantAndPayerMessages() throws Exception {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        JdbcAdminTransactionQueryService service = buildService(jdbcTemplate);
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getString("transaction_status")).thenReturn("FAILED");
        when(resultSet.getString("process_stage")).thenReturn("FINISHED");
        when(resultSet.getString("fail_reason_code")).thenReturn("CHANNEL_DECLINED");
        when(resultSet.getString("channel_code")).thenReturn("MPGS");
        when(resultSet.getString("channel_response_code")).thenReturn("DECLINED");
        when(resultSet.getString("channel_response_message")).thenReturn("Transaction failed");
        when(resultSet.getString("channel_order_no")).thenReturn("REF-1");
        RowMapper<TransactionOperationResponse> mapper =
                (RowMapper<TransactionOperationResponse>) ReflectionTestUtils.invokeMethod(
                        service, "operationMapper", false);

        TransactionOperationResponse row = mapper.mapRow(resultSet, 0);

        assertThat(row.getOpenApiResultCode())
                .isEqualTo(OpenApiResultCode.CHANNEL_RETURNED_ERROR.getCode())
                .isEqualTo(row.getMerchantResponseCode());
        assertThat(row.getInternalResultMessage()).isEqualTo(
                "Channel processing failed (code: DECLINED, message: Transaction failed). "
                        + "Please check the original order or contact platform support.");
        assertThat(row.getMerchantResponseMessage())
                .isEqualTo("The transaction was declined by the issuer bank. Check the original order, "
                        + "then ask the payer to contact their bank or use another payment method when applicable.")
                .doesNotContain("MPGS", "DECLINED", "Transaction failed", "REF-1");
        assertThat(row.getPayerResultMessage())
                .isEqualTo("The transaction was declined. Please contact your card issuer or try a different payment method.")
                .doesNotContain("MPGS", "DECLINED", "Transaction failed", "REF-1");
    }

    /**
     * 状态历史表没有 deleted 字段，详情聚合查询不应拼接 deleted = 0。
     */
    @Test
    void selectDetailRowsShouldSkipDeletedConditionWhenTableHasNoSoftDeleteColumn() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        JdbcAdminTransactionQueryService service = buildService(jdbcTemplate);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(Collections.emptyList());

        invokeSelectDetailRowsByOperationId(
                service, "transaction_status_history", "id, status_history_id", mock(RowMapper.class));

        String sql = captureQuerySql(jdbcTemplate);
        assertThat(sql).contains("FROM transaction_status_history");
        assertThat(sql).contains("WHERE 1 = 1");
        assertThat(sql).contains("AND operation_id = :operationId");
        assertThat(sql).contains("AND transaction_date_time >= :beginTime");
        assertThat(sql).contains("AND transaction_date_time < :endTime");
        assertThat(sql).doesNotContain("deleted = 0");
    }

    /**
     * 渠道请求表包含 deleted 字段，详情聚合查询仍应保留软删过滤。
     */
    @Test
    void selectDetailRowsShouldKeepDeletedConditionWhenTableHasSoftDeleteColumn() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        JdbcAdminTransactionQueryService service = buildService(jdbcTemplate);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(Collections.emptyList());

        invokeSelectDetailRowsByOperationId(
                service, "transaction_channel_request", "id, request_id", mock(RowMapper.class));

        String sql = captureQuerySql(jdbcTemplate);
        assertThat(sql).contains("FROM transaction_channel_request");
        assertThat(sql).contains("WHERE deleted = 0");
        assertThat(sql).contains("AND operation_id = :operationId");
    }

    /**
     * 渠道交互详情使用强类型映射，数据库字段名不得泄漏到 API 属性命名。
     */
    @Test
    @SuppressWarnings("unchecked")
    void channelInteractionMapperShouldReturnTypedFrontendFields() throws Exception {
        JdbcAdminTransactionQueryService service = buildService(mock(NamedParameterJdbcTemplate.class));
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getString("interaction_log_id")).thenReturn("CI202607211953505260922");
        when(resultSet.getString("transaction_id")).thenReturn("202607211953505070920");
        when(resultSet.getString("request_body_json_masked")).thenReturn("{\"apiOperation\":\"PAY\"}");
        when(resultSet.getString("response_body_json_masked")).thenReturn("{\"result\":\"ERROR\"}");
        RowMapper<ChannelInteractionLogResponse> mapper =
                (RowMapper<ChannelInteractionLogResponse>) ReflectionTestUtils.invokeMethod(
                        service, "channelInteractionLogMapper");

        ChannelInteractionLogResponse response = mapper.mapRow(resultSet, 0);

        assertThat(response.getInteractionLogId()).isEqualTo("CI202607211953505260922");
        assertThat(response.getTransactionId()).isEqualTo("202607211953505070920");
        assertThat(response.getRequestBodyJsonMasked()).isEqualTo("{\"apiOperation\":\"PAY\"}");
        assertThat(response.getResponseBodyJsonMasked()).isEqualTo("{\"result\":\"ERROR\"}");
    }

    /**
     * JDBC 的 BIGINT 主键必须在 JSON 序列化前转为字符串，避免浏览器按 IEEE-754 number 解析后丢失尾数。
     */
    @Test
    @SuppressWarnings("unchecked")
    void merchantNotificationLogMapperShouldReturnNumericIdentifiersAsStrings() throws Exception {
        JdbcAdminTransactionQueryService service = buildService(mock(NamedParameterJdbcTemplate.class));
        ResultSet resultSet = mock(ResultSet.class);
        LocalDateTime responseTime = LocalDateTime.of(2026, 3, 1, 12, 0, 0, 123_000_000);
        when(resultSet.getString("id")).thenReturn("202603000000000825");
        when(resultSet.getInt("duration_millis")).thenReturn(123);
        when(resultSet.getTimestamp("create_time")).thenReturn(java.sql.Timestamp.valueOf(responseTime));
        RowMapper<MerchantNotificationDeliveryLogResponse> mapper =
                (RowMapper<MerchantNotificationDeliveryLogResponse>) ReflectionTestUtils.invokeMethod(
                        service, "merchantNotificationDeliveryLogMapper");

        MerchantNotificationDeliveryLogResponse response = mapper.mapRow(resultSet, 0);

        assertThat(response.getId()).isEqualTo("202603000000000825");
        assertThat(response.getDurationMillis()).isEqualTo(123);
        assertThat(response.getHttpMethod()).isEqualTo("POST");
        assertThat(response.getResponseTime()).isEqualTo(responseTime);
    }

    /**
     * 商户通知列表必须使用显式安全投影，禁止返回完整回调地址和明文通知载荷。
     */
    @Test
    void merchantNotificationPageShouldExcludeSensitiveDeliveryFields() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        JdbcAdminTransactionQueryService service = buildService(jdbcTemplate);
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(1L);
        MerchantNotificationResponse notification = new MerchantNotificationResponse();
        notification.setNotifyId("notify-a");
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(notification));
        MerchantNotificationQuery query = new MerchantNotificationQuery();
        query.setBeginTime(LocalDateTime.of(2026, 7, 21, 0, 0));
        query.setEndTime(LocalDateTime.of(2026, 7, 22, 0, 0));

        assertThat(service.pageMerchantNotifications(query).getRecords())
                .containsExactly(notification);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(
                sqlCaptor.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));
        assertThat(sqlCaptor.getValue())
                .contains("target_url_masked", "payload_json_masked")
                .doesNotContain("callback_url", " payload_json,")
                .doesNotContain("notify_config_snapshot_json", "SELECT *");
    }

    /**
     * 管理端通知详情只能返回脱敏 URL，完整通知配置快照不得进入查询结果或 JSON 响应。
     */
    @Test
    void merchantNotificationDetailShouldExcludeSensitiveConfigSnapshot() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        JdbcAdminTransactionQueryService service = buildService(jdbcTemplate);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(new MerchantNotificationResponse()));
        String projection = (String) ReflectionTestUtils.getField(
                JdbcAdminTransactionQueryService.class, "TRANSACTION_MERCHANT_NOTIFICATION_ADMIN_PROJECTION");

        invokeSelectDetailRowsByOperationId(
                service, "transaction_merchant_notification", projection, mock(RowMapper.class));

        String sql = captureQuerySql(jdbcTemplate);
        assertThat(sql).contains("target_url_masked");
        assertThat(sql).doesNotContain("notify_config_snapshot_json");
    }

    @Test
    void detailRecordPageShouldRejectUnknownSectionBeforeQueryingDatabase() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        JdbcAdminTransactionQueryService service = buildService(jdbcTemplate);
        LocalDateTime transactionTime = LocalDateTime.of(2026, 11, 8, 12, 0);

        assertThatThrownBy(() -> service.pageDetailRecords(
                "transaction-a", transactionTime, transactionTime, "UNKNOWN", 1L, 10L))
                .isInstanceOf(ApiException.class);

        verify(jdbcTemplate, never()).query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class));
    }

    @Test
    void detailRecordPageShouldEnforceResultBudgetBeforeQueryingDatabase() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        JdbcAdminTransactionQueryService service = buildService(jdbcTemplate);
        LocalDateTime transactionTime = LocalDateTime.of(2026, 11, 8, 12, 0);

        assertThatThrownBy(() -> service.pageDetailRecords(
                "transaction-a", transactionTime, transactionTime, "CHANNEL", 1L, 101L))
                .isInstanceOf(ApiException.class);

        verify(jdbcTemplate, never()).query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class));
    }

    @Test
    void detailPayloadShouldRejectUnsupportedRecordTypeBeforeQueryingDatabase() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        JdbcAdminTransactionQueryService service = buildService(jdbcTemplate);
        LocalDateTime transactionTime = LocalDateTime.of(2026, 11, 8, 12, 0);

        assertThatThrownBy(() -> service.detailRecordPayload(
                "transaction-a", transactionTime, transactionTime, "CHANNEL_REQUEST", "request-a"))
                .isInstanceOf(ApiException.class);

        verify(jdbcTemplate, never()).query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class));
    }

    @Test
    void detailSummaryProjectionsShouldExcludeLargeMaskedBodies() {
        String channelProjection = (String) ReflectionTestUtils.getField(
                JdbcAdminTransactionQueryService.class, "TRANSACTION_CHANNEL_INTERACTION_SUMMARY_PROJECTION");
        String callbackProjection = (String) ReflectionTestUtils.getField(
                JdbcAdminTransactionQueryService.class, "TRANSACTION_CHANNEL_CALLBACK_LOG_SUMMARY_PROJECTION");
        String notificationProjection = (String) ReflectionTestUtils.getField(
                JdbcAdminTransactionQueryService.class, "TRANSACTION_MERCHANT_NOTIFICATION_LOG_SUMMARY_PROJECTION");
        String merchantApiProjection = (String) ReflectionTestUtils.getField(
                JdbcAdminTransactionQueryService.class, "TRANSACTION_MERCHANT_API_LOG_SUMMARY_PROJECTION");

        assertThat(channelProjection).contains("NULL AS request_body_json_masked", "NULL AS response_body_json_masked");
        assertThat(callbackProjection).contains("NULL AS request_body_json_masked", "NULL AS platform_response_body");
        assertThat(notificationProjection).contains("NULL AS request_body_json_masked", "NULL AS response_body_json_masked");
        assertThat(merchantApiProjection).contains("NULL AS request_plain_json_masked", "NULL AS response_plain_json_masked");
    }

    private JdbcAdminTransactionQueryService buildService(NamedParameterJdbcTemplate jdbcTemplate) {
        return new JdbcAdminTransactionQueryService(
                jdbcTemplate,
                mock(AdminRiskTimelineQueryService.class));
    }

    /**
     * 调用强类型详情查询私有方法，供 SQL 过滤和安全投影测试复用。
     */
    @SuppressWarnings("unchecked")
    private <T> List<T> invokeSelectDetailRowsByOperationId(JdbcAdminTransactionQueryService service,
                                                             String logicalTable,
                                                             String projection,
                                                             RowMapper<T> rowMapper) {
        return (List<T>) ReflectionTestUtils.invokeMethod(
                service,
                "selectDetailRowsByOperationId",
                logicalTable,
                projection,
                LocalDateTime.of(2026, 7, 21, 0, 0),
                LocalDateTime.of(2026, 7, 21, 23, 59),
                "OP202607211953260920709",
                rowMapper);
    }

    /**
     * 捕获详情查询 SQL。
     */
    private String captureQuerySql(NamedParameterJdbcTemplate jdbcTemplate) {
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(
                sqlCaptor.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));
        return sqlCaptor.getValue();
    }

    private TransactionOperationResponse operation(String operationId,
                                                   String transactionId,
                                                   LocalDateTime transactionDateTime) {
        TransactionOperationResponse operation = new TransactionOperationResponse();
        operation.setOperationId(operationId);
        operation.setTransactionId(transactionId);
        operation.setTransactionDateTime(transactionDateTime);
        return operation;
    }

    private TransactionOrderResponse order(String operationId, LocalDateTime transactionDateTime) {
        TransactionOrderResponse order = new TransactionOrderResponse();
        order.setOperationId(operationId);
        order.setTransactionDateTime(transactionDateTime);
        return order;
    }
}
