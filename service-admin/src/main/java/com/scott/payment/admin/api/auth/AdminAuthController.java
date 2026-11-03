package com.scott.payment.admin.api.auth;

import com.scott.payment.admin.application.auth.AdminAuthApplicationService;
import com.scott.payment.admin.application.auth.AdminEmailVerificationApplicationService;
import com.scott.payment.admin.application.auth.AdminPasswordResetApplicationService;
import com.scott.payment.admin.dto.auth.AdminEmailVerificationDTOs.AccountActivationCompleteRequest;
import com.scott.payment.admin.dto.auth.AdminEmailVerificationDTOs.AccountActivationCompleteResponse;
import com.scott.payment.admin.dto.auth.AdminEmailVerificationDTOs.EmailVerificationVerifyRequest;
import com.scott.payment.admin.dto.auth.AdminEmailVerificationDTOs.EmailVerificationVerifyResponse;
import com.scott.payment.admin.dto.auth.AdminPasswordResetDTOs.PasswordResetConfirmRequest;
import com.scott.payment.admin.dto.auth.AdminPasswordResetDTOs.PasswordResetConfirmResponse;
import com.scott.payment.admin.dto.auth.AdminPasswordResetDTOs.PasswordResetEmailVerifyRequest;
import com.scott.payment.admin.dto.auth.AdminPasswordResetDTOs.PasswordResetMfaVerifyRequest;
import com.scott.payment.admin.dto.auth.AdminPasswordResetDTOs.PasswordResetRequest;
import com.scott.payment.admin.dto.auth.AdminPasswordResetDTOs.PasswordResetRequestResponse;
import com.scott.payment.admin.dto.auth.AdminPasswordResetDTOs.PasswordResetVerifyResponse;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.db.auth.dto.AuthAccountDTO;
import com.scott.payment.component.db.auth.dto.AuthLoginRequest;
import com.scott.payment.component.db.auth.dto.AuthLoginResponse;
import com.scott.payment.component.db.auth.constant.AuthConstants;
import com.scott.payment.component.web.auth.BrowserSessionCookieSupport;
import com.scott.payment.component.db.auth.dto.AuthMfaBindConfirmRequest;
import com.scott.payment.component.db.auth.dto.AuthMfaBindInfoResponse;
import com.scott.payment.component.db.auth.dto.AuthMfaVerifyRequest;
import com.scott.payment.component.db.auth.dto.AuthPasswordChangeRequest;
import com.scott.payment.component.db.auth.dto.AuthProfileUpdateRequest;
import com.scott.payment.component.db.auth.dto.AuthRegisterRequest;
import com.scott.payment.component.db.auth.dto.AuthVerifyCodeSendRequest;
import com.scott.payment.component.db.auth.dto.AuthVerifyCodeSendResponse;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * 管理后台登录注册与权限接口
 */
@RestController
@RequestMapping("/admin/auth")
public class AdminAuthController {

    /**
     * 后台认证应用服务。
     */
    private final AdminAuthApplicationService adminAuthApplicationService;

    /**
     * 找回密码应用服务。
     */
    private final AdminPasswordResetApplicationService passwordResetApplicationService;
    /**
     * 账号激活和邮箱验证应用服务。
     */
    private final AdminEmailVerificationApplicationService emailVerificationApplicationService;

    /**
     * 创建管理后台登录权限接口。
     *
     * @param adminAuthApplicationService 后台认证应用服务
     */
    public AdminAuthController(AdminAuthApplicationService adminAuthApplicationService,
                               AdminPasswordResetApplicationService passwordResetApplicationService,
                               AdminEmailVerificationApplicationService emailVerificationApplicationService) {
        this.adminAuthApplicationService = adminAuthApplicationService;
        this.passwordResetApplicationService = passwordResetApplicationService;
        this.emailVerificationApplicationService = emailVerificationApplicationService;
    }

    /**
     * 校验账号激活或邮箱变更邮件中的一次性验证码。
     */
    @PostMapping("/email-verification/verify")
    public CommonResult<EmailVerificationVerifyResponse> verifyEmail(
            @Valid @RequestBody EmailVerificationVerifyRequest request,
            HttpServletRequest servletRequest) {
        return success(emailVerificationApplicationService.verify(request, servletRequest));
    }

    /**
     * 原子消费短期动作令牌，完成新账号首次设密和激活。
     */
    @PostMapping("/account-activation/complete")
    public CommonResult<AccountActivationCompleteResponse> completeAccountActivation(
            @Valid @RequestBody AccountActivationCompleteRequest request,
            HttpServletRequest servletRequest) {
        return success(emailVerificationApplicationService.completeActivation(request, servletRequest));
    }

    /**
     * 创建找回密码请求；无论账号是否存在均返回相同结构。
     */
    @PostMapping("/password-reset/request")
    public CommonResult<PasswordResetRequestResponse> requestPasswordReset(
            @Valid @RequestBody PasswordResetRequest request,
            HttpServletRequest servletRequest) {
        return success(passwordResetApplicationService.requestReset(request, servletRequest));
    }

    /**
     * 校验找回密码邮件验证码。
     */
    @PostMapping("/password-reset/email/verify")
    public CommonResult<PasswordResetVerifyResponse> verifyPasswordResetEmail(
            @Valid @RequestBody PasswordResetEmailVerifyRequest request,
            HttpServletRequest servletRequest) {
        return success(passwordResetApplicationService.verifyEmail(request, servletRequest));
    }

    /**
     * 校验找回密码 MFA 动态验证码。
     */
    @PostMapping("/password-reset/mfa/verify")
    public CommonResult<PasswordResetVerifyResponse> verifyPasswordResetMfa(
            @Valid @RequestBody PasswordResetMfaVerifyRequest request,
            HttpServletRequest servletRequest) {
        return success(passwordResetApplicationService.verifyMfa(request, servletRequest));
    }

    /**
     * 原子消费重置令牌并设置新密码。
     */
    @PostMapping("/password-reset/confirm")
    public CommonResult<PasswordResetConfirmResponse> confirmPasswordReset(
            @Valid @RequestBody PasswordResetConfirmRequest request,
            HttpServletRequest servletRequest) {
        return success(passwordResetApplicationService.confirm(request, servletRequest));
    }

    /**
     * 注册管理后台账号。
     *
     * @param request 注册请求
     * @return 注册后的账号信息
     */
    @PostMapping("/register")
    @RequiresPermission("system:user:add")
    @OperationLog(moduleName = "后台登录权限", businessType = OperationTypeConstants.CREATE,
            operation = "注册管理后台账号", recordRequest = false, recordResponse = false)
    public CommonResult<AuthAccountDTO> register(@Valid @RequestBody AuthRegisterRequest request) {
        return success(adminAuthApplicationService.register(request));
    }

    /**
     * 发送管理后台登录图形验证码。
     *
     * @param request 验证码发送请求
     * @param servletRequest Servlet 请求
     * @return 验证码发送响应
     */
    @PostMapping("/verify-code/send")
    public CommonResult<AuthVerifyCodeSendResponse> sendVerifyCode(@Valid @RequestBody AuthVerifyCodeSendRequest request,
                                                                   HttpServletRequest servletRequest) {
        return success(adminAuthApplicationService.sendVerifyCode(request, servletRequest));
    }

    /**
     * 管理后台账号登录。
     *
     * @param request 登录请求
     * @param servletRequest Servlet 请求
     * @param servletResponse Servlet 响应
     * @return 登录响应
     */
    @PostMapping("/login")
    public CommonResult<AuthLoginResponse> login(@Valid @RequestBody AuthLoginRequest request,
                                                 HttpServletRequest servletRequest,
                                                 HttpServletResponse servletResponse) {
        return success(completeBrowserLogin(
                adminAuthApplicationService.login(request, servletRequest), servletRequest, servletResponse));
    }

    /**
     * 获取管理后台 OTP 绑定信息。
     *
     * @param loginTicket 短期登录票据
     * @return OTP 绑定信息
     */
    @GetMapping("/mfa/bind-info")
    public CommonResult<AuthMfaBindInfoResponse> mfaBindInfo(@RequestParam("loginTicket") String loginTicket) {
        return success(adminAuthApplicationService.mfaBindInfo(loginTicket));
    }

    /**
     * 确认管理后台 OTP 绑定。
     *
     * @param request        绑定确认请求
     * @param servletRequest Servlet 请求
     * @param servletResponse Servlet 响应
     * @return 登录响应
     */
    @PostMapping("/mfa/bind-confirm")
    public CommonResult<AuthLoginResponse> mfaBindConfirm(@Valid @RequestBody AuthMfaBindConfirmRequest request,
                                                          HttpServletRequest servletRequest,
                                                          HttpServletResponse servletResponse) {
        return success(completeBrowserLogin(
                adminAuthApplicationService.mfaBindConfirm(request, servletRequest),
                servletRequest,
                servletResponse));
    }

    /**
     * 验证管理后台 OTP。
     *
     * @param request        OTP 验证请求
     * @param servletRequest Servlet 请求
     * @param servletResponse Servlet 响应
     * @return 登录响应
     */
    @PostMapping("/mfa/verify")
    public CommonResult<AuthLoginResponse> mfaVerify(@Valid @RequestBody AuthMfaVerifyRequest request,
                                                     HttpServletRequest servletRequest,
                                                     HttpServletResponse servletResponse) {
        return success(completeBrowserLogin(
                adminAuthApplicationService.mfaVerify(request, servletRequest),
                servletRequest,
                servletResponse));
    }

    /**
     * 查询当前登录账号、菜单和权限。
     *
     * @param servletRequest Servlet 请求
     * @return 当前登录账号、菜单和权限
     */
    @GetMapping("/me")
    public CommonResult<AuthLoginResponse> me(HttpServletRequest servletRequest) {
        return success(adminAuthApplicationService.currentUser(resolveAuthorization(servletRequest)));
    }

    /**
     * 更新当前后台登录账号个人资料。
     *
     * @param servletRequest Servlet 请求
     * @param request       个人资料更新请求
     * @return 更新后的当前登录账号、菜单和权限
     */
    @PostMapping("/profile")
    @OperationLog(moduleName = "后台个人中心", businessType = OperationTypeConstants.UPDATE,
            operation = "更新后台个人资料", recordRequest = false, recordResponse = false)
    public CommonResult<AuthLoginResponse> updateProfile(HttpServletRequest servletRequest,
                                                         @Valid @RequestBody AuthProfileUpdateRequest request) {
        return success(adminAuthApplicationService.updateCurrentProfile(resolveAuthorization(servletRequest), request));
    }

    /**
     * 修改当前后台登录账号密码。
     *
     * @param servletRequest Servlet 请求
     * @param request       修改密码请求
     * @return 空响应
     */
    @PostMapping("/password/change")
    @OperationLog(moduleName = "后台个人中心", businessType = OperationTypeConstants.UPDATE,
            operation = "修改后台登录密码", recordRequest = false, recordResponse = false)
    public CommonResult<Void> changePassword(HttpServletRequest servletRequest,
                                             @Valid @RequestBody AuthPasswordChangeRequest request) {
        adminAuthApplicationService.changeCurrentPassword(resolveAuthorization(servletRequest), request);
        return success();
    }

    /**
     * 退出登录。
     *
     * @param servletRequest Servlet 请求
     * @param servletResponse Servlet 响应
     * @return 空响应
     */
    @PostMapping("/logout")
    @OperationLog(moduleName = "后台登录权限", businessType = OperationTypeConstants.UPDATE,
            operation = "管理后台账号退出登录", recordRequest = false, recordResponse = false)
    public CommonResult<Void> logout(HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
        adminAuthApplicationService.logout(resolveAuthorization(servletRequest));
        BrowserSessionCookieSupport.clearBrowserSession(
                AuthConstants.APP_ADMIN, servletRequest, servletResponse);
        return success();
    }

    /**
     * 在浏览器 Cookie 模式下签发 HttpOnly 会话，并从 JSON 响应移除令牌。
     *
     * @param loginResponse 登录响应
     * @param request Servlet 请求
     * @param response Servlet 响应
     * @return 适合当前调用模式的登录响应
     */
    private AuthLoginResponse completeBrowserLogin(AuthLoginResponse loginResponse,
                                                   HttpServletRequest request,
                                                   HttpServletResponse response) {
        if (loginResponse != null && BrowserSessionCookieSupport.issueBrowserSession(
                AuthConstants.APP_ADMIN,
                loginResponse.getAccessToken(),
                loginResponse.getExpiresIn(),
                request,
                response)) {
            loginResponse.setAccessToken(null);
            loginResponse.setTokenType("Cookie");
        }
        return loginResponse;
    }

    /**
     * 从显式 Bearer 请求头或管理后台 HttpOnly Cookie 解析认证凭据。
     *
     * @param request Servlet 请求
     * @return Bearer 认证值
     */
    private String resolveAuthorization(HttpServletRequest request) {
        return BrowserSessionCookieSupport.resolveAuthorization(AuthConstants.APP_ADMIN, request);
    }
}
