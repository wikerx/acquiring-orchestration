package com.scott.payment.channel.payment.registry;

import com.scott.payment.channel.payment.api.PaymentChannelCallbackHandler;
import com.scott.payment.channel.payment.dto.callback.ChannelCallbackRequest;
import com.scott.payment.channel.payment.dto.callback.ChannelCallbackResult;
import com.scott.payment.channel.payment.exception.ChannelException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentChannelCallbackRegistryTests
 * @date : 2026-07-12 22:43
 * @email : scott_x@163.com
 * @description : Payment Channel Callback Registry Tests 自动化测试类，位于 渠道适配库，验证当前模块的正常路径、异常边界和回归场景。
 * @status : create
 */
class PaymentChannelCallbackRegistryTests {

    @Test
    void shouldRegisterCallbackHandlerByUppercaseCode() {
        PaymentChannelCallbackRegistry registry = new PaymentChannelCallbackRegistry(Optional.of(List.of(new StubHandler("mpgs"))));

        assertThat(registry.getRequired("MPGS").channelCode()).isEqualTo("mpgs");
        assertThat(registry.registeredHandlers()).containsKey("MPGS");
    }

    @Test
    void shouldRejectDuplicatedCallbackCode() {
        List<PaymentChannelCallbackHandler> handlers = List.of(new StubHandler("MPGS"), new StubHandler("mpgs"));

        assertThatThrownBy(() -> new PaymentChannelCallbackRegistry(Optional.of(handlers)))
                .isInstanceOf(ChannelException.class)
                .hasMessageContaining("渠道回调编码重复：MPGS");
    }

    private static class StubHandler implements PaymentChannelCallbackHandler {

        private final String channelCode;

        private StubHandler(String channelCode) {
            this.channelCode = channelCode;
        }

        /**
         * 返回构造器指定的渠道编码，用于验证回调处理器注册和查找。
         */
        @Override
        public String channelCode() {
            return channelCode;
        }

        /**
         * 返回空回调结果；当前用例只验证注册表路由，不验证渠道报文解析。
         */
        @Override
        public ChannelCallbackResult handle(ChannelCallbackRequest request) {
            return new ChannelCallbackResult();
        }
    }
}
