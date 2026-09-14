package com.scott.payment.data.service.impl;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.Locale;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantCallbackTargetValidator
 * @date : 2026-08-04 15:16
 * @email : scott_x@163.com
 * @description : service-data 商户回调地址结构校验器，允许 HTTP/HTTPS 公网、内网和回环目标
 * @status : create
 */
@Component
public class MerchantCallbackTargetValidator {

    /**
     * 校验本次实际出站地址。业务允许 HTTP/HTTPS 以及公网、内网和回环地址。
     *
     * @param targetUrl 通知任务冻结的原始回调地址
     */
    public void validate(String targetUrl) {
        URI uri = parse(targetUrl);
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"https".equals(scheme) && !"http".equals(scheme)) {
            throw invalid("merchant callback target must use HTTP or HTTPS");
        }
        if (uri.getUserInfo() != null || uri.getFragment() != null) {
            throw invalid("merchant callback target must not contain user info or fragment");
        }
        int port = uri.getPort();
        if (port == 0 || port > 65_535) {
            throw invalid("merchant callback target port is invalid");
        }
    }

    /**
     * 解析并校验回调 URL 的基础结构，拒绝相对地址和无主机地址。
     *
     * @param targetUrl 商户回调地址
     * @return 结构完整的绝对 URI
     */
    private URI parse(String targetUrl) {
        if (!StringUtils.hasText(targetUrl)) {
            throw invalid("merchant callback target is empty");
        }
        try {
            URI uri = URI.create(targetUrl.trim());
            if (!uri.isAbsolute() || !StringUtils.hasText(uri.getScheme()) || !StringUtils.hasText(uri.getHost())) {
                throw invalid("merchant callback target is invalid");
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(),
                    "merchant callback target is invalid", exception);
        }
    }

    /**
     * 统一构造不暴露网络细节的参数异常。
     *
     * @param message 安全的错误描述
     * @return 参数无效异常
     */
    private ServiceException invalid(String message) {
        return new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), message);
    }
}
