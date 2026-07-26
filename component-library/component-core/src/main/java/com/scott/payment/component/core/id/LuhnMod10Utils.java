package com.scott.payment.component.core.id;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;


/**
 * @author : scott
 * @version : v1.0.0
 * @classname : LuhnMod10Utils
 * @date : 2026-06-25 10:37
 * @email : scott_x@163.com
 * @description : Luhn Mod 10 Utils 通用函数集合，位于 公共组件库，封装格式化、校验、脱敏、加密、编码或标准化逻辑，调用方以静态方法获取本地计算结果。
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
