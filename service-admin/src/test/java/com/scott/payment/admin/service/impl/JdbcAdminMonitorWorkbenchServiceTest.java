package com.scott.payment.admin.service.impl;

import com.scott.payment.admin.application.monitor.AdminMonitorCacheApplicationService;
import com.scott.payment.admin.application.monitor.AdminMonitorDatasourceApplicationService;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.AlertActionRequest;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.AlertDetailResponse;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.AlertQuery;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.AlertSearchResponse;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.ApiMonitorQuery;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.ApiMonitorResponse;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.ChannelMonitorQuery;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.ChannelMonitorResponse;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.DependencyStatus;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.MetricSummary;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.ProviderCapability;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.SecurityStatisticsResponse;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.TimeRangeQuery;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.WebhookMonitorQuery;
import com.scott.payment.admin.dto.monitor.MonitorWorkbenchDTOs.WebhookMonitorResponse;
import com.scott.payment.admin.service.AdminTransactionQueryService;
import com.scott.payment.admin.service.monitor.AdminRuntimeMonitorSampler;
import com.scott.payment.admin.service.monitor.MonitorSensitiveTextSanitizer;
import com.scott.payment.admin.service.monitor.MonitorTimeRangeNormalizer;
import com.scott.payment.admin.service.monitor.RocketMqAdminMonitorProvider;
import com.scott.payment.admin.service.monitor.RocketMqAdminMonitorProvider.Snapshot;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.sharding.TransactionLogicalReadExecutor;
import com.scott.payment.component.db.sharding.TransactionQueryJdbcTemplateFactory;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JdbcAdminMonitorWorkbenchServiceTest
 * @date : 2026-09-14 12:30
 * @email : scott_x@163.com
 * @description : JDBC 系统监控聚合、能力声明、查询口径和告警状态更新回归测试。
 * @status : create
 */
class JdbcAdminMonitorWorkbenchServiceTest {

    @Test
    void shouldExposeRocketMqProviderCapabilityFromLiveSnapshot() {
        NamedParameterJdbcTemplate jdbcTemplate = monitorJdbcTemplate();
        RocketMqAdminMonitorProvider provider = mock(RocketMqAdminMonitorProvider.class);
        when(provider.snapshot()).thenReturn(Snapshot.available(1, 2, 2, 8));
        JdbcAdminMonitorWorkbenchService service = createService(
                jdbcTemplate, monitorJdbcTemplateFactory(jdbcTemplate), provider);

        ProviderCapability capability = service.capabilities().stream()
                .filter(item -> "ROCKETMQ_ADMIN".equals(item.getProvider()))
                .findFirst()
                .orElseThrow();

        assertThat(capability.getStatus()).isEqualTo(RocketMqAdminMonitorProvider.STATUS_AVAILABLE);
        assertThat(capability.getReason()).contains("Broker 2", "Topic 8");
    }

    @Test
    void shouldMapUnavailableRocketMqSnapshotToDependencyError() {
        NamedParameterJdbcTemplate jdbcTemplate = monitorJdbcTemplate();
        RocketMqAdminMonitorProvider provider = mock(RocketMqAdminMonitorProvider.class);
        when(provider.snapshot()).thenReturn(Snapshot.unavailable("RocketMQ Admin 连接或查询失败（TimeoutException）"));
        JdbcAdminMonitorWorkbenchService service = createService(
                jdbcTemplate, monitorJdbcTemplateFactory(jdbcTemplate), provider);

        DependencyStatus status = service.rocketMqDependencyStatus();

        assertThat(status.getKey()).isEqualTo("rocketmq");
        assertThat(status.getStatus()).isEqualTo("ERROR");
        assertThat(status.getValue()).isEqualTo("Unavailable");
        assertThat(status.getDescription()).contains("TimeoutException");
    }

    @Test
    void shouldExposeDraftCurrentYearSettlementCalendarAsDependencyError() {
        NamedParameterJdbcTemplate jdbcTemplate = monitorJdbcTemplate();
        when(jdbcTemplate.queryForList(contains("FROM settlement_calendar_year y"),
                any(MapSqlParameterSource.class))).thenReturn(List.of(Map.of(
                "calendar_year", 2026,
                "year_status", "DRAFT",
                "total_days", 365,
                "day_count", 365L)));
        JdbcAdminMonitorWorkbenchService service = createService(
                jdbcTemplate, monitorJdbcTemplateFactory(jdbcTemplate));

        DependencyStatus status = service.settlementCalendarDependencyStatus(2026);

        assertThat(status.getKey()).isEqualTo("settlementCalendar");
        assertThat(status.getStatus()).isEqualTo("ERROR");
        assertThat(status.getValue()).isEqualTo("DRAFT · 365/365");
        assertThat(status.getDescription()).contains("Confirm the 2026 settlement calendar");
    }

    @Test
    void shouldExposeCompleteActiveSettlementCalendarAsHealthyDependency() {
        NamedParameterJdbcTemplate jdbcTemplate = monitorJdbcTemplate();
        when(jdbcTemplate.queryForList(contains("FROM settlement_calendar_year y"),
                any(MapSqlParameterSource.class))).thenReturn(List.of(Map.of(
                "calendar_year", 2026,
                "year_status", "ACTIVE",
                "total_days", 365,
                "day_count", 365L)));
        JdbcAdminMonitorWorkbenchService service = createService(
                jdbcTemplate, monitorJdbcTemplateFactory(jdbcTemplate));

        DependencyStatus status = service.settlementCalendarDependencyStatus(2026);

        assertThat(status.getStatus()).isEqualTo("HEALTHY");
        assertThat(status.getValue()).isEqualTo("ACTIVE · 365/365");
    }

    @Test
    void shouldPreserveNullWhenApiHasNoSamples() {
        NamedParameterJdbcTemplate jdbcTemplate = monitorJdbcTemplate();
        TransactionQueryJdbcTemplateFactory factory = monitorJdbcTemplateFactory(jdbcTemplate);
        Map<String, Object> totals = new HashMap<>();
        totals.put("request_count", 0L);
        totals.put("failed_count", 0L);
        totals.put("average_millis", null);
        totals.put("duration_count", 0L);
        when(jdbcTemplate.queryForMap(contains("COUNT(*) AS request_count"), any(MapSqlParameterSource.class)))
                .thenReturn(totals);
        when(jdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class))).thenReturn(List.of());
        JdbcAdminMonitorWorkbenchService service = createService(jdbcTemplate, factory);

        ApiMonitorResponse response = service.searchApis(apiQuery(
                LocalDateTime.of(2026, 9, 13, 10, 0),
                LocalDateTime.of(2026, 9, 13, 11, 0)));

        assertThat(summary(response.getSummaries(), "requests").getValue()).isEqualByComparingTo("0");
        assertThat(summary(response.getSummaries(), "successRate").getValue()).isNull();
        assertThat(summary(response.getSummaries(), "successRate").getStatus()).isEqualTo("UNKNOWN");
        assertThat(summary(response.getSummaries(), "average").getValue()).isNull();
        assertThat(summary(response.getSummaries(), "p95").getValue()).isNull();
        assertThat(summary(response.getSummaries(), "p99").getValue()).isNull();
        assertThat(response.getLatencyTrend()).allSatisfy(bucket ->
                assertThat(bucket.getValues()).allSatisfy((key, value) -> assertThat(value).isNull()));
    }

    @Test
    void shouldUseDatabaseAggregateForApiCountsAboveRawRowLimit() {
        NamedParameterJdbcTemplate jdbcTemplate = monitorJdbcTemplate();
        TransactionQueryJdbcTemplateFactory factory = monitorJdbcTemplateFactory(jdbcTemplate);
        List<String> executedSql = new ArrayList<>();
        when(jdbcTemplate.queryForMap(contains("COUNT(*) AS request_count"), any(MapSqlParameterSource.class)))
                .thenReturn(Map.of(
                        "request_count", 1501L,
                        "failed_count", 1L,
                        "average_millis", new BigDecimal("12.50"),
                        "duration_count", 1501L));
        when(jdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0, String.class);
            executedSql.add(sql);
            if (sql.contains("AS percentile_value")) {
                return List.of(Map.of("percentile_value", 25));
            }
            if (sql.contains("PARTITION BY api_operation, request_path")) {
                return List.of(Map.of(
                        "api_operation", "PAYMENT_CREATE",
                        "request_path", "/api/rest/payment/v1/create",
                        "request_count", 1501L,
                        "failed_count", 1L,
                        "average_millis", new BigDecimal("12.50"),
                        "p95_millis", 25,
                        "p99_millis", 31,
                        "latest_response_code", "0000",
                        "latest_failure_time", LocalDateTime.of(2026, 9, 13, 10, 30)));
            }
            return List.of();
        });
        JdbcAdminMonitorWorkbenchService service = createService(jdbcTemplate, factory);
        ApiMonitorQuery query = apiQuery(
                LocalDateTime.of(2026, 9, 13, 10, 0),
                LocalDateTime.of(2026, 9, 13, 11, 0));

        ApiMonitorResponse response = service.searchApis(query);

        assertThat(summaryValue(response.getSummaries(), "requests")).isEqualByComparingTo("1501");
        assertThat(summaryValue(response.getSummaries(), "successRate")).isEqualByComparingTo("99.93");
        assertThat(response.getPage().getRecords()).singleElement().satisfies(item -> {
            assertThat(item.getRequestCount()).isEqualTo(1501L);
            assertThat(item.getFailedCount()).isEqualTo(1L);
            assertThat(item.getP95Millis()).isEqualByComparingTo("25");
        });
        assertThat(executedSql).allSatisfy(sql -> assertThat(sql).doesNotContain("LIMIT 1000"));
    }

    @Test
    void shouldUseGlobalOrderedPercentilesAcrossQuarterShards() {
        NamedParameterJdbcTemplate jdbcTemplate = monitorJdbcTemplate();
        TransactionQueryJdbcTemplateFactory factory = monitorJdbcTemplateFactory(jdbcTemplate);
        List<String> executedSql = new ArrayList<>();
        when(jdbcTemplate.queryForMap(contains("COUNT(*) AS request_count"), any(MapSqlParameterSource.class)))
                .thenReturn(Map.of(
                        "request_count", 200L,
                        "failed_count", 0L,
                        "average_millis", 100,
                        "duration_count", 200L));
        when(jdbcTemplate.queryForObject(contains("COUNT(duration_millis)"),
                any(MapSqlParameterSource.class), org.mockito.ArgumentMatchers.eq(Long.class)))
                .thenReturn(200L);
        when(jdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0, String.class);
            executedSql.add(sql);
            if (sql.contains("AS percentile_value")) {
                return List.of(Map.of("percentile_value", 190));
            }
            if (sql.contains("GROUP BY api_operation, request_path")
                    && !sql.contains("PARTITION BY api_operation, request_path")) {
                return List.of(Map.of(
                        "api_operation", "PAYMENT_QUERY",
                        "request_path", "/api/rest/payment/v1/query",
                        "request_count", 200L,
                        "failed_count", 0L,
                        "average_millis", 100,
                        "latest_failure_time", LocalDateTime.of(2026, 9, 30, 23, 30)));
            }
            if (sql.contains("SELECT merchant_response_code")) {
                return List.of(Map.of("merchant_response_code", "0000"));
            }
            return List.of();
        });
        JdbcAdminMonitorWorkbenchService service = createService(jdbcTemplate, factory);
        ApiMonitorQuery query = apiQuery(
                LocalDateTime.of(2026, 9, 30, 23, 0),
                LocalDateTime.of(2026, 10, 1, 1, 0));

        ApiMonitorResponse response = service.searchApis(query);

        assertThat(response.getPage().getRecords()).singleElement().satisfies(item -> {
            assertThat(item.getP95Millis()).isEqualByComparingTo("190");
            assertThat(item.getP99Millis()).isEqualByComparingTo("190");
        });
        assertThat(executedSql).allSatisfy(sql ->
                assertThat(sql).doesNotContain("COUNT(*) OVER (PARTITION BY api_operation, request_path)"));
        assertThat(executedSql).anySatisfy(sql -> assertThat(sql)
                .contains("ORDER BY duration_millis, id")
                .contains("LIMIT :percentileOffset, 1"));
    }

    @Test
    void shouldKeepLowSuccessChannelHealthyWithoutConfiguredAlert() {
        ChannelMonitorResponse response = channelResponseForAlertSeverity(null, 1800L, 1200L, 130L);

        assertThat(summaryValue(response.getSummaries(), "timeouts")).isEqualByComparingTo("130");
        assertThat(summaryValue(response.getSummaries(), "failed")).isEqualByComparingTo("600");
        assertThat(summaryValue(response.getSummaries(), "healthyChannels")).isEqualByComparingTo("1");
        assertThat(response.getPage().getRecords()).singleElement().satisfies(item -> {
            assertThat(item.getSuccessRate()).isEqualByComparingTo("66.67");
            assertThat(item.getStatus()).isEqualTo("HEALTHY");
        });
    }

    @Test
    void shouldKeepEmptyChannelSuccessRateBucketsNull() {
        NamedParameterJdbcTemplate jdbcTemplate = monitorJdbcTemplate();
        TransactionQueryJdbcTemplateFactory factory = monitorJdbcTemplateFactory(jdbcTemplate);
        when(jdbcTemplate.queryForMap(contains("FROM transaction_channel_request r"),
                any(MapSqlParameterSource.class))).thenReturn(Map.of(
                "request_count", 0L,
                "success_count", 0L,
                "timeout_count", 0L));
        when(jdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class))).thenReturn(List.of());
        JdbcAdminMonitorWorkbenchService service = createService(jdbcTemplate, factory);
        ChannelMonitorQuery query = new ChannelMonitorQuery();
        query.setBeginTime(LocalDateTime.of(2026, 9, 13, 10, 0));
        query.setEndTime(LocalDateTime.of(2026, 9, 13, 11, 0));

        ChannelMonitorResponse response = service.searchChannels(query);

        assertThat(response.getSuccessRateTrend()).isNotEmpty().allSatisfy(bucket ->
                assertThat(bucket.getValues()).containsEntry("successRate", null));
    }

    @ParameterizedTest
    @CsvSource({"1,WARNING", "2,ERROR", "3,ERROR"})
    void shouldMapConfiguredChannelAlertSeverityToStatus(int severityRank, String expectedStatus) {
        ChannelMonitorResponse response = channelResponseForAlertSeverity(severityRank, 10L, 9L, 0L);

        assertThat(response.getPage().getRecords()).singleElement()
                .extracting(item -> item.getStatus())
                .isEqualTo(expectedStatus);
    }

    @Test
    void shouldUseDatabaseTotalsAndPaginationForWebhookTasks() {
        NamedParameterJdbcTemplate jdbcTemplate = monitorJdbcTemplate();
        TransactionQueryJdbcTemplateFactory factory = monitorJdbcTemplateFactory(jdbcTemplate);
        List<String> executedSql = new ArrayList<>();
        List<MapSqlParameterSource> executedParams = new ArrayList<>();
        when(jdbcTemplate.queryForMap(contains("COUNT(*) AS notification_count"), any(MapSqlParameterSource.class)))
                .thenReturn(Map.of(
                        "notification_count", 2401L,
                        "success_count", 2100L,
                        "failed_count", 201L,
                        "retrying_count", 100L,
                        "final_failed_count", 81L));
        when(jdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0, String.class);
            MapSqlParameterSource params = invocation.getArgument(1, MapSqlParameterSource.class);
            executedSql.add(sql);
            executedParams.add(params);
            if (sql.contains("ORDER BY n.update_time DESC")) {
                return List.of(Map.ofEntries(
                        Map.entry("notify_id", "NOTIFY-21"),
                        Map.entry("transaction_id", "TX-21"),
                        Map.entry("transaction_date_time", LocalDateTime.of(2026, 9, 13, 10, 21)),
                        Map.entry("merchant_id", "M-1"),
                        Map.entry("event_type", "PAYMENT_SUCCEEDED"),
                        Map.entry("notify_status", "FAILED"),
                        Map.entry("last_attempt_no", 3),
                        Map.entry("fail_reason", "HTTP 500"),
                        Map.entry("http_status", 500),
                        Map.entry("duration_millis", 200),
                        Map.entry("notify_time", LocalDateTime.of(2026, 9, 13, 10, 22)),
                        Map.entry("error_message", "HTTP 500")));
            }
            return List.of();
        });
        JdbcAdminMonitorWorkbenchService service = createService(jdbcTemplate, factory);
        WebhookMonitorQuery query = new WebhookMonitorQuery();
        query.setBeginTime(LocalDateTime.of(2026, 9, 13, 10, 0));
        query.setEndTime(LocalDateTime.of(2026, 9, 13, 11, 0));
        query.setPageNo(2);
        query.setPageSize(20);

        WebhookMonitorResponse response = service.searchWebhooks(query);

        assertThat(summaryValue(response.getSummaries(), "sent")).isEqualByComparingTo("2401");
        assertThat(summaryValue(response.getSummaries(), "failed")).isEqualByComparingTo("201");
        assertThat(summaryValue(response.getSummaries(), "finalFailed")).isEqualByComparingTo("81");
        assertThat(response.getPage().getTotal()).isEqualTo(2401L);
        assertThat(response.getPage().getRecords()).singleElement()
                .extracting(item -> item.getEventId())
                .isEqualTo("NOTIFY-21");
        int pageQueryIndex = -1;
        for (int index = 0; index < executedSql.size(); index++) {
            if (executedSql.get(index).contains("ORDER BY n.update_time DESC")) {
                pageQueryIndex = index;
                break;
            }
        }
        assertThat(pageQueryIndex).isNotNegative();
        assertThat(executedSql.get(pageQueryIndex)).contains("LIMIT :offset, :limit");
        assertThat(executedParams.get(pageQueryIndex).getValue("offset")).isEqualTo(20L);
        assertThat(executedParams.get(pageQueryIndex).getValue("limit")).isEqualTo(20L);
        assertThat(executedSql).allSatisfy(sql -> assertThat(sql).doesNotContain("LIMIT 1000"));
        assertThat(executedSql.stream()
                .filter(sql -> sql.contains("FROM transaction_merchant_notification_log l")
                        && sql.contains("JOIN transaction_merchant_notification n"))
                .toList())
                .isNotEmpty()
                .allSatisfy(sql -> assertThat(sql)
                        .contains("l.transaction_date_time >= :beginTime")
                        .contains("n.transaction_date_time >= :beginTime"));
    }

    @Test
    void shouldKeepEmptyWebhookSuccessRateBucketsAndSummaryNull() {
        NamedParameterJdbcTemplate jdbcTemplate = monitorJdbcTemplate();
        TransactionQueryJdbcTemplateFactory factory = monitorJdbcTemplateFactory(jdbcTemplate);
        when(jdbcTemplate.queryForMap(contains("COUNT(*) AS notification_count"),
                any(MapSqlParameterSource.class))).thenReturn(Map.of(
                        "notification_count", 0L,
                        "success_count", 0L,
                        "failed_count", 0L,
                        "retrying_count", 0L,
                        "final_failed_count", 0L));
        when(jdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class))).thenReturn(List.of());
        JdbcAdminMonitorWorkbenchService service = createService(jdbcTemplate, factory);
        WebhookMonitorQuery query = new WebhookMonitorQuery();
        query.setBeginTime(LocalDateTime.of(2026, 9, 13, 10, 0));
        query.setEndTime(LocalDateTime.of(2026, 9, 13, 11, 0));

        WebhookMonitorResponse response = service.searchWebhooks(query);

        assertThat(summary(response.getSummaries(), "successRate").getValue()).isNull();
        assertThat(summary(response.getSummaries(), "successRate").getStatus()).isEqualTo("UNKNOWN");
        assertThat(response.getSuccessRateTrend()).isNotEmpty().allSatisfy(bucket ->
                assertThat(bucket.getValues()).containsEntry("successRate", null));
    }

    @Test
    void shouldApplyChannelErrorAndPaymentMethodFiltersToEveryChannelFactQuery() {
        NamedParameterJdbcTemplate jdbcTemplate = monitorJdbcTemplate();
        TransactionQueryJdbcTemplateFactory factory = monitorJdbcTemplateFactory(jdbcTemplate);
        List<String> executedSql = new ArrayList<>();
        List<MapSqlParameterSource> executedParams = new ArrayList<>();
        when(jdbcTemplate.queryForMap(contains("FROM transaction_channel_request r"),
                any(MapSqlParameterSource.class))).thenAnswer(invocation -> {
            executedSql.add(invocation.getArgument(0, String.class));
            executedParams.add(invocation.getArgument(1, MapSqlParameterSource.class));
            return Map.of("request_count", 0L, "success_count", 0L, "timeout_count", 0L);
        });
        when(jdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class))).thenAnswer(invocation -> {
            executedSql.add(invocation.getArgument(0, String.class));
            executedParams.add(invocation.getArgument(1, MapSqlParameterSource.class));
            return List.of();
        });
        JdbcAdminMonitorWorkbenchService service = createService(jdbcTemplate, factory);
        ChannelMonitorQuery query = new ChannelMonitorQuery();
        query.setBeginTime(LocalDateTime.of(2026, 9, 13, 10, 0));
        query.setEndTime(LocalDateTime.of(2026, 9, 13, 11, 0));
        query.setErrorCode("DECLINED");
        query.setPaymentMethod("CARD");

        service.searchChannels(query);

        int channelFactQueryCount = 0;
        for (int index = 0; index < executedSql.size(); index++) {
            String sql = executedSql.get(index);
            if (!sql.contains("transaction_channel_request r")) {
                continue;
            }
            channelFactQueryCount++;
            assertThat(sql)
                    .contains(":errorCode")
                    .contains("NULLIF(TRIM(r.platform_result_code), '')")
                    .contains("FROM transaction_order payment_order")
                    .contains(":paymentMethod");
            MapSqlParameterSource params = executedParams.get(index);
            assertThat(params.getValue("errorCode")).isEqualTo("DECLINED");
            assertThat(params.getValue("paymentMethod")).isEqualTo("CARD");
        }
        assertThat(channelFactQueryCount).isPositive();
    }

    @Test
    void shouldAggregateAndPageAlertsWithoutRawRowLimit() {
        NamedParameterJdbcTemplate jdbcTemplate = monitorJdbcTemplate();
        TransactionQueryJdbcTemplateFactory factory = monitorJdbcTemplateFactory(jdbcTemplate);
        List<String> executedSql = new ArrayList<>();
        List<MapSqlParameterSource> executedParams = new ArrayList<>();
        when(jdbcTemplate.queryForMap(contains("COUNT(*) AS total_count"), any(MapSqlParameterSource.class)))
                .thenReturn(Map.of(
                        "total_count", 1501L,
                        "open_count", 1200L,
                        "processing_count", 100L,
                        "critical_count", 35L,
                        "recovered_count", 166L));
        when(jdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0, String.class);
            MapSqlParameterSource params = invocation.getArgument(1, MapSqlParameterSource.class);
            executedSql.add(sql);
            executedParams.add(params);
            if (sql.contains("AS warning_count")) {
                return List.of(Map.of(
                        "bucket_time", LocalDateTime.of(2026, 9, 13, 10, 0),
                        "warning_count", 900L,
                        "error_count", 500L,
                        "critical_count", 101L));
            }
            if (sql.contains("source_type AS category_key")) {
                return List.of(Map.of("category_key", "CHANNEL", "metric_value", 1001L));
            }
            if (sql.contains("ORDER BY last_occurred_at DESC")) {
                return List.of(Map.ofEntries(
                        Map.entry("source_type", "CHANNEL"),
                        Map.entry("source_id", "ALERT-21"),
                        Map.entry("level", "ERROR"),
                        Map.entry("service_name", "channel-test"),
                        Map.entry("module_name", "CHANNEL"),
                        Map.entry("title", "Low success rate"),
                        Map.entry("content", "Channel TEST triggered SUCCESS_RATE_LOW"),
                        Map.entry("occurrence_count", 1L),
                        Map.entry("first_occurred_at", LocalDateTime.of(2026, 9, 13, 10, 20)),
                        Map.entry("last_occurred_at", LocalDateTime.of(2026, 9, 13, 10, 20)),
                        Map.entry("status", "OPEN"),
                        Map.entry("owner_account_id", "1001"),
                        Map.entry("owner_name", "operator"),
                        Map.entry("handle_remark", "investigating"),
                        Map.entry("version", 2)));
            }
            return List.of();
        });
        JdbcAdminMonitorWorkbenchService service = createService(jdbcTemplate, factory);
        AlertQuery query = new AlertQuery();
        query.setBeginTime(LocalDateTime.of(2026, 9, 13, 10, 0));
        query.setEndTime(LocalDateTime.of(2026, 9, 13, 11, 0));
        query.setPageNo(2);
        query.setPageSize(20);

        AlertSearchResponse response = service.searchAlerts(query);

        assertThat(response.getPage().getTotal()).isEqualTo(1501L);
        assertThat(summaryValue(response.getSummaries(), "openAlerts")).isEqualByComparingTo("1200");
        assertThat(response.getSourceTop()).singleElement().satisfies(metric ->
                assertThat(metric.getValue()).isEqualByComparingTo("1001"));
        assertThat(response.getPage().getRecords()).singleElement().satisfies(item ->
                assertThat(item.getSourceId()).isEqualTo("ALERT-21"));
        int pageQueryIndex = -1;
        for (int index = 0; index < executedSql.size(); index++) {
            if (executedSql.get(index).contains("ORDER BY last_occurred_at DESC")) {
                pageQueryIndex = index;
                break;
            }
        }
        assertThat(pageQueryIndex).isNotNegative();
        assertThat(executedSql.get(pageQueryIndex)).contains("LIMIT :offset, :limit");
        assertThat(executedParams.get(pageQueryIndex).getValue("offset")).isEqualTo(20L);
        assertThat(executedParams.get(pageQueryIndex).getValue("limit")).isEqualTo(20L);
        assertThat(executedSql).allSatisfy(sql -> assertThat(sql).doesNotContain("LIMIT :limit"));
    }

    @Test
    void shouldPreserveRecoveredSourceStatusWhenOverlayIsStillOpen() {
        NamedParameterJdbcTemplate jdbcTemplate = monitorJdbcTemplate();
        TransactionQueryJdbcTemplateFactory factory = monitorJdbcTemplateFactory(jdbcTemplate);
        when(jdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0, String.class);
            if (sql.contains("SELECT event_code") && sql.contains("WHERE event_code = :sourceId")) {
                return List.of(Map.ofEntries(
                        Map.entry("event_code", "ALERT-1"),
                        Map.entry("rule_name", "Low success rate"),
                        Map.entry("channel_code", "TEST"),
                        Map.entry("rule_type", "SUCCESS_RATE_LOW"),
                        Map.entry("alert_level", "L2_DEGRADED"),
                        Map.entry("event_status", "RESOLVED"),
                        Map.entry("trigger_time", LocalDateTime.of(2026, 9, 13, 10, 0)),
                        Map.entry("acknowledged_time", LocalDateTime.of(2026, 9, 13, 10, 15)),
                        Map.entry("acknowledged_by", "source-operator"),
                        Map.entry("remark", "source recovered")));
            }
            if (sql.contains("FROM monitor_alert_handle_state")) {
                return List.of(Map.of(
                        "source_type", "CHANNEL",
                        "source_id", "ALERT-1",
                        "status", "OPEN",
                        "owner_account_id", "1001",
                        "owner_name", "manual-operator",
                        "handle_remark", "investigating",
                        "version", 3));
            }
            return List.of();
        });
        JdbcAdminMonitorWorkbenchService service = createService(jdbcTemplate, factory);

        AlertDetailResponse response = service.alertDetail("CHANNEL", "ALERT-1");

        assertThat(response.getAlert().getStatus()).isEqualTo("RECOVERED");
        assertThat(response.getAlert().getOwnerAccountId()).isEqualTo("1001");
        assertThat(response.getAlert().getOwnerName()).isEqualTo("manual-operator");
        assertThat(response.getAlert().getHandleRemark()).isEqualTo("investigating");
        assertThat(response.getAlert().getVersion()).isEqualTo(3);
    }

    @Test
    void shouldRejectProcessingWhenSourceHasRecovered() {
        NamedParameterJdbcTemplate jdbcTemplate = monitorJdbcTemplate();
        TransactionQueryJdbcTemplateFactory factory = monitorJdbcTemplateFactory(jdbcTemplate);
        when(jdbcTemplate.queryForList(contains("SELECT event_code"), any(MapSqlParameterSource.class)))
                .thenReturn(List.of(Map.ofEntries(
                        Map.entry("event_code", "ALERT-1"),
                        Map.entry("rule_name", "Low success rate"),
                        Map.entry("channel_code", "TEST"),
                        Map.entry("rule_type", "SUCCESS_RATE_LOW"),
                        Map.entry("alert_level", "L2_DEGRADED"),
                        Map.entry("event_status", "RESOLVED"),
                        Map.entry("trigger_time", LocalDateTime.of(2026, 9, 13, 10, 0)),
                        Map.entry("acknowledged_time", LocalDateTime.of(2026, 9, 13, 10, 15))))) ;
        when(jdbcTemplate.queryForMap(contains("SELECT status, owner_account_id"),
                any(MapSqlParameterSource.class))).thenReturn(Map.of("status", "OPEN", "version", 4));
        JdbcAdminMonitorWorkbenchService service = createService(jdbcTemplate, factory);
        AlertActionRequest request = new AlertActionRequest();
        request.setVersion(4);

        assertThatThrownBy(() -> service.markProcessing("CHANNEL", "ALERT-1", request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("only open alerts can be marked processing");
    }

    @Test
    void shouldAggregateSecurityStatisticsFromMysqlStringBucketsWithoutRawRowLimit() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        JdbcTemplate delegate = mock(JdbcTemplate.class);
        when(jdbcTemplate.getJdbcTemplate()).thenReturn(delegate);
        TransactionQueryJdbcTemplateFactory factory = mock(TransactionQueryJdbcTemplateFactory.class);
        when(factory.create(any(DataSource.class), any(TransactionShardingProperties.class)))
                .thenReturn(jdbcTemplate);
        when(jdbcTemplate.queryForMap(contains("COUNT(*) AS intercept_count"), any(MapSqlParameterSource.class)))
                .thenReturn(Map.of(
                        "intercept_count", 1500L,
                        "critical_count", 35L,
                        "unhandled_count", 120L));
        when(jdbcTemplate.queryForList(contains("GROUP BY bucket_time"), any(MapSqlParameterSource.class)))
                .thenReturn(List.of(Map.of(
                        "bucket_time", "2026-09-13 10:00:00",
                        "metric_value", 1500L)));
        when(jdbcTemplate.queryForList(contains("TRIM(event_type)"), any(MapSqlParameterSource.class)))
                .thenReturn(List.of(Map.of("category_key", "SIGNATURE_INVALID", "metric_value", 900L)));
        when(jdbcTemplate.queryForList(contains("TRIM(merchant_id)"), any(MapSqlParameterSource.class)))
                .thenReturn(List.of(Map.of("category_key", "M1001", "metric_value", 600L)));
        JdbcAdminMonitorWorkbenchService service = createService(jdbcTemplate, factory);
        TimeRangeQuery query = new TimeRangeQuery();
        query.setBeginTime(LocalDateTime.of(2026, 9, 13, 10, 0));
        query.setEndTime(LocalDateTime.of(2026, 9, 13, 11, 0));

        SecurityStatisticsResponse response = service.securityStatistics(query);

        assertThat(response.getSummaries())
                .extracting(item -> item.getValue())
                .containsExactly(BigDecimal.valueOf(1500), BigDecimal.valueOf(35), BigDecimal.valueOf(120));
        assertThat(response.getTrend()).anySatisfy(bucket -> {
            assertThat(bucket.getTimestamp()).isEqualTo(LocalDateTime.of(2026, 9, 13, 10, 0));
            assertThat(bucket.getValues()).containsEntry("intercepts", BigDecimal.valueOf(1500));
        });
        assertThat(response.getTypeTop()).singleElement().satisfies(metric -> {
            assertThat(metric.getKey()).isEqualTo("SIGNATURE_INVALID");
            assertThat(metric.getValue()).isEqualByComparingTo("900");
        });
        assertThat(response.getMerchantTop()).singleElement().satisfies(metric -> {
            assertThat(metric.getKey()).isEqualTo("M1001");
            assertThat(metric.getValue()).isEqualByComparingTo("600");
        });
        verify(jdbcTemplate).queryForMap(contains("COUNT(*) AS intercept_count"), any(MapSqlParameterSource.class));
        verify(jdbcTemplate).queryForList(contains("GROUP BY bucket_time"), any(MapSqlParameterSource.class));
        verify(jdbcTemplate, times(2)).queryForList(contains("GROUP BY category_key"), any(MapSqlParameterSource.class));
    }

    @Test
    void shouldRejectStaleAlertVersionBeforeUpdatingState() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        JdbcTemplate delegate = mock(JdbcTemplate.class);
        when(jdbcTemplate.getJdbcTemplate()).thenReturn(delegate);
        TransactionQueryJdbcTemplateFactory factory = mock(TransactionQueryJdbcTemplateFactory.class);
        when(factory.create(any(DataSource.class), any(TransactionShardingProperties.class)))
                .thenReturn(jdbcTemplate);
        when(jdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class))).thenReturn(List.of(Map.of(
                "event_code", "ALERT-1",
                "rule_name", "Low success rate",
                "channel_code", "TEST",
                "rule_type", "SUCCESS_RATE_LOW",
                "alert_level", "L2_DEGRADED",
                "event_status", "OPEN",
                "trigger_time", LocalDateTime.of(2026, 9, 13, 10, 0)
        )));
        when(jdbcTemplate.queryForMap(anyString(), any(MapSqlParameterSource.class))).thenReturn(Map.of(
                "status", "OPEN",
                "version", 2
        ));
        JdbcAdminMonitorWorkbenchService service = createService(jdbcTemplate, factory);
        AlertActionRequest request = new AlertActionRequest();
        request.setVersion(1);

        assertThatThrownBy(() -> service.takeOver("CHANNEL", "ALERT-1", request))
                .isInstanceOfSatisfying(ServiceException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(
                                ApiResultEnum.ABNORMAL_CASE_STATE_CONFLICT.getCode()));
        verify(delegate).setQueryTimeout(5);
    }

    private ChannelMonitorResponse channelResponseForAlertSeverity(Integer alertSeverity,
                                                                   long requestCount,
                                                                   long successCount,
                                                                   long timeoutCount) {
        NamedParameterJdbcTemplate jdbcTemplate = monitorJdbcTemplate();
        TransactionQueryJdbcTemplateFactory factory = monitorJdbcTemplateFactory(jdbcTemplate);
        when(jdbcTemplate.queryForMap(contains("FROM transaction_channel_request r"),
                any(MapSqlParameterSource.class))).thenReturn(Map.of(
                "request_count", requestCount,
                "success_count", successCount,
                "timeout_count", timeoutCount));
        when(jdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0, String.class);
            if (sql.contains("FROM channel_alert_event")) {
                return alertSeverity == null
                        ? List.of()
                        : List.of(Map.of("channel_code", "TEST", "severity_rank", alertSeverity));
            }
            if (sql.contains("PARTITION BY r.channel_code")) {
                return List.of(Map.of(
                        "channel_code", "TEST",
                        "request_count", requestCount,
                        "success_count", successCount,
                        "timeout_count", timeoutCount,
                        "average_millis", 120,
                        "p95_millis", 180,
                        "p99_millis", 220,
                        "continuous_failure_count", Math.max(requestCount - successCount, 0L),
                        "latest_error", "CHANNEL_DECLINED",
                        "latest_failure_time", LocalDateTime.of(2026, 9, 13, 10, 30)));
            }
            return List.of();
        });
        JdbcAdminMonitorWorkbenchService service = createService(jdbcTemplate, factory);
        ChannelMonitorQuery query = new ChannelMonitorQuery();
        query.setBeginTime(LocalDateTime.of(2026, 9, 13, 10, 0));
        query.setEndTime(LocalDateTime.of(2026, 9, 13, 11, 0));
        return service.searchChannels(query);
    }

    private ApiMonitorQuery apiQuery(LocalDateTime beginTime, LocalDateTime endTime) {
        ApiMonitorQuery query = new ApiMonitorQuery();
        query.setBeginTime(beginTime);
        query.setEndTime(endTime);
        return query;
    }

    private BigDecimal summaryValue(List<MetricSummary> summaries, String key) {
        return summary(summaries, key).getValue();
    }

    private MetricSummary summary(List<MetricSummary> summaries, String key) {
        return summaries.stream()
                .filter(summary -> key.equals(summary.getKey()))
                .findFirst()
                .orElseThrow();
    }

    private NamedParameterJdbcTemplate monitorJdbcTemplate() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.getJdbcTemplate()).thenReturn(mock(JdbcTemplate.class));
        return jdbcTemplate;
    }

    private TransactionQueryJdbcTemplateFactory monitorJdbcTemplateFactory(
            NamedParameterJdbcTemplate jdbcTemplate) {
        TransactionQueryJdbcTemplateFactory factory = mock(TransactionQueryJdbcTemplateFactory.class);
        when(factory.create(any(DataSource.class), any(TransactionShardingProperties.class)))
                .thenReturn(jdbcTemplate);
        return factory;
    }

    private JdbcAdminMonitorWorkbenchService createService(NamedParameterJdbcTemplate jdbcTemplate,
                                                            TransactionQueryJdbcTemplateFactory factory) {
        RocketMqAdminMonitorProvider rocketMqAdminMonitorProvider = mock(RocketMqAdminMonitorProvider.class);
        when(rocketMqAdminMonitorProvider.snapshot())
                .thenReturn(Snapshot.notConfigured("RocketMQ Admin 监控未启用"));
        return createService(jdbcTemplate, factory, rocketMqAdminMonitorProvider);
    }

    private JdbcAdminMonitorWorkbenchService createService(NamedParameterJdbcTemplate jdbcTemplate,
                                                            TransactionQueryJdbcTemplateFactory factory,
                                                            RocketMqAdminMonitorProvider rocketMqAdminMonitorProvider) {
        TransactionLogicalReadExecutor readExecutor = mock(TransactionLogicalReadExecutor.class);
        doAnswer(invocation -> ((Supplier<?>) invocation.getArgument(0)).get())
                .when(readExecutor).read(any());
        return new JdbcAdminMonitorWorkbenchService(
                mock(DataSource.class),
                readExecutor,
                new TransactionShardingProperties(),
                factory,
                new MonitorTimeRangeNormalizer(),
                mock(AdminRuntimeMonitorSampler.class),
                mock(DiscoveryClient.class),
                mock(AdminTransactionQueryService.class),
                mock(AdminMonitorCacheApplicationService.class),
                mock(AdminMonitorDatasourceApplicationService.class),
                new MonitorSensitiveTextSanitizer(),
                rocketMqAdminMonitorProvider);
    }
}
