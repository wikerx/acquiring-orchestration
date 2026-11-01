package com.scott.payment.openapi.result;

import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.openapi.result.OpenApiResultDefinition;
import com.scott.payment.component.core.openapi.result.OpenApiResultDefinitionRegistry;
import com.scott.payment.component.core.openapi.result.OpenApiResultEvidence;
import com.scott.payment.component.core.openapi.result.OpenApiResultOrigin;
import com.scott.payment.component.core.openapi.result.OpenApiResultCode;
import com.scott.payment.openapi.security.MerchantIpWhitelistAccessDeniedException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 将内部旧码和结构化异常证据收敛为 OpenAPI V1.3 公开码。
 *
 * <p>映射只依赖稳定编码和受控事实，禁止透传内部异常或渠道原始文案；证据不足时失败关闭为平台安全码。</p>
 */
public final class OpenApiErrorMapper {

    /**
     * 结果映射器只提供静态投影能力，不允许实例化。
     */
    private OpenApiErrorMapper() {
    }

    /**
     * 投影服务异常；已是 V1.3 的编码保持不变，旧码按明确语义映射，未知码失败关闭为 F2000。
     *
     * @param exception 内部服务异常
     * @return 公开错误码及其可审计来源证据；HTTP 状态由 Web 边界独立维护
     */
    public static OpenApiErrorProjection project(ServiceException exception) {
        OpenApiErrorContext context = exception instanceof OpenApiContextualException contextualException
                ? contextualException.getErrorContext() : null;
        OpenApiResultCode direct = OpenApiResultCode.fromCode(exception.getCode()).orElse(null);
        OpenApiResultCode resultCode = direct == null
                ? mapLegacyCode(exception, context)
                : normalOutcomeCode(direct) ? OpenApiResultCode.SYSTEM_ERROR : direct;
        return projection(resultCode, exception.getCode(), context);
    }

    /**
     * 正常请求码和交易基础状态只能由正常响应或交易投影产生。
     * 如果它们出现在异常上，说明调用链违反了外层契约，必须失败关闭而不能伪装成成功。
     */
    private static boolean normalOutcomeCode(OpenApiResultCode resultCode) {
        return switch (resultCode) {
            case REQUEST_SUCCESS, REQUEST_ACCEPTED, REQUEST_PARTIALLY_ACCEPTED,
                    TRANSACTION_SUCCESS, TRANSACTION_PROCESSING, TRANSACTION_PENDING -> true;
            default -> false;
        };
    }

    /**
     * 按已登记兼容规则映射内部旧码；模糊网关码必须结合结构化渠道证据，不能仅凭异常类型判断。
     *
     * @param exception 内部服务异常
     * @param context 可选的结构化事实上下文
     * @return 已登记公开码；未知旧码回退为安全故障码
     */
    private static OpenApiResultCode mapLegacyCode(ServiceException exception,
                                                    OpenApiErrorContext context) {
        String code = exception.getCode();
        if (exception instanceof MerchantIpWhitelistAccessDeniedException) {
            return OpenApiResultCode.SOURCE_NOT_ALLOWED;
        }
        if (code == null) {
            return OpenApiResultCode.SYSTEM_ERROR;
        }
        return switch (code) {
            case "T200", "T201", "T202", "T203", "T206" -> OpenApiResultCode.SYSTEM_ERROR;
            case "F400", "Z605" -> OpenApiResultCode.BAD_REQUEST;
            case "F401001" -> OpenApiResultCode.AUTHORIZATION_MISSING;
            case "F401003", "F401004" -> OpenApiResultCode.AUTHORIZATION_TIME_INVALID;
            case "F401", "F401002", "F401005", "F401006", "F401007" ->
                    OpenApiResultCode.AUTHENTICATION_FAILED;
            case "F401008" -> OpenApiResultCode.MERCHANT_SIGNING_MATERIAL_MISSING;
            case "F401009" -> OpenApiResultCode.MERCHANT_INVALID;
            case "F401010" -> OpenApiResultCode.MERCHANT_ACCESS_BLOCKED;
            case "F402001" -> OpenApiResultCode.PARAM_INVALID;
            case "F402002" -> OpenApiResultCode.PARAM_MISSING;
            case "F402003" -> OpenApiResultCode.ENCRYPTED_DATA_INVALID;
            case "F402004" -> OpenApiResultCode.CALLBACK_URL_NOT_ALLOWED;
            case "F403" -> OpenApiResultCode.FORBIDDEN;
            case "F404" -> OpenApiResultCode.API_NOT_FOUND;
            case "F405" -> OpenApiResultCode.METHOD_NOT_ALLOWED;
            case "F429" -> OpenApiResultCode.TOO_MANY_REQUESTS;
            case "F409" -> OpenApiResultCode.MERCHANT_CONFIG_UNAVAILABLE;
            case "F409001" -> OpenApiResultCode.MERCHANT_CONFIG_UNAVAILABLE;
            case "F410", "F413" -> OpenApiResultCode.CARD_NOT_SUPPORTED;
            case "F411" -> OpenApiResultCode.CURRENCY_NOT_SUPPORTED;
            case "F412" -> OpenApiResultCode.TRANSACTION_TYPE_NOT_SUPPORTED;
            case "F414", "F518" -> OpenApiResultCode.ORIGINAL_ACTION_NOT_ALLOWED;
            case "F207" -> OpenApiResultCode.CHANNEL_RETURNED_ERROR;
            case "F210" -> evidenceBasedFailure(context, OpenApiResultCode.TRANSACTION_FAILED);
            case "F502", "F503", "Z606" -> evidenceBasedFailure(context, OpenApiResultCode.SYSTEM_ERROR);
            case "F500", "F513", "F519", "F520" -> OpenApiResultCode.SYSTEM_ERROR;
            case "F500001" -> OpenApiResultCode.SYSTEM_ERROR;
            case "F510", "F515" -> OpenApiResultCode.BUSINESS_IDENTIFIER_CONFLICT;
            case "F510001" -> OpenApiResultCode.IDEMPOTENCY_CONFLICT;
            case "F510002" -> OpenApiResultCode.ORIGINAL_ACTION_NOT_ALLOWED;
            case "F511", "F512" -> OpenApiResultCode.BUSINESS_RESOURCE_NOT_FOUND;
            case "F516" -> OpenApiResultCode.REFUND_AMOUNT_EXCEEDED;
            case "F517" -> OpenApiResultCode.REFUND_APPROVAL_CONFLICT;
            default -> evidenceBasedFailure(context, OpenApiResultCode.SYSTEM_ERROR);
        };
    }

    /**
     * 仅在上下文提供了完整来源证据时区分渠道明确失败和渠道结果未确认。
     * 请求未发出、结果确定或证据不完整时均失败关闭为调用方指定的安全兜底码。
     */
    private static OpenApiResultCode evidenceBasedFailure(OpenApiErrorContext context,
                                                          OpenApiResultCode fallback) {
        if (context == null) {
            return fallback;
        }
        if (context.origin() == OpenApiResultOrigin.CHANNEL_RESPONSE
                && context.evidence() == OpenApiResultEvidence.CONFIRMED_CHANNEL_RESPONSE
                && !Boolean.TRUE.equals(context.outcomeUncertain())) {
            return OpenApiResultCode.CHANNEL_RETURNED_ERROR;
        }
        if (context.origin() == OpenApiResultOrigin.CHANNEL_TRANSPORT
                && context.evidence() == OpenApiResultEvidence.UNCONFIRMED_CHANNEL_OUTCOME
                && Boolean.TRUE.equals(context.requestSent())
                && Boolean.TRUE.equals(context.outcomeUncertain())) {
            return OpenApiResultCode.CHANNEL_CALL_UNCONFIRMED;
        }
        return fallback;
    }

    /**
     * 合并注册表默认元数据和调用链提供的结构化证据，生成不可变异常投影。
     *
     * @param resultCode 已完成语义判定的公开码
     * @param internalCode 内部稳定编码，仅用于审计
     * @param context 可选的结构化事实上下文
     * @return 不包含异常原文的错误投影
     */
    private static OpenApiErrorProjection projection(OpenApiResultCode resultCode,
                                                     String internalCode,
                                                     OpenApiErrorContext context) {
        OpenApiResultDefinition definition = OpenApiResultDefinitionRegistry.definition(resultCode);
        Map<String, String> parameters = new LinkedHashMap<>();
        if (context != null) {
            parameters.putAll(context.messageParameters());
            if (context.fieldPath() != null) {
                parameters.put("field", context.fieldPath());
            }
        }
        return new OpenApiErrorProjection(
                resultCode,
                internalCode,
                context == null || context.phase() == null ? definition.phase() : context.phase(),
                context == null || context.origin() == null ? definition.origin() : context.origin(),
                context == null ? null : context.requestSent(),
                context == null ? null : context.outcomeUncertain(),
                context == null ? null : context.fieldPath(),
                parameters,
                context == null || context.evidence() == null ? definition.evidence() : context.evidence());
    }

}
