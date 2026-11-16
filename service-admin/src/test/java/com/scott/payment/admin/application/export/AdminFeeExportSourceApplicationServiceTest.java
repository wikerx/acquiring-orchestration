package com.scott.payment.admin.application.export;

import com.scott.payment.admin.application.fee.AdminFeeApplicationService;
import com.scott.payment.admin.dto.export.FeeSimulationExportRow;
import com.scott.payment.admin.dto.exporttask.AdminFeeExportInternalDTOs.FeePlanPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminFeeExportInternalDTOs.FeePlanQuery;
import com.scott.payment.admin.dto.exporttask.AdminFeeExportInternalDTOs.FeeSimulationPageRequest;
import com.scott.payment.admin.dto.exporttask.AdminFeeExportInternalDTOs.FeeSimulationQuery;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeePlanSummaryResponse;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeeReviewResponse;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeeSimulationRecordResponse;
import com.scott.payment.admin.service.AdminFeeService;
import com.scott.payment.admin.service.AdminMerchantDataScope;
import com.scott.payment.component.core.model.PageResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 管理端费用域异步导出源测试，验证服务端分页、商户范围和试算逐项展开语义。
 */
class AdminFeeExportSourceApplicationServiceTest {

    /** 费用模板来源必须覆盖分页并只返回既有 Excel 安全投影。 */
    @Test
    void shouldOverrideTemplatePaginationAndProjectSafeFields() {
        AdminFeeService feeService = mock(AdminFeeService.class);
        FeePlanSummaryResponse source = new FeePlanSummaryResponse();
        source.setPlanCode("TPL-1001");
        source.setPlanName("Standard Card Fee");
        source.setCurrentVersionNo(3);
        source.setStatus("ENABLED");
        when(feeService.pageTemplates(any()))
                .thenReturn(PageResult.of(1L, 4, 73, List.of(source)));
        AdminFeeExportSourceApplicationService service = new AdminFeeExportSourceApplicationService(
                feeService, mock(AdminFeeApplicationService.class));
        FeePlanPageRequest request = planRequest(true, Set.of(), 4, 73);

        var response = service.pageTemplates(request);

        ArgumentCaptor<com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeePlanQuery> captor =
                ArgumentCaptor.forClass(com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeePlanQuery.class);
        verify(feeService).pageTemplates(captor.capture());
        assertThat(captor.getValue().getPageNo()).isEqualTo(4);
        assertThat(captor.getValue().getPageSize()).isEqualTo(73);
        assertThat(response.getTotal()).isEqualTo(1L);
        assertThat(response.getRecords().get(0).getPlanCode()).isEqualTo("TPL-1001");
        assertThat(response.getRecords().get(0).getCurrentVersionNo()).isEqualTo(3);
    }

    /** 商户费率与审核来源必须把有限商户范围原样传给权威查询服务。 */
    @Test
    void shouldPropagateFiniteScopeForMerchantFeesAndReviews() {
        AdminFeeService feeService = mock(AdminFeeService.class);
        AdminMerchantDataScope scope = AdminMerchantDataScope.limited(Set.of("M1001", "M1002"));
        FeePlanSummaryResponse merchantFee = new FeePlanSummaryResponse();
        merchantFee.setMerchantId("M1001");
        merchantFee.setPlanCode("MFP-1001");
        FeeReviewResponse review = new FeeReviewResponse();
        review.setMerchantId("M1002");
        review.setPlanCode("MFP-1002");
        when(feeService.pageMerchantFeesForExport(any(), eq(scope)))
                .thenReturn(PageResult.of(1L, 2, 73, List.of(merchantFee)));
        when(feeService.pageReviewsForExport(any(), eq(scope)))
                .thenReturn(PageResult.of(1L, 2, 73, List.of(review)));
        AdminFeeExportSourceApplicationService service = new AdminFeeExportSourceApplicationService(
                feeService, mock(AdminFeeApplicationService.class));
        FeePlanPageRequest request = planRequest(false, Set.of("M1001", "M1002"), 2, 73);

        var merchantResponse = service.pageMerchantFees(request);
        var reviewResponse = service.pageReviews(request);

        verify(feeService).pageMerchantFeesForExport(any(), eq(scope));
        verify(feeService).pageReviewsForExport(any(), eq(scope));
        assertThat(merchantResponse.getRecords()).extracting("merchantId").containsExactly("M1001");
        assertThat(reviewResponse.getRecords()).extracting("merchantId").containsExactly("M1002");
    }

    /** 费用试算必须使用冻结语言展开逐项行，同时保留主记录总数控制分页。 */
    @Test
    void shouldExpandSimulationRowsWithFrozenLocaleAndSourceTotal() {
        AdminFeeService feeService = mock(AdminFeeService.class);
        AdminFeeApplicationService feeApplicationService = mock(AdminFeeApplicationService.class);
        AdminMerchantDataScope scope = AdminMerchantDataScope.limited(Set.of("M2001"));
        FeeSimulationRecordResponse source = new FeeSimulationRecordResponse();
        source.setSimulationNo("SIM-2001");
        source.setMerchantId("M2001");
        when(feeService.pageSimulationRecordsForExport(any(), eq(scope)))
                .thenReturn(PageResult.of(9L, 3, 73, List.of(source)));
        FeeSimulationExportRow first = simulationRow("SIM-2001", 1, "1.23000000");
        FeeSimulationExportRow second = simulationRow("SIM-2001", 2, "0.45000000");
        when(feeApplicationService.toSimulationExportRows(any(), eq(Locale.US)))
                .thenReturn(List.of(first, second));
        AdminFeeExportSourceApplicationService service = new AdminFeeExportSourceApplicationService(
                feeService, feeApplicationService);
        FeeSimulationQuery query = new FeeSimulationQuery();
        query.setMerchantId("M2001");
        query.setTransactionType("PAYMENT");
        FeeSimulationPageRequest request = new FeeSimulationPageRequest();
        request.setCreatorAccountId(88L);
        request.setAllMerchants(false);
        request.setMerchantIds(Set.of("M2001"));
        request.setLocale("en-US");
        request.setQuery(query);
        request.setPageNo(3);
        request.setPageSize(73);

        var response = service.pageSimulations(request);

        ArgumentCaptor<com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeeSimulationRecordQuery> captor =
                ArgumentCaptor.forClass(
                        com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeeSimulationRecordQuery.class);
        verify(feeService).pageSimulationRecordsForExport(captor.capture(), eq(scope));
        verify(feeApplicationService).toSimulationExportRows(List.of(source), Locale.US);
        assertThat(captor.getValue().getPageNo()).isEqualTo(3);
        assertThat(captor.getValue().getPageSize()).isEqualTo(73);
        assertThat(captor.getValue().getTransactionType()).isEqualTo("PAYMENT");
        assertThat(response.getSourceTotal()).isEqualTo(9L);
        assertThat(response.getRecords()).hasSize(2);
        assertThat(response.getRecords().get(1).getFinalFeeUsd()).isEqualByComparingTo("0.45000000");
    }

    /** 创建费用方案内部分页请求。 */
    private FeePlanPageRequest planRequest(
            boolean allMerchants, Set<String> merchantIds, int pageNo, int pageSize) {
        FeePlanQuery query = new FeePlanQuery();
        query.setKeyword("fee");
        query.setStatus("ENABLED");
        FeePlanPageRequest request = new FeePlanPageRequest();
        request.setCreatorAccountId(88L);
        request.setAllMerchants(allMerchants);
        request.setMerchantIds(merchantIds);
        request.setQuery(query);
        request.setPageNo(pageNo);
        request.setPageSize(pageSize);
        return request;
    }

    /** 创建费用试算逐项导出行。 */
    private FeeSimulationExportRow simulationRow(
            String simulationNo, int lineNo, String finalFeeUsd) {
        FeeSimulationExportRow row = new FeeSimulationExportRow();
        row.setSimulationNo(simulationNo);
        row.setLineNo(lineNo);
        row.setFinalFeeUsd(new BigDecimal(finalFeeUsd));
        return row;
    }
}
