package com.scott.payment.channel.payment.worldpay.callback;

import com.scott.payment.channel.payment.worldpay.support.ChannelCode;

import com.scott.payment.channel.payment.api.PaymentChannelCallbackProtocol;
import com.scott.payment.channel.payment.dto.callback.ChannelCallbackAcknowledgement;
import com.scott.payment.channel.payment.dto.callback.ChannelCallbackProtocolDescriptor;
import com.scott.payment.channel.payment.dto.callback.ChannelCallbackVerificationContext;
import com.scott.payment.channel.payment.dto.callback.RawChannelCallbackRequest;
import com.scott.payment.channel.payment.dto.callback.VerifiedChannelEvent;
import com.scott.payment.channel.payment.enums.ChannelCallbackAuthentication;
import com.scott.payment.channel.payment.enums.ChannelCallbackScene;
import com.scott.payment.channel.payment.exception.ChannelCallbackVerificationException;

import java.util.Set;

/**
 * WorldPay 回调协议占位实现，位于渠道适配层；官方认证报文和测试夹具接入前声明 UNSUPPORTED 并统一失败关闭。
 */
public class CallbackProtocol implements PaymentChannelCallbackProtocol {

    private static final ChannelCallbackProtocolDescriptor DESCRIPTOR =
            new ChannelCallbackProtocolDescriptor(
                    Set.of("POST"),
                    Set.of("application/json", "application/xml", "text/xml"),
                    Set.of(ChannelCallbackScene.STANDARD),
                    512 * 1024,
                    ChannelCallbackAuthentication.UNSUPPORTED);

    /**
     * 返回该协议预留处理的 Worldpay JSON 和 XML 渠道编码。
     *
     * @return Worldpay 渠道编码集合
     */
    @Override
    public Set<String> channelCodes() {
        return Set.of(ChannelCode.WPGJSON, ChannelCode.WPGXML);
    }

    /**
     * 返回失败关闭的 Worldpay 回调协议描述。
     *
     * @return 认证能力标记为未支持的协议描述
     */
    @Override
    public ChannelCallbackProtocolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    /**
     * 拒绝未具备正式认证契约的 Worldpay 回调。
     *
     * @param request 原始渠道回调
     * @param context 回调验证上下文
     * @return 本实现不会返回事件
     * @throws ChannelCallbackVerificationException 始终以算法未支持拒绝请求
     */
    @Override
    public VerifiedChannelEvent verifyAndParse(RawChannelCallbackRequest request,
                                               ChannelCallbackVerificationContext context) {
        throw new ChannelCallbackVerificationException(
                ChannelCallbackVerificationException.Reason.ALGORITHM_UNSUPPORTED,
                "WorldPay callback authentication is not production ready");
    }

    /**
     * 构造平台成功处理事件后的确认响应。
     *
     * <p>当前验证入口始终失败关闭，因此该方法仅实现 SPI 完整契约，不能绕过验签单独调用。</p>
     *
     * @param request 原始渠道回调
     * @param event 已验证的标准事件
     * @return 平台统一成功确认
     */
    @Override
    public ChannelCallbackAcknowledgement successAcknowledgement(RawChannelCallbackRequest request,
                                                                  VerifiedChannelEvent event) {
        return ChannelCallbackAcknowledgement.platformSuccess(request.channelCode() + " accepted");
    }
}
