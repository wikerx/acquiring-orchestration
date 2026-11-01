package com.scott.payment.openapi.result;

import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.core.openapi.result.OpenApiResultCode;
import com.scott.payment.component.core.openapi.result.OpenApiResultDefinition;
import com.scott.payment.component.core.openapi.result.OpenApiResultDefinitionRegistry;
import com.scott.payment.component.core.openapi.result.OpenApiResultEvidence;
import com.scott.payment.component.core.openapi.result.OpenApiResultOrigin;
import com.scott.payment.component.core.openapi.result.OpenApiResultPhase;

import java.util.Map;

/**
 * OpenAPI 边界使用的结构化异常。
 *
 * <p>调用链只能在掌握可靠事实时携带来源、证据和结果确定性；公开返回码由结构化上下文投影，
 * 禁止根据异常文案猜测业务结果。</p>
 */
public class OpenApiContextualException extends ApiException {

    private static final long serialVersionUID = 1L;

    /**
     * 仅包含受控枚举、布尔事实和安全模板参数的投影上下文。
     */
    private final OpenApiErrorContext errorContext;

    /**
     * 创建带结构化投影证据的 OpenAPI 异常。
     *
     * @param code 内部或历史错误码
     * @param message 内部稳定摘要，不会直接返回给商户或付款人
     * @param errorContext 可验证的失败上下文
     */
    public OpenApiContextualException(String code,
                                      String message,
                                      OpenApiErrorContext errorContext) {
        super(code, message);
        this.errorContext = errorContext;
    }

    /**
     * 获取投影证据。
     *
     * @return 结构化错误上下文
     */
    public OpenApiErrorContext getErrorContext() {
        return errorContext;
    }

    /**
     * 创建字段级参数异常。字段路径可进入商户模板，诊断原因仅允许 INTERNAL 模板使用。
     *
     * @param resultCode 仅允许 A1001 或 A1002
     * @param fieldPath 已登记的接口字段路径，不得传入字段值
     * @param internalReason 不含请求值或敏感数据的内部稳定原因
     * @return 带请求阶段验证证据的结构化异常
     */
    public static OpenApiContextualException parameter(OpenApiResultCode resultCode,
                                                        String fieldPath,
                                                        String internalReason) {
        if (resultCode != OpenApiResultCode.PARAM_INVALID
                && resultCode != OpenApiResultCode.PARAM_MISSING) {
            throw new IllegalArgumentException("Only parameter result codes can carry a field path");
        }
        OpenApiResultDefinition definition = OpenApiResultDefinitionRegistry.definition(resultCode);
        Map<String, String> parameters = internalReason == null || internalReason.isBlank()
                ? Map.of() : Map.of("internalReason", internalReason);
        return new OpenApiContextualException(
                resultCode.getCode(),
                internalReason == null || internalReason.isBlank()
                        ? resultCode.canonicalKey() : internalReason,
                new OpenApiErrorContext(
                        definition.phase(),
                        definition.origin(),
                        false,
                        false,
                        fieldPath,
                        parameters,
                        definition.evidence()));
    }

    /**
     * 创建内部服务调用失败异常，并保留请求发送边界和业务结果确定性。
     *
     * <p>该上下文只表示平台内部调用结果，不得伪装成渠道超时。调用方取得明确业务错误码时应直接
     * 透传该稳定编码；只有未取得可解析响应时才使用本工厂记录平台故障事实。</p>
     *
     * @param internalReason 不含请求数据或下游异常原文的稳定诊断摘要
     * @param requestSent 是否确认请求已经越过 HTTP 发送边界；无法确认时为 {@code null}
     * @param outcomeUncertain 写操作是否可能已被下游受理但未取得响应
     * @return 带平台调用证据的结构化异常
     */
    public static OpenApiContextualException internalServiceCall(String internalReason,
                                                                 Boolean requestSent,
                                                                 boolean outcomeUncertain) {
        return new OpenApiContextualException(
                OpenApiResultCode.SYSTEM_ERROR.getCode(),
                internalReason,
                new OpenApiErrorContext(
                        OpenApiResultPhase.PLATFORM,
                        OpenApiResultOrigin.PLATFORM_SYSTEM,
                        requestSent,
                        outcomeUncertain,
                        null,
                        Map.of(),
                        OpenApiResultEvidence.PLATFORM_FAILURE));
    }
}
