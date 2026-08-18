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
     * 解析目标主机的全部地址并拒绝任一私网或保留网段，防止 DNS 多结果绕过 SSRF 门禁。
     *
     * @param host 回调目标主机名
     */
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

    /**
     * 判断解析结果是否属于本机、私网、文档示例网段或其他不可出站地址。
     *
     * @param address DNS 解析得到的地址
     * @return 不允许作为商户回调目标时返回 true
     */
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

    /**
     * 统一构造不暴露网络细节的参数异常。
     *
     * @param message 安全的错误描述
     * @return 参数无效异常
     */
    private ServiceException invalid(String message) {
        return new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), message);
    }

    @FunctionalInterface
    interface AddressResolver {
        /**
         * 解析主机的全部地址，生产实现使用系统 DNS，测试可注入确定结果。
         *
         * @param host 待解析主机
         * @return 主机当前解析到的全部地址
         * @throws UnknownHostException 主机无法解析
         */
        InetAddress[] resolve(String host) throws UnknownHostException;
    }
}
