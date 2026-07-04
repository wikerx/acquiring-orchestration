package com.scott.payment.component.core.id;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : GlobalIdValidator
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Global Id Validator，位于 component-library/component-core 的业务组件层，用于说明职责边界、数据语义和关键业务约束。
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
    /**
     * 判断收单支付条件是否满足，供业务分支或权限控制使用。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public static boolean isValid(String id) {
        return LuhnMod10Utils.validate(id);
    }
}
