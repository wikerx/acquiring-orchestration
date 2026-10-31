package com.scott.payment.channel.payment.mpgs.callback;

import com.scott.payment.channel.payment.mpgs.support.ChannelCode;

import com.scott.payment.channel.payment.api.PaymentChannelCallbackProtocol;
import com.scott.payment.channel.payment.dto.callback.ChannelCallbackAcknowledgement;
import com.scott.payment.channel.payment.dto.callback.ChannelCallbackProtocolDescriptor;
import com.scott.payment.channel.payment.dto.callback.ChannelCallbackRequest;
import com.scott.payment.channel.payment.dto.callback.ChannelCallbackResult;
import com.scott.payment.channel.payment.dto.callback.ChannelCallbackVerificationContext;
import com.scott.payment.channel.payment.dto.callback.RawChannelCallbackRequest;
import com.scott.payment.channel.payment.dto.callback.VerifiedChannelEvent;
import com.scott.payment.channel.payment.enums.ChannelCallbackAuthentication;
import com.scott.payment.channel.payment.enums.ChannelCallbackScene;

import java.util.Set;

/**
 * MPGS 完整回调协议，位于 payment-channel-mpgs 适配层，使用 Notification Secret 验证原始请求并映射标准事件和 ACK。
 */
public class CallbackProtocol implements PaymentChannelCallbackProtocol {

    private static final ChannelCallbackProtocolDescriptor DESCRIPTOR =
            new ChannelCallbackProtocolDescriptor(
                    Set.of("POST"),
                    Set.of("application/json", "application/x-www-form-urlencoded"),
                    Set.of(ChannelCallbackScene.STANDARD, ChannelCallbackScene.THREE_DS),
                    256 * 1024,
                    ChannelCallbackAuthentication.REQUIRED);

    private final CallbackVerifier verifier;
    private final CallbackHandler parser;

    /**
     * 创建 MPGS 回调协议并组合认证器与纯解析器。
     *
     * @param verifier 原始请求验签器
     * @param parser 已验签回调解析器
     */
    public CallbackProtocol(CallbackVerifier verifier,
                                              CallbackHandler parser) {
        this.verifier = verifier;
        this.parser = parser;
    }

    /**
     * 返回该协议负责处理的稳定渠道编码。
     *
     * @return 仅包含 MPGS 的渠道编码集合
     */
    @Override
    public Set<String> channelCodes() {
        return Set.of(ChannelCode.MPGS);
    }

    /**
     * 返回 MPGS 回调方法、媒体类型、场景、大小和认证要求。
     *
     * @return MPGS 回调协议描述
     */
    @Override
    public ChannelCallbackProtocolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    /**
     * 验证原始回调签名后，将报文解析为不可变标准事件。
     *
     * @param request 保留原始字节和多值请求头的渠道回调
     * @param context 已定位的回调密钥及时间窗口
     * @return 已通过签名验证的标准渠道事件
     */
    @Override
    public VerifiedChannelEvent verifyAndParse(RawChannelCallbackRequest request,
                                               ChannelCallbackVerificationContext context) {
        verifier.verify(request, context);
        ChannelCallbackResult result = parser.handle(toParserRequest(request));
        result.setSignatureValid(true);
        return VerifiedChannelEvent.from(result, null);
    }

    /**
     * 构造平台完成事件处理后的成功确认响应。
     *
     * @param request 原始渠道回调
     * @param event 已验证并处理成功的标准事件
     * @return 平台统一成功确认
     */
    @Override
    public ChannelCallbackAcknowledgement successAcknowledgement(RawChannelCallbackRequest request,
                                                                  VerifiedChannelEvent event) {
        return ChannelCallbackAcknowledgement.platformSuccess(request.channelCode() + " accepted");
    }

    /**
     * 将无损回调请求转换为旧解析器使用的兼容对象。
     *
     * @param source 已通过验签的原始回调
     * @return 仅供纯解析器读取的回调对象
     */
    private ChannelCallbackRequest toParserRequest(RawChannelCallbackRequest source) {
        ChannelCallbackRequest request = new ChannelCallbackRequest();
        request.setChannelCode(source.channelCode());
        request.setRequestUri(source.rawPath());
        request.setClientIp(source.sourceIp());
        request.setHeaders(source.firstValueHeaders());
        request.setBody(source.bodyUtf8());
        return request;
    }
}
