package com.scott.payment.openapi.service.paymentlink;
import com.scott.payment.openapi.config.PaymentLinkBrowserProperties;
import com.scott.payment.openapi.security.MerchantIpWhitelistAccessService;
import com.scott.payment.component.redis.rate.RedisSlidingWindowRateLimiter;
import com.scott.payment.component.redis.rate.SlidingWindowRateLimitResult;
import com.scott.payment.component.core.exception.RateLimitExceededException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;

/** 验证匿名访问按可信 IP 与链接分别限流，不依赖商户登录或原始转发头。 */
class PaymentLinkBrowserAccessTest {
 private final RedisSlidingWindowRateLimiter limiter=mock(RedisSlidingWindowRateLimiter.class);
 private final MerchantIpWhitelistAccessService addresses=mock(MerchantIpWhitelistAccessService.class);
 private final PaymentLinkBrowserProperties properties=new PaymentLinkBrowserProperties();
 private final PaymentLinkBrowserAccessService service=new PaymentLinkBrowserAccessService(limiter,addresses,properties);
 @Test void readsHaveIndependentIpAndLinkBudgets() {var r=new MockHttpServletRequest();r.addHeader("X-Forwarded-For","attacker");when(addresses.resolveClientIp(r)).thenReturn("127.0.0.1");when(limiter.tryAcquire(anyString(),anyString(),anyInt(),any())).thenReturn(SlidingWindowRateLimitResult.permitted());service.require("resolve","opaque",r);verify(limiter).tryAcquire("PAYMENT_LINK_QUERY_IP","127.0.0.1",120,properties.getWindow());verify(limiter).tryAcquire("PAYMENT_LINK_QUERY_LINK","opaque",120,properties.getWindow());}
 @Test void startUsesSmallerBudgetBeforeCreatingCheckout() {when(limiter.tryAcquire(anyString(),anyString(),anyInt(),any())).thenReturn(SlidingWindowRateLimitResult.rejected(60));assertThatThrownBy(()->service.require("start","opaque",new MockHttpServletRequest())).isInstanceOf(RateLimitExceededException.class);verify(limiter).tryAcquire("PAYMENT_LINK_START_IP","unknown",20,properties.getWindow());}
 @Test void linkSpecificRejectionIsNotLostAfterIpWasAccepted() {when(limiter.tryAcquire(eq("PAYMENT_LINK_QUERY_IP"),anyString(),anyInt(),any())).thenReturn(SlidingWindowRateLimitResult.permitted());when(limiter.tryAcquire(eq("PAYMENT_LINK_QUERY_LINK"),anyString(),anyInt(),any())).thenReturn(SlidingWindowRateLimitResult.rejected(30));assertThatThrownBy(()->service.require("resolve","opaque",new MockHttpServletRequest())).isInstanceOf(RateLimitExceededException.class);}
 @Test void invalidQuotaConfigurationFailsBeforeServingRequests() {properties.setIpQueryLimit(0);assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);}
}
