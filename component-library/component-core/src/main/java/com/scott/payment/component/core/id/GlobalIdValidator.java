package com.scott.payment.component.core.id;

/**
 * 全系统统一唯一标识校验器，集中校验长度、数字格式和 Luhn 校验位。
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
