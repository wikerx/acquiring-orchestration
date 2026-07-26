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
 * @description : PaymentChannelCallbackRegistryTests 自动化测试类，用于验证对应模块的业务规则、异常边界和回归场景，位于 渠道适配层，输入输出边界由所在包和公开方法契约限定。
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
         * channel Code 字段，表示当前模型在所属业务流程中的对应属性。
         * <p>
         * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
         * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
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
