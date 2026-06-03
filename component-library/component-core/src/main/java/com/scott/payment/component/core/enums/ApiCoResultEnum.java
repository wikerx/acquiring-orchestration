package com.scott.payment.component.core.enums;

import com.scott.payment.component.core.result.IResult;
import lombok.Getter;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ApiCoResultEnum
 * @date : 2026-05-28 18:16
 * @email : scott_x@163.com
 * @description : 对外收单 API 统一响应枚举
 * @status : create
 */
@Getter
public enum ApiCoResultEnum implements IResult {

    /**
     * 请求处理成功。
     */
    SUCCESS("T200", "Success"),

    /**
     * 请求已受理，后续结果以查询或回调为准。
     */
    CO_RECEIVED("T201", "Accepted"),

    /**
     * 请求正在处理中，最终结果以查询或回调为准。
     */
    CO_PROCESSING("T202", "Processing"),

    /**
     * 请求结果暂未确认，最终结果以查询或回调为准。
     */
    CO_PENDING("T203", "Pending"),

    /**
     * 请求部分成功或部分受理。
     */
    CO_PARTIALLY_RECEIVED("T206", "Partially accepted"),

    /**
     * 兼容旧调用方的通用失败枚举。
     */
    COMMON_FAILED("F500", "Internal server error"),

    /**
     * 交易处理成功，兼容卡组交易语义。
     */
    CO_SUCCESS("T200", "Success"),

    /**
     * 交易被发卡行、卡组织或上游渠道拒绝。
     */
    CO_BANK_REJECT("F207", "Issuer or acquirer rejected the transaction"),

    /**
     * 交易被支付平台或渠道拒绝。
     */
    CO_REJECTED("F210", "Rejected"),

    /**
     * 请求报文不符合开放接口协议。
     */
    CO_BAD_REQUEST("F400", "Bad request"),

    /**
     * 请求未通过认证。
     */
    CO_UNAUTHORIZED("F401", "Unauthorized"),

    /**
     * 缺少 authorization 请求头。
     */
    CO_UNAUTHORIZED_NULL("F401001", "Authorization header is missing"),

    /**
     * JWT 类型或算法非法。
     */
    CO_UNAUTHORIZED_JWT("F401002", "Authorization JWT is invalid, HS256 is required"),

    /**
     * JWT exp 非法或已过期。
     */
    CO_UNAUTHORIZED_JWT_EXP("F401003", "Authorization JWT exp is invalid or expired"),

    /**
     * JWT iat 非法。
     */
    CO_UNAUTHORIZED_JWT_IAT("F401004", "Authorization JWT iat is invalid"),

    /**
     * JWT iss 非法。
     */
    CO_UNAUTHORIZED_JWT_ISS("F401005", "Authorization JWT iss is invalid"),

    /**
     * JWT aud 非法。
     */
    CO_UNAUTHORIZED_JWT_AUD("F401006", "Authorization JWT aud is invalid"),

    /**
     * JWT 签名验签失败。
     */
    CO_UNAUTHORIZED_JWT_SIGN("F401007", "Authorization JWT signature verification failed"),

    /**
     * 商户未配置 JWT 签名密钥。
     */
    CO_UNAUTHORIZED_JWT_NO_KEY("F401008", "Merchant signing key is not configured"),

    /**
     * 商户号不存在、状态不可用或与请求不匹配。
     */
    CO_UNAUTHORIZED_MER_INVALID("F401009", "Merchant is invalid or unavailable"),

    /**
     * 请求参数值不合法。
     */
    CO_REQUIRED_PARAMETER_INVALID("F402001", "Invalid request parameter"),

    /**
     * 必填请求参数缺失。
     */
    CO_REQUIRED_PARAMETER_MISSING("F402002", "Required request parameter is missing"),

    /**
     * 请求体 data 缺失、格式非法或无法解密。
     */
    CO_REQUIRED_PARAMETER_ILLEGAL("F402003", "Encrypted request data is invalid"),

    /**
     * 请求资源不存在。
     */
    CO_NOT_FOUND("F404", "Not found"),

    /**
     * 请求方法不支持。
     */
    CO_METHOD_NOT_ALLOWED("F405", "Method Not Allowed"),

    /**
     * 商户配置不存在或不可用。
     */
    CO_MERCHANT_CONFIG_NOT_FOUND("F409", "Merchant config not found"),

    /**
     * 卡类型或卡品牌不支持。
     */
    CO_CARD_NOT_SUPPORT("F410", "Card not support"),

    /**
     * 交易币种不支持。
     */
    CO_UNSUPPORTED_CURRENCY("F411", "transaction unsupported currency"),

    /**
     * 交易类型不支持。
     */
    CO_UNSUPPORTED_TRANSACTION_TYPE("F412", "transaction unsupported transactionType"),

    /**
     * 卡品牌不支持。
     */
    CO_UNSUPPORTED_CARD_BRANDS("F413", "Unsupported card brands"),

    /**
     * 服务内部错误。
     */
    CO_INTERNAL_SERVER_ERROR("F500", "Internal Server Error"),

    /**
     * 上游服务不可用或响应异常。
     */
    CO_BAD_GATEWAY("F502", "Bad gateway"),

    /**
     * 服务繁忙，可稍后重试。
     */
    CO_THE_NETWORK_IS_BUSY("F503", "The network is busy, please try again later"),

    /**
     * 商户订单号已存在。
     */
    CO_ORDER_ALREADY_EXIST("F510", "Order already exist"),

    /**
     * 订单不存在。
     */
    CO_ORDER_DOES_NOT_EXIST("F511", "Order does not exist"),

    /**
     * 查询结果不存在。
     */
    CO_QUERY_FAILED("F512", "The search result set is invalid/does not exist"),

    /**
     * 商户交易号重复。
     */
    CO_TRANSACTION_ID_REPEAT("F515", "transactionId repeat"),

    /**
     * 请求报文解析失败。
     */
    CO_REQUEST_PARSE_ERROR("Z605", "Request parse error"),

    /**
     * 响应报文解析失败。
     */
    CO_RESPONSE_PARSE_ERROR("Z606", "Response parse error");

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

    ApiCoResultEnum(String code, String message) {
        this.code = code;
        this.message = message;
    }

    /**
     * 按响应码查找枚举。
     *
     * @param code 响应码
     * @return 响应枚举，不存在时返回 null
     */
    public static ApiCoResultEnum getInstanceByCode(String code) {
        for (ApiCoResultEnum value : ApiCoResultEnum.values()) {
            if (value.getCode().equals(code)) {
                return value;
            }
        }
        return null;
    }
}
