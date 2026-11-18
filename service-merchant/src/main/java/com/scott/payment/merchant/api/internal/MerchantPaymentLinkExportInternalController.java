package com.scott.payment.merchant.api.internal;

import com.scott.payment.merchant.application.paymentlink.MerchantPaymentLinkExportSourceApplicationService;
import com.scott.payment.merchant.dto.paymentlink.PaymentLinkExportDTOs.*;
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
@RequestMapping("/internal/merchant/transactions/payment-links")
public class MerchantPaymentLinkExportInternalController {
    private final MerchantPaymentLinkExportSourceApplicationService application;
    public MerchantPaymentLinkExportInternalController(MerchantPaymentLinkExportSourceApplicationService application) { this.application=application; }
    @PostMapping("/export-page")
    public CommonResult<PageResult<Row>> page(@Valid @RequestBody PageRequest request,HttpServletRequest http) {
        if(!"service-data".equals(http.getHeader(InternalServiceSignature.HEADER_CALLER))) { throw new ServiceException(ApiResultEnum.FORBIDDEN); }
        return CommonResult.success(application.page(request));
    }
}
