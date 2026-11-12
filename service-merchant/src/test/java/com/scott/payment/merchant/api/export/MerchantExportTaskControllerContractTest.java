package com.scott.payment.merchant.api.export;

import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 商户财务异步导出浏览器路由契约测试，防止后续重构放宽既有导出权限。
 */
class MerchantExportTaskControllerContractTest {

    /**
     * 四类结算导出和余额流水导出必须位于下载中心任务根路径并复用现有业务导出权限。
     */
    @Test
    void shouldKeepFinanceExportRoutesAndPermissions() throws Exception {
        RequestMapping root = MerchantExportTaskController.class.getAnnotation(RequestMapping.class);
        assertThat(root.value()).containsExactly("/merchant/operations/export-tasks");

        assertRoute("submitSettlementBatches", "settlement-batches",
                "merchant:settlement:batch:export");
        assertRoute("submitSettlementSummaries", "settlement-summaries",
                "merchant:settlement:batch:summary:export");
        assertRoute("submitSettlementTransactions", "settlement-transactions",
                "merchant:settlement:transaction-item:export");
        assertRoute("submitSettlementReserves", "settlement-reserves",
                "merchant:settlement:reserve-item:export");
        assertRoute("submitFundLedgers", "fund-ledgers",
                "merchant:fund:ledger:export");
    }

    /**
     * 校验单个提交方法的相对路径和精确权限编码。
     */
    private void assertRoute(String methodName, String path, String permission) throws Exception {
        Method method = findMethod(methodName);
        assertThat(method.getAnnotation(PostMapping.class).value()).containsExactly("/" + path);
        assertThat(method.getAnnotation(RequiresPermission.class).value()).isEqualTo(permission);
    }

    /**
     * 按公开方法名称定位单参数提交入口，避免测试依赖 DTO 的实现类名。
     */
    private Method findMethod(String methodName) {
        return java.util.Arrays.stream(MerchantExportTaskController.class.getMethods())
                .filter(method -> method.getName().equals(methodName) && method.getParameterCount() == 1)
                .findFirst()
                .orElseThrow();
    }
}
