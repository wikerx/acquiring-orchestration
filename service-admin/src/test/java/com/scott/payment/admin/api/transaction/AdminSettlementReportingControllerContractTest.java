package com.scott.payment.admin.api.transaction;

import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

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
        Map<String, String> expected = Map.of(
                "exportReviews", "settlement:review-order:export",
                "searchResultItems", "settlement:result-item:list",
                "exportResultItems", "settlement:result-item:export",
                "searchPostings", "settlement:posting:list",
                "exportPostings", "settlement:posting:export");

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
}
