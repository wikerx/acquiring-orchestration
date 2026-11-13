package com.scott.payment.admin.api.channel;

import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证支付路由配置接口使用独立、细粒度的最小权限。 */
class AdminPaymentRouteControllerContractTest {

    @Test
    void everyRouteConfigurationEndpointShouldRequireDedicatedPermission() {
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("pageRules", "channel:route:list");
        expected.put("getRule", "channel:route:detail");
        expected.put("merchantConflicts", "channel:route:detail");
        expected.put("createRule", "channel:route:add");
        expected.put("updateRule", "channel:route:edit");
        expected.put("copyRule", "channel:route:copy");
        expected.put("publishRule", "channel:route:publish");
        expected.put("disableRule", "channel:route:disable");
        expected.put("deleteRule", "channel:route:remove");
        expected.put("validateRule", "channel:route:validate");
        expected.put("pageChangeLogs", "channel:route:history");

        expected.forEach((methodName, permission) -> {
            RequiresPermission annotation = java.util.Arrays.stream(
                            AdminPaymentRouteController.class.getDeclaredMethods())
                    .filter(method -> method.getName().equals(methodName))
                    .findFirst()
                    .orElseThrow()
                    .getAnnotation(RequiresPermission.class);
            assertThat(annotation).isNotNull();
            assertThat(annotation.value()).isEqualTo(permission);
        });
    }
}
