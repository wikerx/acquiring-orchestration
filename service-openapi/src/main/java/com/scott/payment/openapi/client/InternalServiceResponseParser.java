package com.scott.payment.openapi.client;

import com.alibaba.fastjson2.TypeReference;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.openapi.result.OpenApiContextualException;
import org.springframework.util.StringUtils;

/**
 * 解析平台内部 HTTP 统一响应，并在响应缺失或损坏时保留调用结果确定性。
 */
public final class InternalServiceResponseParser {

    /**
     * 工具类不允许实例化。
     */
    private InternalServiceResponseParser() {
    }

    /**
     * 将内部服务响应正文解析为统一响应对象。
     * <p>
     * HTTP 响应已经返回时可以确认请求越过发送边界。正文为空、无法解析或解析结果为空时，
     * 写操作可能已被下游受理，因此必须保留结果不确定事实。
     * </p>
     *
     * @param responseBody HTTP 响应正文
     * @param responseType 统一响应泛型
     * @param internalReason 不含响应原文的稳定诊断摘要
     * @param writeOperation 当前调用是否可能改变业务状态
     * @param <T> 响应 data 类型
     * @return 已解析的统一响应
     * @throws OpenApiContextualException 响应正文不可采信时抛出
     */
    public static <T> CommonResult<T> parse(String responseBody,
                                             TypeReference<CommonResult<T>> responseType,
                                             String internalReason,
                                             boolean writeOperation) {
        if (!StringUtils.hasText(responseBody)) {
            throw malformedResponse(internalReason, writeOperation);
        }
        try {
            CommonResult<T> result = JsonUtils.parseObject(responseBody, responseType);
            if (result == null) {
                throw malformedResponse(internalReason, writeOperation);
            }
            return result;
        } catch (OpenApiContextualException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw malformedResponse(internalReason, writeOperation);
        }
    }

    /**
     * 创建不包含响应正文或解析异常原文的结构化平台调用异常。
     *
     * @param internalReason 稳定诊断摘要
     * @param writeOperation 当前调用是否可能改变业务状态
     * @return 请求已发送的结构化异常
     */
    private static OpenApiContextualException malformedResponse(String internalReason,
                                                                 boolean writeOperation) {
        return OpenApiContextualException.internalServiceCall(
                internalReason,
                Boolean.TRUE,
                writeOperation);
    }
}
