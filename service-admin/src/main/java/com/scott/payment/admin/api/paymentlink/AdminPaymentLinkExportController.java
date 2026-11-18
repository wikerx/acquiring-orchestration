package com.scott.payment.admin.api.paymentlink;

import com.scott.payment.admin.application.paymentlink.AdminPaymentLinkExportApplicationService;
import com.scott.payment.admin.dto.paymentlink.PaymentLinkDTOs.Query;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.TaskSummary;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

/** 异步导出只返回任务号，文件通过已有下载中心领取。 */
@RestController
@RequestMapping("/admin/transactions/payment-links")
public class AdminPaymentLinkExportController {
    private final AdminPaymentLinkExportApplicationService application;
    public AdminPaymentLinkExportController(AdminPaymentLinkExportApplicationService application) { this.application=application; }
    public record Submit(@NotBlank String requestId,@Valid Query query,@jakarta.validation.constraints.Size(max=80) String timezone) { }
    @PostMapping("/export")
    @RequiresPermission("transaction:payment-link:export")
    public CommonResult<TaskSummary> submit(@Valid @RequestBody Submit request) {
        return CommonResult.success(application.submit(request.requestId(),request.query(),request.timezone()));
    }
}
