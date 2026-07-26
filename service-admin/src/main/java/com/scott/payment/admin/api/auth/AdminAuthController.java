package com.scott.payment.admin.api.auth;

import com.scott.payment.admin.application.auth.AdminAuthApplicationService;
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
 * @classname : AdminAuthController
 * @date : 2026-06-06 00:00
 * @email : scott_x@163.com
 * @description : 管理后台登录注册与权限接口
 * @status : create
 */
@RestController
@RequestMapping("/admin/auth")
public class AdminAuthController {

    /**
     * 后台认证应用服务。
     */
    private final AdminAuthApplicationService adminAuthApplicationService;

    /**
     * 创建管理后台登录权限接口。
     *
     * @param adminAuthApplicationService 后台认证应用服务
     */
    public AdminAuthController(AdminAuthApplicationService adminAuthApplicationService) {
        this.adminAuthApplicationService = adminAuthApplicationService;
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
     * @return 登录响应
     */
    @PostMapping("/login")
    public CommonResult<AuthLoginResponse> login(@Valid @RequestBody AuthLoginRequest request,
                                                 HttpServletRequest servletRequest) {
        return success(adminAuthApplicationService.login(request, servletRequest));
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
     * @return 登录响应
     */
    @PostMapping("/mfa/bind-confirm")
    public CommonResult<AuthLoginResponse> mfaBindConfirm(@Valid @RequestBody AuthMfaBindConfirmRequest request,
                                                          HttpServletRequest servletRequest) {
        return success(adminAuthApplicationService.mfaBindConfirm(request, servletRequest));
    }

    /**
     * 验证管理后台 OTP。
     *
     * @param request        OTP 验证请求
     * @param servletRequest Servlet 请求
     * @return 登录响应
     */
    @PostMapping("/mfa/verify")
    public CommonResult<AuthLoginResponse> mfaVerify(@Valid @RequestBody AuthMfaVerifyRequest request,
                                                     HttpServletRequest servletRequest) {
        return success(adminAuthApplicationService.mfaVerify(request, servletRequest));
    }

    /**
     * 查询当前登录账号、菜单和权限。
     *
     * @param authorization Authorization 请求头
     * @return 当前登录账号、菜单和权限
     */
    @GetMapping("/me")
    public CommonResult<AuthLoginResponse> me(@RequestHeader("Authorization") String authorization) {
        return success(adminAuthApplicationService.currentUser(authorization));
    }

    /**
     * 更新当前后台登录账号个人资料。
     *
     * @param authorization Authorization 请求头
     * @param request       个人资料更新请求
     * @return 更新后的当前登录账号、菜单和权限
     */
    @PostMapping("/profile")
    @OperationLog(moduleName = "后台个人中心", businessType = OperationTypeConstants.UPDATE,
            operation = "更新后台个人资料", recordRequest = false, recordResponse = false)
    public CommonResult<AuthLoginResponse> updateProfile(@RequestHeader("Authorization") String authorization,
                                                         @Valid @RequestBody AuthProfileUpdateRequest request) {
        return success(adminAuthApplicationService.updateCurrentProfile(authorization, request));
    }

    /**
     * 修改当前后台登录账号密码。
     *
     * @param authorization Authorization 请求头
     * @param request       修改密码请求
     * @return 空响应
     */
    @PostMapping("/password/change")
    @OperationLog(moduleName = "后台个人中心", businessType = OperationTypeConstants.UPDATE,
            operation = "修改后台登录密码", recordRequest = false, recordResponse = false)
    public CommonResult<Void> changePassword(@RequestHeader("Authorization") String authorization,
                                             @Valid @RequestBody AuthPasswordChangeRequest request) {
        adminAuthApplicationService.changeCurrentPassword(authorization, request);
        return success();
    }

    /**
     * 退出登录。
     *
     * @param authorization Authorization 请求头
     * @return 空响应
     */
    @PostMapping("/logout")
    @OperationLog(moduleName = "后台登录权限", businessType = OperationTypeConstants.UPDATE,
            operation = "管理后台账号退出登录", recordRequest = false, recordResponse = false)
    public CommonResult<Void> logout(@RequestHeader("Authorization") String authorization) {
        adminAuthApplicationService.logout(authorization);
        return success();
    }
}
