package com.scott.payment.openapi.result;

import com.scott.payment.component.core.openapi.result.OpenApiResultDefinition;
import com.scott.payment.component.core.openapi.result.OpenApiResultDefinitionRegistry;
import com.scott.payment.component.core.openapi.result.OpenApiResultEvidence;
import com.scott.payment.component.core.openapi.result.OpenApiResultOrigin;
import com.scott.payment.component.core.openapi.result.OpenApiResultPhase;
import com.scott.payment.component.core.openapi.result.OpenApiResultCode;

import java.util.Map;

/**
 * OpenAPI 异常的公开码与内部可审计证据投影。
 *
 * <p>投影不包含 HTTP 决策；HTTP 状态由接口版本边界独立维护，避免业务码反向改变既有传输契约。</p>
 *
 * @param resultCode 对外 V1.3 结果码
 * @param internalCode 原始内部或历史错误码，仅用于诊断
 * @param phase 失败发生阶段
 * @param origin 可验证的失败来源
 * @param requestSent 请求是否已越过发送边界，未知时为 null
 * @param outcomeUncertain 业务结果是否仍不确定，未知时为 null
 * @param fieldPath 已登记的字段路径
 * @param messageParameters 受控文案参数，仍需由文案目录执行受众过滤
 * @param evidence 支撑本次投影的证据类型
 */
public record OpenApiErrorProjection(OpenApiResultCode resultCode,
                                     String internalCode,
                                     OpenApiResultPhase phase,
                                     OpenApiResultOrigin origin,
                                     Boolean requestSent,
                                     Boolean outcomeUncertain,
                                     String fieldPath,
                                     Map<String, String> messageParameters,
                                     OpenApiResultEvidence evidence) {

    /** 固化文案参数，避免响应生成期间被调用方修改。 */
    public OpenApiErrorProjection {
        messageParameters = messageParameters == null ? Map.of() : Map.copyOf(messageParameters);
    }

    /**
     * 根据已发布定义创建不带额外上下文的投影，适用于框架级参数和协议异常。
     *
     * @param resultCode 已登记公开码
     * @return 使用注册表阶段、来源和证据的投影
     */
    public static OpenApiErrorProjection fromResultCode(OpenApiResultCode resultCode) {
        return fromResultCode(resultCode, null);
    }

    /**
     * 根据已发布定义创建字段级框架异常投影。字段路径仅供 INTERNAL/MERCHANT 模板使用，
     * 最终仍由文案目录执行格式白名单和 PAYER 隔离。
     *
     * @param resultCode 已登记公开码
     * @param fieldPath Spring MVC 解析出的字段路径，不得包含请求值
     * @return 带受控字段路径的投影
     */
    public static OpenApiErrorProjection fromResultCode(OpenApiResultCode resultCode,
                                                        String fieldPath) {
        OpenApiResultDefinition definition = OpenApiResultDefinitionRegistry.definition(resultCode);
        Map<String, String> parameters = fieldPath == null || fieldPath.isBlank()
                ? Map.of() : Map.of("field", fieldPath);
        return new OpenApiErrorProjection(resultCode, null, definition.phase(), definition.origin(),
                null, null, fieldPath, parameters, definition.evidence());
    }
}
