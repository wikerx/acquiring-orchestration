package com.scott.payment.component.core.openapi.result;

import com.scott.payment.component.core.result.IResult;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 商户 OpenAPI V1.3 五位返回码字典。
 *
 * <p>该字典只负责对外协议投影，不替代内部服务结果码、支付核心交易状态或渠道原始状态。
 * 已发布编码和语义属于兼容性契约，变更前必须完成协议评审。</p>
 */
public enum OpenApiResultCode implements IResult {

    /**
     * REQUEST SUCCESS 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    REQUEST_SUCCESS("A0000", "Request processed successfully.", OpenApiHandlingPolicy.NONE),
    /**
     * REQUEST ACCEPTED 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    REQUEST_ACCEPTED("A0001", "Request accepted. Check the result as instructed.", OpenApiHandlingPolicy.QUERY),
    /**
     * REQUEST PARTIALLY ACCEPTED 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    REQUEST_PARTIALLY_ACCEPTED("A0002", "Some items were processed or accepted. Check the individual results.", OpenApiHandlingPolicy.ITEMS),

    /**
     * PARAM INVALID 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    PARAM_INVALID("A1001", "Invalid request parameter. Check the API requirements.", OpenApiHandlingPolicy.FIX),
    /**
     * PARAM MISSING 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    PARAM_MISSING("A1002", "Required request parameter is missing.", OpenApiHandlingPolicy.FIX),
    /**
     * ENCRYPTED DATA INVALID 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    ENCRYPTED_DATA_INVALID("A1003", "Request data validation failed. Check the payload and encryption requirements.", OpenApiHandlingPolicy.FIX_SECURITY),
    /**
     * CALLBACK URL NOT ALLOWED 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    CALLBACK_URL_NOT_ALLOWED("A1004", "The callback URL does not meet the API requirements.", OpenApiHandlingPolicy.FIX),
    /**
     * BAD REQUEST 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    BAD_REQUEST("A1005", "Invalid request format. Check the API requirements.", OpenApiHandlingPolicy.FIX),
    /**
     * METHOD NOT ALLOWED 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    METHOD_NOT_ALLOWED("A1006", "This request method is not supported. Check the API documentation.", OpenApiHandlingPolicy.FIX),
    /**
     * CONTENT TYPE NOT SUPPORTED 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    CONTENT_TYPE_NOT_SUPPORTED("A1007", "This content type is not supported. Check the Content-Type header.", OpenApiHandlingPolicy.FIX),
    /**
     * REQUEST TOO LARGE 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    REQUEST_TOO_LARGE("A1008", "The request content is too large. Reduce its size and submit again.", OpenApiHandlingPolicy.FIX),

    /**
     * AUTHORIZATION MISSING 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    AUTHORIZATION_MISSING("A2001", "Authentication information is required. Check the request headers.", OpenApiHandlingPolicy.FIX_SECURITY),
    /**
     * AUTHENTICATION FAILED 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    AUTHENTICATION_FAILED("A2002", "Authentication failed. Check the credentials and API security requirements.", OpenApiHandlingPolicy.FIX_SECURITY),
    /**
     * AUTHORIZATION TIME INVALID 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    AUTHORIZATION_TIME_INVALID("A2003", "Authentication has expired or its time settings are invalid. Check the clock and authenticate again.", OpenApiHandlingPolicy.FIX_SECURITY),
    /**
     * MERCHANT INVALID 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    MERCHANT_INVALID("A2004", "The merchant identity is invalid or unavailable. Check the integration settings.", OpenApiHandlingPolicy.CONTACT),
    /**
     * MERCHANT ACCESS BLOCKED 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    MERCHANT_ACCESS_BLOCKED("A2005", "This API is unavailable for the merchant account. Contact support.", OpenApiHandlingPolicy.CONTACT),
    /**
     * SECURITY REPLAY REJECTED 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    SECURITY_REPLAY_REJECTED("A2006", "Request security validation failed. Check the original request and security requirements.", OpenApiHandlingPolicy.QUERY),

    /**
     * FORBIDDEN 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    FORBIDDEN("A3001", "The account does not have permission to perform this operation.", OpenApiHandlingPolicy.CONTACT),
    /**
     * API NOT FOUND 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    API_NOT_FOUND("A3002", "The API endpoint was not found. Check the URL and API version.", OpenApiHandlingPolicy.FIX),
    /**
     * SOURCE NOT ALLOWED 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    SOURCE_NOT_ALLOWED("A3003", "This request source is not allowed. Check the access settings.", OpenApiHandlingPolicy.CONTACT),
    /**
     * TOO MANY REQUESTS 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    TOO_MANY_REQUESTS("A4001", "Too many requests. Wait as instructed before retrying.", OpenApiHandlingPolicy.WAIT),

    /**
     * MERCHANT CONFIG UNAVAILABLE 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    MERCHANT_CONFIG_UNAVAILABLE("C1001", "The merchant business configuration is unavailable. Contact support.", OpenApiHandlingPolicy.CONTACT),
    /**
     * PRODUCT NOT ENABLED 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    PRODUCT_NOT_ENABLED("C1002", "This product is not enabled for the merchant account. Contact support.", OpenApiHandlingPolicy.CONTACT),
    /**
     * CURRENCY NOT ENABLED 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    CURRENCY_NOT_ENABLED("C1003", "This currency is not enabled for the merchant. Select another currency or contact support.", OpenApiHandlingPolicy.FIX_BUSINESS),
    /**
     * PAYMENT METHOD NOT ENABLED 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    PAYMENT_METHOD_NOT_ENABLED("C1004", "This payment method is not enabled for the merchant. Select another method or contact support.", OpenApiHandlingPolicy.FIX_BUSINESS),
    /**
     * MERCHANT SIGNING MATERIAL MISSING 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    MERCHANT_SIGNING_MATERIAL_MISSING("C2001", "The merchant security configuration is incomplete. Contact support.", OpenApiHandlingPolicy.CONTACT),
    /**
     * MERCHANT RESPONSE KEY MISSING 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    MERCHANT_RESPONSE_KEY_MISSING("C2002", "The merchant response security configuration is incomplete. Contact support.", OpenApiHandlingPolicy.CONTACT),

    /**
     * TRANSACTION SUCCESS 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    TRANSACTION_SUCCESS("T0000", "Success", OpenApiHandlingPolicy.NONE),
    /**
     * TRANSACTION PROCESSING 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    TRANSACTION_PROCESSING("T0001", "Processing", OpenApiHandlingPolicy.QUERY),
    /**
     * TRANSACTION PENDING 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    TRANSACTION_PENDING("T0002", "Pending", OpenApiHandlingPolicy.CONTEXT),
    /**
     * TRANSACTION FAILED 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    TRANSACTION_FAILED("T0003", "The transaction did not complete. Check the details or contact support.", OpenApiHandlingPolicy.CONTACT),
    /**
     * ORIGINAL ACTION NOT ALLOWED 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    ORIGINAL_ACTION_NOT_ALLOWED("T1001", "The original transaction does not allow this operation. Check its latest status.", OpenApiHandlingPolicy.QUERY),
    /**
     * REFUND AMOUNT EXCEEDED 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    REFUND_AMOUNT_EXCEEDED("T1002", "The refund amount exceeds the remaining refundable amount. Check the amount.", OpenApiHandlingPolicy.FIX_BUSINESS),
    /**
     * REFUND APPROVAL CONFLICT 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    REFUND_APPROVAL_CONFLICT("T1003", "The refund approval state has changed. Refresh and check the latest result.", OpenApiHandlingPolicy.QUERY),
    /**
     * AVAILABLE BALANCE INSUFFICIENT 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    AVAILABLE_BALANCE_INSUFFICIENT("T1004", "The available balance is insufficient. Add funds before submitting again.", OpenApiHandlingPolicy.FIX_BUSINESS),
    /**
     * QUOTE EXPIRED 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    QUOTE_EXPIRED("T1005", "The quote is no longer valid. Obtain and confirm a new quote.", OpenApiHandlingPolicy.FIX_BUSINESS),
    /**
     * BUSINESS RESOURCE NOT FOUND 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    BUSINESS_RESOURCE_NOT_FOUND("T2001", "The business record was not found. Check the reference identifier.", OpenApiHandlingPolicy.QUERY),
    /**
     * BUSINESS IDENTIFIER CONFLICT 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    BUSINESS_IDENTIFIER_CONFLICT("T2002", "The business identifier is already in use. Check the original request and order.", OpenApiHandlingPolicy.QUERY),
    /**
     * IDEMPOTENCY CONFLICT 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    IDEMPOTENCY_CONFLICT("T2003", "The idempotency identifier is associated with a different request. Check the original request.", OpenApiHandlingPolicy.QUERY),
    /**
     * PLATFORM RISK REJECTED 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    PLATFORM_RISK_REJECTED("T3001", "This operation did not pass security checks. Contact support.", OpenApiHandlingPolicy.CONTACT),
    /**
     * SINGLE AMOUNT LIMIT EXCEEDED 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    SINGLE_AMOUNT_LIMIT_EXCEEDED("T4001", "The amount exceeds the applicable transaction limit. Check the amount.", OpenApiHandlingPolicy.FIX_BUSINESS),
    /**
     * CUMULATIVE LIMIT EXCEEDED 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    CUMULATIVE_LIMIT_EXCEEDED("T4002", "The applicable cumulative limit has been reached. Follow the limit requirements.", OpenApiHandlingPolicy.CONTACT),
    /**
     * CARD NOT SUPPORTED 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    CARD_NOT_SUPPORTED("T5001", "This card type or brand is not supported. Select another available method.", OpenApiHandlingPolicy.FIX_BUSINESS),
    /**
     * CURRENCY NOT SUPPORTED 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    CURRENCY_NOT_SUPPORTED("T5002", "This currency is not supported for the operation. Select another available currency.", OpenApiHandlingPolicy.FIX_BUSINESS),
    /**
     * TRANSACTION TYPE NOT SUPPORTED 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    TRANSACTION_TYPE_NOT_SUPPORTED("T5003", "This transaction type is not supported for the operation. Check the request.", OpenApiHandlingPolicy.FIX_BUSINESS),

    /**
     * CHANNEL RETURNED ERROR 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    CHANNEL_RETURNED_ERROR("F1000", "The channel returned an unsuccessful result. Check the original order or contact support.", OpenApiHandlingPolicy.CHANNEL_CONTEXT),
    /**
     * SYSTEM ERROR 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    SYSTEM_ERROR("F2000", "Processing could not be completed. Check the original order or contact support.", OpenApiHandlingPolicy.QUERY),
    /**
     * CHANNEL CALL UNCONFIRMED 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    CHANNEL_CALL_UNCONFIRMED("F3000", "The channel result is not yet confirmed. Check the original order and avoid submitting it again.", OpenApiHandlingPolicy.QUERY);

    /**
     * 已发布编码到规范定义的不可变索引，用于严格反查，未登记编码不会自动成为公开码。
     */
    private static final Map<String, OpenApiResultCode> CODE_INDEX = buildCodeIndex();

    /**
     * 固定五位公开编码，必须作为字符串传输以保留前导零。
     */
    private final String code;

    /**
     * 面向商户服务端的安全英文默认文案，不包含内部异常或渠道原文。
     */
    private final String message;

    /**
     * 处理策略仅提供受控建议，不授权客户端自动重发资金动作。
     */
    private final OpenApiHandlingPolicy handlingPolicy;

    OpenApiResultCode(String code, String message, OpenApiHandlingPolicy handlingPolicy) {
        this.code = code;
        this.message = message;
        this.handlingPolicy = handlingPolicy;
    }

    /**
     * 获取五位 OpenAPI 返回码。
     *
     * @return 已登记的固定五位编码
     */
    @Override
    public String getCode() {
        return code;
    }

    /**
     * 获取商户英文安全回退文案。
     *
     * @return 不含内部诊断信息的稳定文案
     */
    @Override
    public String getMessage() {
        return message;
    }

    /**
     * 获取稳定规范键，文案调整不会改变该标识。
     *
     * @return 与枚举常量名一致的规范键
     */
    public String canonicalKey() {
        return name();
    }

    /**
     * 获取返回码职责类别。
     *
     * @return A、C、T 或 F 类别
     */
    public OpenApiResultCategory category() {
        return OpenApiResultCategory.valueOf(code.substring(0, 1));
    }

    /**
     * 获取返回码分组数字。
     *
     * @return 0 到 9 的分组号
     */
    public int group() {
        return Character.digit(code.charAt(1), 10);
    }

    /**
     * 获取规范处理策略。
     *
     * @return 不直接触发资金重试的受控处理策略
     */
    public OpenApiHandlingPolicy handlingPolicy() {
        return handlingPolicy;
    }

    /**
     * 严格反查已发布的 OpenAPI 返回码。
     *
     * @param code 候选五位编码
     * @return 已登记定义；未知或空编码返回 empty
     */
    public static Optional<OpenApiResultCode> fromCode(String code) {
        return Optional.ofNullable(CODE_INDEX.get(code));
    }

    /**
     * 构建公开编码的不可变反查索引，并在启动期阻止重复编码进入协议字典。
     *
     * @return 编码到公开结果定义的不可变索引
     */
    private static Map<String, OpenApiResultCode> buildCodeIndex() {
        Map<String, OpenApiResultCode> index = new LinkedHashMap<>();
        for (OpenApiResultCode value : values()) {
            OpenApiResultCode previous = index.put(value.code, value);
            if (previous != null) {
                throw new IllegalStateException("Duplicate OpenAPI result code: " + value.code);
            }
        }
        return Collections.unmodifiableMap(index);
    }
}
