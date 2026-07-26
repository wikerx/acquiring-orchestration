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

        /**
         * 渠道编码，用于定位 MPGS、WorldPay 等渠道适配实现和路由配置。
         * <p>
         * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
         * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：自动化测试夹具、Mock 对象或测试用例输入。
         * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
         * </p>
         */
        private final String channelCode;

        private StubHandler(String channelCode) {
            this.channelCode = channelCode;
        }

        @Override
        public String channelCode() {
            return channelCode;
        }

        @Override
        public ChannelCallbackResult handle(ChannelCallbackRequest request) {
            return new ChannelCallbackResult();
        }
    }
}
