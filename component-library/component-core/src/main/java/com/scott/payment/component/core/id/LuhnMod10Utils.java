package com.scott.payment.component.core.id;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;


/**
 * @author : scott
 * @version : v1.0.0
 * @classname : LuhnMod10Utils
 * @date : 2026-06-25 10:37
 * @email : scott_x@163.com
 * @description : LuhnMod10Utils 通用能力封装，用于提供无状态的格式转换、校验或安全处理函数，位于 公共组件层，输入输出边界由所在包和公开方法契约限定。
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
