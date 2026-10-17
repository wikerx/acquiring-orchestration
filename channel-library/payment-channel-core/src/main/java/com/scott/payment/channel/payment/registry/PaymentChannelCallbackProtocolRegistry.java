package com.scott.payment.channel.payment.registry;

import com.scott.payment.channel.payment.api.PaymentChannelCallbackProtocol;
import com.scott.payment.channel.payment.dto.callback.ChannelCallbackProtocolDescriptor;
import com.scott.payment.channel.payment.dto.callback.RawChannelCallbackRequest;
import com.scott.payment.channel.payment.enums.ChannelCallbackAuthentication;
import com.scott.payment.channel.payment.exception.ChannelCallbackVerificationException;
import com.scott.payment.channel.payment.exception.ChannelException;
import com.scott.payment.channel.payment.exception.ChannelRequestException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentChannelCallbackProtocolRegistry
 * @date : 2026-10-17 12:00
 * @email : scott_x@163.com
 * @description : 严格渠道回调协议注册器，位于 payment-channel-core 注册层，对重复/未知渠道、HTTP 契约不匹配和未完成认证能力统一失败关闭。
 * @status : create
 */
@Component
public class PaymentChannelCallbackProtocolRegistry {

    private final Map<String, PaymentChannelCallbackProtocol> protocols;

    /** 构建不可变注册表；同一标准渠道编码只能由一个完整协议实现负责。 */
    public PaymentChannelCallbackProtocolRegistry(List<PaymentChannelCallbackProtocol> callbackProtocols) {
        Map<String, PaymentChannelCallbackProtocol> registry = new LinkedHashMap<>();
        for (PaymentChannelCallbackProtocol protocol
                : callbackProtocols == null ? List.<PaymentChannelCallbackProtocol>of() : callbackProtocols) {
            if (protocol == null || protocol.channelCodes() == null || protocol.channelCodes().isEmpty()) {
                throw new ChannelException("渠道回调协议必须声明渠道编码");
            }
            if (protocol.descriptor() == null) {
                throw new ChannelException("渠道回调协议描述不能为空");
            }
            for (String channelCode : protocol.channelCodes()) {
                String normalized = normalize(channelCode);
                if (!StringUtils.hasText(normalized)) {
                    throw new ChannelException("渠道回调协议编码不能为空");
                }
                if (registry.putIfAbsent(normalized, protocol) != null) {
                    throw new ChannelException("渠道回调协议编码重复：" + normalized);
                }
            }
        }
        this.protocols = Collections.unmodifiableMap(registry);
    }

    /** 按大小写不敏感的标准渠道编码获取唯一协议，未知渠道失败关闭。 */
    public PaymentChannelCallbackProtocol getRequired(String channelCode) {
        String normalized = normalize(channelCode);
        PaymentChannelCallbackProtocol protocol = protocols.get(normalized);
        if (protocol == null) {
            throw new ChannelException("未找到渠道回调协议：" + normalized);
        }
        return protocol;
    }

    /** 在执行渠道代码前校验入口场景、HTTP 契约、正文上限和认证能力。 */
    public PaymentChannelCallbackProtocol validateAndGet(RawChannelCallbackRequest request) {
        if (request == null) {
            throw new ChannelRequestException("渠道回调请求不能为空");
        }
        PaymentChannelCallbackProtocol protocol = getRequired(request.channelCode());
        ChannelCallbackProtocolDescriptor descriptor = protocol.descriptor();
        if (!descriptor.supportsScene(request.scene())) {
            throw new ChannelRequestException("渠道回调场景不受支持：" + request.scene());
        }
        if (!descriptor.supportsMethod(request.method())) {
            throw new ChannelRequestException("渠道回调 HTTP 方法不受支持：" + request.method());
        }
        if (!descriptor.supportsContentType(request.firstHeader("Content-Type"))) {
            throw new ChannelRequestException("渠道回调 Content-Type 不受支持");
        }
        if (request.bodyLength() > descriptor.maxBodyBytes()) {
            throw new ChannelRequestException("渠道回调报文超过渠道协议限制");
        }
        if (descriptor.authentication() != ChannelCallbackAuthentication.REQUIRED) {
            throw new ChannelCallbackVerificationException(
                    ChannelCallbackVerificationException.Reason.ALGORITHM_UNSUPPORTED,
                    descriptor.authentication() == ChannelCallbackAuthentication.NONE_WITH_ACTIVE_QUERY
                            ? "无签名渠道的主动查询确认链路尚未实现，已拒绝处理"
                            : "渠道回调认证协议尚未完成，已拒绝处理");
        }
        return protocol;
    }

    /** 返回只读注册表，用于诊断和测试，不允许调用方修改注册关系。 */
    public Map<String, PaymentChannelCallbackProtocol> registeredProtocols() {
        return protocols;
    }

    private static String normalize(String channelCode) {
        return channelCode == null ? null : channelCode.trim().toUpperCase(Locale.ROOT);
    }
}
