package com.scott.payment.component.core.id;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : GlobalIdValidator
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 全局数字 ID 校验器，校验长度、纯数字格式和 Luhn 校验位，防止非法 transactionId 进入交易链路。
 * @status : create
 */
public final class GlobalIdValidator {

    private GlobalIdValidator() {
    }

    /**
     * 判断是否为合法的全局唯一标识。
     *
     * @param id 编号
     * @return true=合法，false=非法
     */
    public static boolean isValid(String id) {
        return LuhnMod10Utils.validate(id);
    }
}
