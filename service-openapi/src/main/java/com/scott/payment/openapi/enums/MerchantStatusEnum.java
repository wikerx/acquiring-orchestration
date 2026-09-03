package com.scott.payment.openapi.enums;

import lombok.Getter;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantStatusEnum
 * @date : 2026-06-05 00:00
 * @email : scott_x@163.com
 * @description : 商户状态枚举，数据库使用数字码存储，代码层通过枚举表达业务语义
 * @status : create
 */

@Getter
public enum MerchantStatusEnum {

    /**
     * 正常状态，商户可以正常发起 OpenAPI 交易请求。
     */
    ACTIVE(1, "正常"),

    /**
     * 冻结状态，商户保留资料但暂时不允许发起交易。
     */
    FROZEN(2, "冻结"),

    /**
     * 关闭状态，商户已终止合作或不可再发起交易。
     */
    CLOSED(3, "关闭");

    /**
     * 数据库存储的状态编码。
     */
    private final Integer code;

    /**
     * 面向运营、测试日志和后台展示的中文说明。
     */
    private final String description;

    /**
     * 创建商户状态枚举。
     *
     * @param code        数据库存储的状态编码
     * @param description 状态中文说明
     */
    MerchantStatusEnum(Integer code, String description) {
        this.code = code;
        this.description = description;
    }
}
