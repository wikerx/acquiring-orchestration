package com.scott.payment.openapi.enums;

import lombok.Getter;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantRiskLevelEnum
 * @date : 2026-06-05 00:00
 * @email : scott_x@163.com
 * @description : 商户风险等级枚举，数据库使用数字码存储，避免固定状态字段使用字符串造成索引和比较成本浪费
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantRiskLevelEnum
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 商户 OpenAPIMerchant Risk Level 枚举，位于 service-openapi 的业务组件层，用于收敛页面、接口或业务流程中的固定取值。
 * @status : create
 */
@Getter
public enum MerchantRiskLevelEnum {

    /**
     * 低风险商户，通常适用于历史交易稳定、拒付率较低的商户。
     */
    LOW(1, "低风险"),

    /**
     * 默认风险等级，适用于新开户或正常运营中的大多数商户。
     */
    NORMAL(2, "普通风险"),

    /**
     * 高风险商户，后续可用于触发更严格的限额、风控或人工审核策略。
     */
    HIGH(3, "高风险");

    /**
     * 数据库存储的风险等级编码。
     */
    private final Integer code;

    /**
     * 面向运营、测试日志和后台展示的中文说明。
     */
    private final String description;

    /**
     * 创建商户风险等级枚举。
     *
     * @param code        数据库存储的风险等级编码
     * @param description 风险等级中文说明
     */
    MerchantRiskLevelEnum(Integer code, String description) {
        this.code = code;
        this.description = description;
    }
}
