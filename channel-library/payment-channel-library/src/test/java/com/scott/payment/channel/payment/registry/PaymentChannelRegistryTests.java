package com.scott.payment.channel.payment.registry;

import com.scott.payment.channel.payment.api.PaymentChannelClient;
import com.scott.payment.channel.payment.dto.request.ChannelAuthorizeRequest;
import com.scott.payment.channel.payment.dto.request.ChannelQueryRequest;
import com.scott.payment.channel.payment.enums.ChannelCapability;
import com.scott.payment.channel.payment.exception.ChannelException;
import com.scott.payment.channel.payment.exception.ChannelUnsupportedOperationException;
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

        /**
         * 渠道编码，用于定位 MPGS、WorldPay 等渠道适配实现和路由配置。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：Spring 配置和构造器注入的内部客户端依赖。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private final String channelCode;

        private StubChannelClient(String channelCode) {
            this.channelCode = channelCode;
        }

        @Override
        public String channelCode() {
            return channelCode;
        }

        @Override
        public Set<ChannelCapability> capabilities() {
            return Set.of(ChannelCapability.QUERY);
        }
    }
}
