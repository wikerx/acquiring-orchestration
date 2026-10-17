package com.scott.payment.channel.payment.scottchannela;

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
 * @classname : ScottChannelAPaymentChannelCallbackProtocol
 * @date : 2026-10-17 12:00
 * @email : scott_x@163.com
 * @description : ScottChannelA 完整回调协议，位于渠道适配层，按 MID 定位密钥、对原始字节执行 HMAC 验签并返回渠道要求的纯文本 ACK。
 * @status : create
 */
public class ScottChannelAPaymentChannelCallbackProtocol implements PaymentChannelCallbackProtocol {

    private static final ChannelCallbackProtocolDescriptor DESCRIPTOR =
            new ChannelCallbackProtocolDescriptor(
                    Set.of("POST"),
                    Set.of("application/json"),
                    Set.of(ChannelCallbackScene.STANDARD),
                    256 * 1024,
                    ChannelCallbackAuthentication.REQUIRED);

    private final ScottChannelACallbackVerifier verifier;
    private final ScottChannelACallbackHandler parser;

    /** 创建 ScottChannelA 回调协议并组合认证器与纯解析器。 */
    public ScottChannelAPaymentChannelCallbackProtocol(ScottChannelACallbackVerifier verifier,
                                                       ScottChannelACallbackHandler parser) {
        this.verifier = verifier;
        this.parser = parser;
    }

    /** {@inheritDoc} */
    @Override
    public Set<String> channelCodes() {
        return Set.of(ScottChannelACode.CODE);
    }

    /** {@inheritDoc} */
    @Override
    public ChannelCallbackProtocolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    /** {@inheritDoc} */
    @Override
    public String credentialHint(RawChannelCallbackRequest request) {
        String merchantId = request.firstHeader(ScottChannelACallbackVerifier.MERCHANT_ID_HEADER);
        return merchantId == null ? null : merchantId.trim();
    }

    /** {@inheritDoc} */
    @Override
    public VerifiedChannelEvent verifyAndParse(RawChannelCallbackRequest request,
                                               ChannelCallbackVerificationContext context) {
        verifier.verify(request, context);
        ChannelCallbackResult result = parser.handle(toParserRequest(request));
        result.setSignatureValid(true);
        return VerifiedChannelEvent.from(result, credentialHint(request));
    }

    /** {@inheritDoc} */
    @Override
    public ChannelCallbackAcknowledgement successAcknowledgement(RawChannelCallbackRequest request,
                                                                  VerifiedChannelEvent event) {
        return ChannelCallbackAcknowledgement.plainText("SUCCESS");
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
