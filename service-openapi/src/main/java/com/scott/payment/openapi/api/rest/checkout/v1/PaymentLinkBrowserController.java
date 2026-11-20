package com.scott.payment.openapi.api.rest.checkout.v1;

import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.openapi.result.OpenApiMessageAudience;
import com.scott.payment.openapi.application.checkout.OpenApiPaymentLinkApplicationService;
import com.scott.payment.openapi.dto.paymentlink.PaymentLinkDTOs.*;
import com.scott.payment.openapi.result.OpenApiResponseFactory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** 支付链接公开边界只投影非个人信息，禁止代理缓存完整链接或短期 Checkout 入口。 */
@RestController
@RequestMapping("/checkout/api/v1/payment-links")
public class PaymentLinkBrowserController {
    private final OpenApiPaymentLinkApplicationService application;
    private final com.scott.payment.openapi.service.paymentlink.PaymentLinkBrowserAccessService access;
    public PaymentLinkBrowserController(OpenApiPaymentLinkApplicationService application,
                                       com.scott.payment.openapi.service.paymentlink.PaymentLinkBrowserAccessService access) {
        this.application = application; this.access = access;
    }

    public record Resolve(@NotBlank @Pattern(regexp = "(?:L[0-9]{22}|[0-9]{22}|[a-f0-9]{32})\\.[a-f0-9]{64}") String opaqueToken) {
        @Override public String toString() { return "PaymentLinkBrowserRequest[credential=REDACTED]"; }
        @com.fasterxml.jackson.annotation.JsonAnySetter public void reject(String key, Object value) { throw new IllegalArgumentException("Unsupported payment link field"); }
    }
    public record Start(@NotBlank @Pattern(regexp = "(?:L[0-9]{22}|[0-9]{22}|[a-f0-9]{32})\\.[a-f0-9]{64}") String opaqueToken,
                        @NotBlank @Pattern(regexp = "[A-Za-z0-9_.:-]{1,80}") String requestId) {
        @Override public String toString() { return "PaymentLinkBrowserRequest[credential=REDACTED]"; }
        @com.fasterxml.jackson.annotation.JsonAnySetter public void reject(String key, Object value) { throw new IllegalArgumentException("Unsupported payment link field"); }
    }

    /** 先通过匿名访问限流与凭据校验，再返回付款人安全摘要；响应禁止缓存。 */
    @PostMapping("/resolve")
    public ResponseEntity<CommonResult<PublicView>> resolve(@Valid @RequestBody Resolve request, jakarta.servlet.http.HttpServletRequest http) {
        access.require("resolve", request.opaqueToken(), http);
        return response(application.resolve(request.opaqueToken()));
    }
    /** 校验匿名访问及请求号后进入既有收银台会话，不直接发起渠道扣款。 */
    @PostMapping("/start")
    public ResponseEntity<CommonResult<Started>> start(@Valid @RequestBody Start request, jakarta.servlet.http.HttpServletRequest http) {
        access.require("start", request.opaqueToken(), http);
        return response(application.start(request.opaqueToken(), request.requestId()));
    }
    private <T> ResponseEntity<CommonResult<T>> response(T value) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Referrer-Policy", "no-referrer")
                .body(OpenApiResponseFactory.success(value, OpenApiMessageAudience.PAYER));
    }
}
