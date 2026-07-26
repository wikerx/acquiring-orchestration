package com.scott.payment.merchant.application.auth;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
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
import com.scott.payment.component.db.auth.entity.SysAccountDO;
import com.scott.payment.component.db.auth.entity.SysAppDO;
import com.scott.payment.component.db.auth.entity.SysMerchantUserDO;
import com.scott.payment.component.db.auth.mapper.SysAccountMapper;
import com.scott.payment.component.db.auth.mapper.SysAppMapper;
import com.scott.payment.component.db.auth.mapper.SysMerchantUserMapper;
import com.scott.payment.component.db.auth.service.SystemAuthService;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.merchant.dto.MerchantDefaultLoginCredentialDTO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

@Service
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantAuthApplicationService
 * @date : 2026-06-19 19:12
 * @email : scott_x@163.com
 * @description : MerchantAuthApplicationService 应用服务，用于编排接口请求、权限上下文、领域服务和外部依赖，位于 商户后台服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
public class MerchantAuthApplicationService {

    /**
     * DEFAULT LOGIN ACCOUNT 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；敏感或可识别字段，日志输出必须脱敏。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String DEFAULT_LOGIN_ACCOUNT = "admin";
    /**
     * DEFAULT LOGIN PASSWORD 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；高敏感字段，禁止打印日志、禁止写入异常消息，持久化前需确认安全要求。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String DEFAULT_LOGIN_PASSWORD = "Merchant@123456";

    /**
     * 商户鉴权能力。
     */
    private final SystemAuthService systemAuthService;

    /**
     * sys App Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final SysAppMapper sysAppMapper;
    /**
     * sys Account Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；敏感或可识别字段，日志输出必须脱敏。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final SysAccountMapper sysAccountMapper;
    /**
     * sys Merchant User Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final SysMerchantUserMapper sysMerchantUserMapper;
    /**
     * environment 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final Environment environment;

    /**
     * 创建商户门户认证应用服务。
     *
     * @param systemAuthService 商户鉴权能力
     * @param sysAppMapper      应用 Mapper
     * @param sysAccountMapper  登录账号 Mapper
     * @param sysMerchantUserMapper 商户端用户 Mapper
     * @param environment       Spring 环境
     */
    public MerchantAuthApplicationService(SystemAuthService systemAuthService,
                                          SysAppMapper sysAppMapper,
                                          SysAccountMapper sysAccountMapper,
                                          SysMerchantUserMapper sysMerchantUserMapper,
                                          Environment environment) {
        this.systemAuthService = systemAuthService;
        this.sysAppMapper = sysAppMapper;
        this.sysAccountMapper = sysAccountMapper;
        this.sysMerchantUserMapper = sysMerchantUserMapper;
        this.environment = environment;
    }

    /**
     * 注册商户系统账号。
     *
     * @param request 注册请求
     * @return 注册后的账号信息
     */
    public AuthAccountDTO register(AuthRegisterRequest request) {
        return systemAuthService.register(AuthConstants.APP_MERCHANT, request);
    }

    /**
     * 发送商户系统登录图形验证码。
     *
     * @param request        验证码发送请求
     * @param servletRequest Servlet 请求
     * @return 验证码发送响应
     */
    public AuthVerifyCodeSendResponse sendVerifyCode(AuthVerifyCodeSendRequest request, HttpServletRequest servletRequest) {
        return systemAuthService.sendLoginVerifyCode(
                AuthConstants.APP_MERCHANT,
                request,
                clientIp(servletRequest)
        );
    }

    /**
     * 查询商户门户登录页本地开发默认凭据。
     *
     * <p>密码来自明确的本地种子账号初始密码，不从数据库哈希反推，也不暴露任意账号密码。</p>
     *
     * @return 默认登录凭据，未初始化种子账号时返回空字段
     */
    @DS(DataSourceName.SLAVE)
    public MerchantDefaultLoginCredentialDTO defaultLoginCredential() {
        MerchantDefaultLoginCredentialDTO credential = new MerchantDefaultLoginCredentialDTO();
        if (!environment.acceptsProfiles(Profiles.of("dev", "test", "sample", "local"))) {
            return credential;
        }
        SysAppDO merchantApp = sysAppMapper.selectOne(Wrappers.<SysAppDO>lambdaQuery()
                .eq(SysAppDO::getAppCode, AuthConstants.APP_MERCHANT)
                .eq(SysAppDO::getStatus, AuthConstants.ENABLED)
                .eq(SysAppDO::getDeleted, AuthConstants.NOT_DELETED)
                .last("LIMIT 1"));
        if (merchantApp == null) {
            return credential;
        }
        SysMerchantUserDO merchantUser = sysMerchantUserMapper.selectOne(Wrappers.<SysMerchantUserDO>lambdaQuery()
                .eq(SysMerchantUserDO::getLoginAccount, DEFAULT_LOGIN_ACCOUNT)
                .eq(SysMerchantUserDO::getStatus, AuthConstants.ENABLED)
                .eq(SysMerchantUserDO::getDeleted, AuthConstants.NOT_DELETED)
                .orderByAsc(SysMerchantUserDO::getMerchantId)
                .last("LIMIT 1"));
        if (merchantUser == null || merchantUser.getAccountId() == null) {
            return credential;
        }
        SysAccountDO account = sysAccountMapper.selectById(merchantUser.getAccountId());
        if (account == null
                || !merchantApp.getId().equals(account.getAppId())
                || account.getStatus() == null
                || account.getStatus() != AuthConstants.ENABLED
                || account.getDeleted() == null
                || account.getDeleted() != AuthConstants.NOT_DELETED) {
            return credential;
        }
        credential.setMerchantId(merchantUser.getMerchantId());
        credential.setLoginAccount(merchantUser.getLoginAccount());
        credential.setPassword(DEFAULT_LOGIN_PASSWORD);
        return credential;
    }

    /**
     * 商户系统账号登录。
     *
     * @param request        登录请求
     * @param servletRequest Servlet 请求
     * @return 登录响应
     */
    public AuthLoginResponse login(AuthLoginRequest request, HttpServletRequest servletRequest) {
        return systemAuthService.login(
                AuthConstants.APP_MERCHANT,
                request,
                clientIp(servletRequest),
                servletRequest.getHeader("User-Agent")
        );
    }

    /**
     * 查询商户系统 OTP 绑定信息。
     *
     * @param loginTicket 短期登录票据
     * @return OTP 绑定信息
     */
    public AuthMfaBindInfoResponse mfaBindInfo(String loginTicket) {
        return systemAuthService.mfaBindInfo(AuthConstants.APP_MERCHANT, loginTicket);
    }

    /**
     * 确认商户系统 OTP 绑定并完成登录。
     *
     * @param request        绑定确认请求
     * @param servletRequest Servlet 请求
     * @return 登录响应
     */
    public AuthLoginResponse mfaBindConfirm(AuthMfaBindConfirmRequest request, HttpServletRequest servletRequest) {
        return systemAuthService.mfaBindConfirm(
                AuthConstants.APP_MERCHANT,
                request,
                clientIp(servletRequest),
                servletRequest.getHeader("User-Agent")
        );
    }

    /**
     * 验证商户系统 OTP 并完成登录。
     *
     * @param request        OTP 验证请求
     * @param servletRequest Servlet 请求
     * @return 登录响应
     */
    public AuthLoginResponse mfaVerify(AuthMfaVerifyRequest request, HttpServletRequest servletRequest) {
        return systemAuthService.mfaVerify(
                AuthConstants.APP_MERCHANT,
                request,
                clientIp(servletRequest),
                servletRequest.getHeader("User-Agent")
        );
    }

    /**
     * 查询当前商户登录账号、菜单和权限。
     *
     * @param authorization Authorization 请求头
     * @return 当前登录账号、菜单和权限
     */
    public AuthLoginResponse currentUser(String authorization) {
        return systemAuthService.currentUser(AuthConstants.APP_MERCHANT, authorization);
    }

    /**
     * 更新当前商户登录账号个人资料。
     *
     * @param authorization Authorization 请求头
     * @param request       个人资料更新请求
     * @return 更新后的当前商户登录账号、菜单和权限
     */
    public AuthLoginResponse updateCurrentProfile(String authorization, AuthProfileUpdateRequest request) {
        return systemAuthService.updateCurrentProfile(AuthConstants.APP_MERCHANT, authorization, request);
    }

    /**
     * 修改当前商户登录账号密码。
     *
     * @param authorization Authorization 请求头
     * @param request       修改密码请求
     */
    public void changeCurrentPassword(String authorization, AuthPasswordChangeRequest request) {
        systemAuthService.changeCurrentPassword(AuthConstants.APP_MERCHANT, authorization, request);
    }

    /**
     * 退出商户登录。
     *
     * @param authorization Authorization 请求头
     */
    public void logout(String authorization) {
        systemAuthService.logout(AuthConstants.APP_MERCHANT, authorization);
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
