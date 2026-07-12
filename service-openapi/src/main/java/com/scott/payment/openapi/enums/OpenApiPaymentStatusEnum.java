package com.scott.payment.openapi.enums;

import lombok.Getter;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiPaymentStatusEnum
 * @date : 2026-07-11 00:00
 * @email : scott_x@163.com
 * @description : 商户 OpenAPI 支付响应状态枚举，位于 service-openapi 枚举层，对齐字典 transaction_status，用于收敛商户可见交易状态取值。
 * @status : create
 */
@Getter
public enum OpenApiPaymentStatusEnum {

    /**
     * 交易成功。
     */
    SUCCESS("SUCCESS"),

    /**
     * 交易失败。
     */
    FAILED("FAILED"),

    /**
     * 交易等待外部动作或异步结果。
     */
    PENDING("PENDING"),

    /**
     * 交易处理中，最终结果以查询或后续通知为准。
     */
    PROCESSING("PROCESSING");

    private final String code;

    /**
     * 创建商户 OpenAPI 支付响应状态。
     *
     * @param code 商户接口返回的状态编码
     */
    OpenApiPaymentStatusEnum(String code) {
        this.code = code;
    }
}
