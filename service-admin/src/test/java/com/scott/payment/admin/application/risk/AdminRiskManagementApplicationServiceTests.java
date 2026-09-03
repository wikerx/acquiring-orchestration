package com.scott.payment.admin.application.risk;

import com.scott.payment.admin.application.risk.cache.RiskRuleCacheInvalidationCoordinator;
import com.scott.payment.admin.dto.risk.RiskDTOs;
import com.scott.payment.admin.mapper.RiskManagementMapper;
import com.scott.payment.admin.service.MerchantAccessApprovalNotificationService;
import com.scott.payment.admin.support.approval.MerchantAccessApprovalStatus;
import com.scott.payment.admin.support.risk.RiskListValueNormalizer;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.excel.service.ExcelExportService;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.component.excel.support.ExcelLocaleResolver;
import com.scott.payment.component.db.auth.entity.BaseMerchantInfoDO;
import com.scott.payment.component.db.auth.mapper.BaseMerchantInfoMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminRiskManagementApplicationServiceTests
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 管理端风控评估记录查询测试。
 * @status : create
 */
class AdminRiskManagementApplicationServiceTests {

    @AfterEach
    void clearAuthContext() {
        InternalAuthContextHolder.clear();
    }

    @Test
    void shouldDefaultTodayEventsToShanghaiCurrentDay() {
        RiskManagementMapper mapper = mock(RiskManagementMapper.class);
        when(mapper.countEvaluations(isNull(), isNull(), isNull(), isNull(), isNull(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(0L);
        AdminRiskManagementApplicationService service = service(mapper);

        service.pageTodayRiskEvents(null);

        ArgumentCaptor<LocalDateTime> startCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> endCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(mapper).countEvaluations(isNull(), isNull(), isNull(), isNull(), isNull(),
                startCaptor.capture(), endCaptor.capture());
        LocalDateTime start = startCaptor.getValue();
        assertThat(start.toLocalTime()).isEqualTo(LocalTime.MIDNIGHT);
        assertThat(start.toLocalDate()).isEqualTo(java.time.LocalDate.now(ZoneId.of("Asia/Shanghai")));
        assertThat(endCaptor.getValue()).isEqualTo(start.plusDays(1));
    }

    @Test
    void shouldRejectTodayEventsWithOnlyOneTimeBoundary() {
        AdminRiskManagementApplicationService service = service(mock(RiskManagementMapper.class));
        RiskDTOs.EvaluationQueryRequest request = new RiskDTOs.EvaluationQueryRequest();
        request.setEvaluationStartTime(LocalDateTime.of(2026, 8, 4, 0, 0));

        assertThatThrownBy(() -> service.pageTodayRiskEvents(request))
                .isInstanceOf(com.scott.payment.component.core.exception.ServiceException.class)
                .hasMessageContaining("评估时间范围必须同时包含开始和结束时间");
    }

    @Test
    void shouldDisplayZeroHitsForHistoricalNonPassEvaluations() {
        RiskManagementMapper mapper = mock(RiskManagementMapper.class);
        Map<String, Object> rejected = evaluation("REJECT", 7);
        Map<String, Object> passed = evaluation("PASS", 3);
        when(mapper.countEvaluations(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull())).thenReturn(2L);
        when(mapper.selectEvaluations(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), anyLong(), anyLong()))
                .thenReturn(List.of(rejected, passed));
        AdminRiskManagementApplicationService service = new AdminRiskManagementApplicationService(
                mapper,
                mock(RiskListValueNormalizer.class),
                mock(AdminRiskImportLogService.class),
                mock(RiskRuleCacheInvalidationCoordinator.class),
                mock(ExcelExportService.class),
                mock(ExcelI18nMessageResolver.class),
                mock(ExcelLocaleResolver.class),
                mock(BaseMerchantInfoMapper.class),
                mock(MerchantAccessApprovalNotificationService.class)
        );

        List<Map<String, Object>> records = service.pageEvaluations(new RiskDTOs.EvaluationQueryRequest())
                .getRecords();

        assertThat(records).hasSize(2);
        assertThat(records.get(0)).containsEntry("hit_count", 0);
        assertThat(records.get(1)).containsEntry("hit_count", 3);
    }

    @Test
    void shouldForwardRiskLevelAndHalfOpenEvaluationTimeRange() {
        RiskManagementMapper mapper = mock(RiskManagementMapper.class);
        LocalDateTime start = LocalDateTime.of(2026, 8, 4, 0, 0);
        LocalDateTime endExclusive = start.plusDays(1);
        when(mapper.countEvaluations("M1001", null, null, "REJECT", "HIGH", start, endExclusive))
                .thenReturn(1L);
        when(mapper.selectEvaluations("M1001", null, null, "REJECT", "HIGH", start, endExclusive, 0, 20))
                .thenReturn(List.of(evaluation("REJECT", 1)));
        AdminRiskManagementApplicationService service = new AdminRiskManagementApplicationService(
                mapper,
                mock(RiskListValueNormalizer.class),
                mock(AdminRiskImportLogService.class),
                mock(RiskRuleCacheInvalidationCoordinator.class),
                mock(ExcelExportService.class),
                mock(ExcelI18nMessageResolver.class),
                mock(ExcelLocaleResolver.class),
                mock(BaseMerchantInfoMapper.class),
                mock(MerchantAccessApprovalNotificationService.class)
        );
        RiskDTOs.EvaluationQueryRequest request = new RiskDTOs.EvaluationQueryRequest();
        request.setMerchantId("M1001");
        request.setDecisionResult("REJECT");
        request.setRiskLevel("HIGH");
        request.setEvaluationStartTime(start);
        request.setEvaluationEndTimeExclusive(endExclusive);
        request.setPageSize(20);

        assertThat(service.pageEvaluations(request).getTotal()).isOne();
    }

    @Test
    void shouldApprovePendingSourceUrlWithAllowedTransactionByDefault() {
        RiskManagementMapper mapper = mock(RiskManagementMapper.class);
        BaseMerchantInfoMapper merchantMapper = mock(BaseMerchantInfoMapper.class);
        MerchantAccessApprovalNotificationService notificationService =
                mock(MerchantAccessApprovalNotificationService.class);
        AdminRiskManagementApplicationService service = new AdminRiskManagementApplicationService(
                mapper,
                mock(RiskListValueNormalizer.class),
                mock(AdminRiskImportLogService.class),
                mock(RiskRuleCacheInvalidationCoordinator.class),
                mock(ExcelExportService.class),
                mock(ExcelI18nMessageResolver.class),
                mock(ExcelLocaleResolver.class),
                merchantMapper,
                notificationService
        );
        authenticateSourceUrlApprover();
        Map<String, Object> before = sourceUrlRecord(0, 0);
        Map<String, Object> after = sourceUrlRecord(1, 1);
        when(mapper.selectById("risk_rule_source_url", 17L)).thenReturn(before, after);
        when(mapper.approveSourceUrlRule(eq(17L), eq(1), isNull(), eq(1), eq("Risk Reviewer"),
                any(LocalDateTime.class))).thenReturn(1);
        BaseMerchantInfoDO merchant = new BaseMerchantInfoDO();
        merchant.setMerchantId("M1001");
        when(merchantMapper.selectOne(any())).thenReturn(merchant);
        RiskDTOs.MerchantAccessApprovalRequest request = new RiskDTOs.MerchantAccessApprovalRequest();
        request.setApprovalStatus(1);

        RiskDTOs.RiskRecordResponse response = service.approveSourceUrl(17L, request);

        assertThat(response.getApprovalStatus()).isEqualTo(1);
        assertThat(response.getStatus()).isEqualTo(1);
        verify(notificationService).sendAfterCommit(
                eq(merchant),
                eq(MerchantAccessApprovalNotificationService.TYPE_SOURCE_URL),
                eq("https://shop.example.com"),
                eq(MerchantAccessApprovalStatus.APPROVED),
                eq(1),
                isNull(),
                any(LocalDateTime.class)
        );
    }

    @Test
    void shouldRequireReasonWhenRejectingSourceUrl() {
        RiskManagementMapper mapper = mock(RiskManagementMapper.class);
        AdminRiskManagementApplicationService service = service(mapper);
        authenticateSourceUrlApprover();
        RiskDTOs.MerchantAccessApprovalRequest request = new RiskDTOs.MerchantAccessApprovalRequest();
        request.setApprovalStatus(2);

        assertThatThrownBy(() -> service.approveSourceUrl(17L, request))
                .isInstanceOf(com.scott.payment.component.core.exception.ServiceException.class)
                .hasMessageContaining("拒绝原因");
        verify(mapper, never()).approveSourceUrlRule(
                any(), any(), any(), any(), any(), any());
    }

    private Map<String, Object> evaluation(String decision, int hitCount) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("decision_result", decision);
        row.put("hit_count", hitCount);
        return row;
    }

    private Map<String, Object> sourceUrlRecord(int approvalStatus, int status) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 17L);
        row.put("merchant_id", "M1001");
        row.put("source_url", "https://shop.example.com");
        row.put("source_host", "shop.example.com");
        row.put("approval_status", approvalStatus);
        row.put("status", status);
        return row;
    }

    private void authenticateSourceUrlApprover() {
        InternalAuthAccount account = new InternalAuthAccount();
        account.setRealName("Risk Reviewer");
        account.setPermissions(List.of("risk:rule:sourceUrl:status"));
        InternalAuthContextHolder.set(account);
    }

    private AdminRiskManagementApplicationService service(RiskManagementMapper mapper) {
        return new AdminRiskManagementApplicationService(
                mapper,
                mock(RiskListValueNormalizer.class),
                mock(AdminRiskImportLogService.class),
                mock(RiskRuleCacheInvalidationCoordinator.class),
                mock(ExcelExportService.class),
                mock(ExcelI18nMessageResolver.class),
                mock(ExcelLocaleResolver.class),
                mock(BaseMerchantInfoMapper.class),
                mock(MerchantAccessApprovalNotificationService.class)
        );
    }
}
