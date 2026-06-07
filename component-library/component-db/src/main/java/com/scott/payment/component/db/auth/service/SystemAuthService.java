package com.scott.payment.component.db.auth.service;

import com.scott.payment.component.db.auth.dto.AuthAccountDTO;
import com.scott.payment.component.db.auth.dto.AuthLoginRequest;
import com.scott.payment.component.db.auth.dto.AuthLoginResponse;
import com.scott.payment.component.db.auth.dto.AuthRegisterRequest;
import com.scott.payment.component.db.auth.dto.AuthVerifyCodeSendRequest;
import com.scott.payment.component.db.auth.dto.AuthVerifyCodeSendResponse;
import com.scott.payment.component.core.auth.InternalAuthChecker;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SystemAuthService
 * @date : 2026-06-06 00:00
 * @email : scott_x@163.com
 * @description : 管理类系统登录注册与权限服务
 * @status : create
 */
public interface SystemAuthService extends InternalAuthChecker {

    /**
     * 注册系统账号。
     *
     * @param appCode 系统应用编码
     * @param request 注册请求
     * @return 注册后的账号信息
     */
    AuthAccountDTO register(String appCode, AuthRegisterRequest request);

    /**
     * 发送登录动态验证码。
     *
     * @param appCode   系统应用编码
     * @param request   验证码发送请求
     * @param clientIp  客户端IP
     * @return 验证码发送响应
     */
    AuthVerifyCodeSendResponse sendLoginVerifyCode(String appCode, AuthVerifyCodeSendRequest request, String clientIp);

    /**
     * 登录系统账号。
     *
     * @param appCode   系统应用编码
     * @param request   登录请求
     * @param clientIp  客户端IP
     * @param userAgent 客户端 User-Agent
     * @return 登录响应
     */
    AuthLoginResponse login(String appCode, AuthLoginRequest request, String clientIp, String userAgent);

    /**
     * 根据 token 查询当前登录账号。
     *
     * @param appCode 系统应用编码
     * @param token   登录 token
     * @return 当前账号和权限信息
     */
    AuthLoginResponse currentUser(String appCode, String token);

    /**
     * 退出登录。
     *
     * @param appCode 系统应用编码
     * @param token   登录 token
     */
    void logout(String appCode, String token);
}
