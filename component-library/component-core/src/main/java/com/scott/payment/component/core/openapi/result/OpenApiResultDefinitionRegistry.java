package com.scott.payment.component.core.openapi.result;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * OpenAPI V1.3 返回码治理注册表。
 *
 * <p>注册表为 48 个已发布编码集中提供适用契约、事实来源、证据、映射规则和生命周期元数据，
 * 防止调用方仅根据编码前缀或文案推导交易结果。</p>
 */
public final class OpenApiResultDefinitionRegistry {


    private static final String VERSION = "V1.3";

    private static final String OWNER = "OpenAPI Governance";

    private static final Set<String> ENVELOPE = Set.of("OPENAPI_V1_3_ENVELOPE");
    private static final Set<String> TRANSACTION = Set.of(
            "OPENAPI_V1_3_TRANSACTION_INFO", "MERCHANT_NOTIFICATION", "HOSTED_CHECKOUT");
    private static final Set<String> ENVELOPE_OR_TRANSACTION = Set.of(
            "OPENAPI_V1_3_ENVELOPE", "OPENAPI_V1_3_TRANSACTION_INFO",
            "MERCHANT_NOTIFICATION", "HOSTED_CHECKOUT");

    private static final Map<OpenApiResultCode, OpenApiResultDefinition> DEFINITIONS = buildDefinitions();

    /**
     * 禁止实例化 OpenAPI 返回码治理注册表。
     */
    private OpenApiResultDefinitionRegistry() {
    }

    /**
     * 获取已发布码的完整治理定义。
     *
     * @param resultCode 已发布返回码
     * @return 不可变治理定义
     */
    public static OpenApiResultDefinition definition(OpenApiResultCode resultCode) {
        OpenApiResultDefinition definition = DEFINITIONS.get(resultCode);
        if (definition == null) {
            throw new IllegalArgumentException("Unregistered OpenAPI result code: " + resultCode);
        }
        return definition;
    }

    /**
     * 获取 V1.3 全量不可变定义索引。
     *
     * @return 以公开码枚举为键的完整定义
     */
    public static Map<OpenApiResultCode, OpenApiResultDefinition> definitions() {
        return DEFINITIONS;
    }

    /**
     * 构建完整且不可变的治理定义索引，并在启动期校验已发布定义数量。
     *
     * @return 48 个公开码对应的不可变定义索引
     */
    private static Map<OpenApiResultCode, OpenApiResultDefinition> buildDefinitions() {
        EnumMap<OpenApiResultCode, OpenApiResultDefinition> definitions =
                new EnumMap<>(OpenApiResultCode.class);
        for (OpenApiResultCode code : OpenApiResultCode.values()) {
            definitions.put(code, buildDefinition(code));
        }
        if (definitions.size() != 48) {
            throw new IllegalStateException("OpenAPI V1.3 must publish exactly 48 result definitions");
        }
        return Collections.unmodifiableMap(definitions);
    }

    /**
     * 汇总单个公开码在各治理维度上的定义，确保所有元数据来自同一套分类规则。
     *
     * @param code 已发布公开码
     * @return 完整治理定义
     */
    private static OpenApiResultDefinition buildDefinition(OpenApiResultCode code) {
        OpenApiResultOrigin origin = origin(code);
        return new OpenApiResultDefinition(
                code,
                code.getMessage(),
                exclusions(code),
                applicableContracts(code),
                phase(code),
                origin,
                messages(code),
                allowedParameters(code),
                evidence(code),
                code.handlingPolicy(),
                dataContract(code),
                mappingRules(code),
                OWNER,
                VERSION,
                lifecycle(code));
    }

    /**
     * 区分公开字典登记和实际业务启用。保留码及尚无生产触发路径的扩展码只标记为 REGISTERED，
     * 后续必须在真实接口声明场景、实现投影并补齐测试后才能调整为 ACTIVE。
     */
    private static OpenApiResultLifecycle lifecycle(OpenApiResultCode code) {
        return switch (code) {
            case REQUEST_ACCEPTED, REQUEST_PARTIALLY_ACCEPTED,
                    PRODUCT_NOT_ENABLED, CURRENCY_NOT_ENABLED, PAYMENT_METHOD_NOT_ENABLED,
                    AVAILABLE_BALANCE_INSUFFICIENT, QUOTE_EXPIRED,
                    SINGLE_AMOUNT_LIMIT_EXCEEDED, CUMULATIVE_LIMIT_EXCEEDED ->
                    OpenApiResultLifecycle.REGISTERED;
            default -> OpenApiResultLifecycle.ACTIVE;
        };
    }

    /**
     * 适用位置由是否已经形成业务响应决定，不能只按返回码首字母静态推断。
     * A 类只描述外层请求结果；交易基础状态只属于业务对象；C、F 及具体 T 原因码
     * 可以发生在前置拒绝或已形成业务对象之后，因此同时登记外层和内层契约。
     */
    private static Set<String> applicableContracts(OpenApiResultCode code) {
        if (code.category() == OpenApiResultCategory.A) {
            return ENVELOPE;
        }
        if (code == OpenApiResultCode.TRANSACTION_SUCCESS
                || code == OpenApiResultCode.TRANSACTION_PROCESSING
                || code == OpenApiResultCode.TRANSACTION_PENDING
                || code == OpenApiResultCode.TRANSACTION_FAILED) {
            return TRANSACTION;
        }
        return ENVELOPE_OR_TRANSACTION;
    }

    /**
     * 固化三类受众、两种语言的文案快照，供治理检查和文档生成使用。
     *
     * @param code 已发布公开码
     * @return 受众与语言组合到安全文案的不可变映射
     */
    private static Map<String, String> messages(OpenApiResultCode code) {
        return Map.of(
                "internal.zh-CN", OpenApiResultMessageCatalog.message(
                        code, OpenApiMessageAudience.INTERNAL, OpenApiMessageLocale.ZH_CN),
                "internal.en-US", OpenApiResultMessageCatalog.message(
                        code, OpenApiMessageAudience.INTERNAL, OpenApiMessageLocale.EN_US),
                "merchant.zh-CN", OpenApiResultMessageCatalog.message(
                        code, OpenApiMessageAudience.MERCHANT, OpenApiMessageLocale.ZH_CN),
                "merchant.en-US", OpenApiResultMessageCatalog.message(
                        code, OpenApiMessageAudience.MERCHANT, OpenApiMessageLocale.EN_US),
                "payer.zh-CN", OpenApiResultMessageCatalog.message(
                        code, OpenApiMessageAudience.PAYER, OpenApiMessageLocale.ZH_CN),
                "payer.en-US", OpenApiResultMessageCatalog.message(
                        code, OpenApiMessageAudience.PAYER, OpenApiMessageLocale.EN_US));
    }

    /**
     * 根据已发布分类规则确定结果产生阶段，不使用运行时异常文本推断阶段。
     *
     * @param code 已发布公开码
     * @return 结果治理阶段
     */
    private static OpenApiResultPhase phase(OpenApiResultCode code) {
        return switch (code.category()) {
            case A -> code.group() == 2 ? OpenApiResultPhase.SECURITY
                    : code.group() == 3 ? OpenApiResultPhase.AUTHORIZATION : OpenApiResultPhase.REQUEST;
            case C -> OpenApiResultPhase.CONFIGURATION;
            case T -> code.group() == 3 ? OpenApiResultPhase.RISK : OpenApiResultPhase.BUSINESS;
            case F -> code == OpenApiResultCode.SYSTEM_ERROR
                    ? OpenApiResultPhase.PLATFORM : OpenApiResultPhase.CHANNEL;
        };
    }

    /**
     * 确定公开结果所依赖的事实来源，保持平台故障、渠道响应和渠道传输异常相互独立。
     *
     * @param code 已发布公开码
     * @return 可审计的事实来源
     */
    private static OpenApiResultOrigin origin(OpenApiResultCode code) {
        return switch (code.category()) {
            case A -> OpenApiResultOrigin.CLIENT_REQUEST;
            case C -> OpenApiResultOrigin.MERCHANT_CONFIGURATION;
            case T -> OpenApiResultOrigin.PLATFORM_BUSINESS;
            case F -> code == OpenApiResultCode.CHANNEL_RETURNED_ERROR
                    ? OpenApiResultOrigin.CHANNEL_RESPONSE
                    : code == OpenApiResultCode.CHANNEL_CALL_UNCONFIRMED
                    ? OpenApiResultOrigin.CHANNEL_TRANSPORT
                    : OpenApiResultOrigin.PLATFORM_SYSTEM;
        };
    }

    /**
     * 绑定公开码必须具备的最小事实证据，避免将超时或未知结果误判为失败终态。
     *
     * @param code 已发布公开码
     * @return 结果投影所需的证据类型
     */
    private static OpenApiResultEvidence evidence(OpenApiResultCode code) {
        return switch (code) {
            case REQUEST_SUCCESS -> OpenApiResultEvidence.REQUEST_COMPLETED;
            case REQUEST_ACCEPTED -> OpenApiResultEvidence.REQUEST_ACCEPTED;
            case REQUEST_PARTIALLY_ACCEPTED -> OpenApiResultEvidence.PARTIAL_RESULT;
            case AUTHORIZATION_MISSING, AUTHENTICATION_FAILED, AUTHORIZATION_TIME_INVALID,
                    MERCHANT_INVALID, MERCHANT_ACCESS_BLOCKED, SECURITY_REPLAY_REJECTED ->
                    OpenApiResultEvidence.AUTHENTICATION_REJECTION;
            case FORBIDDEN, API_NOT_FOUND, SOURCE_NOT_ALLOWED, TOO_MANY_REQUESTS ->
                    OpenApiResultEvidence.AUTHORIZATION_REJECTION;
            case MERCHANT_CONFIG_UNAVAILABLE, PRODUCT_NOT_ENABLED, CURRENCY_NOT_ENABLED,
                    PAYMENT_METHOD_NOT_ENABLED, MERCHANT_SIGNING_MATERIAL_MISSING,
                    MERCHANT_RESPONSE_KEY_MISSING -> OpenApiResultEvidence.CONFIGURATION_REJECTION;
            case TRANSACTION_SUCCESS -> OpenApiResultEvidence.CONFIRMED_TRANSACTION_SUCCESS;
            case TRANSACTION_PROCESSING, TRANSACTION_PENDING -> OpenApiResultEvidence.NON_TERMINAL_TRANSACTION;
            case CHANNEL_RETURNED_ERROR -> OpenApiResultEvidence.CONFIRMED_CHANNEL_RESPONSE;
            case SYSTEM_ERROR -> OpenApiResultEvidence.PLATFORM_FAILURE;
            case CHANNEL_CALL_UNCONFIRMED -> OpenApiResultEvidence.UNCONFIRMED_CHANNEL_OUTCOME;
            case TRANSACTION_FAILED, ORIGINAL_ACTION_NOT_ALLOWED, REFUND_AMOUNT_EXCEEDED,
                    REFUND_APPROVAL_CONFLICT, AVAILABLE_BALANCE_INSUFFICIENT, QUOTE_EXPIRED,
                    BUSINESS_RESOURCE_NOT_FOUND, BUSINESS_IDENTIFIER_CONFLICT, IDEMPOTENCY_CONFLICT,
                    PLATFORM_RISK_REJECTED, SINGLE_AMOUNT_LIMIT_EXCEEDED, CUMULATIVE_LIMIT_EXCEEDED,
                    CARD_NOT_SUPPORTED, CURRENCY_NOT_SUPPORTED, TRANSACTION_TYPE_NOT_SUPPORTED ->
                    OpenApiResultEvidence.CONFIRMED_TRANSACTION_FAILURE;
            default -> OpenApiResultEvidence.VALIDATION_REJECTION;
        };
    }

    /**
     * 声明文案模板允许使用的受控参数及可见受众；未登记参数一律不得进入模板。
     *
     * @param code 已发布公开码
     * @return 参数名、可见受众组成的白名单
     */
    private static Set<String> allowedParameters(OpenApiResultCode code) {
        return switch (code) {
            case PARAM_INVALID -> Set.of("field:INTERNAL|MERCHANT", "internalReason:INTERNAL");
            case PARAM_MISSING -> Set.of("field:INTERNAL|MERCHANT");
            case ENCRYPTED_DATA_INVALID, BAD_REQUEST, AUTHENTICATION_FAILED,
                    AUTHORIZATION_TIME_INVALID, MERCHANT_ACCESS_BLOCKED, TOO_MANY_REQUESTS,
                    MERCHANT_CONFIG_UNAVAILABLE, SYSTEM_ERROR -> Set.of("internalReason:INTERNAL");
            case CHANNEL_RETURNED_ERROR -> Set.of(
                    "channelName:INTERNAL", "channelResponseCode:INTERNAL",
                    "channelResponseMessage:INTERNAL", "channelReference:INTERNAL");
            default -> Set.of();
        };
    }

    /**
     * 描述与当前编码相邻但禁止混用的场景，约束调用方不要跨职责域复用返回码。
     *
     * @param code 已发布公开码
     * @return 不适用场景说明
     */
    private static String exclusions(OpenApiResultCode code) {
        return switch (code.category()) {
            case A -> "不得代替交易状态、渠道结果或平台资金终态。";
            case C -> "仅用于商户应完成的配置，不包含平台读取、存储或程序故障。";
            case T -> "仅依据平台业务事实，不得用未知渠道结果或异常字符串猜测终态。";
            case F -> "不得仅凭 F 前缀推导 FAILED、自动重付、入账或履约。";
        };
    }

    /**
     * 描述公开码对原业务 data 的兼容约束，避免错误投影改变既有响应对象结构。
     *
     * @param code 已发布公开码
     * @return data 兼容性约束
     */
    private static String dataContract(OpenApiResultCode code) {
        if (code == OpenApiResultCode.REQUEST_SUCCESS) {
            return "外层 data 保留原接口业务对象；交易成功与否由对象内专属字段表达。";
        }
        if (code.category() == OpenApiResultCategory.T || code.category() == OpenApiResultCategory.F) {
            return "已形成交易对象时保留真实 transactionStatus 和既有对象结构，不新增通用状态字段。";
        }
        return "未形成约定业务响应时按原接口错误契约返回 data，通常为空。";
    }

    /**
     * 登记内部旧码和结构化证据到公开码的允许映射来源，仅作为治理规则而非字符串猜测逻辑。
     *
     * @param code 已发布公开码
     * @return 允许映射到当前公开码的规则标识
     */
    private static Set<String> mappingRules(OpenApiResultCode code) {
        return switch (code) {
            case PARAM_INVALID -> Set.of("F402001", "validation-constraint");
            case PARAM_MISSING -> Set.of("F402002", "missing-required-field");
            case ENCRYPTED_DATA_INVALID -> Set.of("F402003", "payload-or-encryption-validation");
            case CALLBACK_URL_NOT_ALLOWED -> Set.of("F402004", "callback-url-policy-rejection");
            case AUTHORIZATION_MISSING -> Set.of("F401001");
            case AUTHENTICATION_FAILED -> Set.of("F401", "F401002", "F401005", "F401006", "F401007");
            case AUTHORIZATION_TIME_INVALID -> Set.of("F401003", "F401004");
            case MERCHANT_INVALID -> Set.of("F401009");
            case MERCHANT_ACCESS_BLOCKED -> Set.of("F401010");
            case MERCHANT_SIGNING_MATERIAL_MISSING -> Set.of("F401008");
            case FORBIDDEN -> Set.of("F403");
            case API_NOT_FOUND -> Set.of("F404");
            case SOURCE_NOT_ALLOWED -> Set.of("merchant-ip-policy-rejection");
            case TOO_MANY_REQUESTS -> Set.of("F429");
            case MERCHANT_CONFIG_UNAVAILABLE -> Set.of("F409", "F409001");
            case ORIGINAL_ACTION_NOT_ALLOWED -> Set.of("F414", "F510002", "F518", "STATE_TRANSITION_DENIED");
            case REFUND_AMOUNT_EXCEEDED -> Set.of("F516");
            case REFUND_APPROVAL_CONFLICT -> Set.of("F517");
            case BUSINESS_RESOURCE_NOT_FOUND -> Set.of("F511", "F512");
            case BUSINESS_IDENTIFIER_CONFLICT -> Set.of("F510", "F515");
            case IDEMPOTENCY_CONFLICT -> Set.of("F510001");
            case PLATFORM_RISK_REJECTED -> Set.of("RISK_REJECTED");
            case CARD_NOT_SUPPORTED -> Set.of("F410", "F413");
            case CURRENCY_NOT_SUPPORTED -> Set.of("F411");
            case TRANSACTION_TYPE_NOT_SUPPORTED -> Set.of("F412");
            case CHANNEL_RETURNED_ERROR -> Set.of("F207", "CHANNEL_DECLINED", "confirmed-channel-error");
            case SYSTEM_ERROR -> Set.of("F500", "F500001", "F513", "F519", "F520", "platform-failure");
            case CHANNEL_CALL_UNCONFIRMED -> Set.of("outcomeUncertain=true", "CHANNEL_RESULT_UNKNOWN");
            default -> Set.of("direct-V1.3-definition");
        };
    }
}
