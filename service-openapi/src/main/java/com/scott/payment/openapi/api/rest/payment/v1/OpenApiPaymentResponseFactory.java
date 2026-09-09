package com.scott.payment.openapi.api.rest.payment.v1;

import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.openapi.vo.payment.PaymentCreateVO;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiPaymentResponseFactory
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : Builds the OpenAPI transport envelope while transaction results remain inside encrypted data.
 * @status : create
 */
public final class OpenApiPaymentResponseFactory {

    private OpenApiPaymentResponseFactory() {
    }

    /**
     * 将支付核心内部结果转换为 OpenAPI 响应模型，并保持商户可见状态语义一致。
     * @param response 下游响应、HTTP 响应或本地处理结果，日志输出前必须完成脱敏或摘要化
     * @return 符合 OpenAPI 契约的支付响应
     */
    public static CommonResult<PaymentCreateVO> from(PaymentCreateVO response) {
        return CommonResult.success(response);
    }
}
