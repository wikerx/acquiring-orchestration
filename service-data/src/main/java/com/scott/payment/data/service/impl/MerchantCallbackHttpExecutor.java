package com.scott.payment.data.service.impl;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.data.entity.DataMerchantNotificationTaskDO;
import com.scott.payment.data.model.MerchantCallbackAttemptResult;
import com.scott.payment.data.model.MerchantCallbackFailureCode;
import com.scott.payment.data.model.MerchantCallbackHttpRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 执行单次商户 HTTP 回调并把外部结果收敛为显式失败分类。
 *
 * <p>该组件只负责出站协议、网络边界和结果分类，不推进通知任务状态。所有日志仅包含
 * 任务标识、目标摘要和异常类型，不记录 URL 查询参数、JWT、密文或商户响应原文。</p>
 */
@Slf4j
@Component
public class MerchantCallbackHttpExecutor {

    /**
     * 商户按协议确认成功时必须返回的固定响应正文。
     */
    private static final String SUCCESS_ACKNOWLEDGEMENT = "succeed";

    /**
     * 具备有界超时、禁止重定向和连接时 DNS 校验的回调客户端。
     */
    private final RestTemplate restTemplate;

    /**
     * 回调 JWT、协议头和加密正文构造器。
     */
    private final MerchantCallbackRequestFactory requestFactory;

    /**
     * 回调 URL 与解析地址的出站策略校验器。
     */
    private final MerchantCallbackTargetValidator targetValidator;

    /**
     * 创建商户回调 HTTP 执行器。
     *
     * @param restTemplate 商户通知专用 HTTP 客户端
     * @param requestFactory 回调协议请求构造器
     * @param targetValidator 回调目标校验器
     */
    public MerchantCallbackHttpExecutor(
            @Qualifier("dataMerchantNotificationRestTemplate") RestTemplate restTemplate,
            MerchantCallbackRequestFactory requestFactory,
            MerchantCallbackTargetValidator targetValidator) {
        this.restTemplate = restTemplate;
        this.requestFactory = requestFactory;
        this.targetValidator = targetValidator;
    }

    /**
     * 校验目标、构造加密请求并执行一次 HTTP 回调。
     *
     * <p>传输异常无法证明商户是否收到请求，因此返回可重试的 unknown 结果。目标策略拒绝和
     * 确定的非瞬时 4xx 返回不可重试结果，避免无意义地重复访问非法或错误端点。</p>
     *
     * @param task 已通过数据库版本 CAS 抢占的通知任务
     * @param attemptNo 当前尝试序号
     * @param callbackEventId 人工重试事件号；自动投递时为空
     * @return 仅包含脱敏审计数据的回调结果
     */
    public MerchantCallbackAttemptResult execute(DataMerchantNotificationTaskDO task,
                                                 int attemptNo,
                                                 String callbackEventId) {
        String targetUrl = task == null ? null : task.getCallbackUrl();
        if (!StringUtils.hasText(targetUrl)) {
            return MerchantCallbackAttemptResult.failed(
                    MerchantCallbackFailureCode.CALLBACK_URL_MISSING,
                    null, null, "merchant callback url is empty", null, null);
        }

        long startNanos = System.nanoTime();
        MerchantCallbackHttpRequest request = null;
        try {
            targetValidator.validate(targetUrl);
            request = StringUtils.hasText(callbackEventId)
                    ? requestFactory.create(task, attemptNo, callbackEventId)
                    : requestFactory.create(task, attemptNo);
            ResponseEntity<String> response = restTemplate.postForEntity(
                    targetUrl,
                    new HttpEntity<>(request.encryptedBody(), request.headers()),
                    String.class);
            return fromResponse(response, request);
        } catch (HttpStatusCodeException exception) {
            return fromHttpException(exception, request);
        } catch (RestClientException exception) {
            MerchantCallbackTargetRejectedException targetRejected = findTargetRejection(exception);
            if (targetRejected != null) {
                return targetRejected(task, targetRejected);
            }
            log.warn("商户回调传输结果未知, event: DATA_MERCHANT_NOTIFY_TRANSPORT_UNKNOWN, traceId: {}, notifyId: {}, transactionId: {}, merchantId: {}, targetUrlHash: {}, exceptionType: {}, durationMs: {}",
                    TraceContext.getTraceId(), task.getNotifyId(), task.getTransactionId(), task.getMerchantId(),
                    task.getTargetUrlHash(), exception.getClass().getSimpleName(), elapsedMillis(startNanos));
            return MerchantCallbackAttemptResult.failed(
                    MerchantCallbackFailureCode.TRANSPORT_UNKNOWN,
                    null, null,
                    "merchant callback transport error: " + exception.getClass().getSimpleName(),
                    eventId(request), auditBody(request));
        } catch (MerchantCallbackTargetRejectedException exception) {
            return targetRejected(task, exception);
        } catch (RuntimeException exception) {
            log.warn("商户回调协议处理失败, event: DATA_MERCHANT_NOTIFY_PROTOCOL_FAILED, traceId: {}, notifyId: {}, transactionId: {}, merchantId: {}, exceptionType: {}, durationMs: {}",
                    TraceContext.getTraceId(), task.getNotifyId(), task.getTransactionId(), task.getMerchantId(),
                    exception.getClass().getSimpleName(), elapsedMillis(startNanos));
            return MerchantCallbackAttemptResult.failed(
                    MerchantCallbackFailureCode.PROTOCOL_ERROR,
                    null, null,
                    "merchant callback protocol error: " + exception.getClass().getSimpleName(),
                    eventId(request), auditBody(request));
        }
    }

    /**
     * 将正常返回的 HTTP 响应映射为成功、确认词错误或显式 HTTP 失败。
     *
     * @param response 商户 HTTP 响应
     * @param request 已构造的回调请求
     * @return 分类后的安全结果
     */
    private MerchantCallbackAttemptResult fromResponse(ResponseEntity<String> response,
                                                       MerchantCallbackHttpRequest request) {
        int status = response.getStatusCode().value();
        String responseAudit = safeResponseAudit(response.getBody());
        if (status == 200 && SUCCESS_ACKNOWLEDGEMENT.equals(trimmed(response.getBody()))) {
            return MerchantCallbackAttemptResult.succeeded(
                    status, responseAudit, request.eventId(), request.auditBody());
        }
        if (status == 200 || status / 100 == 2) {
            return MerchantCallbackAttemptResult.failed(
                    MerchantCallbackFailureCode.ACKNOWLEDGEMENT_INVALID,
                    status, responseAudit,
                    status == 200
                            ? "merchant callback acknowledgement must be succeed"
                            : "merchant callback http status " + status,
                    request.eventId(), request.auditBody());
        }
        return httpFailure(status, responseAudit, request);
    }

    /**
     * 将 RestTemplate 抛出的 4xx/5xx 响应映射为显式重试语义。
     *
     * @param exception HTTP 状态异常
     * @param request 已构造的回调请求
     * @return 分类后的安全结果
     */
    private MerchantCallbackAttemptResult fromHttpException(HttpStatusCodeException exception,
                                                            MerchantCallbackHttpRequest request) {
        return httpFailure(
                exception.getStatusCode().value(),
                safeResponseAudit(exception.getResponseBodyAsString()),
                request);
    }

    /**
     * 根据 HTTP 状态码决定是否允许自动重试。
     *
     * <p>408、425、429 和所有 5xx 属于瞬时或结果不确定错误；其他 3xx/4xx 是确定协议拒绝，
     * 继续重试不会改变结果。</p>
     *
     * @param status HTTP 状态码
     * @param responseAudit 商户响应安全摘要
     * @param request 已构造的回调请求
     * @return HTTP 失败结果
     */
    private MerchantCallbackAttemptResult httpFailure(int status,
                                                      String responseAudit,
                                                      MerchantCallbackHttpRequest request) {
        MerchantCallbackFailureCode failureCode = isTransientStatus(status)
                ? MerchantCallbackFailureCode.HTTP_TRANSIENT
                : MerchantCallbackFailureCode.HTTP_CLIENT_REJECTED;
        return MerchantCallbackAttemptResult.failed(
                failureCode,
                status,
                responseAudit,
                "merchant callback http status " + status,
                eventId(request),
                auditBody(request));
    }

    /**
     * 判断 HTTP 状态是否代表瞬时失败或接收结果未知。
     *
     * @param status HTTP 状态码
     * @return true 表示允许自动重试
     */
    static boolean isTransientStatus(int status) {
        return status == 408 || status == 425 || status == 429 || status >= 500;
    }

    /**
     * 将目标策略拒绝转换为不重试且不暴露网络细节的结果。
     *
     * @param task 通知任务
     * @param exception 目标拒绝异常
     * @return 永久拒绝结果
     */
    private MerchantCallbackAttemptResult targetRejected(DataMerchantNotificationTaskDO task,
                                                         MerchantCallbackTargetRejectedException exception) {
        log.warn("商户回调目标被安全策略拒绝, event: DATA_MERCHANT_NOTIFY_TARGET_REJECTED, traceId: {}, notifyId: {}, transactionId: {}, merchantId: {}, targetUrlHash: {}, code: {}",
                TraceContext.getTraceId(), task.getNotifyId(), task.getTransactionId(), task.getMerchantId(),
                task.getTargetUrlHash(), exception.getCode());
        return MerchantCallbackAttemptResult.failed(
                MerchantCallbackFailureCode.TARGET_REJECTED,
                null, null, ApiResultEnum.CALLBACK_URL_NOT_ALLOWED.getMessage(), null, null);
    }

    /**
     * 沿异常链识别连接阶段的 DNS 策略拒绝，避免被包装后误判为普通网络错误。
     *
     * @param exception HTTP 客户端异常
     * @return 找到的目标拒绝异常；不存在时返回 null
     */
    private MerchantCallbackTargetRejectedException findTargetRejection(Throwable exception) {
        Throwable current = exception;
        for (int depth = 0; current != null && depth < 16; depth++) {
            if (current instanceof MerchantCallbackTargetRejectedException rejectedException) {
                return rejectedException;
            }
            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return null;
    }

    /**
     * 将不可信商户响应转换为固定确认词或不可逆摘要。
     *
     * @param responseBody 商户响应原文
     * @return 可写入审计表的响应摘要
     */
    private String safeResponseAudit(String responseBody) {
        if (responseBody == null || responseBody.isEmpty()) {
            return responseBody;
        }
        if (SUCCESS_ACKNOWLEDGEMENT.equals(responseBody.trim())) {
            return SUCCESS_ACKNOWLEDGEMENT;
        }
        byte[] responseBytes = responseBody.getBytes(StandardCharsets.UTF_8);
        return "length=" + responseBytes.length + ",sha256=" + sha256(responseBytes);
    }

    /**
     * 计算商户响应摘要，JDK 缺少标准算法时阻止继续处理。
     *
     * @param value 响应 UTF-8 字节
     * @return 小写十六进制 SHA-256
     */
    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", exception);
        }
    }

    /**
     * 返回响应正文去除首尾空白后的值。
     *
     * @param value 响应正文
     * @return 规范化正文；空响应返回 null
     */
    private String trimmed(String value) {
        return value == null ? null : value.trim();
    }

    /**
     * 返回已构造请求的事件号，构造前失败时不伪造标识。
     *
     * @param request 回调请求
     * @return 回调事件号
     */
    private String eventId(MerchantCallbackHttpRequest request) {
        return request == null ? null : request.eventId();
    }

    /**
     * 返回请求的脱敏正文摘要，构造前失败时为空。
     *
     * @param request 回调请求
     * @return 脱敏请求摘要
     */
    private String auditBody(MerchantCallbackHttpRequest request) {
        return request == null ? null : request.auditBody();
    }

    /**
     * 计算单次 HTTP 执行耗时。
     *
     * @param startNanos 单调时钟起点
     * @return 耗时毫秒数
     */
    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}
