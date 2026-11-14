package com.scott.payment.admin.api.risk;

import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class AdminRiskTradeBlackControllerPermissionTest {

    @Test
    void shouldProtectEveryFileOperationWithBatchImportPermission() {
        assertPermission("previewFile", "risk:tradeBlack:system:batchAdd");
        assertPermission("executeFile", "risk:tradeBlack:system:batchAdd");
        assertPermission("template", "risk:tradeBlack:system:batchAdd");
        assertPermission("failureReport", "risk:tradeBlack:system:list");
        assertPermission("submitExport", "risk:tradeBlack:system:export");
    }

    private void assertPermission(String methodName, String expectedPermission) {
        Method method = Arrays.stream(AdminRiskTradeBlackController.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
        RequiresPermission permission = method.getAnnotation(RequiresPermission.class);
        assertThat(permission).isNotNull();
        assertThat(permission.value()).isEqualTo(expectedPermission);
    }
}
