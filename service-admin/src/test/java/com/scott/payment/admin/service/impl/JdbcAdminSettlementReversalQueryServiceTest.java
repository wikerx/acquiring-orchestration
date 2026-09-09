package com.scott.payment.admin.service.impl;

import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReversalSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReversalSummary;
import com.scott.payment.admin.service.AdminMerchantDataScope;
import com.scott.payment.component.db.sharding.TransactionLogicalReadExecutor;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JdbcAdminSettlementReversalQueryServiceTest
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 验证 Admin 冲正本地查询的标准分页、稳定排序和商户数据范围。
 * @status : create
 */
class JdbcAdminSettlementReversalQueryServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void searchShouldUseLocalReadExecutorAndApplyMerchantScope() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class))).thenReturn(1L);
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(new ReversalSummary()));
        TransactionLogicalReadExecutor readExecutor = executingReadExecutor();
        JdbcAdminSettlementReversalQueryService service = new JdbcAdminSettlementReversalQueryService(
                jdbc, readExecutor, new TransactionShardingProperties());
        ReversalSearchRequest request = request();
        request.setReversalStatus(" pending_approval ");

        var page = service.search(request, AdminMerchantDataScope.limited(Set.of("M1001")));

        assertThat(page.getTotal()).isEqualTo(1L);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> parameters = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).queryForObject(sql.capture(), parameters.capture(), eq(Long.class));
        assertThat(sql.getValue()).contains(
                "FROM settlement_reversal_order", "reversal_status = :reversalStatus",
                "merchant_id IN (:permittedMerchantIds)");
        assertThat(parameters.getValue().getValue("reversalStatus")).isEqualTo("PENDING_APPROVAL");
        assertThat(parameters.getValue().getValue("permittedMerchantIds")).isEqualTo(Set.of("M1001"));
        verify(readExecutor).read(any());
    }

    @Test
    void emptyMerchantScopeShouldReturnEmptyPageWithoutDatabaseAccess() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        JdbcAdminSettlementReversalQueryService service = new JdbcAdminSettlementReversalQueryService(
                jdbc, executingReadExecutor(), new TransactionShardingProperties());

        var page = service.search(request(), AdminMerchantDataScope.limited(Set.of()));

        assertThat(page.getTotal()).isZero();
        assertThat(page.getRecords()).isEmpty();
        verifyNoInteractions(jdbc);
    }

    private ReversalSearchRequest request() {
        ReversalSearchRequest request = new ReversalSearchRequest();
        request.setBeginSubmittedDate(LocalDate.of(2026, 8, 1));
        request.setEndSubmittedDate(LocalDate.of(2026, 8, 31));
        return request;
    }

    private TransactionLogicalReadExecutor executingReadExecutor() {
        TransactionLogicalReadExecutor executor = mock(TransactionLogicalReadExecutor.class);
        when(executor.read(any())).thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(0).get());
        return executor;
    }
}
