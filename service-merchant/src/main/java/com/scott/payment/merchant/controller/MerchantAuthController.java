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
import com.scott.payment.merchant.dto.MerchantDefaultLoginCredentialDTO;
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

    /**
     * 创建商户管理系统登录权限接口。
     *
     * @param merchantAuthApplicationService 商户认证应用服务
     */
    public MerchantAuthController(MerchantAuthApplicationService merchantAuthApplicationService) {
        this.merchantAuthApplicationService = merchantAuthApplicationService;
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
    /**
     * 完成 register 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 当前方法计算或转换后的业务结果
     */
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
     *
     * @param authorization Authorization 请求头
     * @return 当前登录账号、菜单和权限
     */
    @GetMapping("/me")
    @RequiresPermission("merchant:dashboard:view")
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
/**
 * 写入或更新 update Profile 相关数据，保持数据库记录与当前业务处理结果一致。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param authorization authorization 输入值，含义由调用方法名称和所属业务对象限定
 * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
 * @return 当前方法计算或转换后的业务结果
 */
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
/**
 * 完成 change Password 分支的校验或转换，返回值供当前调用链继续组装结果。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param authorization authorization 输入值，含义由调用方法名称和所属业务对象限定
 * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
 * @return 当前方法计算或转换后的业务结果
 */
    public CommonResult<Void> changePassword(@RequestHeader("Authorization") String authorization,
                                             @Valid @RequestBody AuthPasswordChangeRequest request) {
        merchantAuthApplicationService.changeCurrentPassword(authorization, request);
        return success();
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
    /**
     * 完成 logout 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param authorization authorization 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    public CommonResult<Void> logout(@RequestHeader("Authorization") String authorization) {
        merchantAuthApplicationService.logout(authorization);
        return success();
    }
}
