package com.scott.payment.openapi.api.rest.payment.v1;

import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.openapi.result.OpenApiResponseFactory;
import com.scott.payment.openapi.vo.payment.PaymentCreateVO;

/**
 * 支付接口的 OpenAPI 响应适配器。
 *
 * <p>适配器只构造请求成功的外层协议信封，支付结果继续由加密 data 内的交易字段表达。</p>
 */
public final class OpenApiPaymentResponseFactory {

    /**
     * 支付响应适配器只提供静态方法，不允许实例化。
     */
    private OpenApiPaymentResponseFactory() {
    }

    /**
     * 将已经完成交易结果投影的支付响应包装为 OpenAPI 请求成功信封。
     *
     * @param response 支付业务响应，交易结果由对象内部字段表达
     * @return 外层固定为请求处理成功的 OpenAPI 响应
     */
    public static CommonResult<PaymentCreateVO> from(PaymentCreateVO response) {
        return OpenApiResponseFactory.success(response);
    }
}
