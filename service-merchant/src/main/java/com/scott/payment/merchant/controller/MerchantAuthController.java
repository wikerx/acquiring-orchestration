package com.scott.payment.merchant.controller;

import com.scott.payment.component.core.model.CommonResult;
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
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import com.scott.payment.merchant.application.auth.MerchantAuthApplicationService;
import com.scott.payment.merchant.application.auth.MerchantEmailVerificationApplicationService;
import com.scott.payment.merchant.application.auth.MerchantPasswordResetApplicationService;
import com.scott.payment.merchant.dto.MerchantDefaultLoginCredentialDTO;
import com.scott.payment.merchant.dto.auth.MerchantEmailVerificationDTOs.AccountActivationCompleteRequest;
import com.scott.payment.merchant.dto.auth.MerchantEmailVerificationDTOs.AccountActivationCompleteResponse;
import com.scott.payment.merchant.dto.auth.MerchantEmailVerificationDTOs.EmailVerificationVerifyRequest;
import com.scott.payment.merchant.dto.auth.MerchantEmailVerificationDTOs.EmailVerificationVerifyResponse;
import com.scott.payment.merchant.dto.auth.MerchantPasswordResetDTOs.PasswordResetConfirmRequest;
import com.scott.payment.merchant.dto.auth.MerchantPasswordResetDTOs.PasswordResetConfirmResponse;
import com.scott.payment.merchant.dto.auth.MerchantPasswordResetDTOs.PasswordResetEmailVerifyRequest;
import com.scott.payment.merchant.dto.auth.MerchantPasswordResetDTOs.PasswordResetMfaVerifyRequest;
import com.scott.payment.merchant.dto.auth.MerchantPasswordResetDTOs.PasswordResetRequest;
import com.scott.payment.merchant.dto.auth.MerchantPasswordResetDTOs.PasswordResetRequestResponse;
import com.scott.payment.merchant.dto.auth.MerchantPasswordResetDTOs.PasswordResetVerifyResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantAuthController
 * @date : 2026-06-06 00:00
 * @email : scott_x@163.com
 * @description : 商户管理系统登录注册与权限接口
 * @status : create
 */
@RestController
@RequestMapping("/merchant/auth")
public class MerchantAuthController {

    /**
     * 商户认证应用服务。
     */
    private final MerchantAuthApplicationService merchantAuthApplicationService;
    private final MerchantPasswordResetApplicationService passwordResetApplicationService;
    private final MerchantEmailVerificationApplicationService emailVerificationApplicationService;

    /**
     * 创建商户管理系统登录权限接口。
     *
     * @param merchantAuthApplicationService 商户认证应用服务
     */
    public MerchantAuthController(MerchantAuthApplicationService merchantAuthApplicationService,
                                  MerchantPasswordResetApplicationService passwordResetApplicationService,
                                  MerchantEmailVerificationApplicationService emailVerificationApplicationService) {
        this.merchantAuthApplicationService = merchantAuthApplicationService;
        this.passwordResetApplicationService = passwordResetApplicationService;
        this.emailVerificationApplicationService = emailVerificationApplicationService;
    }

    /** 校验商户账号激活或邮箱变更邮件中的一次性验证码。 */
    @PostMapping("/email-verification/verify")
    public CommonResult<EmailVerificationVerifyResponse> verifyEmail(
            @Valid @RequestBody EmailVerificationVerifyRequest request,
            HttpServletRequest servletRequest) {
        return success(emailVerificationApplicationService.verify(request, servletRequest));
    }

    /** 原子消费短期动作令牌，完成商户新账号首次设密和激活。 */
    @PostMapping("/account-activation/complete")
    public CommonResult<AccountActivationCompleteResponse> completeAccountActivation(
            @Valid @RequestBody AccountActivationCompleteRequest request,
            HttpServletRequest servletRequest) {
        return success(emailVerificationApplicationService.completeActivation(request, servletRequest));
    }

    /** 创建找回密码请求；账号是否存在不会改变响应结构。 */
    @PostMapping("/password-reset/request")
    public CommonResult<PasswordResetRequestResponse> requestPasswordReset(
            @Valid @RequestBody PasswordResetRequest request,
            HttpServletRequest servletRequest) {
        return success(passwordResetApplicationService.requestReset(request, servletRequest));
    }

    /** 校验找回密码邮件验证码。 */
    @PostMapping("/password-reset/email/verify")
    public CommonResult<PasswordResetVerifyResponse> verifyPasswordResetEmail(
            @Valid @RequestBody PasswordResetEmailVerifyRequest request,
            HttpServletRequest servletRequest) {
        return success(passwordResetApplicationService.verifyEmail(request, servletRequest));
    }

    /** 校验找回密码 MFA 动态验证码。 */
    @PostMapping("/password-reset/mfa/verify")
    public CommonResult<PasswordResetVerifyResponse> verifyPasswordResetMfa(
            @Valid @RequestBody PasswordResetMfaVerifyRequest request,
            HttpServletRequest servletRequest) {
        return success(passwordResetApplicationService.verifyMfa(request, servletRequest));
    }

    /** 原子消费重置令牌并设置新密码。 */
    @PostMapping("/password-reset/confirm")
    public CommonResult<PasswordResetConfirmResponse> confirmPasswordReset(
            @Valid @RequestBody PasswordResetConfirmRequest request,
            HttpServletRequest servletRequest) {
        return success(passwordResetApplicationService.confirm(request, servletRequest));
    }

    /**
     * 注册商户系统账号。
     *
     * @param request 注册请求
     * @return 注册后的账号信息
     */
    @PostMapping("/register")
    @RequiresPermission("merchant:account:create")
    @OperationLog(moduleName = "商户登录权限", businessType = OperationTypeConstants.CREATE,
            operation = "注册商户系统账号", recordRequest = false, recordResponse = false)
    public CommonResult<AuthAccountDTO> register(@Valid @RequestBody AuthRegisterRequest request) {
        return success(merchantAuthApplicationService.register(request));
    }

    /**
     * 发送商户系统登录图形验证码。
     *
     * @param request 验证码发送请求
     * @param servletRequest Servlet 请求
     * @return 验证码发送响应
     */
    @PostMapping("/verify-code/send")
    public CommonResult<AuthVerifyCodeSendResponse> sendVerifyCode(@Valid @RequestBody AuthVerifyCodeSendRequest request,
                                                                   HttpServletRequest servletRequest) {
        return success(merchantAuthApplicationService.sendVerifyCode(request, servletRequest));
    }

    /**
     * 查询商户登录页本地开发默认凭据。
     *
     * @return 默认商户号、账号和本地初始密码
     */
    @GetMapping("/default-login-credential")
    public CommonResult<MerchantDefaultLoginCredentialDTO> defaultLoginCredential() {
        return success(merchantAuthApplicationService.defaultLoginCredential());
    }

    /**
     * 商户系统账号登录。
     *
     * @param request 登录请求
     * @param servletRequest Servlet 请求
     * @return 登录响应
     */
    @PostMapping("/login")
    public CommonResult<AuthLoginResponse> login(@Valid @RequestBody AuthLoginRequest request,
                                                 HttpServletRequest servletRequest) {
        return success(merchantAuthApplicationService.login(request, servletRequest));
    }

    /**
     * 获取商户系统 OTP 绑定信息。
     *
     * @param loginTicket 短期登录票据
     * @return OTP 绑定信息
     */
    @GetMapping("/mfa/bind-info")
    public CommonResult<AuthMfaBindInfoResponse> mfaBindInfo(@RequestParam("loginTicket") String loginTicket) {
        return success(merchantAuthApplicationService.mfaBindInfo(loginTicket));
    }

    /**
     * 确认商户系统 OTP 绑定。
     *
     * @param request        绑定确认请求
     * @param servletRequest Servlet 请求
     * @return 登录响应
     */
    @PostMapping("/mfa/bind-confirm")
    public CommonResult<AuthLoginResponse> mfaBindConfirm(@Valid @RequestBody AuthMfaBindConfirmRequest request,
                                                          HttpServletRequest servletRequest) {
        return success(merchantAuthApplicationService.mfaBindConfirm(request, servletRequest));
    }

    /**
     * 验证商户系统 OTP。
     *
     * @param request        OTP 验证请求
     * @param servletRequest Servlet 请求
     * @return 登录响应
     */
    @PostMapping("/mfa/verify")
    public CommonResult<AuthLoginResponse> mfaVerify(@Valid @RequestBody AuthMfaVerifyRequest request,
                                                     HttpServletRequest servletRequest) {
        return success(merchantAuthApplicationService.mfaVerify(request, servletRequest));
    }

    /**
     * 查询当前商户登录账号、菜单和权限。
     * <p>
     * 该接口只依赖统一登录态拦截，不绑定具体业务菜单权限，否则无首页权限的合法账号
     * 无法恢复自己的会话与权限集合。
     * </p>
     *
     * @param authorization Authorization 请求头
     * @return 当前登录账号、菜单和权限
    */
    @GetMapping("/me")
    public CommonResult<AuthLoginResponse> me(@RequestHeader("Authorization") String authorization) {
        return success(merchantAuthApplicationService.currentUser(authorization));
    }

    /**
     * 更新当前商户登录账号个人资料。
     *
     * @param authorization Authorization 请求头
     * @param request       个人资料更新请求
     * @return 更新后的当前商户登录账号、菜单和权限
     */
    @PostMapping("/profile")
    @OperationLog(moduleName = "商户个人中心", businessType = OperationTypeConstants.UPDATE,
            operation = "更新商户个人资料", recordRequest = false, recordResponse = false)
    public CommonResult<AuthLoginResponse> updateProfile(@RequestHeader("Authorization") String authorization,
                                                         @Valid @RequestBody AuthProfileUpdateRequest request) {
        return success(merchantAuthApplicationService.updateCurrentProfile(authorization, request));
    }

    /**
     * 修改当前商户登录账号密码。
     *
     * @param authorization Authorization 请求头
     * @param request       修改密码请求
     * @return 空响应
     */
    @PostMapping("/password/change")
    @OperationLog(moduleName = "商户个人中心", businessType = OperationTypeConstants.UPDATE,
            operation = "修改商户登录密码", recordRequest = false, recordResponse = false)
    public CommonResult<Void> changePassword(@RequestHeader("Authorization") String authorization,
                                             @Valid @RequestBody AuthPasswordChangeRequest request) {
        merchantAuthApplicationService.changeCurrentPassword(authorization, request);
        return success();
    }

    /**
     * 退出登录。
     * <p>
     * 任意有效商户登录态都必须能够主动退出，不依赖首页或其他业务权限。
     * </p>
     *
     * @param authorization Authorization 请求头
     * @return 空响应
    */
    @PostMapping("/logout")
    @OperationLog(moduleName = "商户登录权限", businessType = OperationTypeConstants.UPDATE,
            operation = "商户系统账号退出登录", recordRequest = false, recordResponse = false)
    public CommonResult<Void> logout(@RequestHeader("Authorization") String authorization) {
        merchantAuthApplicationService.logout(authorization);
        return success();
    }
}
