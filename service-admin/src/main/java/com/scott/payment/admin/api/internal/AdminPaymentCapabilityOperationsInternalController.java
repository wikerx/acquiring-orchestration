package com.scott.payment.admin.api.internal;

import com.scott.payment.admin.application.operation.MerchantPaymentCapabilityOperations;
import com.scott.payment.admin.application.operation.MerchantPaymentCapabilityOperations.BoundaryRequest;
import com.scott.payment.admin.application.operation.MerchantPaymentCapabilityOperations.BoundaryResponse;
import com.scott.payment.admin.application.operation.MerchantPaymentCapabilityOperations.RecoveryRequest;
import com.scott.payment.admin.application.operation.MerchantPaymentCapabilityOperations.RecoveryResponse;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.internal.InternalServiceSignature;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 接受经过内部签名拦截器验证的 Job 支付方式复核命令。 */
@RestController
@RequestMapping("/internal/admin/payment-capability-operations")
public class AdminPaymentCapabilityOperationsInternalController {

    private final MerchantPaymentCapabilityOperations operations;

    public AdminPaymentCapabilityOperationsInternalController(MerchantPaymentCapabilityOperations operations) {
        this.operations = operations;
    }

    @PostMapping("/boundaries")
    public CommonResult<BoundaryResponse> scan(@RequestHeader(InternalServiceSignature.HEADER_CALLER) String caller,
                                               @RequestBody BoundaryRequest request) {
        requireJob(caller);
        return CommonResult.success(operations.boundaries(request));
    }

    @PostMapping("/delivery-recovery")
    public CommonResult<RecoveryResponse> recover(@RequestHeader(InternalServiceSignature.HEADER_CALLER) String caller,
                                                   @RequestBody RecoveryRequest request) {
        requireJob(caller);
        return CommonResult.success(operations.recover(request.limit()));
    }

    private static void requireJob(String caller) {
        if (!"service-job".equals(caller)) {
            throw new ServiceException(ApiResultEnum.FORBIDDEN);
        }
    }
}
