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
     * code，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
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
