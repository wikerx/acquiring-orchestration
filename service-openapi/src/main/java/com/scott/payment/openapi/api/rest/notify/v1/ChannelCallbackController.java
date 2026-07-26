package com.scott.payment.openapi.api.rest.notify.v1;

import com.scott.payment.component.core.model.ApiResult;
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
        log.info("event=OPENAPI_CHANNEL_CALLBACK_RECEIVE_START channelCode={} method={} path={} sourceIp={} bodyLength={}",
                channelCode,
                request.getMethod(),
                request.getRequestURI(),
                resolveClientIp(request),
                rawBody == null ? 0 : rawBody.length());
        OpenApiCallbackSecuritySupport.CallbackSecurityResult securityResult =
                callbackSecuritySupport.verifyChannelCallback(channelCode, request, rawBody);
        paymentInternalClient.recordChannelCallback(buildCallbackRequest(channelCode, request, rawBody, securityResult));
        log.info("event=OPENAPI_CHANNEL_CALLBACK_RECEIVE_END channelCode={} signatureValid={} ipAllowed={} durationMs={}",
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
            headers.put(headerName, request.getHeader(headerName));
        }
        return headers;
    }

    /**
     * 接收 resolve Client Ip 接口调用，完成 Web 层参数承接并委托应用服务返回统一响应。
     * <p>
     * 层级边界：商户开放接口服务层；输入来源、输出结构和异常语义由 ChannelCallbackController 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param request request 入参，来源于当前接口、服务或任务调用链，字段含义按所属 DTO、实体或协议模型定义
     * @return 解析或查询得到的业务值
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
     * 接收 elapsed Millis 接口调用，完成 Web 层参数承接并委托应用服务返回统一响应。
     * <p>
     * 层级边界：商户开放接口服务层；输入来源、输出结构和异常语义由 ChannelCallbackController 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param startNanos start Nanos 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}
