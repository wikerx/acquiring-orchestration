package com.scott.payment.openapi.api.rest.payment.v1;

import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.openapi.vo.payment.PaymentCreateVO;

/** Builds the OpenAPI transport envelope while transaction results remain inside encrypted data. */
public final class OpenApiPaymentResponseFactory {

    private OpenApiPaymentResponseFactory() {
    }

    public static CommonResult<PaymentCreateVO> from(PaymentCreateVO response) {
        return CommonResult.success(response);
    }
}
