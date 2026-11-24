package com.scott.payment.openapi.support;

import com.scott.payment.component.core.id.GlobalIdConstants;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.openapi.client.payment.PaymentInternalRestClient;
import com.scott.payment.openapi.client.payment.dto.CallbackDeliveryResponseRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** 记录进入渠道回调控制器的 HTTP 投递及 Spring MVC 最终生成的应答。 */
@Component
@Slf4j
public class CallbackDeliveryAuditInterceptor implements HandlerInterceptor {

    private static final String ATTRIBUTE = CallbackDeliveryAuditInterceptor.class.getName();

    private final PaymentInternalRestClient paymentClient;

    public CallbackDeliveryAuditInterceptor(PaymentInternalRestClient paymentClient) {
        this.paymentClient = paymentClient;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(ATTRIBUTE, new Delivery(UUID.randomUUID().toString().replace("-", ""),
                LocalDateTime.now(GlobalIdConstants.DEFAULT_ZONE_ID), System.nanoTime()));
        return true;
    }

    /** 已通过安全校验并开始向 Payment 提交后，才回写该投递的应答。 */
    public static Delivery delivery(HttpServletRequest request) {
        Delivery delivery = (Delivery) request.getAttribute(ATTRIBUTE);
        if (delivery != null) {
            delivery.submitted = true;
        }
        return delivery;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception exception) {
        Delivery delivery = (Delivery) request.getAttribute(ATTRIBUTE);
        if (delivery == null || !delivery.submitted || exception != null) {
            return;
        }
        int status = response.getStatus();
        String body = status == delivery.ackStatus ? delivery.safeBody : null;
        long durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - delivery.startNanos);
        try {
            if (!paymentClient.completeCallbackDelivery(
                    new CallbackDeliveryResponseRequest(delivery.id, status, body, durationMillis))) {
                log.warn("渠道回调应答审计未命中投递记录, traceId: {}, deliveryId: {}, httpStatus: {}",
                        TraceContext.getTraceId(), delivery.id, status);
            }
        } catch (RuntimeException failure) {
            log.warn("渠道回调应答审计回写失败, traceId: {}, deliveryId: {}, httpStatus: {}, errorType: {}",
                    TraceContext.getTraceId(), delivery.id, status, failure.getClass().getSimpleName());
        }
    }

    /** 单个请求的观测值，仅在入口线程使用。 */
    public static final class Delivery {
        private final String id;
        private final LocalDateTime receivedTime;
        private final long startNanos;
        private boolean submitted;
        private int ackStatus;
        private String safeBody;

        private Delivery(String id, LocalDateTime receivedTime, long startNanos) {
            this.id = id;
            this.receivedTime = receivedTime;
            this.startNanos = startNanos;
        }

        public String id() {
            return id;
        }

        public LocalDateTime receivedTime() {
            return receivedTime;
        }

        public void recordSafeBody(int status, String body) {
            this.ackStatus = status;
            this.safeBody = body;
        }
    }
}
