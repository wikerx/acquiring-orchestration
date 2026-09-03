package com.scott.payment.channel.payment.registry;

import com.scott.payment.channel.payment.api.PaymentChannelCallbackVerifier;
import com.scott.payment.channel.payment.dto.callback.ChannelCallbackVerificationRequest;
import com.scott.payment.channel.payment.exception.ChannelException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentChannelCallbackVerifierRegistry
 * @date : 2026-08-12 00:00
 * @email : scott_x@163.com
 * @description : 渠道回调验签注册器，优先选择 provider verifier，并为迁移期未知渠道保留唯一协议中立回退实现。
 * @status : create
 */
@Component
public class PaymentChannelCallbackVerifierRegistry {

    private final Map<String, PaymentChannelCallbackVerifier> verifiers;
    private final PaymentChannelCallbackVerifier fallbackVerifier;

    public PaymentChannelCallbackVerifierRegistry(List<PaymentChannelCallbackVerifier> callbackVerifiers) {
        Map<String, PaymentChannelCallbackVerifier> registry = new LinkedHashMap<>();
        PaymentChannelCallbackVerifier fallback = null;
        for (PaymentChannelCallbackVerifier verifier : callbackVerifiers == null ? List.<PaymentChannelCallbackVerifier>of() : callbackVerifiers) {
            if (verifier.channelCodes() == null || verifier.channelCodes().isEmpty()) {
                if (fallback != null) {
                    throw new ChannelException("渠道回调验签回退实现重复");
                }
                fallback = verifier;
                continue;
            }
            for (String channelCode : verifier.channelCodes()) {
                String normalized = normalize(channelCode);
                if (registry.putIfAbsent(normalized, verifier) != null) {
                    throw new ChannelException("渠道回调验签编码重复：" + normalized);
                }
            }
        }
        this.verifiers = Map.copyOf(registry);
        this.fallbackVerifier = fallback;
    }

    /**
     * 按规范化渠道编码选择回调验签器并执行校验。
     *
     * <p>未注册渠道专用实现时才使用唯一兜底验签器；两者均不存在时直接失败，
     * 禁止未验签回调进入渠道回调处理链。</p>
     *
     * @param request 渠道回调验签上下文，必须包含可识别的渠道编码
     */
    public void verify(ChannelCallbackVerificationRequest request) {
        PaymentChannelCallbackVerifier verifier = verifiers.get(normalize(request.channelCode()));
        if (verifier == null) {
            verifier = fallbackVerifier;
        }
        if (verifier == null) {
            throw new ChannelException("未找到渠道回调验签实现：" + normalize(request.channelCode()));
        }
        verifier.verify(request);
    }

    /**
     * 返回按规范化渠道编码注册的回调验签器只读视图。
     * <p>
     * 返回不可变注册表视图，不修改验签器注册状态。
     * </p>
     * @return 符合当前条件的只读集合或映射结果
     */
    public Map<String, PaymentChannelCallbackVerifier> registeredVerifiers() {
        return verifiers;
    }

    private static String normalize(String channelCode) {
        return channelCode == null ? null : channelCode.trim().toUpperCase(Locale.ROOT);
    }
}
