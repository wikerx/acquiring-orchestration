package com.scott.payment.admin.service.impl;

import com.scott.payment.admin.dto.transaction.AdminChannelMatchAbnormalDTOs.AbnormalQuery;
import com.scott.payment.admin.dto.transaction.AdminChannelMatchAbnormalDTOs.AbnormalSummary;
import com.scott.payment.admin.service.AdminTransactionQueryService;
import com.scott.payment.component.db.sharding.TransactionLogicalReadExecutor;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.Collections;
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
 * @author : scott
 * @version : v1.0.0
 * @classname : JdbcAdminChannelMatchAbnormalQueryServiceTests
 * @date : 2026-08-08 00:50
 * @email : scott_x@163.com
 * @description : 管理端勾兑异常本地查询测试，验证案件逻辑表筛选、统计口径和 ShardingSphere 普通读路由。
 * @status : create
 */
@Slf4j
class JdbcAdminChannelMatchAbnormalQueryServiceTests {

    /** 案件分页和统计必须使用 transaction_abnormal_event 逻辑表及普通读作用域。 */
    @Test
    @SuppressWarnings("unchecked")
    void searchShouldUseLogicalTableAndReplicaEligibleReadScope() throws Exception {
        log.info("用例：验证勾兑异常查询使用逻辑表和可路由副本的普通读作用域");
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        TransactionLogicalReadExecutor readExecutor = executingReadExecutor();
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getLong(anyString())).thenReturn(0L);
        when(jdbcTemplate.queryForObject(
                anyString(), any(MapSqlParameterSource.class), eq(Long.class))).thenReturn(0L);
        when(jdbcTemplate.queryForObject(
                anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> invocation.<RowMapper<AbnormalSummary>>getArgument(2)
                        .mapRow(resultSet, 0));
        JdbcAdminChannelMatchAbnormalQueryService service =
                new JdbcAdminChannelMatchAbnormalQueryService(
                        jdbcTemplate, readExecutor, mock(AdminTransactionQueryService.class),
                        new TransactionShardingProperties());
        AbnormalQuery query = new AbnormalQuery();
        query.setBeginTime(LocalDateTime.of(2026, 8, 1, 0, 0));
        query.setEndTime(LocalDateTime.of(2026, 8, 8, 0, 0));
        query.setMerchantId("merchant-a");
        query.setEventStatus("OPEN");

        service.search(query);

        ArgumentCaptor<String> countSql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> countParams =
                ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).queryForObject(
                countSql.capture(), countParams.capture(), eq(Long.class));
        assertThat(countSql.getValue()).contains(
                "FROM transaction_abnormal_event",
                "merchant_id = :merchantId",
                "event_status = :eventStatus",
                "transaction_date_time >= :beginTime");
        assertThat(countParams.getValue().getValue("merchantId")).isEqualTo("merchant-a");
        verify(readExecutor).read(any());
        verify(readExecutor, never()).readPrimary(any());
        log.info("结果：案件分页和状态统计均在transaction普通读作用域执行");
    }

    /** 非法出现次数必须在进入读作用域和访问数据库前失败。 */
    @Test
    void searchShouldRejectInvalidOccurrenceCountBeforeDatabaseAccess() {
        log.info("用例：验证最小出现次数小于1时拒绝访问数据库");
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        TransactionLogicalReadExecutor readExecutor = mock(TransactionLogicalReadExecutor.class);
        JdbcAdminChannelMatchAbnormalQueryService service =
                new JdbcAdminChannelMatchAbnormalQueryService(
                        jdbcTemplate, readExecutor, mock(AdminTransactionQueryService.class),
                        new TransactionShardingProperties());
        AbnormalQuery query = new AbnormalQuery();
        query.setMinimumOccurrenceCount(0);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.search(query))
                .isInstanceOf(RuntimeException.class);

        verify(readExecutor, never()).read(any());
        verify(jdbcTemplate, never()).queryForObject(
                anyString(), any(MapSqlParameterSource.class), eq(Long.class));
        log.info("结果：非法出现次数在数据库访问前被拒绝");
    }

    /** 案件详情首查必须绑定案件号和毫秒级真实分片时间。 */
    @Test
    @SuppressWarnings("unchecked")
    void detailShouldBindExactTransactionShardTime() {
        log.info("用例：验证勾兑异常详情使用案件号和毫秒级分片时间精确查询");
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        TransactionLogicalReadExecutor readExecutor = executingReadExecutor();
        when(jdbcTemplate.query(
                anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(Collections.emptyList());
        JdbcAdminChannelMatchAbnormalQueryService service =
                new JdbcAdminChannelMatchAbnormalQueryService(
                        jdbcTemplate, readExecutor, mock(AdminTransactionQueryService.class),
                        new TransactionShardingProperties());
        LocalDateTime transactionTime = LocalDateTime.of(
                2026, 8, 8, 10, 20, 30, 456_000_000);

        assertThatThrownBy(() -> service.detail("event-1", transactionTime))
                .isInstanceOf(RuntimeException.class);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> parameters =
                ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).query(sql.capture(), parameters.capture(), any(RowMapper.class));
        assertThat(sql.getValue()).contains(
                "abnormal_event_id = :eventId",
                "transaction_date_time = :transactionDateTime");
        assertThat(parameters.getValue().getValue("transactionDateTime"))
                .isEqualTo(transactionTime);
        verify(readExecutor).read(any());
        log.info("结果：案件详情使用精确毫秒分片时间且未执行范围扫描");
    }

    private TransactionLogicalReadExecutor executingReadExecutor() {
        TransactionLogicalReadExecutor executor = mock(TransactionLogicalReadExecutor.class);
        when(executor.read(any())).thenAnswer(invocation ->
                invocation.<Supplier<?>>getArgument(0).get());
        return executor;
    }
}
