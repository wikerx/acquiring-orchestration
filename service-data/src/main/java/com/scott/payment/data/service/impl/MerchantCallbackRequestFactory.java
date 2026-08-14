package com.scott.payment.data.service.impl;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.security.crypto.OpenApiPayloadCrypto;
import com.scott.payment.component.security.jwt.MerchantCallbackJwtSigner;
import com.scott.payment.data.config.DataMerchantNotificationProperties;
import com.scott.payment.data.entity.DataMerchantNotificationTaskDO;
import com.scott.payment.data.model.MerchantCallbackHttpRequest;
import com.scott.payment.data.model.MerchantCallbackSecurityMaterial;
import com.scott.payment.data.service.MerchantCallbackSecurityMaterialProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;

/** 构造 v1 商户回调请求，统一绑定 JWT、Header 事件标识和加密正文。 */
@Component
public class MerchantCallbackRequestFactory {

    /** 当前商户回调协议版本。 */
    public static final String CALLBACK_VERSION = "v1";
    /** 回调协议版本请求头。 */
    public static final String HEADER_CALLBACK_VERSION = "X-Callback-Version";
    /** 当前回调尝试次数请求头。 */
    public static final String HEADER_CALLBACK_TIMES = "X-Callback-Times";
    /** 本次回调事件号请求头，必须与 JWT 的 eventId 和 jti 一致。 */
    public static final String HEADER_CALLBACK_EVENT_ID = "X-Callback-Event-Id";
    /** 平台通知任务号请求头。 */
    public static final String HEADER_NOTIFY_ID = "X-OPGS-Notify-Id";
    /** 平台交易号请求头。 */
    public static final String HEADER_TRANSACTION_ID = "X-OPGS-Transaction-Id";

    /** 商户回调安全材料提供器。 */
    private final MerchantCallbackSecurityMaterialProvider materialProvider;
    /** 回调正文加密器。 */
    private final OpenApiPayloadCrypto payloadCrypto;
    /** 平台回调 JWT 签发器。 */
    private final MerchantCallbackJwtSigner jwtSigner;
    /** 回调 JWT 有效期等协议配置。 */
    private final DataMerchantNotificationProperties properties;
    /** JWT 签发时钟，测试使用固定时钟验证 claims。 */
    private final Clock clock;

    /**
     * 创建使用 UTC 系统时钟的回调请求工厂。
     *
     * @param materialProvider 商户安全材料提供器
     * @param payloadCrypto 回调正文加密器
     * @param jwtSigner 回调 JWT 签发器
     * @param properties 回调协议配置
     */
    @Autowired
    public MerchantCallbackRequestFactory(MerchantCallbackSecurityMaterialProvider materialProvider,
                                          OpenApiPayloadCrypto payloadCrypto,
                                          MerchantCallbackJwtSigner jwtSigner,
                                          DataMerchantNotificationProperties properties) {
        this(materialProvider, payloadCrypto, jwtSigner, properties, Clock.systemUTC());
    }

    MerchantCallbackRequestFactory(MerchantCallbackSecurityMaterialProvider materialProvider,
                                   OpenApiPayloadCrypto payloadCrypto,
                                   MerchantCallbackJwtSigner jwtSigner,
                                   DataMerchantNotificationProperties properties,
                                   Clock clock) {
        this.materialProvider = materialProvider;
        this.payloadCrypto = payloadCrypto;
        this.jwtSigner = jwtSigner;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 以通知任务号作为稳定事件号构造自动投递请求。
     *
     * @param task 商户通知任务
     * @param callbackTimes 当前通知尝试次数
     * @return 已完成 JWT、Header 和正文加密的 HTTP 请求
     */
    public MerchantCallbackHttpRequest create(DataMerchantNotificationTaskDO task, int callbackTimes) {
        if (task == null || !StringUtils.hasText(task.getNotifyId())) {
            throw new IllegalArgumentException("merchant callback notify id can not be blank");
        }
        return create(task, callbackTimes, task.getNotifyId());
    }

    /**
     * 使用调用方提供的稳定事件号构造 v1 商户回调请求。
     *
     * <p>后台人工重发使用 MQ 消息号作为事件号，RocketMQ 重投时 Header、JWT eventId 和 jti 保持一致，
     * 商户 SDK 因而可以对同一人工重发事件做持久幂等。</p>
     *
     * @param task 商户通知任务
     * @param callbackTimes 当前通知尝试次数
     * @param eventId 本次回调事件唯一号
     * @return 已完成 JWT、Header 和正文加密的 HTTP 请求
     */
    public MerchantCallbackHttpRequest create(DataMerchantNotificationTaskDO task,
                                              int callbackTimes,
                                              String eventId) {
        if (!StringUtils.hasText(eventId)) {
            throw new IllegalArgumentException("merchant callback event id can not be blank");
        }
        MerchantCallbackSecurityMaterial material = materialProvider.load(task.getMerchantId());
        String callbackPayloadJson = callbackPayloadJson(task);
        String compactPayload = payloadCrypto.encrypt(
                callbackPayloadJson,
                payloadCrypto.readPublicKey(material.getResponsePublicKey()));
        Instant issuedAt = clock.instant();
        String token = jwtSigner.sign(
                task.getMerchantId(),
                material.getJwtSecret(),
                eventId,
                task.getNotifyId(),
                task.getTransactionId(),
                sha256Hex(compactPayload),
                callbackTimes,
                issuedAt,
                properties.getCallbackJwtTtlSeconds());
        String body = JsonUtils.toJsonString(Map.of("data", compactPayload));
        String auditBody = JsonUtils.toJsonString(Map.of("data", "***", "cipherLength", compactPayload.length()));
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8));
        headers.set(HEADER_CALLBACK_VERSION, CALLBACK_VERSION);
        headers.set(HEADER_CALLBACK_TIMES, String.valueOf(callbackTimes));
        headers.set(HEADER_CALLBACK_EVENT_ID, eventId);
        headers.set(HEADER_NOTIFY_ID, task.getNotifyId());
        headers.set(HEADER_TRANSACTION_ID, task.getTransactionId());
        return new MerchantCallbackHttpRequest(eventId, headers, body, auditBody);
    }

    /**
     * 读取正式协议载荷明文；日志脱敏列不得作为商户业务报文来源。
     *
     * @param task 商户通知任务
     * @return 与同步 API 响应字段口径一致的商户回调 JSON
     */
    private String callbackPayloadJson(DataMerchantNotificationTaskDO task) {
        String payloadJson = task == null ? null : task.getPayloadJson();
        if (!StringUtils.hasText(payloadJson)) {
            throw new IllegalStateException("merchant callback payload is required");
        }
        return payloadJson;
    }

    /** 计算密文载荷摘要，用于把 JWT 与本次 RequestBody 绑定。 */
    private String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
