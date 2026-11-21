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

    /**
     * 读取登录商户范围内的支付方式授权及配置列表。
     */
    @PostMapping("/payment-methods/search")
    @RequiresPermission("merchant:payment-method:view")
    public CommonResult<PageResult<MethodRow>> methods(@Valid @RequestBody(required = false) Search query) {
        return success(application.methods(query));
    }

    /**
     * 读取当前商户的指定支付方式及开通条件。
     */
    @GetMapping("/payment-methods/{itemKey}")
    @RequiresPermission("merchant:payment-method:view")
    public CommonResult<MethodRow> method(@PathVariable("itemKey") String item) {
        return success(application.method(item));
    }

    /**
     * 为当前登录商户提交新的支付方式申请批次。
     */
    @PostMapping("/payment-method-applications")
    @RequiresPermission("merchant:payment-method:apply")
    public CommonResult<BatchReceipt> apply(@Valid @RequestBody Apply request) {
        return success(application.apply(request));
    }

    /**
     * 查询当前商户的支付方式申请历史。
     */
    @PostMapping("/payment-method-applications/search")
    @RequiresPermission("merchant:payment-method:application:view")
    public CommonResult<PageResult<ApplicationRow>> applications(@Valid @RequestBody(required = false) Search query) {
        return success(application.applications(query));
    }

    /**
     * 查看当前商户指定申请的审核轮次及明细。
     */
    @GetMapping("/payment-method-applications/{applicationNo}")
    @RequiresPermission("merchant:payment-method:application:view")
    public CommonResult<ApplicationRow> application(@PathVariable("applicationNo") String applicationNo) {
        return success(application.application(applicationNo));
    }

    /**
     * 对当前商户被要求补充材料的申请提交新一轮说明。
     */
    @PostMapping("/payment-method-application-items/{itemId}/resubmit")
    @RequiresPermission("merchant:payment-method:resubmit")
    public CommonResult<ItemReceipt> resubmit(@PathVariable("itemId") long item, @Valid @RequestBody Change request) {
        return success(application.change(item, request, true));
    }

    /**
     * 撤回当前商户符合撤回状态的申请项。
     */
    @PostMapping("/payment-method-application-items/{itemId}/withdraw")
    @RequiresPermission("merchant:payment-method:withdraw")
    public CommonResult<ItemReceipt> withdraw(@PathVariable("itemId") long item, @Valid @RequestBody Change request) {
        return success(application.change(item, request, false));
    }
}
