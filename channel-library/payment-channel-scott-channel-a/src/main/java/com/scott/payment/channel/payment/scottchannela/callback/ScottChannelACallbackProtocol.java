package com.scott.payment.channel.payment.scottchannela.callback;

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
import com.scott.payment.channel.payment.scottchannela.support.ChannelCode;

import java.util.Set;

/**
 * 使用原始请求体、MID 级密钥和纯文本 ACK 的 ScottChannelA 回调协议。
 **/
public class ScottChannelACallbackProtocol implements PaymentChannelCallbackProtocol {

    private static final ChannelCallbackProtocolDescriptor DESCRIPTOR =
            new ChannelCallbackProtocolDescriptor(
                    Set.of("POST"),
                    Set.of("application/json"),
                    Set.of(ChannelCallbackScene.STANDARD),
                    256 * 1024,
                    ChannelCallbackAuthentication.REQUIRED);

    private final CallbackVerifier verifier;
    private final CallbackHandler parser;

    /**
     * 创建 ScottChannelA 回调协议并组合认证器与纯解析器。
     *
     * @param verifier 基于原始 HTTP 请求体执行 HMAC-SHA256 验签的认证器
     * @param parser 验签通过后解析交易身份、状态和渠道证据的解析器
     **/
    public ScottChannelACallbackProtocol(CallbackVerifier verifier, CallbackHandler parser) {
        this.verifier = verifier;
        this.parser = parser;
    }

    /**
     * 返回本协议能够处理的稳定渠道编码。
     *
     * @return 仅包含 {@code SCOTT_CHANNEL_A} 的不可变集合
     **/
    @Override
    public Set<String> channelCodes() {
        return Set.of(ChannelCode.VALUE);
    }

    /**
     * 声明 ScottChannelA 只接受 JSON POST 回调，并强制认证及 256 KiB 请求体上限。
     *
     * @return 回调入口在读取正文前使用的协议约束
     **/
    @Override
    public ChannelCallbackProtocolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    /**
     * 从请求头读取渠道 MID，供上层选择该 MID 对应的回调密钥。
     *
     * <p>该值仅用于定位凭据，不能代替验签；解析器还会校验请求头 MID 与正文 MID 一致。</p>
     *
     * @param request 未解析的渠道回调请求
     * @return 去除首尾空白的 MID；请求头缺失时返回 {@code null}
     **/
    @Override
    public String credentialHint(RawChannelCallbackRequest request) {
        String merchantId = request.firstHeader(CallbackVerifier.MERCHANT_ID_HEADER);
        return merchantId == null ? null : merchantId.trim();
    }

    /**
     * 先对原始请求字节验签，再解析并校验交易身份，防止解析或重新序列化改变签名内容。
     *
     * @param request 未解析的渠道回调请求
     * @param context 上层按 MID 解析出的回调密钥上下文
     * @return 已认证且已标准化的渠道事件
     * @throws com.scott.payment.channel.payment.exception.ChannelCallbackVerificationException 验签失败时抛出
     * @throws com.scott.payment.channel.payment.exception.ChannelRequestException 回调正文或交易身份无效时抛出
     **/
    @Override
    public VerifiedChannelEvent verifyAndParse(RawChannelCallbackRequest request,
                                               ChannelCallbackVerificationContext context) {
        verifier.verify(request, context);
        ChannelCallbackResult result = parser.handle(toParserRequest(request));
        result.setSignatureValid(true);
        return VerifiedChannelEvent.from(result, credentialHint(request));
    }

    /**
     * 生成渠道要求的成功确认正文。该方法仅在事件已经验签并被平台接受后调用。
     *
     * @param request 原始回调请求
     * @param event 已验证的渠道事件
     * @return 内容为 {@code SUCCESS} 的纯文本确认响应
     **/
    @Override
    public ChannelCallbackAcknowledgement successAcknowledgement(RawChannelCallbackRequest request,
                                                                  VerifiedChannelEvent event) {
        return ChannelCallbackAcknowledgement.plainText("SUCCESS");
    }

    /**
     * 将协议层原始请求转换为解析器输入，不修改正文内容。
     **/
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
