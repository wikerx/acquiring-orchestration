package com.scott.payment.component.core.id;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : LuhnMod10Utils
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Luhn Mod10 工具，位于 component-library/component-core 的业务组件层，用于说明职责边界、数据语义和关键业务约束。
 * @status : create
 */
public final class LuhnMod10Utils {

    private LuhnMod10Utils() {
    }

    /**
     * 根据 21 位数字正文计算 Luhn 校验位。
     *
     * @param body 不含校验位的 21 位数字字符串
     * @return 校验位，范围 0-9
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param body 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public static int calculateCheckDigit(String body) {
        if (!isDigits(body) || body.length() != GlobalIdConstants.BODY_LENGTH) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "全局唯一标识正文格式非法");
        }
        int sum = 0;
        boolean doubleDigit = true;
        for (int index = body.length() - 1; index >= 0; index--) {
            int value = body.charAt(index) - '0';
            if (doubleDigit) {
                value *= 2;
                if (value > 9) {
                    value -= 9;
                }
            }
            sum += value;
            doubleDigit = !doubleDigit;
        }
        return (10 - (sum % 10)) % 10;
    }

    /**
     * 校验完整数字字符串是否符合 Luhn 规则。
     *
     * @param fullNumber 完整数字字符串
     * @return true=校验通过，false=校验失败
     */
    /**
     * 校验收单支付业务规则，发现不符合要求的数据时抛出业务异常。
     * @param fullNumber 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public static boolean validate(String fullNumber) {
        if (!isDigits(fullNumber) || fullNumber.length() != GlobalIdConstants.ID_LENGTH) {
            return false;
        }
        String body = fullNumber.substring(0, GlobalIdConstants.BODY_LENGTH);
        int expected = calculateCheckDigit(body);
        int actual = fullNumber.charAt(GlobalIdConstants.BODY_LENGTH) - '0';
        return expected == actual;
    }

    /**
     * 判断字符串是否为非空纯数字。
     *
     * @param value 待判断字符串
     * @return true=非空纯数字
     */
    private static boolean isDigits(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            if (!Character.isDigit(value.charAt(index))) {
                return false;
            }
        }
        return true;
    }
}
