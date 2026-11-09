package com.scott.payment.settlement.api.internal;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证历史标签本金差额调整只暴露在 settlement 内部受签名路径下。
 */
class SettlementPrincipalCorrectionInternalControllerContractTest {

    @Test
    void controllerShouldUseInternalSettlementNamespace() {
        RequestMapping mapping = SettlementPrincipalCorrectionInternalController.class
                .getAnnotation(RequestMapping.class);

        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly("/internal/settlement/v1/principal-corrections");
    }
}
