package com.scott.payment.openapi.security;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import org.springframework.util.StringUtils;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantIpWhitelistAccessDeniedException
 * @date : 2026-10-17 00:00
 * @email : scott_x@163.com
 * @description : 商户 OpenAPI IP 准入拒绝异常，对外固定为 403，内部保留不泄露配置状态的审计原因。
 * @status : create
 */
public class MerchantIpWhitelistAccessDeniedException extends ApiException {

    private static final long serialVersionUID = 1L;

    private final MerchantIpWhitelistDenyReason reason;

    /** 平台从可信网关上下文识别到的规范化请求 IP。 */
    private final String requestIp;

    /**
     * 创建统一对外响应的商户 IP 准入拒绝异常。
     *
     * @param reason    内部安全审计原因
     * @param requestIp 平台识别到的规范化请求 IP；无法识别时为空
     */
    public MerchantIpWhitelistAccessDeniedException(MerchantIpWhitelistDenyReason reason, String requestIp) {
        super(ApiResultEnum.FORBIDDEN,
                "merchant ip not allowed, requestIp: " + displayRequestIp(requestIp));
        this.reason = reason;
        this.requestIp = StringUtils.hasText(requestIp) ? requestIp.trim() : null;
    }

    /**
     * 返回仅供内部审计使用的拒绝原因。
     *
     * @return 内部拒绝原因
     */
    public MerchantIpWhitelistDenyReason getReason() {
        return reason;
    }

    /**
     * 返回平台识别到的规范化请求 IP，供受控安全日志记录。
     *
     * @return 规范化请求 IP；无法识别时为空
     */
    public String getRequestIp() {
        return requestIp;
    }

    private static String displayRequestIp(String requestIp) {
        return StringUtils.hasText(requestIp) ? requestIp.trim() : "unknown";
    }
}
