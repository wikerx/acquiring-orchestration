package com.scott.payment.admin.support.approval;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantAccessSubmitSource
 * @date : 2026-08-06 00:00
 * @email : scott_x@163.com
 * @description : 商户访问控制配置的提交来源，用于区分管理端直增与商户端待审申请。
 * @status : create
 */
public enum MerchantAccessSubmitSource {

    /**
     * ADMIN 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    ADMIN,
    MERCHANT
}
