package com.sinopay.payment.component.core.enums;

import com.sinopay.payment.component.core.result.IResult;
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

    SUCCESS("T200", "SUCCESS"),
    COMMON_FAILED("T500", "system error"),

    CO_SUCCESS("T200", "Success"),
    CO_PENDING("T205", "Pending"),
    CO_BANK_REJECT("T207", "Bank reject"),
    CO_RECEIVED("T208", "Received"),
    CO_REJECTED("T210", "Rejected"),

    CO_BAD_REQUEST("F400", "Bad request"),
    CO_UNAUTHORIZED("F401", "Unauthorized"),
    CO_UNAUTHORIZED_NULL("F401", "Unauthorized [The request header authorization does not exist]"),
    CO_UNAUTHORIZED_JWT("F401", "Unauthorized [The encryption method of the authorization JWT in the request header is illegal. Please use HS256(HMACSHA256) encryption.]"),
    CO_UNAUTHORIZED_JWT_EXP("F401", "Unauthorized [The request authorization parameters-exp are invalid or missing.]"),
    CO_UNAUTHORIZED_JWT_IAT("F401", "Unauthorized [The request authorization parameters-iat are invalid or missing.]"),
    CO_UNAUTHORIZED_JWT_ISS("F401", "Unauthorized [The request authorization parameters-iss are invalid or missing.]"),
    CO_UNAUTHORIZED_JWT_AUD("F401", "Unauthorized [The request authorization parameters-aud are invalid or missing.]"),
    CO_UNAUTHORIZED_JWT_SIGN("F401", "Unauthorized [The request authorization Signature verification failed.]"),
    CO_UNAUTHORIZED_JWT_NO_KEY("F401", "Unauthorized [The merchant has not configured the signing key: merchantKey.]"),
    CO_UNAUTHORIZED_MER_INVALID("F401", "Unauthorized [Request authorization parameter-merchantId is invalid or does not exist.]"),

    CO_REQUIRED_PARAMETER_INVALID("F402", "Required parameter invalid"),
    CO_REQUIRED_PARAMETER_MISSING("F403", "Required parameter missing"),
    CO_REQUIRED_PARAMETER_ILLEGAL("F403", "The request parameter[data] format is invalid."),

    CO_MERCHANT_CONFIG_NOT_FOUND("F409", "Merchant config not found"),
    CO_CARD_NOT_SUPPORT("F410", "Card not support"),
    CO_UNSUPPORTED_CURRENCY("F411", "transaction unsupported currency"),
    CO_UNSUPPORTED_TRANSACTION_TYPE("F412", "transaction unsupported transactionType"),
    CO_UNSUPPORTED_CARD_BRANDS("F413", "Unsupported card brands"),

    CO_INTERNAL_SERVER_ERROR("F500", "Internal Server Error"),
    CO_THE_NETWORK_IS_BUSY("F503", "The network is busy, please try again later"),

    CO_ORDER_ALREADY_EXIST("F510", "Order already exist"),
    CO_ORDER_DOES_NOT_EXIST("F511", "Order does not exist"),
    CO_QUERY_FAILED("F512", "The search result set is invalid/does not exist"),
    CO_TRANSACTION_ID_REPEAT("F515", "transactionId repeat"),

    CO_REQUEST_PARSE_ERROR("Z605", "Request parse error"),
    CO_RESPONSE_PARSE_ERROR("Z606", "Response parse error");

    private final String code;
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
