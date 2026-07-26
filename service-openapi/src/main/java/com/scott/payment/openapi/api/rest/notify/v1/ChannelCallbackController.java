package com.scott.payment.openapi.api.rest.notify.v1;

import com.scott.payment.component.core.model.ApiResult;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.core.util.SensitiveDataMaskUtils;
import com.scott.payment.openapi.client.payment.PaymentInternalClient;
import com.scott.payment.openapi.client.payment.dto.TransactionChannelCallbackClientRequestDTO;
import com.scott.payment.openapi.support.OpenApiCallbackSecuritySupport;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
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
 * @description : 渠道回调入口控制器，位于 service-openapi 接口层，负责渠道维度签名/IP 边界校验并把回调原文转发到支付核心落库。
 * @status : create
 */
@RestController
@RequestMapping("/channel/v1/callbacks")
@Slf4j
public class ChannelCallbackController {

    /**
     * 回调类入口安全校验组件。
     */
    private final OpenApiCallbackSecuritySupport callbackSecuritySupport;

    /**
     * 支付核心内部客户端，用于保存渠道回调原文和业务幂等记录。
     */
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
        log.info("event: OPENAPI_CHANNEL_CALLBACK_RECEIVE_START stage=CALLBACK_RECEIVE traceId: {} channelCode: {} method: {} path: {} sourceIp: {} headerSummary: {} bodyLength: {} bodyDigest: {} bodySummary: {}",
                TraceContext.getTraceId(),
                channelCode,
                request.getMethod(),
                request.getRequestURI(),
                resolveClientIp(request),
                headers(request),
                rawBody == null ? 0 : rawBody.length(),
                digest16(rawBody),
                safeLength(SensitiveDataMaskUtils.maskJsonSafely(rawBody), 1200));
        OpenApiCallbackSecuritySupport.CallbackSecurityResult securityResult =
                callbackSecuritySupport.verifyChannelCallback(channelCode, request, rawBody);
        paymentInternalClient.recordChannelCallback(buildCallbackRequest(channelCode, request, rawBody, securityResult));
        log.info("event: OPENAPI_CHANNEL_CALLBACK_RECEIVE_END stage=CALLBACK_RECEIVE traceId: {} channelCode: {} signatureValid: {} ipAllowed: {} durationMs: {}",
                TraceContext.getTraceId(),
                channelCode,
                securityResult.signatureValid(),
                securityResult.ipAllowed(),
                elapsedMillis(startNanos));
        return success(channelCode + " accepted");
    }

    private TransactionChannelCallbackClientRequestDTO buildCallbackRequest(
            String channelCode,
            HttpServletRequest request,
            String rawBody,
            OpenApiCallbackSecuritySupport.CallbackSecurityResult securityResult) {
        TransactionChannelCallbackClientRequestDTO requestDTO = new TransactionChannelCallbackClientRequestDTO();
        requestDTO.setChannelCode(channelCode);
        requestDTO.setCallbackType("CHANNEL_CALLBACK");
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
                || "api-key".equalsIgnoreCase(headerName));
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

    /**
     * 解析resolveclientip，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 商户开放接口服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 构造、转换或解析后的业务值
     */
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

    /**
     * 整理耗时毫秒数，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 商户开放接口服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param startNanos start Nanos 输入值，参与 startnanos 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}
