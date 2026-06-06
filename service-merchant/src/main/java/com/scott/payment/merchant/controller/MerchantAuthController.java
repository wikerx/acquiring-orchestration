package com.scott.payment.merchant.controller;

import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.db.auth.constant.AuthConstants;
import com.scott.payment.component.db.auth.dto.AuthAccountDTO;
import com.scott.payment.component.db.auth.dto.AuthLoginRequest;
import com.scott.payment.component.db.auth.dto.AuthLoginResponse;
import com.scott.payment.component.db.auth.dto.AuthRegisterRequest;
import com.scott.payment.component.db.auth.service.SystemAuthService;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
     * 登录权限服务。
     */
    private final SystemAuthService systemAuthService;

    /**
     * 创建商户管理系统登录权限接口。
     *
     * @param systemAuthService 登录权限服务
     */
    public MerchantAuthController(SystemAuthService systemAuthService) {
        this.systemAuthService = systemAuthService;
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
        return CommonResult.success(systemAuthService.register(AuthConstants.APP_MERCHANT, request));
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
        return CommonResult.success(systemAuthService.login(
                AuthConstants.APP_MERCHANT,
                request,
                clientIp(servletRequest),
                servletRequest.getHeader("User-Agent")
        ));
    }

    /**
     * 查询当前商户登录账号、菜单和权限。
     *
     * @param authorization Authorization 请求头
     * @return 当前登录账号、菜单和权限
     */
    @GetMapping("/me")
    @RequiresPermission("merchant:dashboard:view")
    public CommonResult<AuthLoginResponse> me(@RequestHeader("Authorization") String authorization) {
        return CommonResult.success(systemAuthService.currentUser(AuthConstants.APP_MERCHANT, authorization));
    }

    /**
     * 退出登录。
     *
     * @param authorization Authorization 请求头
     * @return 空响应
     */
    @PostMapping("/logout")
    @RequiresPermission("merchant:dashboard:view")
    @OperationLog(moduleName = "商户登录权限", businessType = OperationTypeConstants.UPDATE,
            operation = "商户系统账号退出登录", recordRequest = false, recordResponse = false)
    public CommonResult<Void> logout(@RequestHeader("Authorization") String authorization) {
        systemAuthService.logout(AuthConstants.APP_MERCHANT, authorization);
        return CommonResult.success();
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
