package com.scott.payment.component.core.auth;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : InternalAuthChecker
 * @date : 2026-06-06 00:00
 * @email : scott_x@163.com
 * @description : 内部管理类接口 token 与资源权限校验器
 * @status : create
 */
public interface InternalAuthChecker {

    /**
     * 校验当前请求是否允许访问。
     *
     * @param appCode       应用编码，例如 ADMIN、MERCHANT
     * @param authorization Authorization 请求头
     * @param requestMethod HTTP 请求方法
     * @param requestPath   请求路径
     * @return 当前登录账号上下文
     */
    InternalAuthAccount check(String appCode, String authorization, String requestMethod, String requestPath);
}
