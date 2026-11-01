package com.scott.payment.channel.payment.worldpay.callback;

import com.scott.payment.channel.payment.dto.callback.ChannelCallbackVerificationContext;
import com.scott.payment.channel.payment.dto.callback.RawChannelCallbackRequest;
import com.scott.payment.channel.payment.enums.ChannelCallbackAuthentication;
import com.scott.payment.channel.payment.enums.ChannelCallbackScene;
import com.scott.payment.channel.payment.exception.ChannelCallbackVerificationException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 Worldpay 正式回调协议在认证契约缺失时保持失败关闭。
 */
class CallbackProtocolTests {

    /**
     * 协议应同时声明 JSON 和 XML 渠道编码，且不得宣称已支持认证。
     */
    @Test
    void shouldDeclareUnsupportedAuthenticationForBothChannelCodes() {
        CallbackProtocol protocol = new CallbackProtocol();

        assertThat(protocol.channelCodes()).containsExactlyInAnyOrder("WPGJSON", "WPGXML");
        assertThat(protocol.descriptor().authentication())
                .isEqualTo(ChannelCallbackAuthentication.UNSUPPORTED);
    }

    /**
     * 未建立官方验签契约前，JSON 回调必须拒绝进入业务状态机。
     */
    @Test
    void shouldRejectJsonCallbackWithoutSupportedAuthenticationContract() {
        assertUnsupported(callbackRequest("WPGJSON", "application/json", "{\"eventType\":\"sentForSettlement\"}"));
    }

    /**
     * 未建立官方验签契约前，XML 回调必须拒绝进入业务状态机。
     */
    @Test
    void shouldRejectXmlCallbackWithoutSupportedAuthenticationContract() {
        assertUnsupported(callbackRequest("WPGXML", "application/xml", "<paymentService/>"));
    }

    /**
     * 断言指定回调因认证算法未支持而被安全拒绝。
     *
     * @param request 原始渠道回调请求
     */
    private void assertUnsupported(RawChannelCallbackRequest request) {
        CallbackProtocol protocol = new CallbackProtocol();
        ChannelCallbackVerificationContext context =
                new ChannelCallbackVerificationContext(null, Map.of(), 0L, System.currentTimeMillis());

        assertThatThrownBy(() -> protocol.verifyAndParse(request, context))
                .isInstanceOf(ChannelCallbackVerificationException.class)
                .extracting(exception -> ((ChannelCallbackVerificationException) exception).getReason())
                .isEqualTo(ChannelCallbackVerificationException.Reason.ALGORITHM_UNSUPPORTED);
    }

    /**
     * 构造保留正文原始字节的测试回调请求。
     *
     * @param channelCode 渠道编码
     * @param contentType HTTP Content-Type
     * @param body 回调正文
     * @return 原始渠道回调请求
     */
    private RawChannelCallbackRequest callbackRequest(String channelCode, String contentType, String body) {
        return new RawChannelCallbackRequest(
                channelCode,
                ChannelCallbackScene.STANDARD,
                "POST",
                "/channel/v1/callbacks/" + channelCode,
                null,
                Map.of("Content-Type", List.of(contentType)),
                body.getBytes(StandardCharsets.UTF_8),
                "127.0.0.1");
    }
}
