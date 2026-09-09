package com.scott.payment.openapi.security;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.core.util.net.IpAddressNormalizer;
import com.scott.payment.component.core.util.net.IpAddressNormalizer.NormalizedIp;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantIpWhitelistAccessService
 * @date : 2026-07-18 00:00
 * @email : scott_x@163.com
 * @description : 商户 OpenAPI IP 白名单访问控制服务，位于 service-openapi 安全层，在 JWT 验签后、防重放登记前执行精确 IP 校验。
 * @status : create
 */
@Service
public class MerchantIpWhitelistAccessService {

    /**
     * Gateway 写入的可信客户端 IP 请求头，下游只使用该头作为白名单匹配来源。
     */
    public static final String HEADER_GATEWAY_CLIENT_IP = "X-Gateway-Client-Ip";

    private final MerchantOpenApiAccessPolicyCacheService policyCacheService;

    /**
     * 创建商户 IP 白名单访问控制服务。
     *
     * @param policyCacheService 商户 IP 访问策略缓存服务
     */
    public MerchantIpWhitelistAccessService(MerchantOpenApiAccessPolicyCacheService policyCacheService) {
        this.policyCacheService = policyCacheService;
    }

    /**
     * 校验网关注入的可信客户端 IP 是否满足商户当前白名单策略。
     *
     * <p>未启用白名单时仅返回规范化前的可信请求头；启用后，请求头缺失、
     * IP 格式非法或未命中精确地址集合均统一拒绝，避免泄露具体失败原因。</p>
     *
     * @param merchantId 商户号，用于读取对应的 OpenAPI 访问策略
     * @param request 当前 OpenAPI HTTP 请求，只信任网关注入的客户端 IP 请求头
     * @return 校验通过的客户端 IP；未启用白名单且请求头缺失时返回 null
     */
    public String checkAccess(String merchantId, HttpServletRequest request) {
        MerchantOpenApiAccessPolicy policy = policyCacheService.findPolicy(merchantId);
        if (!policy.isWhitelistEnabled()) {
            return resolveClientIp(request);
        }
        String rawClientIp = resolveClientIp(request);
        if (!StringUtils.hasText(rawClientIp)) {
            throw new ApiException(ApiResultEnum.FORBIDDEN, "merchant ip not allowed");
        }
        NormalizedIp clientIp;
        try {
            clientIp = IpAddressNormalizer.normalizeExact(rawClientIp);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ApiResultEnum.FORBIDDEN, "merchant ip not allowed");
        }
        if (!policy.getAllowedIps().contains(clientIp.ipValue())) {
            throw new ApiException(ApiResultEnum.FORBIDDEN, "merchant ip not allowed");
        }
        return clientIp.ipValue();
    }

    /**
     * 读取网关完成可信代理链解析后写入的客户端 IP。
     *
     * @param request 当前 OpenAPI HTTP 请求
     * @return 网关注入的规范客户端 IP；请求头缺失时返回 null
     */
    public String resolveClientIp(HttpServletRequest request) {
        String value = request.getHeader(HEADER_GATEWAY_CLIENT_IP);
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
