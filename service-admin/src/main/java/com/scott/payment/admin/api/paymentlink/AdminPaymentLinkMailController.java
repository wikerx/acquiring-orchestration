package com.scott.payment.admin.api.paymentlink;

import com.scott.payment.admin.application.paymentlink.AdminPaymentLinkMailApplicationService;
import com.scott.payment.admin.dto.paymentlink.PaymentLinkDTOs.*;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/** 发送和投递查询独立授权，日志不采集邮箱、邮件正文或分享地址。 */
@RestController
@RequestMapping("/admin/transactions/payment-links")
public class AdminPaymentLinkMailController {
    private final AdminPaymentLinkMailApplicationService application;
    public AdminPaymentLinkMailController(AdminPaymentLinkMailApplicationService application) { this.application=application; }
    @PostMapping("/{linkId}/send-email")
    @RequiresPermission("transaction:payment-link:send")
    @OperationLog(moduleName="支付链接",operation="发送收单支付邀请",businessType=com.scott.payment.component.web.operation.constant.OperationTypeConstants.CREATE,recordRequest=false,recordResponse=false)
    public CommonResult<Delivery> send(@PathVariable("linkId") String linkId,@Valid @RequestBody SendEmail input) {
        return CommonResult.success(application.send(linkId,input));
    }
    @PostMapping("/{linkId}/deliveries/search")
    @RequiresPermission("transaction:payment-link:delivery")
    public CommonResult<PageResult<Delivery>> page(@PathVariable("linkId") String linkId,@Valid @RequestBody(required=false) Query query) {
        return CommonResult.success(application.page(linkId,query));
    }
}
