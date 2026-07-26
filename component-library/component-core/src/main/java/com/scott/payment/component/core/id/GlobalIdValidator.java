package com.scott.payment.component.core.id;


/**
 * @author : scott
 * @version : v1.0.0
 * @classname : GlobalIdValidator
 * @date : 2026-06-25 10:37
 * @email : scott_x@163.com
 * @description : GlobalIdValidator Java 类型，用于封装当前包内的领域数据、服务契约或模块协作逻辑，位于 公共组件层，输入输出边界由所在包和公开方法契约限定。
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
