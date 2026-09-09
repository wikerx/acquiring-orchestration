package com.scott.payment.openapi.api.rest.notify.v1;

import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.core.model.ApiResult;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.openapi.client.payment.PaymentInternalClient;
import com.scott.payment.openapi.client.payment.dto.TransactionChannelCallbackClientRequestDTO;
import com.scott.payment.openapi.support.OpenApiCallbackSecuritySupport;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Enumeration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.scott.payment.component.core.model.ApiResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelCallbackController
 * @date : 2026-05-28 10:58
 * @email : scott_x@163.com
 * @description : 渠道回调 HTTP 控制器，位于 商户开放接口服务，只承接参数、鉴权注解和统一响应，业务编排委托应用服务。
 * @status : create
 */
@RestController
@RequestMapping("/channel/v1/callbacks")
@Slf4j
public class ChannelCallbackController {

    /**
     * 普通渠道异步通知的内部回调分类。
     */
    private static final String CHANNEL_CALLBACK_TYPE = "CHANNEL_CALLBACK";

    /**
     * 3DS 认证通知的统一内部回调分类。
     */
    private static final String THREE_DS_CALLBACK_TYPE = "THREE_DS_AUTHENTICATION_CALLBACK";

    /**
     * 3DS 回调写入支付核心时使用的统一渠道事件类型。
     */
    private static final String THREE_DS_EVENT_TYPE = "THREE_DS_CALLBACK";

    /**
     * 回调类入口安全校验组件。
     */
    private final OpenApiCallbackSecuritySupport callbackSecuritySupport;

    private final PaymentInternalClient paymentInternalClient;

    /**
     * 创建渠道回调控制器。
     *
     * @param callbackSecuritySupport 回调类入口安全校验组件
     * @param paymentInternalClient 支付核心内部客户端
     */
    public ChannelCallbackController(OpenApiCallbackSecuritySupport callbackSecuritySupport,
                                     PaymentInternalClient paymentInternalClient) {
        this.callbackSecuritySupport = callbackSecuritySupport;
        this.paymentInternalClient = paymentInternalClient;
    }

    /**
     * 接收渠道侧回调通知。
     *
     * @param channelCode 渠道编码
     * @param request HTTP 请求上下文
     * @param rawBody 渠道回调原文
     * @return 回调受理结果
     */
    @PostMapping("/{channelCode}")
    public ApiResult<String> receive(@PathVariable("channelCode") String channelCode,
                                     HttpServletRequest request,
                                     @RequestBody(required = false) String rawBody) {
        long startNanos = System.nanoTime();
        log.info("event: OPENAPI_CHANNEL_CALLBACK_RECEIVE_START stage=CALLBACK_RECEIVE traceId: {} channelCode: {} method: {} path: {} sourceIp: {} headerCount: {} bodyLength: {} bodyDigest: {}",
                TraceContext.getTraceId(),
                channelCode,
                request.getMethod(),
                request.getRequestURI(),
                resolveClientIp(request),
                headerCount(request),
                utf8Length(rawBody),
                digest16(rawBody));
        OpenApiCallbackSecuritySupport.CallbackSecurityResult securityResult =
                callbackSecuritySupport.verifyChannelCallback(channelCode, request, rawBody);
        paymentInternalClient.recordChannelCallback(buildCallbackRequest(channelCode, CHANNEL_CALLBACK_TYPE,
                null, request, rawBody, securityResult));
        log.info("event: OPENAPI_CHANNEL_CALLBACK_RECEIVE_END stage=CALLBACK_RECEIVE traceId: {} channelCode: {} signatureValid: {} ipAllowed: {} durationMs: {}",
                TraceContext.getTraceId(),
                channelCode,
                securityResult.signatureValid(),
                securityResult.ipAllowed(),
                elapsedMillis(startNanos));
        return success(channelCode + " accepted");
    }

    /**
     * 接收渠道 3DS 认证回调通知。
     *
     * @param channelCode 渠道编码
     * @param request HTTP 请求上下文
     * @param rawBody 渠道 3DS 回调原文
     * @return 回调受理结果
     */
    @PostMapping("/{channelCode}/3ds")
    public ApiResult<String> receiveThreeDs(@PathVariable("channelCode") String channelCode,
                                            HttpServletRequest request,
                                            @RequestBody(required = false) String rawBody) {
        long startNanos = System.nanoTime();
        log.info("event: OPENAPI_CHANNEL_3DS_CALLBACK_RECEIVE_START stage=CALLBACK_RECEIVE traceId: {} channelCode: {} method: {} path: {} sourceIp: {} headerCount: {} bodyLength: {} bodyDigest: {}",
                TraceContext.getTraceId(),
                channelCode,
                request.getMethod(),
                request.getRequestURI(),
                resolveClientIp(request),
                headerCount(request),
                utf8Length(rawBody),
                digest16(rawBody));
        OpenApiCallbackSecuritySupport.CallbackSecurityResult securityResult =
                callbackSecuritySupport.verifyChannelCallback(channelCode, request, rawBody);
        paymentInternalClient.recordChannelCallback(buildCallbackRequest(channelCode, THREE_DS_CALLBACK_TYPE,
                THREE_DS_EVENT_TYPE, request, rawBody, securityResult));
        log.info("event: OPENAPI_CHANNEL_3DS_CALLBACK_RECEIVE_END stage=CALLBACK_RECEIVE traceId: {} channelCode: {} signatureValid: {} ipAllowed: {} durationMs: {}",
                TraceContext.getTraceId(),
                channelCode,
                securityResult.signatureValid(),
                securityResult.ipAllowed(),
                elapsedMillis(startNanos));
        return success(channelCode + " 3ds accepted");
    }

    /**
     * Webhook providers determine delivery from HTTP status, so callback failures must not be wrapped in HTTP 200.
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResult<Void>> handleCallbackApiException(ApiException exception) {
        HttpStatus status = switch (exception.getCode()) {
            case "F401" -> HttpStatus.UNAUTHORIZED;
            case "F502" -> HttpStatus.BAD_GATEWAY;
            case "F500" -> HttpStatus.INTERNAL_SERVER_ERROR;
            default -> exception.getCode() != null && exception.getCode().startsWith("F4")
                    ? HttpStatus.BAD_REQUEST : HttpStatus.SERVICE_UNAVAILABLE;
        };
        return ResponseEntity.status(status).body(ApiResult.fail(exception.getCode(), exception.getMessage()));
    }

    /**
     * 组装发送至支付核心的渠道回调记录请求。
     *
     * <p>请求携带渠道安全校验结论和原始报文，由支付核心负责持久化、幂等处理及后续状态流转；
     * 本方法不依据 Redis 或回调内容直接确认交易终态。原始报文仅用于受控审计链路，不应写入普通日志。</p>
     *
     * @param channelCode     渠道编码
     * @param callbackType    内部回调分类
     * @param channelEventType 渠道事件类型；普通回调可为空
     * @param request         当前 HTTP 请求
     * @param rawBody         渠道回调原始报文
     * @param securityResult  渠道签名与来源 IP 校验结果
     * @return 支付核心回调记录请求
     */
    private TransactionChannelCallbackClientRequestDTO buildCallbackRequest(
            String channelCode,
            String callbackType,
            String channelEventType,
            HttpServletRequest request,
            String rawBody,
            OpenApiCallbackSecuritySupport.CallbackSecurityResult securityResult) {
        TransactionChannelCallbackClientRequestDTO requestDTO = new TransactionChannelCallbackClientRequestDTO();
        requestDTO.setChannelCode(channelCode);
        requestDTO.setCallbackType(callbackType);
        requestDTO.setChannelEventType(channelEventType);
        requestDTO.setRequestUri(request.getRequestURI());
        requestDTO.setHttpMethod(request.getMethod());
        requestDTO.setSourceIp(resolveClientIp(request));
        requestDTO.setRequestHeaders(headers(request));
        requestDTO.setRequestBody(rawBody);
        requestDTO.setSignatureValid(securityResult.signatureValid());
        requestDTO.setIpAllowed(securityResult.ipAllowed());
        requestDTO.setReceivedTime(LocalDateTime.now());
        return requestDTO;
    }

    /**
     * 提取数量和长度受控的回调请求头摘要。
     *
     * <p>认证、会话和签名类请求头只保留存在性、长度及不可逆摘要，不回传明文凭据；
     * 最多采集 32 个请求头，防止异常请求制造无界持久化数据。</p>
     *
     * @param request 当前 HTTP 请求
     * @return 可写入受控回调审计记录的请求头摘要
     */
    private Map<String, String> headers(HttpServletRequest request) {
        Enumeration<String> headerNames = request.getHeaderNames();
        if (headerNames == null) {
            return Collections.emptyMap();
        }
        Map<String, String> headers = new LinkedHashMap<>();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            if (headers.size() >= 32) {
                break;
            }
            headers.put(headerName, safeHeaderValue(headerName, request.getHeader(headerName)));
        }
        return headers;
    }

    /**
     * 统计回调请求头数量，普通日志只记录数量，不输出任何请求头名称或值。
     *
     * @param request 当前 HTTP 请求
     * @return 非负请求头数量
     */
    private int headerCount(HttpServletRequest request) {
        Enumeration<String> headerNames = request.getHeaderNames();
        if (headerNames == null) {
            return 0;
        }
        int count = 0;
        while (headerNames.hasMoreElements()) {
            headerNames.nextElement();
            count++;
        }
        return count;
    }

    /**
     * 计算回调原文的 UTF-8 字节数，避免多字节文本使用字符数造成审计偏差。
     *
     * @param value 回调原文
     * @return UTF-8 字节数；空值返回 0
     */
    private int utf8Length(String value) {
        return value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length;
    }

    /**
     * 生成渠道回调请求头安全摘要。
     *
     * @param headerName 请求头名称
     * @param headerValue 请求头原始值
     * @return 可写入日志和回调日志表的摘要值
     */
    private String safeHeaderValue(String headerName, String headerValue) {
        if (!StringUtils.hasText(headerValue)) {
            return headerValue;
        }
        if (isSecretHeader(headerName)) {
            return "present,length=" + headerValue.length() + ",digest=" + digest16(headerValue);
        }
        if (isSignatureHeader(headerName)) {
            return "length=" + headerValue.length() + ",digest=" + digest16(headerValue);
        }
        return safeLength(headerValue, 160);
    }

    /**
     * 判断是否为认证或会话请求头。
     *
     * @param headerName 请求头名称
     * @return true 表示只能记录存在性、长度和摘要
     */
    private boolean isSecretHeader(String headerName) {
        return headerName != null
                && ("authorization".equalsIgnoreCase(headerName)
                || "cookie".equalsIgnoreCase(headerName)
                || "set-cookie".equalsIgnoreCase(headerName)
                || "x-api-key".equalsIgnoreCase(headerName)
                || "api-key".equalsIgnoreCase(headerName)
                || "x-notification-secret".equalsIgnoreCase(headerName));
    }

    /**
     * 判断是否为渠道签名类请求头。
     *
     * @param headerName 请求头名称
     * @return true 表示只记录摘要
     */
    private boolean isSignatureHeader(String headerName) {
        if (headerName == null) {
            return false;
        }
        String normalized = headerName.toLowerCase();
        return normalized.contains("signature")
                || normalized.contains("sign")
                || normalized.contains("hmac")
                || normalized.contains("digest");
    }

    /**
     * 截断日志摘要文本。
     *
     * @param value 原始文本
     * @param maxLength 最大长度
     * @return 长度受控的摘要
     */
    private String safeLength(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    /**
     * 计算文本 SHA-256 短摘要，用于关联渠道回调报文而不暴露原文。
     *
     * @param value 原始文本
     * @return 16 位十六进制摘要
     */
    private String digest16(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes).substring(0, 16);
        } catch (NoSuchAlgorithmException exception) {
            return "sha256_unavailable";
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp;
        }
        return request.getRemoteAddr();
    }

    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}
