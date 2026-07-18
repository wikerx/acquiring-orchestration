package com.scott.payment.openapi.security;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.core.util.net.IpAddressNormalizer;
import com.scott.payment.component.core.util.net.IpAddressNormalizer.NormalizedIp;
import com.scott.payment.component.db.auth.entity.MerchantIpWhitelistDO;
import com.scott.payment.component.db.auth.entity.MerchantOpenApiAccessConfigDO;
import com.scott.payment.component.db.auth.mapper.MerchantIpWhitelistMapper;
import com.scott.payment.component.db.auth.mapper.MerchantOpenApiAccessConfigMapper;
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

    private static final long NOT_DELETED = 0L;
    private static final int ENABLED = 1;

    private final MerchantOpenApiAccessConfigMapper accessConfigMapper;
    private final MerchantIpWhitelistMapper whitelistMapper;

    /**
     * 创建商户 IP 白名单访问控制服务。
     *
     * @param accessConfigMapper 商户访问配置 Mapper
     * @param whitelistMapper    商户 IP 白名单 Mapper
     */
    public MerchantIpWhitelistAccessService(MerchantOpenApiAccessConfigMapper accessConfigMapper,
                                            MerchantIpWhitelistMapper whitelistMapper) {
        this.accessConfigMapper = accessConfigMapper;
        this.whitelistMapper = whitelistMapper;
    }

    /**
     * 按商户配置校验当前请求 IP 是否允许访问 OpenAPI。
     *
     * @param merchantId 商户号，来自已验签 JWT
     * @param request    当前 HTTP 请求
     * @return 规范化后的客户端 IP；未启用白名单时可能为空
     */
    public String checkAccess(String merchantId, HttpServletRequest request) {
        MerchantOpenApiAccessConfigDO config = accessConfigMapper.selectOne(Wrappers.<MerchantOpenApiAccessConfigDO>lambdaQuery()
                .eq(MerchantOpenApiAccessConfigDO::getDeleted, NOT_DELETED)
                .eq(MerchantOpenApiAccessConfigDO::getMerchantId, merchantId)
                .last("LIMIT 1"));
        if (config == null || config.getIpWhitelistEnabled() == null || config.getIpWhitelistEnabled() != ENABLED) {
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
        Long matched = whitelistMapper.selectCount(Wrappers.<MerchantIpWhitelistDO>lambdaQuery()
                .eq(MerchantIpWhitelistDO::getDeleted, NOT_DELETED)
                .eq(MerchantIpWhitelistDO::getStatus, ENABLED)
                .eq(MerchantIpWhitelistDO::getMerchantId, merchantId)
                .eq(MerchantIpWhitelistDO::getIpValue, clientIp.ipValue()));
        if (matched == null || matched <= 0) {
            throw new ApiException(ApiResultEnum.FORBIDDEN, "merchant ip not allowed");
        }
        return clientIp.ipValue();
    }

    private String resolveClientIp(HttpServletRequest request) {
        String value = request.getHeader(HEADER_GATEWAY_CLIENT_IP);
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
