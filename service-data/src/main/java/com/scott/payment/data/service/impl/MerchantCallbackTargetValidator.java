package com.scott.payment.data.service.impl;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.data.config.DataMerchantNotificationProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantCallbackTargetValidator
 * @date : 2026-08-04 15:16
 * @email : scott_x@163.com
 * @description : service-data 商户回调出站地址门禁，在建立 HTTP 连接前拒绝明文协议和平台私网目标
 * @status : create
 */
@Component
public class MerchantCallbackTargetValidator {

    /** 商户回调安全策略。 */
    private final DataMerchantNotificationProperties properties;
    /** 主机解析边界，测试可注入确定地址。 */
    private final AddressResolver addressResolver;

    /**
     * 创建生产回调地址校验器。
     *
     * @param properties 商户回调安全策略
     */
    @Autowired
    public MerchantCallbackTargetValidator(DataMerchantNotificationProperties properties) {
        this(properties, InetAddress::getAllByName);
    }

    MerchantCallbackTargetValidator(DataMerchantNotificationProperties properties,
                                    AddressResolver addressResolver) {
        this.properties = properties;
        this.addressResolver = addressResolver;
    }

    /**
     * 校验本次实际出站地址。默认只允许 HTTPS 和公网解析结果。
     *
     * @param targetUrl 通知任务冻结的原始回调地址
     */
    public void validate(String targetUrl) {
        URI uri = parse(targetUrl);
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"https".equals(scheme) && !(properties.isAllowHttp() && "http".equals(scheme))) {
            throw invalid("merchant callback target must use HTTPS");
        }
        if (uri.getUserInfo() != null || uri.getFragment() != null) {
            throw invalid("merchant callback target must not contain user info or fragment");
        }
        int port = uri.getPort();
        if (port == 0 || port > 65_535) {
            throw invalid("merchant callback target port is invalid");
        }
        if (!properties.isAllowPrivateNetwork()) {
            validatePublicHost(uri.getHost());
        }
    }

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

    private void validatePublicHost(String host) {
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if ("localhost".equals(normalizedHost) || normalizedHost.endsWith(".localhost")
                || normalizedHost.endsWith(".local")) {
            throw invalid("merchant callback target resolves to a private or reserved address");
        }
        try {
            InetAddress[] addresses = addressResolver.resolve(host);
            if (addresses == null || addresses.length == 0) {
                throw invalid("merchant callback target host can not be resolved");
            }
            for (InetAddress address : addresses) {
                if (isPrivateOrReserved(address)) {
                    throw invalid("merchant callback target resolves to a private or reserved address");
                }
            }
        } catch (UnknownHostException exception) {
            throw new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(),
                    "merchant callback target host can not be resolved", exception);
        }
    }

    private boolean isPrivateOrReserved(InetAddress address) {
        if (address == null || address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int first = bytes[0] & 0xff;
            int second = bytes[1] & 0xff;
            int third = bytes[2] & 0xff;
            return first == 0
                    || first >= 224
                    || (first == 100 && second >= 64 && second <= 127)
                    || (first == 192 && second == 0 && third == 0)
                    || (first == 192 && second == 0 && third == 2)
                    || (first == 192 && second == 88 && third == 99)
                    || (first == 198 && (second == 18 || second == 19))
                    || (first == 198 && second == 51 && third == 100)
                    || (first == 203 && second == 0 && third == 113);
        }
        return bytes.length == 16
                && (((bytes[0] & 0xfe) == 0xfc)
                || ((bytes[0] & 0xff) == 0x20
                && (bytes[1] & 0xff) == 0x01
                && (bytes[2] & 0xff) == 0x0d
                && (bytes[3] & 0xff) == 0xb8));
    }

    private ServiceException invalid(String message) {
        return new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), message);
    }

    @FunctionalInterface
    interface AddressResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }
}
