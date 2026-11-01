package com.scott.payment.component.core.openapi.result;

/**
 * 将支付核心事实投影为 OpenAPI V1.3 结果。
 *
 * <p>投影只使用受控状态和原因码，不解释渠道原文，也不根据历史商户响应码猜测渠道事实。
 * 对象相关的公开文案统一从 {@link OpenApiResultMessageCatalog} 取得，避免支付核心、管理端和
 * OpenAPI 各自维护文案而产生语义漂移。</p>
 */
public final class OpenApiTransactionResultProjector {

    /**
     * {@code CHANNEL_RESULT_UNKNOWN}常量，统一 {@code OpenApiTransactionResultProjector} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String CHANNEL_RESULT_UNKNOWN = "CHANNEL_RESULT_UNKNOWN";
    /**
     * {@code CHANNEL_DECLINED}常量，统一 {@code OpenApiTransactionResultProjector} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String CHANNEL_DECLINED = "CHANNEL_DECLINED";
    /**
     * {@code RISK_REJECTED}常量，统一 {@code OpenApiTransactionResultProjector} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String RISK_REJECTED = "RISK_REJECTED";
    /**
     * {@code STATE_TRANSITION_DENIED}常量，统一 {@code OpenApiTransactionResultProjector} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String STATE_TRANSITION_DENIED = "STATE_TRANSITION_DENIED";
    /**
     * 渠道请求失败常量，统一 {@code OpenApiTransactionResultProjector} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：请求链路、回调链路或跨服务调用上下文。
     * </p>
     */
    private static final String CHANNEL_REQUEST_FAILED = "CHANNEL_REQUEST_FAILED";
    /**
     * {@code CHANNEL_REQUEST_NOT_SENT}常量，统一 {@code OpenApiTransactionResultProjector} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：请求链路、回调链路或跨服务调用上下文。
     * </p>
     */
    private static final String CHANNEL_REQUEST_NOT_SENT = "CHANNEL_REQUEST_NOT_SENT";
    /**
     * 渠道超时常量，统一 {@code OpenApiTransactionResultProjector} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String CHANNEL_TIMEOUT = "CHANNEL_TIMEOUT";
    /**
     * {@code CHANNEL_RESPONSE_INVALID}，用于定位 {@code OpenApiTransactionResultProjector} 关联的上游配置、渠道、账号、角色或业务记录。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：请求链路、回调链路或跨服务调用上下文。
     * 字段关系：与 channelCode、channelMidId 或渠道交易号共同定位渠道侧记录。
     * </p>
     */
    private static final String CHANNEL_RESPONSE_INVALID = "CHANNEL_RESPONSE_INVALID";

    private OpenApiTransactionResultProjector() {
    }

    /**
     * 投影交易结果，不修改传入状态，也不使用渠道原始码或原始文案。
     * <p>
     * {@code F3000} 表示尚未取得可验证的渠道结论，可以与 PROCESSING/PENDING 并存；只有
     * {@code CHANNEL_DECLINED} 这种明确证据才返回 {@code F1000}。legacyMerchantCode 仅保留为兼容入参，
     * 不参与渠道拒绝判定，避免把历史 F210 误解释为渠道事实。
     * </p>
     *
     * @param transactionStatus 支付核心持久化交易状态
     * @param processStage 支付核心处理阶段，仅作为诊断上下文
     * @param failureReasonCode 支付核心受控失败原因码
     * @param pendingReasonCode 支付核心受控等待原因码
     * @param legacyMerchantCode 历史内部商户响应码，不作为渠道事实证据
     * @return 对外五位结果码和标准化失败原因
     */
    public static OpenApiTransactionProjection project(String transactionStatus,
                                                       String processStage,
                                                       String failureReasonCode,
                                                       String pendingReasonCode,
                                                       String legacyMerchantCode) {
        if ("SUCCESS".equals(transactionStatus)) {
            return successful(OpenApiResultCode.TRANSACTION_SUCCESS);
        }
        if ("FAILED".equals(transactionStatus)) {
            return projectFailure(failureReasonCode);
        }
        if (CHANNEL_RESULT_UNKNOWN.equals(pendingReasonCode)) {
            return unconfirmedChannelResult();
        }
        if ("PROCESSING".equals(transactionStatus)) {
            return successful(OpenApiResultCode.TRANSACTION_PROCESSING);
        }
        if ("PENDING".equals(transactionStatus)) {
            return successful(OpenApiResultCode.TRANSACTION_PENDING);
        }
        return failure(OpenApiResultCode.SYSTEM_ERROR,
                "PLATFORM_PROCESSING_ERROR",
                OpenApiResultCode.SYSTEM_ERROR.getMessage());
    }

    /**
     * 根据支付核心保存的受控失败原因选择公开码；未知原因只投影为通用交易失败。
     *
     * @param failureReasonCode 支付核心受控失败原因码
     * @return 已确认失败终态对应的公开投影
     */
    private static OpenApiTransactionProjection projectFailure(String failureReasonCode) {
        if (CHANNEL_DECLINED.equals(failureReasonCode)) {
            return failure(OpenApiResultCode.CHANNEL_RETURNED_ERROR,
                    "CHANNEL_DECLINED",
                    merchantMessage(OpenApiResultCode.CHANNEL_RETURNED_ERROR));
        }
        if (RISK_REJECTED.equals(failureReasonCode)) {
            return failure(OpenApiResultCode.PLATFORM_RISK_REJECTED,
                    "PLATFORM_RISK_REJECTED",
                    OpenApiResultCode.PLATFORM_RISK_REJECTED.getMessage());
        }
        if (STATE_TRANSITION_DENIED.equals(failureReasonCode)
                || "F414".equals(failureReasonCode)
                || "F518".equals(failureReasonCode)) {
            return failure(OpenApiResultCode.ORIGINAL_ACTION_NOT_ALLOWED,
                    "ORIGINAL_ACTION_NOT_ALLOWED",
                    OpenApiResultCode.ORIGINAL_ACTION_NOT_ALLOWED.getMessage());
        }
        if (CHANNEL_REQUEST_FAILED.equals(failureReasonCode)
                || CHANNEL_TIMEOUT.equals(failureReasonCode)
                || CHANNEL_RESPONSE_INVALID.equals(failureReasonCode)) {
            return failure(OpenApiResultCode.SYSTEM_ERROR,
                    "PLATFORM_PROCESSING_ERROR",
                    OpenApiResultCode.SYSTEM_ERROR.getMessage());
        }
        if (CHANNEL_REQUEST_NOT_SENT.equals(failureReasonCode)) {
            return failure(OpenApiResultCode.SYSTEM_ERROR,
                    "PLATFORM_PROCESSING_ERROR",
                    OpenApiResultCode.SYSTEM_ERROR.getMessage());
        }
        return failure(OpenApiResultCode.TRANSACTION_FAILED,
                "TRANSACTION_FAILED",
                OpenApiResultCode.TRANSACTION_FAILED.getMessage());
    }

    /**
     * 构造渠道结果未确认投影；该投影不授权调用方将交易状态改为 FAILED 或自动重提交易。
     */
    private static OpenApiTransactionProjection unconfirmedChannelResult() {
        return failure(OpenApiResultCode.CHANNEL_CALL_UNCONFIRMED,
                "CHANNEL_RESULT_UNCONFIRMED",
                OpenApiResultCode.CHANNEL_CALL_UNCONFIRMED.getMessage());
    }

    /**
     * 构造不携带失败原因的正常交易状态投影。
     */
    private static OpenApiTransactionProjection successful(OpenApiResultCode resultCode) {
        return new OpenApiTransactionProjection(resultCode, null, null);
    }

    /**
     * 返回支付核心可持久化的商户安全文案，禁止在投影器中复制目录模板。
     *
     * @param resultCode 已确认的 OpenAPI 交易结果码
     * @return 英文商户文案，不包含渠道原始响应
     */
    private static String merchantMessage(OpenApiResultCode resultCode) {
        return OpenApiResultMessageCatalog.message(
                resultCode, OpenApiMessageAudience.MERCHANT, OpenApiMessageLocale.EN_US);
    }

    /**
     * 构造携带标准化失败原因的交易投影，原因文案必须已经过受众安全约束。
     */
    private static OpenApiTransactionProjection failure(OpenApiResultCode resultCode,
                                                        String failureReasonCode,
                                                        String failureReasonMessage) {
        return new OpenApiTransactionProjection(resultCode, failureReasonCode, failureReasonMessage);
    }
}
