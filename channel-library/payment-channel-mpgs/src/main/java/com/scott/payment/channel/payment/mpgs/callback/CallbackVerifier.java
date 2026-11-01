package com.scott.payment.channel.payment.mpgs.callback;

import com.scott.payment.channel.payment.dto.callback.ChannelCallbackVerificationContext;
import com.scott.payment.channel.payment.dto.callback.RawChannelCallbackRequest;
import com.scott.payment.channel.payment.exception.ChannelCallbackVerificationException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * MPGS Webhook 验证器，按官方 X-Notification-Secret 契约校验通知密钥。
 */
public class CallbackVerifier {

    /**
     * MPGS 通知密钥请求头；请求值和配置值均禁止写入日志。
     */
    public static final String NOTIFICATION_SECRET_HEADER = "X-Notification-Secret";

    /**
     * MPGS 通知唯一标识请求头，用于回调链路关联和重复通知识别。
     */
    public static final String NOTIFICATION_ID_HEADER = "X-Notification-ID";

    /**
     * MPGS 通知投递次数请求头，用于排查渠道重试过程。
     */
    public static final String NOTIFICATION_ATTEMPT_HEADER = "X-Notification-Attempt";

    /**
     * 校验 MPGS 通知请求头中的共享密钥。
     *
     * <p>请求头或平台配置缺失时分别返回明确的内部失败原因；密钥使用
     * {@link MessageDigest#isEqual(byte[], byte[])} 比较，避免普通字符串比较泄露时序信息。</p>
     *
     * @param request MPGS 原始回调请求
     * @param context MPGS 回调验签上下文，包含平台默认密钥
     */
    public void verify(RawChannelCallbackRequest request, ChannelCallbackVerificationContext context) {
        String actualSecret = request.firstHeader(NOTIFICATION_SECRET_HEADER);
        if (!hasText(actualSecret)) {
            throw failure(ChannelCallbackVerificationException.Reason.HEADER_MISSING,
                    "MPGS notification secret header is required");
        }
        if (!hasText(context.defaultSecret())) {
            throw failure(ChannelCallbackVerificationException.Reason.SECRET_MISSING,
                    "MPGS notification secret is not configured");
        }
        if (!MessageDigest.isEqual(
                context.defaultSecret().getBytes(StandardCharsets.UTF_8),
                actualSecret.getBytes(StandardCharsets.UTF_8))) {
            throw failure(ChannelCallbackVerificationException.Reason.SIGNATURE_INVALID,
                    "MPGS notification secret is invalid");
        }
    }

    /**
     * 判断验签配置或请求头是否包含有效文本。
     *
     * @param value 待检查文本
     * @return true 表示文本非空白
     */
    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * 创建不包含密钥或签名原文的稳定验签异常。
     *
     * @param reason 验签失败分类
     * @param message 安全错误说明
     * @return 渠道回调验签异常
     */
    private ChannelCallbackVerificationException failure(ChannelCallbackVerificationException.Reason reason,
                                                         String message) {
        return new ChannelCallbackVerificationException(reason, message);
    }
}
