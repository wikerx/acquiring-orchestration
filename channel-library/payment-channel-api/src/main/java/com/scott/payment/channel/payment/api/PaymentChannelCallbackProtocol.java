package com.scott.payment.channel.payment.api;

import com.scott.payment.channel.payment.dto.callback.ChannelCallbackAcknowledgement;
import com.scott.payment.channel.payment.dto.callback.ChannelCallbackProtocolDescriptor;
import com.scott.payment.channel.payment.dto.callback.ChannelCallbackVerificationContext;
import com.scott.payment.channel.payment.dto.callback.RawChannelCallbackRequest;
import com.scott.payment.channel.payment.dto.callback.VerifiedChannelEvent;

import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentChannelCallbackProtocol
 * @date : 2026-10-17 12:00
 * @email : scott_x@163.com
 * @description : 渠道回调唯一 SPI，位于 payment-channel-api 契约层，完整声明 HTTP 约束、认证解析和可靠受理后的 ACK 规则。
 * @status : create
 */
public interface PaymentChannelCallbackProtocol {

    /** 返回该协议实现唯一负责的标准渠道编码集合。 */
    Set<String> channelCodes();

    /** 返回渠道回调 HTTP、安全和报文大小契约。 */
    ChannelCallbackProtocolDescriptor descriptor();

    /** 从未解析原始请求中提取密钥定位提示；不得返回密钥本身。 */
    default String credentialHint(RawChannelCallbackRequest request) {
        return null;
    }

    /** 完成渠道认证并解析标准事件；认证失败不得返回事件。 */
    VerifiedChannelEvent verifyAndParse(RawChannelCallbackRequest request,
                                        ChannelCallbackVerificationContext context);

    /** 返回 Inbox 事务提交成功后应发送给渠道的确认协议。 */
    ChannelCallbackAcknowledgement successAcknowledgement(RawChannelCallbackRequest request,
                                                           VerifiedChannelEvent event);
}
