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
    /** 按当前管理账号的数据范围提供商户选项，不授予额外商户资料权限。 */
    @PostMapping("/merchants/search")
    @RequiresPermission("transaction:payment-link:list")
    public CommonResult<java.util.List<AdminPaymentLinkApplicationService.MerchantChoice>> merchants(@Valid @RequestBody MerchantSearch query) {
        return CommonResult.success(application.merchantChoices(query.keyword()));
    }

    /** 提供列表筛选所需基础选项，查询权限不等同于创建权限。 */
    @PostMapping("/filter-options")
    @RequiresPermission("transaction:payment-link:list")
    public CommonResult<AdminPaymentLinkApplicationService.FilterChoices> filterOptions() {
        return CommonResult.success(application.filterChoices());
    }

    /** 校验筛选参数并分页返回当前账号可见链接，不暴露付款凭据。 */
    @PostMapping("/search")
    @RequiresPermission("transaction:payment-link:list")
    public CommonResult<PageResult<View>> search(@Valid @RequestBody(required = false) Query query) { return CommonResult.success(application.search(query)); }

    /** 按与列表一致的授权范围返回状态计数和分币种成功金额。 */
    @PostMapping("/summary")
    @RequiresPermission("transaction:payment-link:list")
    public CommonResult<Summary> summary(@Valid @RequestBody(required = false) Query query) { return CommonResult.success(application.summary(query)); }

    /** 校验创建权限后读取目标商户当前支持的链接参数范围。 */
    @PostMapping("/options")
    @RequiresPermission("transaction:payment-link:add")
    public CommonResult<Options> options(@RequestParam("merchantId") String merchantId) { return CommonResult.success(application.options(merchantId)); }

    /** 校验创建请求并进入本端幂等管理流程，金额与订单发布后固定。 */
    @PostMapping
    @RequiresPermission("transaction:payment-link:add")
    @OperationLog(moduleName = "支付链接", operation = "创建收单支付链接", businessType = com.scott.payment.component.web.operation.constant.OperationTypeConstants.CREATE, recordRequest = false)
    public CommonResult<View> create(@RequestParam("merchantId") String merchantId, @Valid @RequestBody Create input) {
        return CommonResult.success(application.create(merchantId, input));
    }

    /** 返回当前账号有权读取的链接详情，匿名付款人使用独立投影。 */
    @GetMapping("/{linkId}")
    @RequiresPermission("transaction:payment-link:detail")
    public CommonResult<View> detail(@PathVariable("linkId") String linkId) { return CommonResult.success(application.detail(linkId)); }

    /** 通过独立分享权限取得含付款凭据的地址，并禁止响应缓存。 */
    @PostMapping("/{linkId}/share")
    @RequiresPermission("transaction:payment-link:share")
    @OperationLog(moduleName = "支付链接", operation = "获取支付分享入口", recordRequest = false, recordResponse = false)
    public org.springframework.http.ResponseEntity<CommonResult<Share>> share(@PathVariable("linkId") String linkId) {
        return org.springframework.http.ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer").body(CommonResult.success(application.share(linkId)));
    }

    /** 以请求号和版本执行不可恢复的入口停用，在途交易继续按原流程收敛。 */
    @PostMapping("/{linkId}/disable")
    @RequiresPermission("transaction:payment-link:disable")
    @OperationLog(moduleName = "支付链接", operation = "停用支付链接", businessType = com.scott.payment.component.web.operation.constant.OperationTypeConstants.UPDATE, recordRequest = false)
    public CommonResult<View> disable(@PathVariable("linkId") String linkId, @Valid @RequestBody Disable input) {
        return CommonResult.success(application.disable(linkId, input));
    }

    /** 分页读取授权链接下的收款会话，不触发新的付款。 */
    @PostMapping("/{linkId}/collections/search")
    @RequiresPermission("transaction:payment-link:detail")
    public CommonResult<PageResult<Collection>> collections(@PathVariable("linkId") String linkId, @Valid @RequestBody(required = false) Query query) {
        return CommonResult.success(application.collections(linkId, query));
    }

    /** 分页读取授权链接的真实付款尝试，包括尚未生成系统订单的失败。 */
    @PostMapping("/{linkId}/transactions/search")
    @RequiresPermission("transaction:payment-link:detail")
    public CommonResult<PageResult<Attempt>> attempts(@PathVariable("linkId") String linkId, @Valid @RequestBody(required = false) Query query) {
        return CommonResult.success(application.attempts(linkId, query));
    }

    /** 分页读取授权链接的操作和支付时间线，保留来源与操作者。 */
    @PostMapping("/{linkId}/events/search")
    @RequiresPermission("transaction:payment-link:detail")
    public CommonResult<PageResult<Event>> events(@PathVariable("linkId") String linkId, @Valid @RequestBody(required = false) Query query) {
        return CommonResult.success(application.events(linkId, query));
    }
}
