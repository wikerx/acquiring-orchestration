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
     * {@code NOTIFICATION_SECRET_HEADER}，表示 HTTP 请求或响应头集合，敏感头只能记录摘要。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；高敏感字段，禁止明文打印日志，禁止写入异常消息。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    public static final String NOTIFICATION_SECRET_HEADER = "X-Notification-Secret";
    /**
     * 通知ID请求头，表示 HTTP 请求或响应头集合，敏感头只能记录摘要。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    public static final String NOTIFICATION_ID_HEADER = "X-Notification-ID";
    /**
     * {@code NOTIFICATION_ATTEMPT_HEADER}，表示 HTTP 请求或响应头集合，敏感头只能记录摘要。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    public static final String NOTIFICATION_ATTEMPT_HEADER = "X-Notification-Attempt";

    /**
     * 校验 MPGS 通知请求头中的共享密钥。
     *
     * <p>请求头或平台配置缺失时分别返回明确的内部失败原因；密钥使用
     * {@link MessageDigest#isEqual(byte[], byte[])} 比较，避免普通字符串比较泄露时序信息。</p>
     *
     * @param request MPGS 回调验签上下文，包含通知请求头和平台默认密钥
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
