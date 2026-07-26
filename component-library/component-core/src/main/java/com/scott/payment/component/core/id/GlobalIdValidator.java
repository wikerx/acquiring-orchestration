package com.scott.payment.component.core.id;


/**
 * @author : scott
 * @version : v1.0.0
 * @classname : GlobalIdValidator
 * @date : 2026-06-25 10:37
 * @email : scott_x@163.com
 * @description : Global ID Validator 校验组件，位于 公共组件库，执行参数、状态、权限或配置规则校验，失败时返回统一异常。
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
