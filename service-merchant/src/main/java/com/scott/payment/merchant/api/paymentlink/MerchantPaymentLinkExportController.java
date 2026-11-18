package com.scott.payment.merchant.api.paymentlink;

import com.scott.payment.merchant.application.paymentlink.MerchantPaymentLinkExportApplicationService;
import com.scott.payment.merchant.dto.paymentlink.PaymentLinkDTOs.Query;
import com.scott.payment.merchant.dto.exporttask.MerchantExportTaskDTOs.TaskSummary;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

/** 异步导出只返回任务号，文件通过已有下载中心领取。 */
@RestController
@RequestMapping("/merchant/transactions/payment-links")
public class MerchantPaymentLinkExportController {
    private final MerchantPaymentLinkExportApplicationService application;
    public MerchantPaymentLinkExportController(MerchantPaymentLinkExportApplicationService application) { this.application=application; }
    public record Submit(@NotBlank String requestId,@Valid Query query,@jakarta.validation.constraints.Size(max=80) String timezone) { }
    @PostMapping("/export")
    @RequiresPermission("merchant:transaction:payment-link:export")
    public CommonResult<TaskSummary> submit(@Valid @RequestBody Submit request) {
        return CommonResult.success(application.submit(request.requestId(),request.query(),request.timezone()));
    }
}
