package com.scott.payment.merchant.service.impl;

import com.scott.payment.component.db.sharding.TransactionLogicalReadExecutor;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.merchant.dto.transaction.MerchantNotificationDTOs.DeliveryLog;
import com.scott.payment.merchant.dto.transaction.MerchantNotificationDTOs.NotificationQuery;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 商户交易通知 JDBC 查询测试，保护租户隔离、分片谓词和脱敏投影。
 */
class JdbcMerchantNotificationQueryServiceTests {

    private static final LocalDateTime BEGIN_TIME = LocalDateTime.of(2026, 11, 4, 0, 0);
    private static final LocalDateTime END_TIME = LocalDateTime.of(2026, 11, 4, 23, 59, 59);
    private static final LocalDateTime TRANSACTION_TIME =
            LocalDateTime.of(2026, 11, 4, 16, 20, 0, 123_000_000);

    /**
     * 分页统计 SQL 必须始终绑定认证商户和半开交易时间范围。
     */
    @Test
    void pageShouldBindMerchantAndShardTimeBeforeOptionalFilters() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        TransactionLogicalReadExecutor readExecutor = executingReadExecutor();
        when(jdbcTemplate.queryForObject(
                anyString(), any(MapSqlParameterSource.class), eq(Long.class))).thenReturn(0L);
        JdbcMerchantNotificationQueryService service = service(jdbcTemplate, readExecutor);
        NotificationQuery query = query();
        query.setMerchantOrderNo("order-1");

        service.page("merchant-a", query);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> parameters =
                ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).queryForObject(sql.capture(), parameters.capture(), eq(Long.class));
        assertThat(sql.getValue()).contains(
                "n.merchant_id = :merchantId",
                "n.transaction_date_time >= :beginTime",
                "n.transaction_date_time < :endTime",
                "n.merchant_order_no = :merchantOrderNo");
        assertThat(parameters.getValue().getValue("merchantId")).isEqualTo("merchant-a");
        assertThat(parameters.getValue().getValue("merchantOrderNo")).isEqualTo("order-1");
        verify(readExecutor).read(any());
        verify(readExecutor, never()).readPrimary(any());
    }

    /**
     * 详情首查必须同时绑定商户号、通知任务号和毫秒级真实分片时间。
     */
    @Test
    @SuppressWarnings("unchecked")
    void detailShouldBindMerchantNotifyIdAndExactShardTime() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        TransactionLogicalReadExecutor readExecutor = executingReadExecutor();
        when(jdbcTemplate.query(
                anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(Collections.emptyList());
        JdbcMerchantNotificationQueryService service = service(jdbcTemplate, readExecutor);
        LocalDateTime transactionTime =
                LocalDateTime.of(2026, 11, 4, 16, 20, 0, 123_000_000);

        assertThatThrownBy(() -> service.detail("merchant-a", "notify-1", transactionTime))
                .isInstanceOf(RuntimeException.class);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> parameters =
                ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).query(sql.capture(), parameters.capture(), any(RowMapper.class));
        assertThat(sql.getValue()).contains(
                "n.merchant_id = :merchantId",
                "n.notify_id = :notifyId",
                "n.transaction_date_time = :transactionDateTime",
                "target_url_masked",
                "payload_json_masked")
                .doesNotContain("callback_url", "target_url_hash", "notify_config_version", " payload_json, ");
        assertThat(parameters.getValue().getValue("merchantId")).isEqualTo("merchant-a");
        assertThat(parameters.getValue().getValue("notifyId")).isEqualTo("notify-1");
        assertThat(parameters.getValue().getValue("transactionDateTime")).isEqualTo(transactionTime);
        verify(readExecutor).readPrimary(any());
    }

    /**
     * 投递日志必须绑定认证商户与精确分片时间，并按最新尝试倒序分页。
     */
    @Test
    @SuppressWarnings("unchecked")
    void deliveryLogsShouldEnforceMerchantBoundaryAndPageNewestFirst() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        TransactionLogicalReadExecutor readExecutor = executingReadExecutor();
        DeliveryLog latestAttempt = new DeliveryLog();
        latestAttempt.setAttemptNo(12);
        when(jdbcTemplate.queryForObject(
                anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(1L, 27L);
        when(jdbcTemplate.query(
                anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(latestAttempt));
        JdbcMerchantNotificationQueryService service = service(jdbcTemplate, readExecutor);

        PageResult<DeliveryLog> page = service.pageDeliveryLogs(
                "merchant-a", "notify-1", TRANSACTION_TIME, 2L, 10L);

        assertThat(page.getTotal()).isEqualTo(27L);
        assertThat(page.getRecords()).containsExactly(latestAttempt);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> parameters =
                ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).query(sql.capture(), parameters.capture(), any(RowMapper.class));
        assertThat(sql.getValue()).contains(
                "l.merchant_id = :merchantId",
                "l.notify_id = :notifyId",
                "l.transaction_date_time = :transactionDateTime",
                "ORDER BY l.attempt_no DESC, l.id DESC",
                "LIMIT :offset, :limit");
        assertThat(parameters.getValue().getValue("merchantId")).isEqualTo("merchant-a");
        assertThat(parameters.getValue().getValue("offset")).isEqualTo(10L);
        assertThat(parameters.getValue().getValue("limit")).isEqualTo(10L);
        verify(readExecutor).readPrimary(any());
    }

    /**
     * 非法分页参数必须在访问交易数据源前被拒绝。
     */
    @Test
    void deliveryLogsShouldRejectPageSizeAboveBudgetBeforeDatabaseAccess() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        TransactionLogicalReadExecutor readExecutor = mock(TransactionLogicalReadExecutor.class);
        JdbcMerchantNotificationQueryService service = service(jdbcTemplate, readExecutor);

        assertThatThrownBy(() -> service.pageDeliveryLogs(
                "merchant-a", "notify-1", TRANSACTION_TIME, 1L, 101L))
                .isInstanceOf(RuntimeException.class);

        verify(readExecutor, never()).readPrimary(any());
        verify(jdbcTemplate, never()).queryForObject(
                anyString(), any(MapSqlParameterSource.class), eq(Long.class));
    }

    /**
     * 人工重发资格查询必须同时绑定认证商户、任务号、交易终态和自动计划终态。
     */
    @Test
    @SuppressWarnings("unchecked")
    void retryTargetShouldEnforceMerchantOwnershipAndEligibleStates() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        TransactionLogicalReadExecutor readExecutor = executingReadExecutor();
        when(jdbcTemplate.query(
                anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(Collections.emptyList());
        JdbcMerchantNotificationQueryService service = service(jdbcTemplate, readExecutor);

        assertThatThrownBy(() -> service.retryTarget(
                "merchant-a", "notify-1", TRANSACTION_TIME))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("not available");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> parameters =
                ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).query(sql.capture(), parameters.capture(), any(RowMapper.class));
        assertThat(sql.getValue()).contains(
                "notification.merchant_id = :merchantId",
                "notification.notify_id = :notifyId",
                "operation_record.transaction_status IN ('SUCCESS', 'FAILED')",
                "notification.notify_status IN ('SUCCESS', 'CLOSED')",
                "notification.next_retry_time IS NULL");
        assertThat(parameters.getValue().getValue("merchantId")).isEqualTo("merchant-a");
        assertThat(parameters.getValue().getValue("notifyId")).isEqualTo("notify-1");
        verify(readExecutor).readPrimary(any());
    }

    /**
     * 缺少认证商户号时不得进入交易数据源或访问数据库。
     */
    @Test
    void pageShouldRejectMissingMerchantBeforeDatabaseAccess() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        TransactionLogicalReadExecutor readExecutor = mock(TransactionLogicalReadExecutor.class);
        JdbcMerchantNotificationQueryService service = service(jdbcTemplate, readExecutor);

        assertThatThrownBy(() -> service.page(" ", query())).isInstanceOf(RuntimeException.class);

        verify(readExecutor, never()).read(any());
        verify(jdbcTemplate, never()).queryForObject(
                anyString(), any(MapSqlParameterSource.class), eq(Long.class));
    }

    private JdbcMerchantNotificationQueryService service(
            NamedParameterJdbcTemplate jdbcTemplate,
            TransactionLogicalReadExecutor readExecutor) {
        return new JdbcMerchantNotificationQueryService(
                jdbcTemplate, readExecutor, new TransactionShardingProperties());
    }

    private TransactionLogicalReadExecutor executingReadExecutor() {
        TransactionLogicalReadExecutor executor = mock(TransactionLogicalReadExecutor.class);
        when(executor.read(any())).thenAnswer(invocation ->
                invocation.<Supplier<?>>getArgument(0).get());
        when(executor.readPrimary(any())).thenAnswer(invocation ->
                invocation.<Supplier<?>>getArgument(0).get());
        return executor;
    }

    private NotificationQuery query() {
        NotificationQuery query = new NotificationQuery();
        query.setBeginTime(BEGIN_TIME);
        query.setEndTime(END_TIME);
        return query;
    }
}
