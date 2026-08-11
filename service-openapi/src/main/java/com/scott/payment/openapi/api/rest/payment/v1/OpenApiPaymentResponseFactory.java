package com.scott.payment.openapi.api.rest.payment.v1;

import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.openapi.vo.payment.PaymentCreateVO;
import org.springframework.util.StringUtils;

/** Builds an OpenAPI envelope that matches the current transaction result while retaining data. */
public final class OpenApiPaymentResponseFactory {

    private OpenApiPaymentResponseFactory() {
    }

    public static CommonResult<PaymentCreateVO> from(PaymentCreateVO response) {
        PaymentCreateVO.TransactionInfoVO transactionInfo = response == null ? null : response.getTransactionInfo();
        if (transactionInfo == null
                || !StringUtils.hasText(transactionInfo.getCode())
                || !StringUtils.hasText(transactionInfo.getMessage())) {
            return CommonResult.success(response);
        }
        return CommonResult.success(transactionInfo.getCode(), transactionInfo.getMessage(), response);
    }
}
