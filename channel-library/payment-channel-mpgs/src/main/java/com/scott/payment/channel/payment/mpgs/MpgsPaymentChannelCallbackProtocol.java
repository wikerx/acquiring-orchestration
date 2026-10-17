package com.scott.payment.channel.payment.mpgs;

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
 * @author : scott
 * @version : v1.0.0
 * @classname : MpgsPaymentChannelCallbackProtocol
 * @date : 2026-10-17 12:00
 * @email : scott_x@163.com
 * @description : MPGS 完整回调协议，位于 payment-channel-mpgs 适配层，使用 Notification Secret 验证原始请求并映射标准事件和 ACK。
 * @status : create
 */
public class MpgsPaymentChannelCallbackProtocol implements PaymentChannelCallbackProtocol {

    private static final ChannelCallbackProtocolDescriptor DESCRIPTOR =
            new ChannelCallbackProtocolDescriptor(
                    Set.of("POST"),
                    Set.of("application/json", "application/x-www-form-urlencoded"),
                    Set.of(ChannelCallbackScene.STANDARD, ChannelCallbackScene.THREE_DS),
                    256 * 1024,
                    ChannelCallbackAuthentication.REQUIRED);

    private final MpgsCallbackVerifier verifier;
    private final MpgsPaymentChannelCallbackHandler parser;

    /** 创建 MPGS 回调协议并组合认证器与纯解析器。 */
    public MpgsPaymentChannelCallbackProtocol(MpgsCallbackVerifier verifier,
                                              MpgsPaymentChannelCallbackHandler parser) {
        this.verifier = verifier;
        this.parser = parser;
    }

    /** {@inheritDoc} */
    @Override
    public Set<String> channelCodes() {
        return Set.of(MpgsChannelCode.MPGS);
    }

    /** {@inheritDoc} */
    @Override
    public ChannelCallbackProtocolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    /** {@inheritDoc} */
    @Override
    public VerifiedChannelEvent verifyAndParse(RawChannelCallbackRequest request,
                                               ChannelCallbackVerificationContext context) {
        verifier.verify(request, context);
        ChannelCallbackResult result = parser.handle(toParserRequest(request));
        result.setSignatureValid(true);
        return VerifiedChannelEvent.from(result, null);
    }

    /** {@inheritDoc} */
    @Override
    public ChannelCallbackAcknowledgement successAcknowledgement(RawChannelCallbackRequest request,
                                                                  VerifiedChannelEvent event) {
        return ChannelCallbackAcknowledgement.platformSuccess(request.channelCode() + " accepted");
    }

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
