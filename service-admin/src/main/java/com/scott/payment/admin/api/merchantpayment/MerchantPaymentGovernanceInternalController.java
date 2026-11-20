package com.scott.payment.admin.api.merchantpayment;

import com.scott.payment.admin.application.merchantpayment.MerchantPaymentGovernanceService;
import com.scott.payment.admin.application.merchantpayment.MerchantPaymentGovernanceService.Scope;
import com.scott.payment.admin.dto.merchantpayment.MerchantPaymentModels.*;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.internal.InternalServiceAuthProperties;
import com.scott.payment.component.web.internal.InternalServiceSignature;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.*;

import static com.scott.payment.component.core.model.CommonResult.success;

/** 接受经过商户端登录态授权并以 HMAC 绑定正文与租户的访问。 */
@RestController
@RequestMapping("/internal/merchant/{merchantId}/payment-methods")
public class MerchantPaymentGovernanceInternalController {
    private static final String MERCHANT_CALLER = "service-merchant";
    private static final AntPathMatcher PATHS = new AntPathMatcher();
    private final MerchantPaymentGovernanceService governance;
    private final InternalServiceAuthProperties auth;

    public MerchantPaymentGovernanceInternalController(MerchantPaymentGovernanceService governance,
                                                        InternalServiceAuthProperties auth) {
        this.governance = governance;
        this.auth = auth;
    }

    @PostMapping("/search")
    public CommonResult<PageResult<MethodRow>> methods(@PathVariable("merchantId") String merchant,
                                                        @Valid @RequestBody SignedSearch input, HttpServletRequest request) {
        return success(governance.methods(scope(merchant, input.actor(), request), merchant, input.query()));
    }

    @PostMapping("/access-summary")
    public CommonResult<AccessSummary> accessSummary(@PathVariable("merchantId") String merchant,
                                                      @Valid @RequestBody SignedIdentity input, HttpServletRequest request) {
        return success(governance.accessSummary(scope(merchant, input.actor(), request), merchant));
    }

    @PostMapping("/fee-coverage")
    public CommonResult<FeeAuthorizationProjection> feeCoverage(@PathVariable("merchantId") String merchant,
                                                                 @Valid @RequestBody SignedIdentity input,
                                                                 HttpServletRequest request) {
        return success(governance.feeAuthorizations(scope(merchant, input.actor(), request), merchant));
    }

    @PostMapping("/method/{itemKey}")
    public CommonResult<MethodRow> method(@PathVariable("merchantId") String merchant,
                                          @PathVariable("itemKey") String item,
                                          @Valid @RequestBody SignedIdentity input, HttpServletRequest request) {
        return success(governance.method(scope(merchant, input.actor(), request), merchant, item));
    }

    @PostMapping("/applications")
    public CommonResult<BatchReceipt> apply(@PathVariable("merchantId") String merchant,
                                            @Valid @RequestBody SignedApply input, HttpServletRequest request) {
        return success(governance.apply(scope(merchant, input.actor(), request), merchant, input.command()));
    }

    @PostMapping("/applications/search")
    public CommonResult<PageResult<ApplicationRow>> applications(@PathVariable("merchantId") String merchant,
                                                                  @Valid @RequestBody SignedSearch input,
                                                                  HttpServletRequest request) {
        return success(governance.applications(scope(merchant, input.actor(), request), input.query()));
    }

    @PostMapping("/applications/{applicationNo}")
    public CommonResult<ApplicationRow> application(@PathVariable("merchantId") String merchant,
                                                    @PathVariable("applicationNo") String application,
                                                    @Valid @RequestBody SignedIdentity input, HttpServletRequest request) {
        return success(governance.application(scope(merchant, input.actor(), request), application));
    }

    @PostMapping("/items/{itemId}/resubmit")
    public CommonResult<ItemReceipt> resubmit(@PathVariable("merchantId") String merchant,
                                               @PathVariable("itemId") long item,
                                               @Valid @RequestBody SignedChange input, HttpServletRequest request) {
        return success(governance.changeApplication(scope(merchant, input.actor(), request), item, input.command(), true));
    }

    @PostMapping("/items/{itemId}/withdraw")
    public CommonResult<ItemReceipt> withdraw(@PathVariable("merchantId") String merchant,
                                               @PathVariable("itemId") long item,
                                               @Valid @RequestBody SignedChange input, HttpServletRequest request) {
        return success(governance.changeApplication(scope(merchant, input.actor(), request), item, input.command(), false));
    }

    private Scope scope(String merchant, Identity caller, HttpServletRequest request) {
        String path = request.getRequestURI();
        if (!auth.isEnabled() || !MERCHANT_CALLER.equals(request.getHeader(InternalServiceSignature.HEADER_CALLER))
                || !auth.isPathAllowed(MERCHANT_CALLER, path)
                || auth.getWhitelist().stream().anyMatch(pattern -> PATHS.match(pattern, path))) {
            throw new ServiceException(ApiResultEnum.FORBIDDEN);
        }
        return governance.merchantScope(merchant, caller.accountId(), caller.userId());
    }

    public record Identity(@NotNull @Positive Long accountId, @NotNull @Positive Long userId) {}
    public record SignedIdentity(@NotNull @Valid Identity actor) {}
    public record SignedSearch(@NotNull @Valid Identity actor, @Valid Search query) {}
    public record SignedApply(@NotNull @Valid Identity actor, @NotNull @Valid Apply command) {}
    public record SignedChange(@NotNull @Valid Identity actor, @NotNull @Valid Change command) {}
}
