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
 * @description : PaymentChannelRegistryTests 自动化测试类，用于验证对应模块的业务规则、异常边界和回归场景，位于 渠道适配层，输入输出边界由所在包和公开方法契约限定。
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
         * channel Code 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
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
