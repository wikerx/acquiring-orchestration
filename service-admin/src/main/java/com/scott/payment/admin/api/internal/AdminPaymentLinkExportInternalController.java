package com.scott.payment.admin.api.internal;

import com.scott.payment.admin.application.paymentlink.AdminPaymentLinkExportSourceApplicationService;
import com.scott.payment.admin.dto.paymentlink.PaymentLinkExportDTOs.*;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.web.internal.InternalServiceSignature;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/** 导出源只接收 Data 的已签名任务请求，禁止其他内部调用者选择任意账号范围。 */
@RestController
@RequestMapping("/internal/admin/transactions/payment-links")
public class AdminPaymentLinkExportInternalController {
    private final AdminPaymentLinkExportSourceApplicationService application;
    public AdminPaymentLinkExportInternalController(AdminPaymentLinkExportSourceApplicationService application) { this.application=application; }
    /** 为可信导出调用读取授权分页，内部调用仍按任务创建人的商户范围过滤。 */
    @PostMapping("/export-page")
    public CommonResult<PageResult<Row>> page(@Valid @RequestBody PageRequest request,HttpServletRequest http) {
        if(!"service-data".equals(http.getHeader(InternalServiceSignature.HEADER_CALLER))) { throw new ServiceException(ApiResultEnum.FORBIDDEN); }
        return CommonResult.success(application.page(request));
    }
}
