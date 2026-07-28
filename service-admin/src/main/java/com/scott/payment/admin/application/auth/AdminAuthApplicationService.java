package com.scott.payment.admin.application.auth;

import com.scott.payment.component.db.auth.constant.AuthConstants;
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
import com.scott.payment.component.db.auth.service.SystemAuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminAuthApplicationService
 * @date : 2026-06-19 21:28
 * @email : scott_x@163.com
 * @description : 管理后台认证应用服务
 * @status : create
 *
 * <p>负责管理后台认证相关用例编排，统一收敛注册、验证码发送、登录、当前用户查询和退出登录入口，
 * 便于后续在应用层继续补充设备识别、审计留痕和登录风控扩展。</p>
 */
@Service
public class AdminAuthApplicationService {

    /**
     * 后台鉴权能力。
     */
    private final SystemAuthService systemAuthService;

    /**
     * 创建管理后台认证应用服务。
     *
     * @param systemAuthService 后台鉴权能力
     */
    public AdminAuthApplicationService(SystemAuthService systemAuthService) {
        this.systemAuthService = systemAuthService;
    }

    /**
     * 注册管理后台账号。
     *
     * @param request 注册请求
     * @return 注册后的账号信息
     */
    public AuthAccountDTO register(AuthRegisterRequest request) {
        return systemAuthService.register(AuthConstants.APP_ADMIN, request);
    }

    /**
     * 发送管理后台登录图形验证码。
     *
     * @param request        验证码发送请求
     * @param servletRequest Servlet 请求
     * @return 验证码发送响应
     */
    public AuthVerifyCodeSendResponse sendVerifyCode(AuthVerifyCodeSendRequest request, HttpServletRequest servletRequest) {
        return systemAuthService.sendLoginVerifyCode(
                AuthConstants.APP_ADMIN,
                request,
                clientIp(servletRequest)
        );
    }

    /**
     * 管理后台账号登录。
     *
     * @param request        登录请求
     * @param servletRequest Servlet 请求
     * @return 登录响应
     */
    public AuthLoginResponse login(AuthLoginRequest request, HttpServletRequest servletRequest) {
        return systemAuthService.login(
                AuthConstants.APP_ADMIN,
                request,
                clientIp(servletRequest),
                servletRequest.getHeader("User-Agent")
        );
    }

    /**
     * 查询管理后台 OTP 绑定信息。
     *
     * @param loginTicket 短期登录票据
     * @return OTP 绑定信息
     */
    public AuthMfaBindInfoResponse mfaBindInfo(String loginTicket) {
        return systemAuthService.mfaBindInfo(AuthConstants.APP_ADMIN, loginTicket);
    }

    /**
     * 确认管理后台 OTP 绑定并完成登录。
     *
     * @param request        绑定确认请求
     * @param servletRequest Servlet 请求
     * @return 登录响应
     */
    public AuthLoginResponse mfaBindConfirm(AuthMfaBindConfirmRequest request, HttpServletRequest servletRequest) {
        return systemAuthService.mfaBindConfirm(
                AuthConstants.APP_ADMIN,
                request,
                clientIp(servletRequest),
                servletRequest.getHeader("User-Agent")
        );
    }

    /**
     * 验证管理后台 OTP 并完成登录。
     *
     * @param request        OTP 验证请求
     * @param servletRequest Servlet 请求
     * @return 登录响应
     */
    public AuthLoginResponse mfaVerify(AuthMfaVerifyRequest request, HttpServletRequest servletRequest) {
        return systemAuthService.mfaVerify(
                AuthConstants.APP_ADMIN,
                request,
                clientIp(servletRequest),
                servletRequest.getHeader("User-Agent")
        );
    }

    /**
     * 查询当前登录账号、菜单和权限。
     *
     * @param authorization Authorization 请求头
     * @return 当前登录账号、菜单和权限
     */
    public AuthLoginResponse currentUser(String authorization) {
        return systemAuthService.currentUser(AuthConstants.APP_ADMIN, authorization);
    }

    /**
     * 更新当前后台登录账号个人资料。
     *
     * @param authorization Authorization 请求头
     * @param request       个人资料更新请求
     * @return 更新后的当前登录账号、菜单和权限
     */
    public AuthLoginResponse updateCurrentProfile(String authorization, AuthProfileUpdateRequest request) {
        return systemAuthService.updateCurrentProfile(AuthConstants.APP_ADMIN, authorization, request);
    }

    /**
     * 修改当前后台登录账号密码。
     *
     * @param authorization Authorization 请求头
     * @param request       修改密码请求
     */
    public void changeCurrentPassword(String authorization, AuthPasswordChangeRequest request) {
        systemAuthService.changeCurrentPassword(AuthConstants.APP_ADMIN, authorization, request);
    }

    /**
     * 退出后台登录。
     *
     * @param authorization Authorization 请求头
     */
    public void logout(String authorization) {
        systemAuthService.logout(AuthConstants.APP_ADMIN, authorization);
    }

    /**
     * 获取客户端 IP。
     *
     * @param request Servlet 请求
     * @return 客户端 IP
     */
    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
