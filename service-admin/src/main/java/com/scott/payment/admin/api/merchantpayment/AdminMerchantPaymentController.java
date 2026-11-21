package com.scott.payment.admin.api.merchantpayment;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.admin.application.merchantpayment.AdminMerchantPaymentScope;
import com.scott.payment.component.db.constant.DataSourceName;

import com.scott.payment.capability.governance.MerchantPaymentGovernanceService;
import com.scott.payment.admin.application.merchantpayment.MerchantPaymentBatchReviewService;
import com.scott.payment.capability.governance.MerchantPaymentModels.*;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.scott.payment.component.core.model.CommonResult.success;

/** 管理端支付方式治理。方法权限与商户数据范围均由服务端决定。 */
@RestController
@RequestMapping("/admin")
public class AdminMerchantPaymentController {
    private final MerchantPaymentGovernanceService governance;
    private final AdminMerchantPaymentScope merchantScope;
    private final MerchantPaymentBatchReviewService batchReview;

    public AdminMerchantPaymentController(MerchantPaymentGovernanceService governance,
                                          AdminMerchantPaymentScope merchantScope,
                                          MerchantPaymentBatchReviewService batchReview) {
        this.governance = governance;
        this.merchantScope = merchantScope;
        this.batchReview = batchReview;
    }

    /**
     * 在当前管理员可见范围内检索商户选择项。
     */
    @DS(DataSourceName.MASTER)
    @GetMapping("/merchant-payment-methods/merchant-options")
    @RequiresPermission("merchant:payment-method:view")
    public CommonResult<List<MerchantOption>> merchantOptions(
            @RequestParam(name = "keyword", required = false) @Size(max = 128) String keyword) {
        return success(governance.merchantOptions(merchantScope.current(), keyword));
    }

    /**
     * 分页查看指定商户的支付方式授权及配置状态。
     */
    @DS(DataSourceName.MASTER)
    @PostMapping("/merchants/{merchantId}/payment-methods/search")
    @RequiresPermission("merchant:payment-method:view")
    public CommonResult<PageResult<MethodRow>> methods(@PathVariable("merchantId") String merchant,
                                                        @Valid @RequestBody(required = false) Search query) {
        return success(governance.methods(merchantScope.current(), merchant, query));
    }

    /**
     * 查看单项支付方式的授权及配置详情。
     */
    @DS(DataSourceName.MASTER)
    @GetMapping("/merchants/{merchantId}/payment-methods/{itemKey}")
    @RequiresPermission("merchant:payment-method:view")
    public CommonResult<MethodRow> method(@PathVariable("merchantId") String merchant, @PathVariable("itemKey") String item) {
        return success(governance.method(merchantScope.current(), merchant, item));
    }

    /**
     * 以平台操作员身份代商户提交支付方式申请批次。
     */
    @DS(DataSourceName.MASTER)
    @PostMapping("/merchants/{merchantId}/payment-method-applications")
    @RequiresPermission("merchant:payment-method:apply")
    public CommonResult<BatchReceipt> apply(@PathVariable("merchantId") String merchant,
                                            @Valid @RequestBody Apply request) {
        return success(governance.apply(merchantScope.current(), merchant, request));
    }

    /**
     * 按管理员数据范围分页检索支付方式申请批次。
     */
    @DS(DataSourceName.MASTER)
    @PostMapping("/merchant-payment-applications/search")
    @RequiresPermission("merchant:payment-method:view")
    public CommonResult<PageResult<ApplicationRow>> applications(@Valid @RequestBody(required = false) Search query) {
        return success(governance.applications(merchantScope.current(), query));
    }

    /**
     * 查看单个支付方式申请及其明细审核历史。
     */
    @DS(DataSourceName.MASTER)
    @GetMapping("/merchant-payment-applications/{applicationNo}")
    @RequiresPermission("merchant:payment-method:view")
    public CommonResult<ApplicationRow> application(@PathVariable("applicationNo") String application) {
        return success(governance.application(merchantScope.current(), application));
    }

    /**
     * 提交单个申请项的审核决定，可按原命令编号重放回执。
     */
    @DS(DataSourceName.MASTER)
    @PostMapping("/merchant-payment-application-items/{itemId}/decisions")
    @RequiresPermission("merchant:payment-method:review")
    public CommonResult<ItemReceipt> decide(@PathVariable("itemId") long itemId, @Valid @RequestBody Decision request) {
        return success(governance.decide(merchantScope.current(), itemId, request));
    }

    /**
     * 按批次提交逐项审核决定并返回各项结果。
     */
    @DS(DataSourceName.MASTER)
    @PostMapping("/merchant-payment-application-items/decisions/batch")
    @RequiresPermission("merchant:payment-method:review")
    public CommonResult<BatchDecisionReceipt> decideBatch(@Valid @RequestBody BatchDecision request) {
        return success(batchReview.decide(merchantScope.current(), request));
    }

    /**
     * 暂停商户指定支付方式的授权。
     */
    @DS(DataSourceName.MASTER)
    @PostMapping("/merchants/{merchantId}/payment-methods/{itemKey}/suspend")
    @RequiresPermission("merchant:payment-method:suspend")
    public CommonResult<ItemReceipt> suspend(@PathVariable("merchantId") String merchant,
                                              @PathVariable("itemKey") String item, @Valid @RequestBody Change request) {
        return change(merchant, item, "SUSPEND", request);
    }

    /**
     * 恢复仍符合有效期条件的支付方式授权。
     */
    @DS(DataSourceName.MASTER)
    @PostMapping("/merchants/{merchantId}/payment-methods/{itemKey}/restore")
    @RequiresPermission("merchant:payment-method:restore")
    public CommonResult<ItemReceipt> restore(@PathVariable("merchantId") String merchant,
                                              @PathVariable("itemKey") String item, @Valid @RequestBody Change request) {
        return change(merchant, item, "RESTORE", request);
    }

    /**
     * 提交终止授权命令，并在历史中保留原授权版本。
     */
    @DS(DataSourceName.MASTER)
    @PostMapping("/merchants/{merchantId}/payment-methods/{itemKey}/terminate")
    @RequiresPermission("merchant:payment-method:terminate")
    public CommonResult<ItemReceipt> terminate(@PathVariable("merchantId") String merchant,
                                                @PathVariable("itemKey") String item, @Valid @RequestBody Change request) {
        return change(merchant, item, "TERMINATE", request);
    }

    /**
     * 提交授权有效期变更命令，交由治理服务校验时间边界。
     */
    @DS(DataSourceName.MASTER)
    @PostMapping("/merchants/{merchantId}/payment-methods/{itemKey}/validity")
    @RequiresPermission("merchant:payment-method:validity")
    public CommonResult<ItemReceipt> validity(@PathVariable("merchantId") String merchant,
                                               @PathVariable("itemKey") String item, @Valid @RequestBody Change request) {
        return change(merchant, item, "VALIDITY", request);
    }

    /**
     * 分页查看当前商户指定支付方式的授权与审核审计记录。
     */
    @DS(DataSourceName.MASTER)
    @GetMapping("/merchants/{merchantId}/payment-methods/{itemKey}/history")
    @RequiresPermission("merchant:payment-method:history")
    public CommonResult<PageResult<HistoryRow>> history(@PathVariable("merchantId") String merchant,
                                                        @PathVariable("itemKey") String item, @Valid Search query) {
        return success(governance.history(merchantScope.current(), merchant, item, query));
    }

    /**
     * 提交历史商户的准入基线供独立复核。
     */
    @DS(DataSourceName.MASTER)
    @PostMapping("/merchants/{merchantId}/payment-method-history-baselines")
    @RequiresPermission("merchant:payment-method:baseline:submit")
    public CommonResult<BaselineReceipt> submitBaseline(@PathVariable("merchantId") String merchant,
                                                          @Valid @RequestBody BaselineSubmit request) {
        return success(governance.submitBaseline(merchantScope.current(), merchant, request));
    }

    /**
     * 查看指定历史基线批次和冻结的依据。
     */
    @DS(DataSourceName.MASTER)
    @GetMapping("/merchants/{merchantId}/payment-method-history-baselines/{batchRef}")
    @RequiresPermission("merchant:payment-method:history")
    public CommonResult<BaselineDetail> baseline(@PathVariable("merchantId") String merchant,
                                                   @PathVariable("batchRef") String batchRef) {
        return success(governance.baseline(merchantScope.current(), merchant, batchRef));
    }

    /**
     * 提交历史准入基线的复核决定。
     */
    @DS(DataSourceName.MASTER)
    @PostMapping("/merchants/{merchantId}/payment-method-history-baselines/{batchRef}/approve")
    @RequiresPermission("merchant:payment-method:baseline:approve")
    public CommonResult<BaselineReceipt> approveBaseline(@PathVariable("merchantId") String merchant,
                                                           @PathVariable("batchRef") String batchRef,
                                                           @Valid @RequestBody BaselineApprove request) {
        return success(governance.approveBaseline(merchantScope.current(), merchant, batchRef, request));
    }

    private CommonResult<ItemReceipt> change(String merchant, String item, String action, Change request) {
        return success(governance.changeGrant(merchantScope.current(), merchant, item, action, request));
    }
}
