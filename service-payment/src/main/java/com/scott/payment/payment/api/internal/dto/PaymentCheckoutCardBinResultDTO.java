package com.scott.payment.payment.api.internal.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentCheckoutCardBinResultDTO
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 收银台 BIN 品牌识别结果。
 * @status : create
 */
@Data
public class PaymentCheckoutCardBinResultDTO implements Serializable {
    private static final long serialVersionUID = 1L;
    /**
     * 卡品牌编码，用于渠道能力匹配、路由和运营展示。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    private String cardBrand;
    /**
     * {@code recognized}，用于明确 {@code PaymentCheckoutCardBinResultDTO} 当前业务分支是否成立。
     * <p>
     * 单位：无；格式：布尔值或 0/1 开关；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：仅允许平台约定的启停取值；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    private Boolean recognized;
    /**
     * {@code supported}，表示当前渠道、配置或接口是否支持对应能力。
     * <p>
     * 单位：无；格式：布尔值或 0/1 开关；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：仅允许平台约定的启停取值；数据来源：上游接口请求、内部服务调用或远程服务响应。
     * </p>
     */
    private Boolean supported;
}
