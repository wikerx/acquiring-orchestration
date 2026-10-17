package com.scott.payment.payment.support;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ProcessorNodeIdentityTests
 * @date : 2026-10-17 00:00
 * @email : scott_x@163.com
 * @description : 支付处理节点身份解析测试，覆盖默认关闭、显式环境变量和非法 Pod IP。
 * @status : create
 */
class ProcessorNodeIdentityTests {

    @Test
    void shouldRemainDisabledWithoutExplicitRolloutSwitch() {
        ProcessorNodeIdentity identity = ProcessorNodeIdentity.from(Map.of(
                "POD_NAME", "payment-7b9f",
                "POD_IP", "10.20.30.40"
        ));

        assertThat(identity.persistenceEnabled()).isFalse();
        assertThat(identity.serviceName()).isNull();
        assertThat(identity.privateIp()).isNull();
    }

    @Test
    void shouldUseOnlyExplicitDeploymentIdentity() {
        ProcessorNodeIdentity identity = ProcessorNodeIdentity.from(Map.of(
                "PAYMENT_PROCESSOR_AUDIT_ENABLED", "true",
                "SPRING_APPLICATION_NAME", "service-payment",
                "POD_NAME", "payment-7b9f",
                "HOSTNAME", "payment-7b9f",
                "POD_IP", "10.20.30.40"
        ));

        assertThat(identity.persistenceEnabled()).isTrue();
        assertThat(identity.serviceName()).isEqualTo("service-payment");
        assertThat(identity.instanceId()).isEqualTo("payment-7b9f");
        assertThat(identity.hostName()).isEqualTo("payment-7b9f");
        assertThat(identity.privateIp()).isEqualTo("10.20.30.40");
    }

    @Test
    void shouldDiscardInvalidPrivateIpInsteadOfGuessingNetworkInterface() {
        ProcessorNodeIdentity identity = ProcessorNodeIdentity.from(Map.of(
                "PAYMENT_PROCESSOR_AUDIT_ENABLED", "true",
                "POD_IP", "not-an-ip"
        ));

        assertThat(identity.persistenceEnabled()).isTrue();
        assertThat(identity.serviceName()).isEqualTo("service-payment");
        assertThat(identity.privateIp()).isNull();
    }
}
