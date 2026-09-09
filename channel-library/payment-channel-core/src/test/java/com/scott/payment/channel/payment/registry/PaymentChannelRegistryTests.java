package com.scott.payment.channel.payment.registry;

import com.scott.payment.channel.payment.api.PaymentChannelClient;
import com.scott.payment.channel.payment.dto.request.ChannelAuthorizeRequest;
import com.scott.payment.channel.payment.dto.request.ChannelQueryRequest;
import com.scott.payment.channel.payment.dto.request.ChannelThreeDsAuthenticationRequest;
import com.scott.payment.channel.payment.enums.ChannelCapability;
import com.scott.payment.channel.payment.exception.ChannelException;
import com.scott.payment.channel.payment.exception.ChannelUnsupportedOperationException;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentChannelRegistryTests
 * @date : 2026-07-12 22:43
 * @email : scott_x@163.com
 * @description : Payment Channel Registry Tests 自动化测试类，位于 渠道适配库，验证当前模块的正常路径、异常边界和回归场景。
 * @status : create
 */
@Slf4j
class PaymentChannelRegistryTests {

    @Test
    void shouldRegisterChannelClientByUppercaseCode() {
        PaymentChannelRegistry registry = new PaymentChannelRegistry(Optional.of(List.of(new StubChannelClient("mpgs"))));

        assertThat(registry.getRequired("MPGS").channelCode()).isEqualTo("mpgs");
        assertThat(registry.registeredClients()).containsKey("MPGS");
    }

    @Test
    void shouldRejectDuplicatedChannelCode() {
        List<PaymentChannelClient> clients = List.of(new StubChannelClient("MPGS"), new StubChannelClient("mpgs"));

        assertThatThrownBy(() -> new PaymentChannelRegistry(Optional.of(clients)))
                .isInstanceOf(ChannelException.class)
                .hasMessageContaining("渠道编码重复：MPGS");
    }

    @Test
    void shouldThrowUnsupportedOperationWhenCapabilityNotImplemented() {
        PaymentChannelClient client = new StubChannelClient("MPGS");

        assertThatThrownBy(() -> client.authorize(new ChannelAuthorizeRequest()))
                .isInstanceOf(ChannelUnsupportedOperationException.class)
                .hasMessageContaining("当前渠道[MPGS]不支持交易能力[AUTHORIZATION]");
    }

    @Test
    void shouldDefaultThreeDsAuthenticationToUnsupported() {
        log.info("渠道SPI默认能力测试开始，case: 未实现3DS的渠道返回不支持");
        PaymentChannelClient client = new StubChannelClient("WPGXML");

        assertThatThrownBy(() -> client.authenticateThreeDs(new ChannelThreeDsAuthenticationRequest()))
                .isInstanceOf(ChannelUnsupportedOperationException.class)
                .hasMessageContaining("当前渠道[WPGXML]不支持交易能力[THREE_DS_AUTHENTICATION]");
        log.info("渠道SPI默认能力测试完成，channelCode: WPGXML, capability: THREE_DS_AUTHENTICATION");
    }

    @Test
    void defaultClientShouldSupportAnyPersistedQueryReference() {
        PaymentChannelClient client = new StubChannelClient("WPGXML");
        ChannelQueryRequest byRequestId = new ChannelQueryRequest();
        byRequestId.getExtension().put("requestId", "CR-LOCAL-001");
        ChannelQueryRequest byOrder = new ChannelQueryRequest();
        byOrder.setChannelOrderNo("ORDER-001");
        ChannelQueryRequest missing = new ChannelQueryRequest();

        assertThat(client.supportsQueryReference(byRequestId)).isTrue();
        assertThat(client.supportsQueryReference(byOrder)).isTrue();
        assertThat(client.supportsQueryReference(missing)).isFalse();
    }

    private static class StubChannelClient implements PaymentChannelClient {

        private final String channelCode;

        private StubChannelClient(String channelCode) {
            this.channelCode = channelCode;
        }

        /**
         * 返回构造器指定的渠道编码，用于验证渠道客户端注册和查找。
         */
        @Override
        public String channelCode() {
            return channelCode;
        }

        /**
         * 固定声明查询能力，使注册表能力筛选具有确定结果。
         */
        @Override
        public Set<ChannelCapability> capabilities() {
            return Set.of(ChannelCapability.QUERY);
        }
    }
}
