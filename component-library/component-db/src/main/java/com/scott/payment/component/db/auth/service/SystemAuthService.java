package com.scott.payment.component.db.auth.service;

import com.scott.payment.component.db.auth.dto.AuthAccountDTO;
import com.scott.payment.component.db.auth.dto.AuthLoginRequest;
import com.scott.payment.component.db.auth.dto.AuthLoginResponse;
import com.scott.payment.component.db.auth.dto.AuthMfaBindConfirmRequest;
import com.scott.payment.component.db.auth.dto.AuthMfaBindInfoResponse;
import com.scott.payment.component.db.auth.dto.AuthMfaVerifyRequest;
import com.scott.payment.component.db.auth.dto.AuthPasswordChangeRequest;
import com.scott.payment.component.db.auth.dto.AuthProfileUpdateRequest;
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
     * 生成登录页图形验证码。
     *
     * @param appCode   系统应用编码
     * @param request   验证码生成请求，登录页图形验证码不依赖账号
     * @param clientIp  客户端IP
     * @return 验证码图片、验证码记录ID和有效期
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
     * 获取 OTP 绑定信息。
     *
     * @param appCode     系统应用编码
     * @param loginTicket 短期登录票据
     * @return OTP 绑定二维码 URI 和账号信息
     */
    AuthMfaBindInfoResponse mfaBindInfo(String appCode, String loginTicket);

    /**
     * 确认 OTP 绑定并签发登录会话。
     *
     * @param appCode   系统应用编码
     * @param request   绑定确认请求
     * @param clientIp  客户端IP
     * @param userAgent 客户端 User-Agent
     * @return 登录响应
     */
    AuthLoginResponse mfaBindConfirm(String appCode, AuthMfaBindConfirmRequest request, String clientIp, String userAgent);

    /**
     * 验证 OTP 并签发登录会话。
     *
     * @param appCode   系统应用编码
     * @param request   OTP 验证请求
     * @param clientIp  客户端IP
     * @param userAgent 客户端 User-Agent
     * @return 登录响应
     */
    AuthLoginResponse mfaVerify(String appCode, AuthMfaVerifyRequest request, String clientIp, String userAgent);

    /**
     * 根据 token 查询当前登录账号。
     *
     * @param appCode 系统应用编码
     * @param token   登录 token
     * @return 当前账号和权限信息
     */
    AuthLoginResponse currentUser(String appCode, String token);

    /**
     * 更新当前登录账号个人资料。
     *
     * @param appCode 系统应用编码
     * @param token   登录 token
     * @param request 个人资料更新请求
     * @return 更新后的账号、菜单和权限信息
     */
    AuthLoginResponse updateCurrentProfile(String appCode, String token, AuthProfileUpdateRequest request);

    /**
     * 修改当前登录账号密码。
     *
     * @param appCode 系统应用编码
     * @param token   登录 token
     * @param request 修改密码请求
     */
    void changeCurrentPassword(String appCode, String token, AuthPasswordChangeRequest request);

    /**
     * 退出登录。
     *
     * @param appCode 系统应用编码
     * @param token   登录 token
     */
    void logout(String appCode, String token);
}
