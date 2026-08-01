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

    /**
     * NOT DELETED，用于保存 Merchant IP Whitelist Access Service 中与 notdeleted 相关的业务属性。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
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
     * 按商户配置校验当前请求 IP 是否允许访问 OpenAPI。
     *
     * @param merchantId 商户号，来自已验签 JWT
     * @param request    当前 HTTP 请求
     * @return 规范化后的客户端 IP；未启用白名单时可能为空
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
     * 解析resolveclientip，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 商户开放接口服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param request request，来源于接口入参、内部服务调用或任务调度，字段含义按所属模型定义
     * @return 构造、转换或解析后的业务值
     */
    public String resolveClientIp(HttpServletRequest request) {
        String value = request.getHeader(HEADER_GATEWAY_CLIENT_IP);
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
