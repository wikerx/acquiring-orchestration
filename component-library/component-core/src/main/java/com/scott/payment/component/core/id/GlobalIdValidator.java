package com.scott.payment.component.core.id;


/**
 * Global ID Validator 校验组件，位于 公共组件库，执行参数、状态、权限或配置规则校验，失败时返回统一异常。
 */
public final class GlobalIdValidator {

    /**
     * 禁止实例化全局编号校验工具类。
     */
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
