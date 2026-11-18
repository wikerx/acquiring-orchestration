package com.scott.payment.openapi.service.paymentlink;

import com.scott.payment.openapi.config.PaymentLinkBrowserProperties;
import com.scott.payment.openapi.security.MerchantIpWhitelistAccessService;
import com.scott.payment.component.redis.rate.RedisSlidingWindowRateLimiter;
import com.scott.payment.component.core.exception.RateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/**
 * 以可信来源 IP 与链接摘要独立限流；只复用 IP 解析，不套用商户 IP 白名单，也不把转发头当身份。
 */
@Service
public class PaymentLinkBrowserAccessService {
    private final RedisSlidingWindowRateLimiter limiter;
    private final MerchantIpWhitelistAccessService addresses;
    private final PaymentLinkBrowserProperties properties;
    public PaymentLinkBrowserAccessService(RedisSlidingWindowRateLimiter limiter,MerchantIpWhitelistAccessService addresses,
                                            PaymentLinkBrowserProperties properties) {
        this.limiter=limiter;this.addresses=addresses;this.properties=properties;
    }
    /**
     * 凭据不进入日志或 Redis 键；通用限流器先摘要身份，再执行原子窗口。
     */
    public void require(String action,String token,HttpServletRequest request) {
        boolean start="start".equals(action);
        String ip=addresses.resolveClientIp(request);
        acquire(start?"PAYMENT_LINK_START_IP":"PAYMENT_LINK_QUERY_IP",ip==null || ip.isBlank()?"unknown":ip,
                start?properties.getIpStartLimit():properties.getIpQueryLimit());
        acquire(start?"PAYMENT_LINK_START_LINK":"PAYMENT_LINK_QUERY_LINK",token,
                start?properties.getLinkStartLimit():properties.getLinkQueryLimit());
    }
    private void acquire(String scope,String identity,int limit) {
        var decision=limiter.tryAcquire(scope,identity,limit,properties.getWindow());
        if(!decision.allowed()) throw new RateLimitExceededException(decision.retryAfterSeconds());
    }
}
