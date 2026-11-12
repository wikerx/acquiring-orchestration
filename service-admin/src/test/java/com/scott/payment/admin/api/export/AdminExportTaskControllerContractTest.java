package com.scott.payment.admin.api.export;

import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 异步导出与下载中心公共接口权限和操作审计契约测试。
 */
class AdminExportTaskControllerContractTest {

    /**
     * 列表、详情、下载、重试和待办提交必须使用独立且稳定的权限编码。
     */
    @Test
    void shouldRequireDedicatedPermissionForEveryCapability() {
        RequestMapping root = AdminExportTaskController.class.getAnnotation(RequestMapping.class);
        assertThat(root.value()).containsExactly("/admin/operations/export-tasks");
        Map<String, String> expected = Map.of(
                "search", "operations:export-task:list",
                "detail", "operations:export-task:detail",
                "download", "operations:export-task:download",
                "retry", "operations:export-task:retry");

        expected.forEach((methodName, permission) -> {
            RequiresPermission annotation = method(AdminExportTaskController.class, methodName)
                    .getAnnotation(RequiresPermission.class);
            assertThat(annotation).isNotNull();
            assertThat(annotation.value()).isEqualTo(permission);
        });

        RequiresPermission submit = method(AdminOperationalWorkItemExportController.class, "submit")
                .getAnnotation(RequiresPermission.class);
        assertThat(submit.value()).isEqualTo("operations:work-item:export");
        assertThat(method(AdminTransactionExportTaskController.class, "submitOrders")
                .getAnnotation(RequiresPermission.class).value()).isEqualTo("transaction:order:export");
        assertThat(method(AdminTransactionExportTaskController.class, "submitOperations")
                .getAnnotation(RequiresPermission.class).value()).isEqualTo("transaction:operation:export");
        assertThat(method(AdminTransactionExportTaskController.class, "submitMerchantNotifications")
                .getAnnotation(RequiresPermission.class).value())
                .isEqualTo("transaction:merchant-notification:export");
        assertThat(method(AdminTransactionExportTaskController.class, "submitRefunds")
                .getAnnotation(RequiresPermission.class).value()).isEqualTo("transaction:refund:export");
        assertThat(method(AdminSettlementExportTaskController.class, "submitTransactions")
                .getAnnotation(RequiresPermission.class).value()).isEqualTo("settlement:result-item:export");
        assertThat(method(AdminSettlementExportTaskController.class, "submitReserves")
                .getAnnotation(RequiresPermission.class).value()).isEqualTo("settlement:reserve-item:export");
        assertThat(method(AdminBaseExportTaskController.class, "submitCardBins")
                .getAnnotation(RequiresPermission.class).value()).isEqualTo("base:cardBin:export");
        assertThat(method(AdminBaseExportTaskController.class, "submitCountries")
                .getAnnotation(RequiresPermission.class).value()).isEqualTo("base:country:export");
        assertThat(method(AdminBaseExportTaskController.class, "submitCurrencies")
                .getAnnotation(RequiresPermission.class).value()).isEqualTo("base:currency:export");
        assertThat(method(AdminBaseExportTaskController.class, "submitRegionCurrencies")
                .getAnnotation(RequiresPermission.class).value()).isEqualTo("base:countryCurrency:export");
        assertThat(method(AdminBaseExportTaskController.class, "submitMccCodes")
                .getAnnotation(RequiresPermission.class).value()).isEqualTo("base:mcc:tree:export");
        assertThat(method(AdminSystemExportTaskController.class, "submitDepartments")
                .getAnnotation(RequiresPermission.class).value()).isEqualTo("system:dept:export");
        assertThat(method(AdminSystemExportTaskController.class, "submitPosts")
                .getAnnotation(RequiresPermission.class).value()).isEqualTo("system:post:export");
        assertThat(method(AdminSystemExportTaskController.class, "submitDictTypes")
                .getAnnotation(RequiresPermission.class).value()).isEqualTo("system:dict:export");
        assertThat(method(AdminSystemExportTaskController.class, "submitDictData")
                .getAnnotation(RequiresPermission.class).value()).isEqualTo("system:dictData:export");
        assertThat(method(AdminSystemExportTaskController.class, "submitHolidayCalendar")
                .getAnnotation(RequiresPermission.class).value()).isEqualTo("system:calendar:export");
        assertThat(method(AdminSystemExportTaskController.class, "submitSystemUsers")
                .getAnnotation(RequiresPermission.class).value()).isEqualTo("system:user:export");
        assertThat(method(AdminSystemExportTaskController.class, "submitSystemConfigs")
                .getAnnotation(RequiresPermission.class).value()).isEqualTo("system:config:export");
        assertThat(method(AdminMerchantExportTaskController.class, "submitMerchantIpWhitelists")
                .getAnnotation(RequiresPermission.class).value()).isEqualTo("merchant:ip-whitelist:export");
        assertThat(method(AdminMonitorExportTaskController.class, "submitJobRunLogs")
                .getAnnotation(RequiresPermission.class).value()).isEqualTo("monitor:jobLog:export");
        assertThat(method(AdminTransactionExportTaskController.class, "submitChannelMatchAbnormalities")
                .getAnnotation(RequiresPermission.class).value())
                .isEqualTo("transaction:match-abnormal:export");
        assertThat(method(AdminSettlementExportTaskController.class, "submitReviews")
                .getAnnotation(RequiresPermission.class).value())
                .isEqualTo("settlement:review-order:export");
        assertThat(method(AdminSettlementExportTaskController.class, "submitPostings")
                .getAnnotation(RequiresPermission.class).value())
                .isEqualTo("settlement:posting:export");
        assertThat(method(AdminSecurityExportTaskController.class, "submit")
                .getAnnotation(RequiresPermission.class).value())
                .isEqualTo("security:intercept-event:export");
        assertThat(method(AdminFundExportTaskController.class, "submitAccounts")
                .getAnnotation(RequiresPermission.class).value()).isEqualTo("fund:account:export");
        assertThat(method(AdminFundExportTaskController.class, "submitAccountLedgers")
                .getAnnotation(RequiresPermission.class).value()).isEqualTo("fund:ledger:export");
        assertThat(method(AdminFundExportTaskController.class, "submitAllLedgers")
                .getAnnotation(RequiresPermission.class).value()).isEqualTo("fund:ledger:all:export");
        assertThat(method(AdminFundExportTaskController.class, "submitRecharges")
                .getAnnotation(RequiresPermission.class).value()).isEqualTo("fund:recharge:export");
        assertThat(method(AdminFundExportTaskController.class, "submitDeductions")
                .getAnnotation(RequiresPermission.class).value()).isEqualTo("fund:deduction:export");
        assertThat(method(AdminExchangeExportTaskController.class, "submitSources")
                .getAnnotation(RequiresPermission.class).value()).isEqualTo("exchange:source:export");
        assertThat(method(AdminExchangeExportTaskController.class, "submitRules")
                .getAnnotation(RequiresPermission.class).value()).isEqualTo("exchange:rule:export");
        assertThat(method(AdminExchangeExportTaskController.class, "submitVolatility")
                .getAnnotation(RequiresPermission.class).value()).isEqualTo("exchange:volatility:export");
        assertThat(method(AdminExchangeExportTaskController.class, "submitAlertRules")
                .getAnnotation(RequiresPermission.class).value()).isEqualTo("exchange:alert-rule:export");
        assertThat(method(AdminExchangeExportTaskController.class, "submitAlertEvents")
                .getAnnotation(RequiresPermission.class).value()).isEqualTo("exchange:alert-event:export");
        assertThat(method(AdminFeeExportTaskController.class, "submitTemplates")
                .getAnnotation(RequiresPermission.class).value()).isEqualTo("fee:template:export");
        assertThat(method(AdminFeeExportTaskController.class, "submitMerchantFees")
                .getAnnotation(RequiresPermission.class).value()).isEqualTo("fee:merchant:export");
        assertThat(method(AdminFeeExportTaskController.class, "submitReviews")
                .getAnnotation(RequiresPermission.class).value()).isEqualTo("fee:review:export");
        assertThat(method(AdminFeeExportTaskController.class, "submitSimulations")
                .getAnnotation(RequiresPermission.class).value()).isEqualTo("fee:simulation:record:export");
    }

    /**
     * 创建、下载和人工重试均会改变可审计事实，必须写入统一操作日志。
     */
    @Test
    void shouldAuditSubmissionDownloadAndRetry() {
        Set.of("download", "retry").forEach(methodName ->
                assertThat(method(AdminExportTaskController.class, methodName)
                        .getAnnotation(OperationLog.class)).isNotNull());
        assertThat(method(AdminOperationalWorkItemExportController.class, "submit")
                .getAnnotation(OperationLog.class)).isNotNull();
        assertThat(method(AdminTransactionExportTaskController.class, "submitOrders")
                .getAnnotation(OperationLog.class)).isNotNull();
        assertThat(method(AdminTransactionExportTaskController.class, "submitOperations")
                .getAnnotation(OperationLog.class)).isNotNull();
        assertThat(method(AdminTransactionExportTaskController.class, "submitMerchantNotifications")
                .getAnnotation(OperationLog.class)).isNotNull();
        assertThat(method(AdminTransactionExportTaskController.class, "submitRefunds")
                .getAnnotation(OperationLog.class)).isNotNull();
        assertThat(method(AdminSettlementExportTaskController.class, "submitTransactions")
                .getAnnotation(OperationLog.class)).isNotNull();
        assertThat(method(AdminSettlementExportTaskController.class, "submitReserves")
                .getAnnotation(OperationLog.class)).isNotNull();
        assertThat(method(AdminBaseExportTaskController.class, "submitCardBins")
                .getAnnotation(OperationLog.class)).isNotNull();
        Set.of("submitCountries", "submitCurrencies", "submitRegionCurrencies", "submitMccCodes")
                .forEach(methodName -> assertThat(method(AdminBaseExportTaskController.class, methodName)
                        .getAnnotation(OperationLog.class)).isNotNull());
        Set.of("submitDepartments", "submitPosts", "submitDictTypes", "submitDictData",
                        "submitHolidayCalendar", "submitSystemUsers", "submitSystemConfigs")
                .forEach(methodName -> assertThat(method(AdminSystemExportTaskController.class, methodName)
                        .getAnnotation(OperationLog.class)).isNotNull());
        assertThat(method(AdminMerchantExportTaskController.class, "submitMerchantIpWhitelists")
                .getAnnotation(OperationLog.class)).isNotNull();
        assertThat(method(AdminMonitorExportTaskController.class, "submitJobRunLogs")
                .getAnnotation(OperationLog.class)).isNotNull();
        assertThat(method(AdminTransactionExportTaskController.class, "submitChannelMatchAbnormalities")
                .getAnnotation(OperationLog.class)).isNotNull();
        assertThat(method(AdminSettlementExportTaskController.class, "submitReviews")
                .getAnnotation(OperationLog.class)).isNotNull();
        assertThat(method(AdminSettlementExportTaskController.class, "submitPostings")
                .getAnnotation(OperationLog.class)).isNotNull();
        assertThat(method(AdminSecurityExportTaskController.class, "submit")
                .getAnnotation(OperationLog.class)).isNotNull();
        Set.of("submitAccounts", "submitAccountLedgers", "submitAllLedgers",
                        "submitRecharges", "submitDeductions")
                .forEach(methodName -> assertThat(method(AdminFundExportTaskController.class, methodName)
                        .getAnnotation(OperationLog.class)).isNotNull());
        Set.of("submitSources", "submitRules", "submitVolatility", "submitAlertRules", "submitAlertEvents")
                .forEach(methodName -> assertThat(method(AdminExchangeExportTaskController.class, methodName)
                        .getAnnotation(OperationLog.class)).isNotNull());
        Set.of("submitTemplates", "submitMerchantFees", "submitReviews", "submitSimulations")
                .forEach(methodName -> assertThat(method(AdminFeeExportTaskController.class, methodName)
                        .getAnnotation(OperationLog.class)).isNotNull());
    }

    /**
     * 按方法名查找 Controller 契约方法，避免测试依赖参数实现细节。
     */
    private Method method(Class<?> controller, String methodName) {
        return java.util.Arrays.stream(controller.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst().orElseThrow();
    }
}
