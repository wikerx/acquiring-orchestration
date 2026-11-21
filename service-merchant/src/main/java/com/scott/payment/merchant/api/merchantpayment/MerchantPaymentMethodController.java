package com.scott.payment.merchant.api.merchantpayment;

import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.merchant.application.merchantpayment.MerchantPaymentMethodApplicationService;
import com.scott.payment.capability.governance.MerchantPaymentModels.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import static com.scott.payment.component.core.model.CommonResult.success;

/** 商户支付方式目录、申请和逐项结果。 */
@RestController
@RequestMapping("/merchant")
public class MerchantPaymentMethodController {
    private final MerchantPaymentMethodApplicationService application;

    public MerchantPaymentMethodController(MerchantPaymentMethodApplicationService application) {
        this.application = application;
    }

    @PostMapping("/payment-methods/search")
    @RequiresPermission("merchant:payment-method:view")
    public CommonResult<PageResult<MethodRow>> methods(@Valid @RequestBody(required = false) Search query) {
        return success(application.methods(query));
    }

    @GetMapping("/payment-methods/{itemKey}")
    @RequiresPermission("merchant:payment-method:view")
    public CommonResult<MethodRow> method(@PathVariable("itemKey") String item) {
        return success(application.method(item));
    }

    @PostMapping("/payment-method-applications")
    @RequiresPermission("merchant:payment-method:apply")
    public CommonResult<BatchReceipt> apply(@Valid @RequestBody Apply request) {
        return success(application.apply(request));
    }

    @PostMapping("/payment-method-applications/search")
    @RequiresPermission("merchant:payment-method:application:view")
    public CommonResult<PageResult<ApplicationRow>> applications(@Valid @RequestBody(required = false) Search query) {
        return success(application.applications(query));
    }

    @GetMapping("/payment-method-applications/{applicationNo}")
    @RequiresPermission("merchant:payment-method:application:view")
    public CommonResult<ApplicationRow> application(@PathVariable("applicationNo") String applicationNo) {
        return success(application.application(applicationNo));
    }

    @PostMapping("/payment-method-application-items/{itemId}/resubmit")
    @RequiresPermission("merchant:payment-method:resubmit")
    public CommonResult<ItemReceipt> resubmit(@PathVariable("itemId") long item, @Valid @RequestBody Change request) {
        return success(application.change(item, request, true));
    }

    @PostMapping("/payment-method-application-items/{itemId}/withdraw")
    @RequiresPermission("merchant:payment-method:withdraw")
    public CommonResult<ItemReceipt> withdraw(@PathVariable("itemId") long item, @Valid @RequestBody Change request) {
        return success(application.change(item, request, false));
    }
}
