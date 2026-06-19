package com.scott.payment.admin.application.auth;

import com.scott.payment.component.db.auth.constant.AuthConstants;
import com.scott.payment.component.db.auth.dto.AuthAccountDTO;
import com.scott.payment.component.db.auth.dto.AuthLoginRequest;
import com.scott.payment.component.db.auth.dto.AuthLoginResponse;
import com.scott.payment.component.db.auth.dto.AuthRegisterRequest;
import com.scott.payment.component.db.auth.dto.AuthVerifyCodeSendRequest;
import com.scott.payment.component.db.auth.dto.AuthVerifyCodeSendResponse;
import com.scott.payment.component.db.auth.service.SystemAuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/**
 * 管理后台认证应用服务。
 * <p>
 * 当前负责收敛后台认证入口的应用层编排，后续可继续在这里补充设备信息、审计和风控扩展。
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
     * 发送管理后台登录动态验证码。
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
     * 查询当前登录账号、菜单和权限。
     *
     * @param authorization Authorization 请求头
     * @return 当前登录账号、菜单和权限
     */
    public AuthLoginResponse currentUser(String authorization) {
        return systemAuthService.currentUser(AuthConstants.APP_ADMIN, authorization);
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
