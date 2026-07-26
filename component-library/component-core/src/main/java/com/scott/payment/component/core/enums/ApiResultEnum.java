package com.scott.payment.component.core.enums;

import com.scott.payment.component.core.result.IResult;
import lombok.Getter;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ApiResultEnum
 * @date : 2026-05-28 18:16
 * @email : scott_x@163.com
 * @description : 支付框架 OpenAPI 统一响应枚举
 * @status : create
 */

@Getter
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ApiResultEnum
 * @date : 2026-05-28 18:16
 * @email : scott_x@163.com
 * @description : ApiResultEnum 枚举类型，用于限定业务状态、配置选项或协议取值范围，位于 公共组件层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
public enum ApiResultEnum implements IResult {

    /**
     * 请求处理成功。
     */
    SUCCESS("T200", "Success"),

    /**
     * 请求已受理，后续结果以查询或回调为准。
     */
    ACCEPTED("T201", "Accepted"),

    /**
     * 请求正在处理中，最终结果以查询或回调为准。
     */
    PROCESSING("T202", "Processing"),

    /**
     * 请求结果暂未确认，最终结果以查询或回调为准。
     */
    PENDING("T203", "Pending"),

    /**
     * 请求部分成功或部分受理。
     */
    PARTIALLY_ACCEPTED("T206", "Partially accepted"),

    /**
     * 通用失败枚举，作为未细分系统异常的兜底返回。
     */
    COMMON_FAILED("F500", "Internal server error"),

    /**
     * 支付、退款或代付交易处理成功。
     */
    PAYMENT_SUCCESS("T200", "Success"),

    /**
     * 交易被发卡行、卡组织或上游渠道拒绝。
     */
    PAYMENT_REJECTED_BY_ISSUER("F207", "Issuer or acquirer rejected the transaction"),

    /**
     * 交易被支付平台或渠道拒绝。
     */
    PAYMENT_REJECTED("F210", "The transaction was declined; please contact your card issuer or try again."),

    /**
     * 请求报文不符合开放接口协议。
     */
    BAD_REQUEST("F400", "Bad request"),

    /**
     * 请求未通过认证。
     */
    UNAUTHORIZED("F401", "Unauthorized"),

    /**
     * 缺少 authorization 请求头。
     */
    AUTHORIZATION_HEADER_MISSING("F401001", "Authorization required"),

    /**
     * JWT 类型或算法非法。
     */
    AUTHORIZATION_JWT_INVALID("F401002", "Authorization JWT is invalid, HS256 is required"),

    /**
     * JWT exp 非法或已过期。
     */
    AUTHORIZATION_JWT_EXPIRED("F401003", "Authorization JWT exp is invalid or expired"),

    /**
     * JWT iat 非法。
     */
    AUTHORIZATION_JWT_IAT_INVALID("F401004", "Authorization JWT iat is invalid"),

    /**
     * JWT iss 非法。
     */
    AUTHORIZATION_JWT_ISS_INVALID("F401005", "Authorization JWT iss is invalid"),

    /**
     * JWT aud 非法。
     */
    AUTHORIZATION_JWT_AUD_INVALID("F401006", "Authorization JWT aud is invalid"),

    /**
     * JWT 签名验签失败。
     */
    AUTHORIZATION_JWT_SIGNATURE_INVALID("F401007", "Authorization JWT signature verification failed"),

    /**
     * 商户未配置 JWT 签名密钥。
     */
    MERCHANT_SIGNING_KEY_NOT_CONFIGURED("F401008", "Merchant signing key is not configured"),

    /**
     * 商户号不存在、状态不可用或与请求不匹配。
     */
    MERCHANT_INVALID("F401009", "Merchant is invalid or unavailable"),

    /**
     * 请求参数值不合法。
     */
    PARAM_INVALID("F402001", "Invalid request parameter"),

    /**
     * 必填请求参数缺失。
     */
    PARAM_MISSING("F402002", "Required request parameter is missing"),

    /**
     * 请求体 data 缺失、格式非法或无法解密。
     */
    ENCRYPTED_DATA_INVALID("F402003", "Encrypted request data is invalid"),

    /**
     * 登录账号无权访问当前资源。
     */
    FORBIDDEN("F403", "Forbidden"),

    /**
     * 请求资源不存在。
     */
    NOT_FOUND("F404", "Not found"),

    /**
     * 请求方法不支持。
     */
    METHOD_NOT_ALLOWED("F405", "Method Not Allowed"),

    /**
     * 请求过于频繁。
     */
    TOO_MANY_REQUESTS("F429", "Too many requests"),

    /**
     * 商户配置不存在或不可用。
     */
    MERCHANT_CONFIG_NOT_FOUND("F409", "Merchant config not found"),

    /**
     * 卡类型或卡品牌不支持。
     */
    CARD_NOT_SUPPORTED("F410", "Card not support"),

    /**
     * 交易币种不支持。
     */
    CURRENCY_NOT_SUPPORTED("F411", "Transaction currency is not supported"),

    /**
     * 交易类型不支持。
     */
    TRANSACTION_TYPE_NOT_SUPPORTED("F412", "Transaction type is not supported"),

    /**
     * 卡品牌不支持。
     */
    CARD_BRAND_NOT_SUPPORTED("F413", "Unsupported card brands"),

    /**
     * 服务内部错误。
     */
    INTERNAL_SERVER_ERROR("F500", "Internal Server Error"),

    /**
     * 上游服务不可用或响应异常。
     */
    BAD_GATEWAY("F502", "Bad gateway"),

    /**
     * 服务繁忙，可稍后重试。
     */
    NETWORK_BUSY("F503", "The network is busy, please try again later"),

    /**
     * 商户订单号已存在。
     */
    ORDER_ALREADY_EXISTS("F510", "Order already exist"),

    /**
     * 订单不存在。
     */
    ORDER_NOT_FOUND("F511", "Order does not exist"),

    /**
     * 查询结果不存在。
     */
    QUERY_RESULT_NOT_FOUND("F512", "The search result set is invalid/does not exist"),

    /**
     * 商户交易号重复。
     */
    TRANSACTION_ID_DUPLICATED("F515", "transactionId repeat"),

    /**
     * 请求报文解析失败。
     */
    REQUEST_PARSE_ERROR("Z605", "Request parse error"),

    /**
     * 响应报文解析失败。
     */
    RESPONSE_PARSE_ERROR("Z606", "Response parse error");

    /**
     * 对外响应码。
     * <p>
     * T 表示成功、受理或处理中；F 表示商户可感知的业务失败；Z 表示报文解析或渠道协议类异常。
     */
    private final String code;

    /**
     * 对外响应描述，面向商户展示，内容应保持稳定、可读、避免泄露内部实现细节。
     */
    private final String message;

    ApiResultEnum(String code, String message) {
        this.code = code;
        this.message = message;
    }

    /**
     * 按响应码查找枚举。
     *
     * @param code 响应码
     * @return 响应枚举，不存在时返回 null
     */
    public static ApiResultEnum getInstanceByCode(String code) {
        for (ApiResultEnum value : ApiResultEnum.values()) {
            if (value.getCode().equals(code)) {
                return value;
            }
        }
        return null;
    }
}
