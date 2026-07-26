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

    /**
     * code 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
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
