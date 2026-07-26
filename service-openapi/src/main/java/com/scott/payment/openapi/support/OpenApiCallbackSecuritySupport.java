package com.scott.payment.openapi.support;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.web.internal.InternalServiceSignature;
import com.scott.payment.openapi.config.OpenApiCallbackProperties;
import com.scott.payment.openapi.security.SecurityInterceptEventRecorder;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiCallbackSecuritySupport
 * @date : 2026-07-11 00:00
 * @email : scott_x@163.com
 * @description : OpenAPI 回调类入口安全校验组件，负责渠道回调签名和商户通知重试维护密钥校验。
 * @status : create
 */
@Component
public class OpenApiCallbackSecuritySupport {

    /**
     * 渠道回调时间戳请求头。
     */
    public static final String CHANNEL_TIMESTAMP_HEADER = "X-Channel-Timestamp";

    /**
     * 渠道回调随机串请求头。
     */
    public static final String CHANNEL_NONCE_HEADER = "X-Channel-Nonce";

    /**
     * 渠道回调签名请求头。
     */
    public static final String CHANNEL_SIGNATURE_HEADER = "X-Channel-Signature";

    /**
     * 商户通知重试维护密钥请求头。
     */
    public static final String NOTIFY_RETRY_TOKEN_HEADER = "X-Notify-Retry-Token";

    /**
     * 回调安全配置。
     */
    private final OpenApiCallbackProperties callbackProperties;

    /**
     * 安全拦截事件记录器，仅记录脱敏排查元数据。
     */
    private final SecurityInterceptEventRecorder securityInterceptEventRecorder;

    /**
     * 创建回调入口安全校验组件。
     *
     * @param callbackProperties 回调安全配置
     * @param securityInterceptEventRecorder 安全拦截事件记录器
     */
    public OpenApiCallbackSecuritySupport(OpenApiCallbackProperties callbackProperties,
                                          SecurityInterceptEventRecorder securityInterceptEventRecorder) {
        this.callbackProperties = callbackProperties;
        this.securityInterceptEventRecorder = securityInterceptEventRecorder;
    }

    /**
     * 校验商户通知重试维护密钥，避免外部直接触发通知重试。
     *
     * @param request HTTP 请求
     */
    public void verifyNotifyRetryToken(HttpServletRequest request) {
        String expectedToken = callbackProperties.getNotifyRetryToken();
        String actualToken = request.getHeader(NOTIFY_RETRY_TOKEN_HEADER);
        if (!StringUtils.hasText(expectedToken)
                || !InternalServiceSignature.matches(expectedToken, actualToken)) {
            throw recordAndReturnOpenApiException(request,
                    "OPENAPI_NOTIFY_RETRY_TOKEN_INVALID",
                    SecurityInterceptEventRecorder.RISK_HIGH,
                    "OPENAPI_NOTIFY_RETRY_TOKEN",
                    "merchant notify retry token is invalid");
        }
    }

    /**
     * 校验渠道回调签名。
     * <p>
     * 签名文本包含 method、path、timestamp、nonce、channelCode 和原始 body 的 SHA-256 摘要，避免回调
     * 业务报文被篡改后仍通过路径级签名。
     *
     * @param channelCode 渠道编码
     * @param request     HTTP 请求
     * @param rawBody     渠道回调原始 body，按 UTF-8 计算 SHA-256 摘要
     */
    public CallbackSecurityResult verifyChannelCallback(String channelCode, HttpServletRequest request, String rawBody) {
        if (!callbackProperties.isChannelSignatureRequired()) {
            return new CallbackSecurityResult(true, true);
        }
        String timestampText = request.getHeader(CHANNEL_TIMESTAMP_HEADER);
        String nonce = request.getHeader(CHANNEL_NONCE_HEADER);
        String signature = request.getHeader(CHANNEL_SIGNATURE_HEADER);
        if (!StringUtils.hasText(channelCode)
                || !StringUtils.hasText(timestampText)
                || !StringUtils.hasText(nonce)
                || !StringUtils.hasText(signature)) {
            throw recordAndReturnChannelException(request,
                    "CHANNEL_SIGNATURE_HEADER_MISSING",
                    SecurityInterceptEventRecorder.RISK_HIGH,
                    "CHANNEL_CALLBACK_SIGNATURE",
                    "channel callback signature headers are required");
        }
        long timestamp;
        try {
            timestamp = parseTimestamp(timestampText);
        } catch (ApiException exception) {
            securityInterceptEventRecorder.recordBlocked(
                    request,
                    SecurityInterceptEventRecorder.SOURCE_CHANNEL,
                    "CHANNEL_SIGNATURE_TIMESTAMP_INVALID",
                    SecurityInterceptEventRecorder.RISK_HIGH,
                    null,
                    "CHANNEL_CALLBACK_SIGNATURE",
                    securityInterceptEventRecorder.reasonCode(exception),
                    securityInterceptEventRecorder.reasonMessage(exception)
            );
            throw exception;
        }
        if (Math.abs(InternalServiceSignature.currentTimeMillis() - timestamp) > callbackProperties.getAllowedClockSkewMillis()) {
            throw recordAndReturnChannelException(request,
                    "CHANNEL_SIGNATURE_TIMESTAMP_EXPIRED",
                    SecurityInterceptEventRecorder.RISK_HIGH,
                    "CHANNEL_CALLBACK_SIGNATURE",
                    "channel callback signature timestamp is expired");
        }
        String normalizedChannelCode = channelCode.toLowerCase(Locale.ROOT);
        String channelSecret = callbackProperties.getChannelSecrets().get(channelCode);
        if (!StringUtils.hasText(channelSecret)) {
            channelSecret = callbackProperties.getChannelSecrets().get(normalizedChannelCode);
        }
        if (!StringUtils.hasText(channelSecret)) {
            throw recordAndReturnChannelException(request,
                    "CHANNEL_CALLBACK_SECRET_MISSING",
                    SecurityInterceptEventRecorder.RISK_CRITICAL,
                    "CHANNEL_CALLBACK_SIGNATURE",
                    "channel callback secret is not configured");
        }
        String expectedSignature = InternalServiceSignature.sign(
                request.getMethod(),
                request.getRequestURI(),
                timestamp,
                nonce,
                normalizedChannelCode,
                sha256Hex(rawBody),
                channelSecret
        );
        if (!InternalServiceSignature.matches(expectedSignature, signature)) {
            throw recordAndReturnChannelException(request,
                    "CHANNEL_SIGNATURE_INVALID",
                    SecurityInterceptEventRecorder.RISK_CRITICAL,
                    "CHANNEL_CALLBACK_SIGNATURE",
                    "channel callback signature is invalid");
        }
        return new CallbackSecurityResult(true, true);
    }

/**
 * 记录andreturnopenapi异常，写入安全、审计或链路排障所需的脱敏上下文。
 * <p>
 * 前置条件：调用方已准备 商户开放接口服务 当前步骤需要的输入对象和业务标识。
 * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
 * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
 * </p>
 * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
 * @param eventType event Type 输入值，参与 eventtype 的查询、校验、转换、写入或日志摘要
 * @param riskLevel risk Level 输入值，参与 风控level 的查询、校验、转换、写入或日志摘要
 * @param hitRuleCode hit Rule Code 输入值，参与 hit规则编码 的查询、校验、转换、写入或日志摘要
 * @param message 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
 * @return 方法执行后的业务结果、更新行数、转换对象或空结果
 */
    private ApiException recordAndReturnOpenApiException(HttpServletRequest request,
                                                         String eventType,
                                                         String riskLevel,
                                                         String hitRuleCode,
                                                         String message) {
        ApiException exception = new ApiException(ApiResultEnum.UNAUTHORIZED, message);
        securityInterceptEventRecorder.recordBlocked(
                request,
                SecurityInterceptEventRecorder.SOURCE_OPENAPI,
                eventType,
                riskLevel,
                null,
                hitRuleCode,
                securityInterceptEventRecorder.reasonCode(exception),
                securityInterceptEventRecorder.reasonMessage(exception)
        );
        return exception;
    }

/**
 * 记录andreturn渠道异常，写入安全、审计或链路排障所需的脱敏上下文。
 * <p>
 * 前置条件：调用方已准备 商户开放接口服务 当前步骤需要的输入对象和业务标识。
 * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
 * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
 * </p>
 * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
 * @param eventType event Type 输入值，参与 eventtype 的查询、校验、转换、写入或日志摘要
 * @param riskLevel risk Level 输入值，参与 风控level 的查询、校验、转换、写入或日志摘要
 * @param hitRuleCode hit Rule Code 输入值，参与 hit规则编码 的查询、校验、转换、写入或日志摘要
 * @param message 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
 * @return 方法执行后的业务结果、更新行数、转换对象或空结果
 */
    private ApiException recordAndReturnChannelException(HttpServletRequest request,
                                                         String eventType,
                                                         String riskLevel,
                                                         String hitRuleCode,
                                                         String message) {
        ApiException exception = new ApiException(ApiResultEnum.UNAUTHORIZED, message);
        securityInterceptEventRecorder.recordBlocked(
                request,
                SecurityInterceptEventRecorder.SOURCE_CHANNEL,
                eventType,
                riskLevel,
                null,
                hitRuleCode,
                securityInterceptEventRecorder.reasonCode(exception),
                securityInterceptEventRecorder.reasonMessage(exception)
        );
        return exception;
    }

    /**
     * 解析parsetimestamp，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 商户开放接口服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param timestampText 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @return 构造、转换或解析后的业务值
     */
    private long parseTimestamp(String timestampText) {
        try {
            return Long.parseLong(timestampText);
        } catch (NumberFormatException exception) {
            throw new ApiException(ApiResultEnum.UNAUTHORIZED, "channel callback signature timestamp is invalid");
        }
    }

    /**
     * 计算SHA-256 十六进制摘要，用不可逆指纹关联原始内容而不暴露明文。
     * <p>
     * 前置条件：调用方已准备 商户开放接口服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param rawBody raw Body 输入值，参与 raw报文体 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String sha256Hex(String rawBody) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((rawBody == null ? "" : rawBody).getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new ApiException(ApiResultEnum.INTERNAL_SERVER_ERROR, "channel callback body digest can not be calculated");
        }
    }

    /**
     * 渠道回调入口安全校验结果。
     *
     * @param signatureValid 签名校验是否通过
     * @param ipAllowed IP 白名单是否通过；当前配置尚未启用独立 IP 规则时按通过记录
     */
    public record CallbackSecurityResult(boolean signatureValid, boolean ipAllowed) {
    }
}
