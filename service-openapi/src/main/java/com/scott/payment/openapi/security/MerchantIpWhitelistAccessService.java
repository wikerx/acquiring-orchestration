package com.scott.payment.openapi.security;

import com.scott.payment.component.core.security.GatewayIngressSignature;
import com.scott.payment.component.core.util.net.IpAddressNormalizer;
import com.scott.payment.component.core.util.net.IpAddressNormalizer.NormalizedIp;
import com.scott.payment.component.web.gateway.GatewayIngressAuthFilter;
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
    public static final String HEADER_GATEWAY_CLIENT_IP = GatewayIngressSignature.HEADER_CLIENT_IP;

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
     * <p>商户业务 API 采用默认拒绝：有效白名单为空、请求 IP 缺失或未命中时均拒绝。
     * 拒绝消息返回平台识别到的规范化请求 IP，方便商户核对其实际出口地址。</p>
     *
     * @param merchantId 商户号，用于读取对应的 OpenAPI 访问策略
     * @param request 当前 OpenAPI HTTP 请求，只信任网关注入的客户端 IP 请求头
     * @return 校验通过且规范化后的客户端 IP
     */
    public String checkAccess(String merchantId, HttpServletRequest request) {
        String rawClientIp = resolveClientIp(request);
        if (!StringUtils.hasText(rawClientIp)) {
            throw denied(MerchantIpWhitelistDenyReason.CLIENT_IP_MISSING, null);
        }
        NormalizedIp clientIp;
        try {
            clientIp = IpAddressNormalizer.normalizeExact(rawClientIp);
        } catch (IllegalArgumentException ex) {
            throw denied(MerchantIpWhitelistDenyReason.CLIENT_IP_INVALID, null);
        }
        MerchantOpenApiAccessPolicy policy = policyCacheService.findPolicy(merchantId);
        if (policy.getAllowedIps() == null || policy.getAllowedIps().isEmpty()) {
            throw denied(MerchantIpWhitelistDenyReason.WHITELIST_EMPTY, clientIp.ipValue());
        }
        if (!policy.getAllowedIps().contains(clientIp.ipValue())) {
            throw denied(MerchantIpWhitelistDenyReason.IP_NOT_ALLOWED, clientIp.ipValue());
        }
        return clientIp.ipValue();
    }

    private MerchantIpWhitelistAccessDeniedException denied(MerchantIpWhitelistDenyReason reason,
                                                             String requestIp) {
        return new MerchantIpWhitelistAccessDeniedException(reason, requestIp);
    }

    /**
     * 读取网关完成可信代理链解析后写入的客户端 IP。
     *
     * @param request 当前 OpenAPI HTTP 请求
     * @return 网关注入的规范客户端 IP；请求头缺失时返回 null
     */
    public String resolveClientIp(HttpServletRequest request) {
        if (GatewayIngressAuthFilter.isGatewayAuthenticated(request)) {
            String value = request.getHeader(HEADER_GATEWAY_CLIENT_IP);
            return StringUtils.hasText(value) ? value.trim() : null;
        }
        String remoteAddr = request.getRemoteAddr();
        return StringUtils.hasText(remoteAddr) ? remoteAddr.trim() : null;
    }
}
