package com.scott.payment.admin.api.transaction;

import com.scott.payment.admin.application.transaction.AdminSettlementReportingApplicationService;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminSettlementReportingControllerContractTest
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 验证结算报表查询和导出使用独立最小权限并记录操作审计。
 * @status : create
 */
class AdminSettlementReportingControllerContractTest {

    @Test
    void everyReportingRouteShouldRequireDedicatedPermissionAndAudit() {
        RequestMapping root = AdminSettlementReportingController.class.getAnnotation(RequestMapping.class);
        assertThat(root.value()).containsExactly("/admin/settlement");
        Map<String, String> expected = Map.ofEntries(
                Map.entry("exportReviews", "settlement:review-order:export"),
                Map.entry("searchResultItems", "settlement:result-item:list"),
                Map.entry("reconciliationRecordsByTransaction", "reconciliation:record:detail"),
                Map.entry("resultItemsByTransaction", "settlement:result-item:transaction-detail"),
                Map.entry("exportResultItems", "settlement:result-item:export"),
                Map.entry("searchReserveItems", "settlement:reserve-item:list"),
                Map.entry("reserveItemsByTransaction", "settlement:reserve-item:transaction-detail"),
                Map.entry("exportReserveItems", "settlement:reserve-item:export"),
                Map.entry("searchPostings", "settlement:posting:list"),
                Map.entry("exportPostings", "settlement:posting:export"));

        expected.forEach((methodName, permission) -> {
            java.lang.reflect.Method method = java.util.Arrays.stream(
                            AdminSettlementReportingController.class.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(methodName))
                    .findFirst().orElseThrow();
            assertThat(method.getAnnotation(RequiresPermission.class)).extracting(RequiresPermission::value)
                    .isEqualTo(permission);
            assertThat(method.getAnnotation(OperationLog.class)).isNotNull();
        });
    }

    @Test
    void reconciliationRouteShouldBindExactTransactionIdentity() throws Exception {
        AdminSettlementReportingApplicationService applicationService =
                mock(AdminSettlementReportingApplicationService.class);
        LocalDateTime transactionDateTime = LocalDateTime.of(2026, 9, 9, 17, 34, 8, 160_000_000);
        when(applicationService.findReconciliationRecordsByTransaction(
                "T-1001", transactionDateTime)).thenReturn(List.of());
        MockMvc mockMvc = standaloneSetup(new AdminSettlementReportingController(applicationService)).build();

        mockMvc.perform(get("/admin/settlement/reconciliation-records/transactions/{transactionId}", "T-1001")
                        .param("transactionDateTime", "2026-09-09T17:34:08.160"))
                .andExpect(status().isOk());

        verify(applicationService).findReconciliationRecordsByTransaction("T-1001", transactionDateTime);
    }
}
