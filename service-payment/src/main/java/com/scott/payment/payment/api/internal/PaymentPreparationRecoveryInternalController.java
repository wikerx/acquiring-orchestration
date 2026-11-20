package com.scott.payment.payment.api.internal;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.internal.InternalServiceAuthInterceptor;
import com.scott.payment.payment.service.capability.PaymentPreparationOwnershipService;
import com.scott.payment.payment.service.capability.PaymentPreparationOwnershipService.CancellationDecision;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

/** Risk 在释放受保护预占之前须由 Payment 权威裁定本代准备归属。 */
@RestController
@RequestMapping("/internal/payment/preparation-ownership")
public class PaymentPreparationRecoveryInternalController {
    private final PaymentPreparationOwnershipService ownership;

    public PaymentPreparationRecoveryInternalController(PaymentPreparationOwnershipService ownership) {
        this.ownership = ownership;
    }

    @PostMapping("/reconcile-cancellation")
    public CommonResult<CancellationDecision> reconcile(@Valid @RequestBody CancellationRequest request,
            @RequestAttribute(name = InternalServiceAuthInterceptor.AUTHENTICATED_CALLER_ATTRIBUTE, required = false)
            String caller) {
        if (!"service-risk".equals(caller)) {
            throw new ServiceException(ApiResultEnum.FORBIDDEN);
        }
        LocalDateTime transactionTime;
        try {
            transactionTime = LocalDateTime.parse(request.transactionDateTime());
        } catch (DateTimeParseException exception) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        return CommonResult.success(ownership.reconcileCancellation(request.merchantId(), request.ownershipKey(),
                request.generation(), request.candidateTransactionId(), transactionTime));
    }

    public record CancellationRequest(@NotBlank @Size(max = 64) String merchantId,
                                      @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String ownershipKey,
                                      @Positive long generation,
                                      @NotBlank @Size(max = 64) String candidateTransactionId,
                                      @NotBlank @Size(max = 32) String transactionDateTime) {
    }
}
