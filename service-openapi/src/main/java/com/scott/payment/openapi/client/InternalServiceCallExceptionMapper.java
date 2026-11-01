package com.scott.payment.openapi.client;

import com.alibaba.fastjson2.TypeReference;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.openapi.result.OpenApiContextualException;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * 将平台内部 HTTP 调用异常转换为 OpenAPI 稳定错误码和结果确定性证据。
 *
 * <p>下游已经返回统一业务错误时保留其机器码，供 OpenAPI 结果映射器准确投影；连接中断、超时或
 * 非法响应只记录平台故障，不根据异常文案推断渠道状态。</p>
 */
public final class InternalServiceCallExceptionMapper {

    /**
     * 工具类不允许实例化。
     */
    private InternalServiceCallExceptionMapper() {
    }

    /**
     * 转换内部 REST 调用异常。
     *
     * @param exception Spring REST 客户端异常
     * @param internalReason 不含敏感数据的稳定诊断摘要
     * @param writeOperation 当前调用是否可能改变业务状态
     * @return 可交给 OpenAPI 统一异常出口处理的异常
     */
    public static ApiException map(RestClientException exception,
                                   String internalReason,
                                   boolean writeOperation) {
        if (exception instanceof RestClientResponseException responseException) {
            ApiException businessException = parseBusinessException(responseException);
            if (businessException != null) {
                return businessException;
            }
            return OpenApiContextualException.internalServiceCall(
                    internalReason, Boolean.TRUE, writeOperation);
        }
        return OpenApiContextualException.internalServiceCall(
                internalReason, null, writeOperation);
    }

    /**
     * 从非 2xx HTTP 响应中提取内部服务统一业务码。
     *
     * <p>响应正文不会写入日志。正文缺失、格式非法、成功码与 HTTP 状态冲突时返回 {@code null}，
     * 由调用方按平台故障处理。</p>
     *
     * @param exception 带 HTTP 响应的客户端异常
     * @return 明确业务失败异常；响应不可采信时返回 {@code null}
     */
    private static ApiException parseBusinessException(RestClientResponseException exception) {
        String responseBody = exception.getResponseBodyAsString();
        if (!StringUtils.hasText(responseBody)) {
            return null;
        }
        try {
            CommonResult<Object> result = JsonUtils.parseObject(
                    responseBody,
                    new TypeReference<CommonResult<Object>>() {
                    });
            if (result == null || CommonResult.isSuccess(result) || !StringUtils.hasText(result.getCode())) {
                return null;
            }
            return new ApiException(result.getCode(), result.getMessage());
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
