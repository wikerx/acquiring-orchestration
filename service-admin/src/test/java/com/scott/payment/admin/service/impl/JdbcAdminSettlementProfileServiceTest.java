package com.scott.payment.admin.service.impl;

import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ProfileSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ProfileSummary;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ProfileUpdateRequest;
import com.scott.payment.admin.service.AdminMerchantDataScope;
import com.scott.payment.component.db.sharding.TransactionLogicalReadExecutor;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JdbcAdminSettlementProfileServiceTest
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 验证结算档案的本地分页、商户数据范围和乐观锁更新。
 * @status : create
 */
class JdbcAdminSettlementProfileServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void searchShouldJoinMerchantAndAccountAndApplyCustomScope() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(1L);
        ProfileSummary row = new ProfileSummary();
        row.setId(9L);
        row.setSettlementProfileNo("SP-M1001-USD");
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(row));
        JdbcAdminSettlementProfileService service = new JdbcAdminSettlementProfileService(
                jdbcTemplate, executingReadExecutor(), new TransactionShardingProperties());
        ProfileSearchRequest request = new ProfileSearchRequest();
        request.setMerchantId("M1001");
        request.setProcessingMode("MANUAL");
        request.setPageNo(1);
        request.setPageSize(20);

        var page = service.search(request, AdminMerchantDataScope.limited(Set.of("M1001", "M1002")));

        assertThat(page.getTotal()).isEqualTo(1L);
        assertThat(page.getRecords()).singleElement()
                .extracting(ProfileSummary::getSettlementProfileNo)
                .isEqualTo("SP-M1001-USD");
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> parameters = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).query(sql.capture(), parameters.capture(), any(RowMapper.class));
        assertThat(sql.getValue()).contains(
                "FROM merchant_settlement_profile profile",
                "LEFT JOIN base_merchant_info merchant",
                "LEFT JOIN merchant_fund_account account",
                "profile.merchant_id IN (:permittedMerchantIds)",
                "ORDER BY profile.profile_status = 'ACTIVE' DESC, profile.id DESC",
                "LIMIT :offset, :limit");
        assertThat(parameters.getValue().getValue("merchantId")).isEqualTo("M1001");
        assertThat(parameters.getValue().getValue("processingMode")).isEqualTo("MANUAL");
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchWithoutFiltersShouldSeparateWhereClauseFromOrderBy() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Long.class)))
                .thenReturn(1L);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(new ProfileSummary()));
        JdbcAdminSettlementProfileService service = new JdbcAdminSettlementProfileService(
                jdbcTemplate, executingReadExecutor(), new TransactionShardingProperties());

        service.search(new ProfileSearchRequest(), AdminMerchantDataScope.all());

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sql.capture(), any(MapSqlParameterSource.class), any(RowMapper.class));
        assertThat(sql.getValue())
                .contains("WHERE 1 = 1\nORDER BY")
                .doesNotContain("1ORDER BY");
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateShouldUseProfileIdentityScopeAndExpectedVersionAsCasConditions() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
        ProfileSummary updated = new ProfileSummary();
        updated.setId(9L);
        updated.setSettlementProfileNo("SP-M1001-USD");
        updated.setVersion(4L);
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(updated));
        JdbcAdminSettlementProfileService service = new JdbcAdminSettlementProfileService(
                jdbcTemplate, executingReadExecutor(), new TransactionShardingProperties());
        ProfileUpdateRequest request = new ProfileUpdateRequest();
        request.setProcessingMode("AUTO_REVIEW");
        request.setBusinessTimeZone("Asia/Tokyo");
        request.setDailyCutoffTime(LocalTime.of(23, 30));
        request.setExpectedVersion(3L);

        ProfileSummary result = service.update(
                "SP-M1001-USD", request, AdminMerchantDataScope.limited(Set.of("M1001")));

        assertThat(result.getVersion()).isEqualTo(4L);
        ArgumentCaptor<String> updateSql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> parameters = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).update(updateSql.capture(), parameters.capture());
        assertThat(updateSql.getValue()).contains(
                "processing_mode = :processingMode",
                "business_time_zone = :businessTimeZone",
                "daily_cutoff_time = :dailyCutoffTime",
                "version = version + 1",
                "settlement_profile_no = :settlementProfileNo",
                "merchant_id IN (:permittedMerchantIds)",
                "version = :expectedVersion");
        assertThat(parameters.getValue().getValue("expectedVersion")).isEqualTo(3L);
    }

    private TransactionLogicalReadExecutor executingReadExecutor() {
        TransactionLogicalReadExecutor executor = mock(TransactionLogicalReadExecutor.class);
        when(executor.read(any())).thenAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(0);
            return supplier.get();
        });
        when(executor.readPrimary(any())).thenAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(0);
            return supplier.get();
        });
        return executor;
    }
}
