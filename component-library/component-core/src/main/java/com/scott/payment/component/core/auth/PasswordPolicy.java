package com.scott.payment.component.core.auth;

import java.util.regex.Pattern;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PasswordPolicy
 * @date : 2026-10-15 00:00
 * @email : scott_x@163.com
 * @description : 登录密码共享安全策略；统一约束长度和字符类别，避免各认证入口采用不同强度规则。
 * @status : create
 */
public final class PasswordPolicy {

    /** 新密码最小长度，按当前 PCI 管理要求固定为 12 位。 */
    public static final int MIN_LENGTH = 12;
    /** 新密码最大长度，避免截断和异常大的哈希输入。 */
    public static final int MAX_LENGTH = 64;
    /** 必须同时包含大小写字母、数字和特殊字符，且不允许空白字符。 */
    public static final String REGEX = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s])\\S{12,64}$";
    /** Bean Validation 和服务层共享的编译后模式。 */
    private static final Pattern PATTERN = Pattern.compile(REGEX);

    private PasswordPolicy() {
    }

    /**
     * 校验候选新密码是否满足统一规则。
     *
     * @param password 候选密码明文，仅用于当前请求内校验，禁止记录日志
     * @return 满足 12-64 位及四类字符要求时返回 true
     */
    public static boolean isValid(String password) {
        return password != null && PATTERN.matcher(password).matches();
    }
}
