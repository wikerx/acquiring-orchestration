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
 * @classname : AdminSettlementReviewControllerContractTest
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 验证 Admin 预审查询和每个 Maker-Checker 命令均使用独立最小权限及操作审计。
 * @status : create
 */
class AdminSettlementReviewControllerContractTest {

    @Test
    void everyReviewRouteShouldRequireDedicatedPermissionAndAudit() {
        RequestMapping root = AdminSettlementReviewController.class.getAnnotation(RequestMapping.class);
        assertThat(root.value()).containsExactly("/admin/settlement");
        Map<String, String> expected = Map.ofEntries(
                Map.entry("transactionCandidates", "settlement:transaction-candidate:list"),
                Map.entry("transactionCandidateDetail", "settlement:transaction-candidate:detail"),
                Map.entry("reserveCandidates", "settlement:reserve-candidate:list"),
                Map.entry("reserveCandidateDetail", "settlement:reserve-candidate:detail"),
                Map.entry("reviews", "settlement:review-order:list"),
                Map.entry("reviewDetail", "settlement:review-order:detail"),
                Map.entry("submitTransactionReview", "settlement:transaction-review:create"),
                Map.entry("submitReserveReview", "settlement:reserve-review:create"),
                Map.entry("approve", "settlement:review-order:approve"),
                Map.entry("reject", "settlement:review-order:reject"),
                Map.entry("cancel", "settlement:review-order:cancel"));

        expected.forEach((methodName, permission) -> {
            java.lang.reflect.Method method = java.util.Arrays.stream(
                            AdminSettlementReviewController.class.getDeclaredMethods())
                    .filter(candidate -> candidate.getName().equals(methodName))
                    .findFirst().orElseThrow();
            RequiresPermission authorization = method.getAnnotation(RequiresPermission.class);
            assertThat(authorization).isNotNull();
            assertThat(authorization.value()).isEqualTo(permission);
            assertThat(method.getAnnotation(OperationLog.class)).isNotNull();
        });
    }
}
