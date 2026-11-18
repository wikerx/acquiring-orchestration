package com.scott.payment.admin.api.paymentlink;

import com.scott.payment.admin.application.paymentlink.AdminPaymentLinkApplicationService;
import com.scott.payment.admin.dto.paymentlink.PaymentLinkDTOs.*;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/** 支付链接登录入口，操作权限与对象数据范围分层校验。 */
@RestController
@RequestMapping("/admin/transactions/payment-links")
public class AdminPaymentLinkController {
    private final AdminPaymentLinkApplicationService application;
    public AdminPaymentLinkController(AdminPaymentLinkApplicationService application) { this.application = application; }

    public record MerchantSearch(@jakarta.validation.constraints.Size(max = 120) String keyword) { }
    @PostMapping("/merchants/search")
    @RequiresPermission("transaction:payment-link:list")
    public CommonResult<java.util.List<AdminPaymentLinkApplicationService.MerchantChoice>> merchants(@Valid @RequestBody MerchantSearch query) {
        return CommonResult.success(application.merchantChoices(query.keyword()));
    }

    @PostMapping("/filter-options")
    @RequiresPermission("transaction:payment-link:list")
    public CommonResult<AdminPaymentLinkApplicationService.FilterChoices> filterOptions() {
        return CommonResult.success(application.filterChoices());
    }

    @PostMapping("/search")
    @RequiresPermission("transaction:payment-link:list")
    public CommonResult<PageResult<View>> search(@Valid @RequestBody(required = false) Query query) { return CommonResult.success(application.search(query)); }

    @PostMapping("/summary")
    @RequiresPermission("transaction:payment-link:list")
    public CommonResult<Summary> summary(@Valid @RequestBody(required = false) Query query) { return CommonResult.success(application.summary(query)); }

    @PostMapping("/options")
    @RequiresPermission("transaction:payment-link:add")
    public CommonResult<Options> options(@RequestParam("merchantId") String merchantId) { return CommonResult.success(application.options(merchantId)); }

    @PostMapping
    @RequiresPermission("transaction:payment-link:add")
    @OperationLog(moduleName = "支付链接", operation = "创建收单支付链接", businessType = com.scott.payment.component.web.operation.constant.OperationTypeConstants.CREATE, recordRequest = false)
    public CommonResult<View> create(@RequestParam("merchantId") String merchantId, @Valid @RequestBody Create input) {
        return CommonResult.success(application.create(merchantId, input));
    }

    @GetMapping("/{linkId}")
    @RequiresPermission("transaction:payment-link:detail")
    public CommonResult<View> detail(@PathVariable("linkId") String linkId) { return CommonResult.success(application.detail(linkId)); }

    @PostMapping("/{linkId}/share")
    @RequiresPermission("transaction:payment-link:share")
    @OperationLog(moduleName = "支付链接", operation = "获取支付分享入口", recordRequest = false, recordResponse = false)
    public org.springframework.http.ResponseEntity<CommonResult<Share>> share(@PathVariable("linkId") String linkId) {
        return org.springframework.http.ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer").body(CommonResult.success(application.share(linkId)));
    }

    @PostMapping("/{linkId}/disable")
    @RequiresPermission("transaction:payment-link:disable")
    @OperationLog(moduleName = "支付链接", operation = "停用支付链接", businessType = com.scott.payment.component.web.operation.constant.OperationTypeConstants.UPDATE, recordRequest = false)
    public CommonResult<View> disable(@PathVariable("linkId") String linkId, @Valid @RequestBody Disable input) {
        return CommonResult.success(application.disable(linkId, input));
    }

    @PostMapping("/{linkId}/collections/search")
    @RequiresPermission("transaction:payment-link:detail")
    public CommonResult<PageResult<Collection>> collections(@PathVariable("linkId") String linkId, @Valid @RequestBody(required = false) Query query) {
        return CommonResult.success(application.collections(linkId, query));
    }

    @PostMapping("/{linkId}/transactions/search")
    @RequiresPermission("transaction:payment-link:detail")
    public CommonResult<PageResult<Attempt>> attempts(@PathVariable("linkId") String linkId, @Valid @RequestBody(required = false) Query query) {
        return CommonResult.success(application.attempts(linkId, query));
    }

    @PostMapping("/{linkId}/events/search")
    @RequiresPermission("transaction:payment-link:detail")
    public CommonResult<PageResult<Event>> events(@PathVariable("linkId") String linkId, @Valid @RequestBody(required = false) Query query) {
        return CommonResult.success(application.events(linkId, query));
    }
}
