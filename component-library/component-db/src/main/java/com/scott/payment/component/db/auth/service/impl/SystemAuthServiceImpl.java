package com.scott.payment.component.db.auth.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.LoginTokenUtils;
import com.scott.payment.component.core.auth.PasswordHashUtils;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.auth.constant.AuthConstants;
import com.scott.payment.component.db.auth.dto.AuthAccountDTO;
import com.scott.payment.component.db.auth.dto.AuthLoginRequest;
import com.scott.payment.component.db.auth.dto.AuthLoginResponse;
import com.scott.payment.component.db.auth.dto.AuthMfaBindConfirmRequest;
import com.scott.payment.component.db.auth.dto.AuthMfaBindInfoResponse;
import com.scott.payment.component.db.auth.dto.AuthMfaVerifyRequest;
import com.scott.payment.component.db.auth.dto.AuthPasswordChangeRequest;
import com.scott.payment.component.db.auth.dto.AuthProfileUpdateRequest;
import com.scott.payment.component.db.auth.dto.AuthMenuDTO;
import com.scott.payment.component.db.auth.dto.AuthRegisterRequest;
import com.scott.payment.component.db.auth.dto.AuthVerifyCodeSendRequest;
import com.scott.payment.component.db.auth.dto.AuthVerifyCodeSendResponse;
import com.scott.payment.component.db.auth.entity.BaseMerchantInfoDO;
import com.scott.payment.component.db.auth.entity.SysAccountDO;
import com.scott.payment.component.db.auth.entity.SysAccountMfaDO;
import com.scott.payment.component.db.auth.entity.SysAccountMfaLogDO;
import com.scott.payment.component.db.auth.entity.SysAccountMfaTokenDO;
import com.scott.payment.component.db.auth.entity.SysAccountRoleDO;
import com.scott.payment.component.db.auth.entity.SysAppDO;
import com.scott.payment.component.db.auth.entity.SysLoginLogDO;
import com.scott.payment.component.db.auth.entity.SysLoginSessionDO;
import com.scott.payment.component.db.auth.entity.SysMerchantMenuGrantDO;
import com.scott.payment.component.db.auth.entity.SysMerchantPermissionGrantDO;
import com.scott.payment.component.db.auth.entity.SysMerchantUserDO;
import com.scott.payment.component.db.auth.entity.SysMerchantUserRoleDO;
import com.scott.payment.component.db.auth.entity.SysMenuDO;
import com.scott.payment.component.db.auth.entity.SysPermissionDO;
import com.scott.payment.component.db.auth.entity.SysRoleDO;
import com.scott.payment.component.db.auth.entity.SysRoleMenuDO;
import com.scott.payment.component.db.auth.entity.SysRolePermissionDO;
import com.scott.payment.component.db.auth.entity.SysUserDO;
import com.scott.payment.component.db.auth.entity.SysVerifyCodeDO;
import com.scott.payment.component.db.auth.mapper.BaseMerchantInfoMapper;
import com.scott.payment.component.db.auth.mapper.SysAccountMfaLogMapper;
import com.scott.payment.component.db.auth.mapper.SysAccountMfaMapper;
import com.scott.payment.component.db.auth.mapper.SysAccountMfaTokenMapper;
import com.scott.payment.component.db.auth.mapper.SysAccountMapper;
import com.scott.payment.component.db.auth.mapper.SysAccountRoleMapper;
import com.scott.payment.component.db.auth.mapper.SysAppMapper;
import com.scott.payment.component.db.auth.mapper.SysLoginLogMapper;
import com.scott.payment.component.db.auth.mapper.SysLoginSessionMapper;
import com.scott.payment.component.db.auth.mapper.SysMerchantMenuGrantMapper;
import com.scott.payment.component.db.auth.mapper.SysMerchantPermissionGrantMapper;
import com.scott.payment.component.db.auth.mapper.SysMerchantUserMapper;
import com.scott.payment.component.db.auth.mapper.SysMerchantUserRoleMapper;
import com.scott.payment.component.db.auth.mapper.SysMenuMapper;
import com.scott.payment.component.db.auth.mapper.SysPermissionMapper;
import com.scott.payment.component.db.auth.mapper.SysRoleMapper;
import com.scott.payment.component.db.auth.mapper.SysRoleMenuMapper;
import com.scott.payment.component.db.auth.mapper.SysRolePermissionMapper;
import com.scott.payment.component.db.auth.mapper.SysUserMapper;
import com.scott.payment.component.db.auth.mapper.SysVerifyCodeMapper;
import com.scott.payment.component.db.auth.service.SystemAuthService;
import com.scott.payment.component.db.auth.support.MfaSecretCrypto;
import com.scott.payment.component.db.auth.support.TotpUtils;
import com.scott.payment.component.db.constant.DataSourceName;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SystemAuthServiceImpl
 * @date : 2026-06-06 00:00
 * @email : scott_x@163.com
 * @description : 管理类系统登录注册与权限服务实现
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SystemAuthServiceImpl
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 系统管理System Auth Service Impl，位于 component-library/component-db 的服务实现层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Service
public class SystemAuthServiceImpl implements SystemAuthService {

    /**
     * 登录成功状态。
     */
    private static final int LOGIN_SUCCESS = 1;

    /**
     * 登录失败状态。
     */
    private static final int LOGIN_FAILED = 0;

    /**
     * 未退出状态。
     */
    private static final int NOT_LOGOUT = 0;

    /**
     * 已退出状态。
     */
    private static final int LOGOUT = 1;

    /**
     * 顶级菜单父ID。
     */
    private static final long ROOT_MENU_PARENT_ID = 0L;

    /**
     * 已废弃的内风控规则菜单编码。
     */
    private static final Set<String> DEPRECATED_RISK_RULE_MENU_CODES = Set.of(
            "risk_rule_issuer_country",
            "risk_rule_card_bin"
    );

    /**
     * 已废弃的内风控规则路由。
     */
    private static final Set<String> DEPRECATED_RISK_RULE_ROUTE_PATHS = Set.of(
            "/risk/rule/issuer-country",
            "/risk/rule/card-bin"
    );

    /**
     * 已废弃的内风控规则权限前缀。
     */
    private static final Set<String> DEPRECATED_RISK_RULE_PERMISSION_PREFIXES = Set.of(
            "risk:rule:issuerCountry",
            "risk:rule:cardBin"
    );

    /**
     * 登录验证码场景。
     */
    private static final String VERIFY_SCENE_LOGIN = "LOGIN";

    /**
     * 登录验证码有效期，单位秒。
     */
    private static final int VERIFY_CODE_TTL_SECONDS = 300;

    /**
     * 登录验证码最大验证次数。
     */
    private static final int VERIFY_CODE_MAX_ATTEMPTS = 5;

    /**
     * 管理端和商户端登录会话闲置超时时间，单位分钟。
     */
    private static final long SESSION_IDLE_TIMEOUT_MINUTES = 30L;

    /**
     * 同一 IP 的登录页图形验证码刷新统计窗口，单位秒。
     */
    private static final int VERIFY_CODE_SEND_LIMIT_WINDOW_SECONDS = 60;

    /**
     * 同一 IP 在统计窗口内允许刷新图形验证码的最大次数。
     */
    private static final long VERIFY_CODE_SEND_LIMIT_COUNT = 10;

    /**
     * 登录页图形验证码类型。
     */
    private static final String VERIFY_CODE_RECEIVER_TYPE_CAPTCHA = "CAPTCHA";

    /**
     * 登录页图形验证码接收人占位，避免记录账号、邮箱或手机号。
     */
    private static final String VERIFY_CODE_CAPTCHA_RECEIVER = "LOGIN_PAGE";

    /**
     * 登录页图形验证码生成渠道。
     */
    private static final String VERIFY_CODE_SEND_CHANNEL = "PAGE_CAPTCHA";

    /**
     * 图形验证码图片前缀。
     */
    private static final String CAPTCHA_IMAGE_PREFIX = "data:image/png;base64,";

    /**
     * MFA 登录票据类型。
     */
    private static final String MFA_TOKEN_TYPE_LOGIN = "LOGIN_MFA";

    /**
     * MFA 登录票据有效期，单位秒。
     */
    private static final int MFA_LOGIN_TICKET_TTL_SECONDS = 300;

    /**
     * TOTP 时间步长，单位秒。
     */
    private static final int MFA_TOTP_PERIOD_SECONDS = 30;

    /**
     * TOTP 容忍窗口，允许前后各一个时间步。
     */
    private static final int MFA_TOTP_WINDOW = 1;

    /**
     * TOTP 连续失败最大次数。
     */
    private static final int MFA_MAX_FAILED_ATTEMPTS = 5;

    /**
     * TOTP 临时锁定时间，单位分钟。
     */
    private static final int MFA_LOCK_MINUTES = 15;

    /**
     * MFA 审计结果：成功。
     */
    private static final String MFA_RESULT_SUCCESS = "SUCCESS";

    /**
     * MFA 审计结果：失败。
     */
    private static final String MFA_RESULT_FAILED = "FAILED";

    /**
     * 验证码随机数生成器。
     */
    private static final SecureRandom VERIFY_CODE_RANDOM = new SecureRandom();
    /**
     * 图形验证码字符集，排除 0/O/1/I 等容易混淆字符。
     */
    private static final char[] CAPTCHA_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    /**
     * 接口资源通配路径匹配器。
     */
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final SysAppMapper sysAppMapper;
    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final SysUserMapper sysUserMapper;
    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final SysAccountMapper sysAccountMapper;
    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final SysRoleMapper sysRoleMapper;
    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final SysAccountRoleMapper sysAccountRoleMapper;
    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final SysRoleMenuMapper sysRoleMenuMapper;
    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final SysRolePermissionMapper sysRolePermissionMapper;
    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final SysMenuMapper sysMenuMapper;
    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final SysPermissionMapper sysPermissionMapper;
    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final SysMerchantMenuGrantMapper sysMerchantMenuGrantMapper;
    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final SysMerchantPermissionGrantMapper sysMerchantPermissionGrantMapper;
    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final SysMerchantUserMapper sysMerchantUserMapper;
    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final SysMerchantUserRoleMapper sysMerchantUserRoleMapper;
    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final SysLoginLogMapper sysLoginLogMapper;
    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final SysLoginSessionMapper sysLoginSessionMapper;
    /**
     * 系统管理编码或编号字段，用于业务识别、查询和幂等关联。
     */
    private final SysVerifyCodeMapper sysVerifyCodeMapper;
    /**
     * MFA 配置数据访问接口。
     */
    private final SysAccountMfaMapper sysAccountMfaMapper;
    /**
     * MFA 短期票据数据访问接口。
     */
    private final SysAccountMfaTokenMapper sysAccountMfaTokenMapper;
    /**
     * MFA 安全审计日志数据访问接口。
     */
    private final SysAccountMfaLogMapper sysAccountMfaLogMapper;
    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final BaseMerchantInfoMapper baseMerchantInfoMapper;
    /**
     * 创建系统登录权限服务。
     *
     * @param sysAppMapper            系统应用 Mapper
     * @param sysUserMapper           用户主体 Mapper
     * @param sysAccountMapper        系统账号 Mapper
     * @param sysRoleMapper           角色 Mapper
     * @param sysAccountRoleMapper    账号角色 Mapper
     * @param sysRoleMenuMapper       角色菜单 Mapper
     * @param sysRolePermissionMapper 角色权限 Mapper
     * @param sysMenuMapper           菜单 Mapper
     * @param sysPermissionMapper     权限 Mapper
     * @param sysMerchantMenuGrantMapper 商户菜单授权 Mapper
     * @param sysMerchantPermissionGrantMapper 商户权限授权 Mapper
     * @param sysMerchantUserMapper   商户端登录用户 Mapper
     * @param sysMerchantUserRoleMapper 商户端用户角色 Mapper
     * @param sysLoginLogMapper       登录日志 Mapper
     * @param sysLoginSessionMapper   登录会话 Mapper
     * @param sysVerifyCodeMapper     动态验证码 Mapper
     * @param sysAccountMfaMapper     MFA 配置 Mapper
     * @param sysAccountMfaTokenMapper MFA 票据 Mapper
     * @param sysAccountMfaLogMapper  MFA 日志 Mapper
     * @param baseMerchantInfoMapper  商户基础信息 Mapper
     */
    public SystemAuthServiceImpl(SysAppMapper sysAppMapper,
                                 SysUserMapper sysUserMapper,
                                 SysAccountMapper sysAccountMapper,
                                 SysRoleMapper sysRoleMapper,
                                 SysAccountRoleMapper sysAccountRoleMapper,
                                 SysRoleMenuMapper sysRoleMenuMapper,
                                 SysRolePermissionMapper sysRolePermissionMapper,
                                 SysMenuMapper sysMenuMapper,
                                 SysPermissionMapper sysPermissionMapper,
                                 SysMerchantMenuGrantMapper sysMerchantMenuGrantMapper,
                                 SysMerchantPermissionGrantMapper sysMerchantPermissionGrantMapper,
                                 SysMerchantUserMapper sysMerchantUserMapper,
                                 SysMerchantUserRoleMapper sysMerchantUserRoleMapper,
                                 SysLoginLogMapper sysLoginLogMapper,
                                 SysLoginSessionMapper sysLoginSessionMapper,
                                 SysVerifyCodeMapper sysVerifyCodeMapper,
                                 SysAccountMfaMapper sysAccountMfaMapper,
                                 SysAccountMfaTokenMapper sysAccountMfaTokenMapper,
                                 SysAccountMfaLogMapper sysAccountMfaLogMapper,
                                 BaseMerchantInfoMapper baseMerchantInfoMapper) {
        this.sysAppMapper = sysAppMapper;
        this.sysUserMapper = sysUserMapper;
        this.sysAccountMapper = sysAccountMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysAccountRoleMapper = sysAccountRoleMapper;
        this.sysRoleMenuMapper = sysRoleMenuMapper;
        this.sysRolePermissionMapper = sysRolePermissionMapper;
        this.sysMenuMapper = sysMenuMapper;
        this.sysPermissionMapper = sysPermissionMapper;
        this.sysMerchantMenuGrantMapper = sysMerchantMenuGrantMapper;
        this.sysMerchantPermissionGrantMapper = sysMerchantPermissionGrantMapper;
        this.sysMerchantUserMapper = sysMerchantUserMapper;
        this.sysMerchantUserRoleMapper = sysMerchantUserRoleMapper;
        this.sysLoginLogMapper = sysLoginLogMapper;
        this.sysLoginSessionMapper = sysLoginSessionMapper;
        this.sysVerifyCodeMapper = sysVerifyCodeMapper;
        this.sysAccountMfaMapper = sysAccountMfaMapper;
        this.sysAccountMfaTokenMapper = sysAccountMfaTokenMapper;
        this.sysAccountMfaLogMapper = sysAccountMfaLogMapper;
        this.baseMerchantInfoMapper = baseMerchantInfoMapper;
    }

    /**
     * 注册系统账号。
     *
     * @param appCode 系统应用编码
     * @param request 注册请求
     * @return 注册后的账号信息
     */
    /**
     * 执行系统管理相关处理，保持当前层级的职责边界和返回语义。
     * @param appCode 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public AuthAccountDTO register(String appCode, AuthRegisterRequest request) {
        SysAppDO app = getEnabledApp(appCode);
        validateMerchantRegister(appCode, request.getMerchantId());
        assertAccountNotExists(app.getId(), request.getLoginAccount());

        LocalDateTime now = LocalDateTime.now();
        SysUserDO user = buildUser(appCode, request, now);
        sysUserMapper.insert(user);

        SysAccountDO account = buildAccount(app, user, request, now);
        sysAccountMapper.insert(account);

        SysRoleDO role = getRegisterRole(app, request);
        bindRole(app, account, role, now);
        return toAccountDTO(app, user, account);
    }

    /**
     * 生成登录页图形验证码。
     *
     * @param appCode  系统应用编码
     * @param request  验证码生成请求，登录页图形验证码不依赖账号
     * @param clientIp 客户端 IP，用于验证码刷新频率限制
     * @return 验证码图片、验证码记录ID和有效期
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public AuthVerifyCodeSendResponse sendLoginVerifyCode(String appCode,
                                                          AuthVerifyCodeSendRequest request,
                                                          String clientIp) {
        if (!VERIFY_SCENE_LOGIN.equals(request.getScene())) {
            throw new ServiceException(ApiResultEnum.BAD_REQUEST.getCode(), "verify code scene is invalid");
        }
        SysAppDO app = getEnabledApp(appCode);

        LocalDateTime now = LocalDateTime.now();
        validateVerifyCodeSendLimit(app.getId(), clientIp, now);
        String code = generateVerifyCode();
        String salt = PasswordHashUtils.generateSalt();
        SysVerifyCodeDO verifyCode = new SysVerifyCodeDO();
        verifyCode.setAppId(app.getId());
        verifyCode.setScene(VERIFY_SCENE_LOGIN);
        verifyCode.setReceiverType(VERIFY_CODE_RECEIVER_TYPE_CAPTCHA);
        verifyCode.setReceiver(VERIFY_CODE_CAPTCHA_RECEIVER);
        verifyCode.setCodeSalt(salt);
        verifyCode.setCodeHash(PasswordHashUtils.hashPassword(code.toLowerCase(), salt));
        verifyCode.setExpireAt(now.plusSeconds(VERIFY_CODE_TTL_SECONDS));
        verifyCode.setUsed(AuthConstants.DISABLED);
        verifyCode.setVerifyCount(0);
        verifyCode.setSendIp(normalizeIp(clientIp));
        verifyCode.setSendChannel(VERIFY_CODE_SEND_CHANNEL);
        verifyCode.setSendStatus(AuthConstants.ENABLED);
        verifyCode.setCreatedAt(now);
        sysVerifyCodeMapper.insert(verifyCode);

        AuthVerifyCodeSendResponse response = new AuthVerifyCodeSendResponse();
        response.setVerifyCodeId(String.valueOf(verifyCode.getId()));
        response.setReceiverType(verifyCode.getReceiverType());
        response.setMaskedReceiver("页面图形验证码");
        response.setCaptchaImage(generateCaptchaImage(code));
        response.setExpireSeconds(VERIFY_CODE_TTL_SECONDS);
        return response;
    }

    /**
     * 登录系统账号。
     *
     * @param appCode   系统应用编码
     * @param request   登录请求
     * @param clientIp  客户端IP
     * @param userAgent 客户端 User-Agent
     * @return 登录响应
     */
    /**
     * 执行系统管理相关处理，保持当前层级的职责边界和返回语义。
     * @param appCode 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param clientIp 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param userAgent 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public AuthLoginResponse login(String appCode, AuthLoginRequest request, String clientIp, String userAgent) {
        SysAppDO app = getEnabledApp(appCode);
        validateMerchantLogin(appCode, request.getMerchantId());
        SysAccountDO account = findLoginAccount(app, request.getLoginAccount(), request.getMerchantId());
        if (account == null || account.getStatus() == null || account.getStatus() != AuthConstants.ENABLED) {
            recordLoginLog(app, account, request.getLoginAccount(), clientIp, userAgent, LOGIN_FAILED, "account disabled or not found");
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED.getCode(), "account disabled or not found");
        }
        if (account.getLocked() != null && account.getLocked() == AuthConstants.ENABLED) {
            recordLoginLog(app, account, request.getLoginAccount(), clientIp, userAgent, LOGIN_FAILED, "account locked");
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED.getCode(), "account locked");
        }
        if (StringUtils.hasText(request.getMerchantId()) && !request.getMerchantId().equals(account.getMerchantId())) {
            recordLoginLog(app, account, request.getLoginAccount(), clientIp, userAgent, LOGIN_FAILED, "merchant mismatch");
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED.getCode(), "merchant mismatch");
        }
        validateLoginVerifyCode(app, account, request, clientIp);
        if (!PasswordHashUtils.matches(request.getPassword(), account.getPasswordSalt(), account.getPasswordHash())) {
            increaseFailedLoginCount(account);
            recordLoginLog(app, account, request.getLoginAccount(), clientIp, userAgent, LOGIN_FAILED, "password mismatch");
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED.getCode(), "account or password is invalid");
        }

        SysUserDO user = getEnabledUser(account.getUserId());
        LocalDateTime now = LocalDateTime.now();
        SysAccountMfaDO mfa = loadMfa(app.getId(), account.getId());
        String challengeType = resolveMfaChallenge(mfa, now);
        if (StringUtils.hasText(challengeType)) {
            if (AuthConstants.MFA_CHALLENGE_LOCKED.equals(challengeType)) {
                recordMfaLog(app, account, mfa, "LOGIN_LOCKED", MFA_RESULT_FAILED, "mfa locked", null, clientIp, userAgent);
                return buildMfaLockedResponse(app, user, account, mfa);
            }
            SysAccountMfaDO preparedMfa = preparePendingMfaIfNecessary(app, account, mfa, challengeType, now);
            MfaLoginTicket ticket = createMfaLoginTicket(app, account, challengeType, clientIp, userAgent, now);
            recordMfaLog(app, account, preparedMfa, "LOGIN_CHALLENGE", MFA_RESULT_SUCCESS, challengeType, null, clientIp, userAgent);
            return buildMfaChallengeResponse(app, user, account, preparedMfa, ticket);
        }
        return issueLoginSession(app, user, account, request.getLoginAccount(), clientIp, userAgent);
    }

    /**
     * 获取 OTP 绑定信息。
     *
     * @param appCode     系统应用编码
     * @param loginTicket 短期登录票据
     * @return OTP 绑定信息
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public AuthMfaBindInfoResponse mfaBindInfo(String appCode, String loginTicket) {
        SysAppDO app = getEnabledApp(appCode);
        MfaTicketContext context = requireMfaTicket(app, loginTicket);
        if (!AuthConstants.MFA_CHALLENGE_BIND_REQUIRED.equals(context.ticket().getChallengeType())
                && !AuthConstants.MFA_CHALLENGE_RESET_BIND_REQUIRED.equals(context.ticket().getChallengeType())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "mfa bind is not required");
        }
        SysAccountMfaDO mfa = requireMfa(context.account());
        LocalDateTime now = LocalDateTime.now();
        SysAccountMfaDO preparedMfa = preparePendingMfaIfNecessary(app, context.account(), mfa, context.ticket().getChallengeType(), now);
        String secret = MfaSecretCrypto.decrypt(preparedMfa.getPendingSecretCipher());

        AuthMfaBindInfoResponse response = new AuthMfaBindInfoResponse();
        response.setMfaType(AuthConstants.MFA_TYPE_TOTP);
        response.setMfaStatus(preparedMfa.getMfaStatus());
        response.setIssuer(preparedMfa.getIssuer());
        response.setAccountLabel(preparedMfa.getAccountLabel());
        response.setOtpauthUri(TotpUtils.buildOtpauthUri(preparedMfa.getIssuer(), preparedMfa.getAccountLabel(), secret));
        response.setDigits(6);
        response.setPeriodSeconds(MFA_TOTP_PERIOD_SECONDS);
        response.setMaskedLoginAccount(maskLoginAccount(context.account().getLoginAccount()));
        response.setLoginTicketExpireAt(context.ticket().getExpireAt());
        return response;
    }

    /**
     * 确认 OTP 绑定并签发登录会话。
     *
     * @param appCode   系统应用编码
     * @param request   绑定确认请求
     * @param clientIp  客户端IP
     * @param userAgent 客户端 User-Agent
     * @return 登录响应
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public AuthLoginResponse mfaBindConfirm(String appCode,
                                            AuthMfaBindConfirmRequest request,
                                            String clientIp,
                                            String userAgent) {
        SysAppDO app = getEnabledApp(appCode);
        MfaTicketContext context = requireMfaTicket(app, request.getLoginTicket());
        if (!AuthConstants.MFA_CHALLENGE_BIND_REQUIRED.equals(context.ticket().getChallengeType())
                && !AuthConstants.MFA_CHALLENGE_RESET_BIND_REQUIRED.equals(context.ticket().getChallengeType())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "mfa bind is not required");
        }
        SysAccountMfaDO mfa = requireMfa(context.account());
        SysUserDO user = getEnabledUser(context.account().getUserId());
        String beforePolicy = mfa.getMfaPolicy();
        String beforeStatus = mfa.getMfaStatus();
        Long timeStep = verifyTotpOrRecordFailure(app, context.account(), mfa, mfa.getPendingSecretCipher(),
                request.getTotpCode(), "BIND_CONFIRM", clientIp, userAgent);
        LocalDateTime now = LocalDateTime.now();
        mfa.setSecretCipher(mfa.getPendingSecretCipher());
        mfa.setPendingSecretCipher(null);
        mfa.setMfaPolicy(AuthConstants.MFA_POLICY_REQUIRED);
        mfa.setMfaStatus(AuthConstants.MFA_STATUS_ENABLED);
        mfa.setMfaType(AuthConstants.MFA_TYPE_TOTP);
        mfa.setBindTime(now);
        mfa.setLastVerifyTime(now);
        mfa.setLastSuccessTimeStep(timeStep);
        mfa.setFailedVerifyCount(0);
        mfa.setLockedUntil(null);
        mfa.setUpdatedAt(now);
        sysAccountMfaMapper.updateById(mfa);
        markMfaTicketUsed(context.ticket(), now);
        recordMfaLog(app, context.account(), mfa, "BIND_CONFIRM", MFA_RESULT_SUCCESS,
                statusChangeReason(beforePolicy, beforeStatus, mfa), null, clientIp, userAgent);
        return issueLoginSession(app, user, context.account(), context.account().getLoginAccount(), clientIp, userAgent);
    }

    /**
     * 验证 OTP 并签发登录会话。
     *
     * @param appCode   系统应用编码
     * @param request   OTP 验证请求
     * @param clientIp  客户端IP
     * @param userAgent 客户端 User-Agent
     * @return 登录响应
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public AuthLoginResponse mfaVerify(String appCode,
                                       AuthMfaVerifyRequest request,
                                       String clientIp,
                                       String userAgent) {
        SysAppDO app = getEnabledApp(appCode);
        MfaTicketContext context = requireMfaTicket(app, request.getLoginTicket());
        if (!AuthConstants.MFA_CHALLENGE_VERIFY_REQUIRED.equals(context.ticket().getChallengeType())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "mfa verify is not required");
        }
        SysAccountMfaDO mfa = requireMfa(context.account());
        if (!AuthConstants.MFA_STATUS_ENABLED.equals(mfa.getMfaStatus())) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED.getCode(), "mfa is not enabled");
        }
        SysUserDO user = getEnabledUser(context.account().getUserId());
        Long timeStep = verifyTotpOrRecordFailure(app, context.account(), mfa, mfa.getSecretCipher(),
                request.getTotpCode(), "LOGIN_VERIFY", clientIp, userAgent);
        LocalDateTime now = LocalDateTime.now();
        mfa.setLastVerifyTime(now);
        mfa.setLastSuccessTimeStep(timeStep);
        mfa.setFailedVerifyCount(0);
        mfa.setLockedUntil(null);
        mfa.setUpdatedAt(now);
        sysAccountMfaMapper.updateById(mfa);
        markMfaTicketUsed(context.ticket(), now);
        recordMfaLog(app, context.account(), mfa, "LOGIN_VERIFY", MFA_RESULT_SUCCESS, null, null, clientIp, userAgent);
        return issueLoginSession(app, user, context.account(), context.account().getLoginAccount(), clientIp, userAgent);
    }

    /**
     * 根据 token 查询当前登录账号。
     *
     * @param appCode 系统应用编码
     * @param token   登录 token
     * @return 当前账号和权限信息
     */
    /**
     * 执行系统管理相关处理，保持当前层级的职责边界和返回语义。
     * @param appCode 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param token 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    @DS(DataSourceName.MASTER)
    public AuthLoginResponse currentUser(String appCode, String token) {
        SysAppDO app = getEnabledApp(appCode);
        SysLoginSessionDO session = getActiveSession(app.getId(), token);
        SysAccountDO account = getEnabledAccount(session.getAccountId());
        SysUserDO user = getEnabledUser(session.getUserId());
        return buildLoginResponse(app, user, account, null, session.getExpireAt());
    }

    /**
     * 更新当前登录账号个人资料。
     *
     * @param appCode 系统应用编码
     * @param token   登录 token
     * @param request 个人资料更新请求
     * @return 更新后的账号、菜单和权限信息
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public AuthLoginResponse updateCurrentProfile(String appCode, String token, AuthProfileUpdateRequest request) {
        SysAppDO app = getEnabledApp(appCode);
        SysLoginSessionDO session = getActiveSession(app.getId(), token);
        SysAccountDO account = getEnabledAccount(session.getAccountId());
        SysUserDO user = getEnabledUser(session.getUserId());
        LocalDateTime now = LocalDateTime.now();

        String nickname = requiredText(request.getNickname(), "nickname");
        String mobile = optionalText(request.getMobile());
        String email = requiredText(request.getEmail(), "email");

        user.setNickname(nickname);
        user.setMobile(mobile);
        user.setEmail(email);
        user.setUpdatedAt(now);
        user.setUpdatedBy(account.getId());
        sysUserMapper.updateById(user);

        account.setMobile(mobile);
        account.setEmail(email);
        account.setUpdatedAt(now);
        account.setUpdatedBy(account.getId());
        sysAccountMapper.updateById(account);

        if (AuthConstants.APP_MERCHANT.equals(app.getAppCode())) {
            SysMerchantUserDO merchantUser = getEnabledMerchantUser(account);
            merchantUser.setRealName(nickname);
            merchantUser.setUpdatedAt(now);
            merchantUser.setUpdatedBy(account.getId());
            sysMerchantUserMapper.updateById(merchantUser);
        }

        return buildLoginResponse(app, user, account, null, session.getExpireAt());
    }

    /**
     * 修改当前登录账号密码。
     *
     * @param appCode 系统应用编码
     * @param token   登录 token
     * @param request 修改密码请求
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public void changeCurrentPassword(String appCode, String token, AuthPasswordChangeRequest request) {
        SysAppDO app = getEnabledApp(appCode);
        SysLoginSessionDO session = getActiveSession(app.getId(), token);
        SysAccountDO account = getEnabledAccount(session.getAccountId());
        String oldPassword = requiredText(request.getOldPassword(), "oldPassword");
        String newPassword = requiredText(request.getNewPassword(), "newPassword");
        if (oldPassword.equals(newPassword)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "new password can not equal old password");
        }
        if (!PasswordHashUtils.matches(oldPassword, account.getPasswordSalt(), account.getPasswordHash())) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED.getCode(), "old password is invalid");
        }
        LocalDateTime now = LocalDateTime.now();
        String salt = PasswordHashUtils.generateSalt();
        account.setPasswordSalt(salt);
        account.setPasswordHash(PasswordHashUtils.hashPassword(newPassword, salt));
        account.setPasswordAlgo(PasswordHashUtils.ALGORITHM);
        account.setPasswordExpired(AuthConstants.DISABLED);
        account.setPasswordUpdatedAt(now);
        account.setUpdatedAt(now);
        account.setUpdatedBy(account.getId());
        sysAccountMapper.updateById(account);
    }

    /**
     * 退出登录。
     *
     * @param appCode 系统应用编码
     * @param token   登录 token
     */
    /**
     * 执行系统管理相关处理，保持当前层级的职责边界和返回语义。
     * @param appCode 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param token 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */
    @Override
    @DS(DataSourceName.MASTER)
    public void logout(String appCode, String token) {
        SysAppDO app = getEnabledApp(appCode);
        SysLoginSessionDO session = getActiveSession(app.getId(), token);
        LocalDateTime now = LocalDateTime.now();
        session.setLogout(LOGOUT);
        session.setLogoutAt(now);
        session.setUpdatedAt(now);
        sysLoginSessionMapper.updateById(session);
    }

    /**
     * 校验当前请求登录态和接口权限。
     *
     * @param appCode       应用编码
     * @param authorization Authorization 请求头
     * @param requestMethod HTTP 请求方法
     * @param requestPath   请求路径
     * @param permissionCode 接口显式声明的权限编码
     * @return 当前登录账号上下文
     */
    /**
     * 校验系统管理业务规则，发现不符合要求的数据时抛出业务异常。
     * @param appCode 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param authorization 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param requestMethod 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param requestPath 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param permissionCode 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    @DS(DataSourceName.MASTER)
    public InternalAuthAccount check(String appCode,
                                     String authorization,
                                     String requestMethod,
                                     String requestPath,
                                     String permissionCode) {
        SysAppDO app = getEnabledApp(appCode);
        SysLoginSessionDO session = getActiveSession(app.getId(), authorization);
        SysAccountDO account = getEnabledAccount(session.getAccountId());
        SysUserDO user = getEnabledUser(session.getUserId());
        List<String> roleCodes = queryRoleCodes(app, account);
        List<String> permissionCodes = queryPermissionCodes(app, account);
        String requiredPermission = StringUtils.hasText(permissionCode)
                ? permissionCode
                : findRequiredPermission(app.getId(), requestMethod, requestPath);
        if (StringUtils.hasText(requiredPermission) && !permissionCodes.contains(requiredPermission) && !permissionCodes.contains("*:*:*")) {
            throw new ServiceException(ApiResultEnum.FORBIDDEN);
        }
        return buildInternalAuthAccount(app, user, account, roleCodes, permissionCodes);
    }

    /**
     * 构建用户主体。
     *
     * @param appCode 应用编码
     * @param request 注册请求
     * @param now     当前时间
     * @return 用户主体实体
     */
    private SysUserDO buildUser(String appCode, AuthRegisterRequest request, LocalDateTime now) {
        SysUserDO user = new SysUserDO();
        user.setUserType(AuthConstants.APP_MERCHANT.equals(appCode)
                ? AuthConstants.USER_TYPE_MERCHANT
                : AuthConstants.USER_TYPE_PLATFORM);
        user.setRealName(request.getRealName());
        user.setMobile(request.getMobile());
        user.setEmail(request.getEmail());
        user.setStatus(AuthConstants.ENABLED);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        user.setDeleted(AuthConstants.NOT_DELETED);
        return user;
    }

    /**
     * 构建登录账号。
     *
     * @param app     系统应用
     * @param user    用户主体
     * @param request 注册请求
     * @param now     当前时间
     * @return 登录账号实体
     */
    private SysAccountDO buildAccount(SysAppDO app, SysUserDO user, AuthRegisterRequest request, LocalDateTime now) {
        String salt = PasswordHashUtils.generateSalt();
        SysAccountDO account = new SysAccountDO();
        account.setAppId(app.getId());
        account.setUserId(user.getId());
        account.setMerchantId(request.getMerchantId());
        account.setLoginAccount(request.getLoginAccount());
        account.setPasswordSalt(salt);
        account.setPasswordHash(PasswordHashUtils.hashPassword(request.getPassword(), salt));
        account.setPasswordAlgo(PasswordHashUtils.ALGORITHM);
        account.setMobile(request.getMobile());
        account.setEmail(request.getEmail());
        account.setMfaEnabled(AuthConstants.DISABLED);
        account.setPasswordExpired(AuthConstants.DISABLED);
        account.setPasswordUpdatedAt(now);
        account.setFailedLoginCount(0);
        account.setLocked(AuthConstants.DISABLED);
        account.setStatus(AuthConstants.ENABLED);
        account.setCreatedAt(now);
        account.setUpdatedAt(now);
        account.setDeleted(AuthConstants.NOT_DELETED);
        return account;
    }

    /**
     * 绑定默认或指定角色。
     *
     * @param app     系统应用
     * @param account 登录账号
     * @param role    角色
     * @param now     当前时间
     */
    private void bindRole(SysAppDO app, SysAccountDO account, SysRoleDO role, LocalDateTime now) {
        if (AuthConstants.APP_MERCHANT.equals(app.getAppCode())) {
            SysMerchantUserDO merchantUser = new SysMerchantUserDO();
            merchantUser.setMerchantInfoId(getMerchantInfoId(account.getMerchantId()));
            merchantUser.setMerchantId(account.getMerchantId());
            merchantUser.setUserId(account.getUserId());
            merchantUser.setAccountId(account.getId());
            merchantUser.setLoginAccount(account.getLoginAccount());
            merchantUser.setStatus(AuthConstants.ENABLED);
            merchantUser.setCreatedAt(now);
            merchantUser.setUpdatedAt(now);
            merchantUser.setDeleted(AuthConstants.NOT_DELETED);
            sysMerchantUserMapper.insert(merchantUser);

            SysMerchantUserRoleDO relation = new SysMerchantUserRoleDO();
            relation.setAppId(app.getId());
            relation.setMerchantInfoId(merchantUser.getMerchantInfoId());
            relation.setMerchantUserId(merchantUser.getId());
            relation.setRoleId(role.getId());
            relation.setCreatedAt(now);
            relation.setDeleted(AuthConstants.NOT_DELETED);
            sysMerchantUserRoleMapper.insert(relation);
            return;
        }
        SysAccountRoleDO relation = new SysAccountRoleDO();
        relation.setAppId(app.getId());
        relation.setAccountId(account.getId());
        relation.setRoleId(role.getId());
        relation.setCreatedAt(now);
        relation.setDeleted(AuthConstants.NOT_DELETED);
        sysAccountRoleMapper.insert(relation);
    }

    /**
     * 查询商户主表ID。
     *
     * @param merchantId 商户号
     * @return 商户主表ID
     */
    private Long getMerchantInfoId(String merchantId) {
        BaseMerchantInfoDO merchantInfo = baseMerchantInfoMapper.selectOne(
                Wrappers.<BaseMerchantInfoDO>lambdaQuery()
                        .eq(BaseMerchantInfoDO::getMerchantId, merchantId)
                        .eq(BaseMerchantInfoDO::getMerchantStatus, AuthConstants.ENABLED)
                        .eq(BaseMerchantInfoDO::getDeleted, 0)
                        .last("LIMIT 1")
        );
        if (merchantInfo == null) {
            throw new ServiceException(ApiResultEnum.MERCHANT_INVALID);
        }
        return merchantInfo.getId();
    }

    /**
     * 校验登录动态验证码。
     *
     * @param app     系统应用
     * @param account 登录账号
     * @param request 登录请求
     */
    private void validateLoginVerifyCode(SysAppDO app, SysAccountDO account, AuthLoginRequest request, String clientIp) {
        if (!StringUtils.hasText(request.getVerifyCodeId()) || !StringUtils.hasText(request.getVerifyCode())) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED.getCode(), "verify code is required");
        }
        Long verifyCodeId = parseVerifyCodeId(request.getVerifyCodeId());
        SysVerifyCodeDO verifyCode = sysVerifyCodeMapper.selectById(verifyCodeId);
        if (verifyCode == null
                || !Objects.equals(verifyCode.getAppId(), app.getId())
                || !VERIFY_SCENE_LOGIN.equals(verifyCode.getScene())
                || !VERIFY_CODE_RECEIVER_TYPE_CAPTCHA.equals(verifyCode.getReceiverType())
                || !VERIFY_CODE_CAPTCHA_RECEIVER.equals(verifyCode.getReceiver())
                || verifyCode.getUsed() == null
                || verifyCode.getUsed() == AuthConstants.ENABLED
                || verifyCode.getExpireAt() == null
                || verifyCode.getExpireAt().isBefore(LocalDateTime.now())
                || !Objects.equals(normalizeIp(clientIp), normalizeIp(verifyCode.getSendIp()))) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED.getCode(), "verify code is invalid or expired");
        }
        if (defaultInt(verifyCode.getVerifyCount()) >= VERIFY_CODE_MAX_ATTEMPTS) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED.getCode(), "verify code retry limit exceeded");
        }
        verifyCode.setVerifyCount(defaultInt(verifyCode.getVerifyCount()) + 1);
        if (!PasswordHashUtils.matches(request.getVerifyCode().trim().toLowerCase(), verifyCode.getCodeSalt(), verifyCode.getCodeHash())) {
            sysVerifyCodeMapper.updateById(verifyCode);
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED.getCode(), "verify code is invalid or expired");
        }
        verifyCode.setUsed(AuthConstants.ENABLED);
        verifyCode.setUsedAt(LocalDateTime.now());
        sysVerifyCodeMapper.updateById(verifyCode);
    }

    /**
     * 校验登录页图形验证码刷新频率，同一系统同一 IP 在短窗口内只能刷新有限次数。
     *
     * @param appId    系统应用 ID
     * @param clientIp 客户端 IP
     * @param now      当前时间
     */
    private void validateVerifyCodeSendLimit(Long appId, String clientIp, LocalDateTime now) {
        Long count = sysVerifyCodeMapper.selectCount(
                Wrappers.<SysVerifyCodeDO>lambdaQuery()
                        .eq(SysVerifyCodeDO::getAppId, appId)
                        .eq(SysVerifyCodeDO::getScene, VERIFY_SCENE_LOGIN)
                        .eq(SysVerifyCodeDO::getReceiverType, VERIFY_CODE_RECEIVER_TYPE_CAPTCHA)
                        .eq(SysVerifyCodeDO::getSendIp, normalizeIp(clientIp))
                        .ge(SysVerifyCodeDO::getCreatedAt, now.minusSeconds(VERIFY_CODE_SEND_LIMIT_WINDOW_SECONDS))
        );
        if (count != null && count >= VERIFY_CODE_SEND_LIMIT_COUNT) {
            throw new ServiceException(ApiResultEnum.TOO_MANY_REQUESTS.getCode(), "verify code send too frequently");
        }
    }

    /**
     * 规范化客户端 IP，避免空值导致限频条件不可预期。
     *
     * @param clientIp 客户端 IP
     * @return 规范化后的 IP
     */
    private String normalizeIp(String clientIp) {
        return StringUtils.hasText(clientIp) ? clientIp.trim() : "-";
    }

    /**
     * 解析验证码ID。
     *
     * @param verifyCodeId 验证码ID字符串
     * @return 验证码ID
     */
    private Long parseVerifyCodeId(String verifyCodeId) {
        try {
            return Long.valueOf(verifyCodeId);
        } catch (NumberFormatException exception) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED.getCode(), "verify code is invalid or expired");
        }
    }

    /**
     * 生成 5 位图形验证码字符，排除容易混淆的 I、O、0、1。
     *
     * @return 验证码
     */
    private String generateVerifyCode() {
        StringBuilder code = new StringBuilder(5);
        for (int i = 0; i < 5; i++) {
            code.append(CAPTCHA_ALPHABET[VERIFY_CODE_RANDOM.nextInt(CAPTCHA_ALPHABET.length)]);
        }
        return code.toString();
    }

    /**
     * 生成登录页图形验证码图片。
     *
     * @param code 验证码明文，仅用于本次响应图片绘制，不落库
     * @return data URL 图片
     */
    private String generateCaptchaImage(String code) {
        int width = 150;
        int height = 50;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(new Color(245, 249, 255));
            graphics.fillRoundRect(0, 0, width, height, 10, 10);
            drawCaptchaNoise(graphics, width, height);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
            FontMetrics metrics = graphics.getFontMetrics();
            int charGap = 24;
            int startX = Math.max(12, (width - charGap * code.length()) / 2 + 4);
            int baseY = (height - metrics.getHeight()) / 2 + metrics.getAscent();
            for (int i = 0; i < code.length(); i++) {
                graphics.setColor(captchaTextColor(i));
                double angle = Math.toRadians(VERIFY_CODE_RANDOM.nextInt(25) - 12);
                graphics.rotate(angle, startX + (double) i * charGap, baseY);
                graphics.drawString(String.valueOf(code.charAt(i)), startX + i * charGap, baseY + VERIFY_CODE_RANDOM.nextInt(5) - 2);
                graphics.rotate(-angle, startX + (double) i * charGap, baseY);
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            return CAPTCHA_IMAGE_PREFIX + Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (IOException exception) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(), "captcha image can not be generated", exception);
        } finally {
            graphics.dispose();
        }
    }

    private void drawCaptchaNoise(Graphics2D graphics, int width, int height) {
        for (int i = 0; i < 8; i++) {
            graphics.setColor(new Color(180 + VERIFY_CODE_RANDOM.nextInt(50), 195 + VERIFY_CODE_RANDOM.nextInt(40), 215 + VERIFY_CODE_RANDOM.nextInt(35)));
            int x1 = VERIFY_CODE_RANDOM.nextInt(width);
            int y1 = VERIFY_CODE_RANDOM.nextInt(height);
            int x2 = VERIFY_CODE_RANDOM.nextInt(width);
            int y2 = VERIFY_CODE_RANDOM.nextInt(height);
            graphics.drawLine(x1, y1, x2, y2);
        }
        for (int i = 0; i < 60; i++) {
            graphics.setColor(new Color(160 + VERIFY_CODE_RANDOM.nextInt(70), 175 + VERIFY_CODE_RANDOM.nextInt(60), 200 + VERIFY_CODE_RANDOM.nextInt(45)));
            graphics.fillOval(VERIFY_CODE_RANDOM.nextInt(width), VERIFY_CODE_RANDOM.nextInt(height), 2, 2);
        }
    }

    private Color captchaTextColor(int index) {
        Color[] colors = {
                new Color(29, 78, 216),
                new Color(15, 118, 110),
                new Color(126, 34, 206),
                new Color(190, 80, 20),
                new Color(8, 47, 73)
        };
        return colors[index % colors.length];
    }

    /**
     * 脱敏验证码接收人。
     *
     * @param receiver     接收人
     * @param receiverType 接收方式
     * @return 脱敏接收人
     */
    private String maskReceiver(String receiver, String receiverType) {
        if (!StringUtils.hasText(receiver)) {
            return "-";
        }
        if ("EMAIL".equals(receiverType) && receiver.contains("@")) {
            String[] parts = receiver.split("@", 2);
            String prefix = parts[0].length() <= 2 ? parts[0] : parts[0].substring(0, 2);
            return prefix + "***@" + parts[1];
        }
        if (receiver.length() >= 7) {
            return receiver.substring(0, 3) + "****" + receiver.substring(receiver.length() - 4);
        }
        return receiver.charAt(0) + "***";
    }

    /**
     * 查询注册角色。
     *
     * @param app      系统应用
     * @param roleCode 指定角色编码
     * @return 角色
     */
    private SysRoleDO getRegisterRole(SysAppDO app, AuthRegisterRequest request) {
        String targetRole = StringUtils.hasText(request.getRoleCode())
                ? request.getRoleCode()
                : defaultRoleCode(app.getAppCode(), request.getMerchantId());
        SysRoleDO role = sysRoleMapper.selectOne(
                Wrappers.<SysRoleDO>lambdaQuery()
                        .eq(SysRoleDO::getAppId, app.getId())
                        .eq(SysRoleDO::getRoleCode, targetRole)
                        .eq(SysRoleDO::getStatus, AuthConstants.ENABLED)
                        .eq(SysRoleDO::getDeleted, AuthConstants.NOT_DELETED)
                        .last("LIMIT 1")
        );
        if (role == null && AuthConstants.APP_MERCHANT.equals(app.getAppCode())) {
            role = sysRoleMapper.selectOne(
                    Wrappers.<SysRoleDO>lambdaQuery()
                            .eq(SysRoleDO::getAppId, app.getId())
                            .eq(SysRoleDO::getRoleCode, AuthConstants.DEFAULT_MERCHANT_ROLE)
                            .eq(SysRoleDO::getStatus, AuthConstants.ENABLED)
                            .eq(SysRoleDO::getDeleted, AuthConstants.NOT_DELETED)
                            .last("LIMIT 1")
            );
        }
        if (role == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "role not found:" + targetRole);
        }
        return role;
    }

    /**
     * 获取应用默认角色编码。
     *
     * @param appCode    应用编码
     * @param merchantId 商户号
     * @return 默认角色编码
     */
    private String defaultRoleCode(String appCode, String merchantId) {
        if (!AuthConstants.APP_MERCHANT.equals(appCode)) {
            return AuthConstants.DEFAULT_ADMIN_ROLE;
        }
        if (StringUtils.hasText(merchantId)) {
            return AuthConstants.DEFAULT_MERCHANT_ROLE + "_" + merchantId;
        }
        return AuthConstants.DEFAULT_MERCHANT_ROLE;
    }

    /**
     * 校验商户系统注册请求。
     *
     * @param appCode    应用编码
     * @param merchantId 商户号
     */
    private void validateMerchantRegister(String appCode, String merchantId) {
        if (!AuthConstants.APP_MERCHANT.equals(appCode)) {
            return;
        }
        if (!StringUtils.hasText(merchantId)) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "merchantId is required");
        }
        BaseMerchantInfoDO merchantInfo = baseMerchantInfoMapper.selectOne(
                Wrappers.<BaseMerchantInfoDO>lambdaQuery()
                        .eq(BaseMerchantInfoDO::getMerchantId, merchantId)
                        .eq(BaseMerchantInfoDO::getMerchantStatus, AuthConstants.ENABLED)
                        .eq(BaseMerchantInfoDO::getDeleted, 0)
                        .last("LIMIT 1")
        );
        if (merchantInfo == null) {
            throw new ServiceException(ApiResultEnum.MERCHANT_INVALID);
        }
    }

    /**
     * 校验商户系统登录必须明确商户号。
     *
     * @param appCode    应用编码
     * @param merchantId 商户号
     */
    private void validateMerchantLogin(String appCode, String merchantId) {
        if (!AuthConstants.APP_MERCHANT.equals(appCode)) {
            return;
        }
        if (!StringUtils.hasText(merchantId)) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "merchantId is required");
        }
        BaseMerchantInfoDO merchantInfo = baseMerchantInfoMapper.selectOne(
                Wrappers.<BaseMerchantInfoDO>lambdaQuery()
                        .eq(BaseMerchantInfoDO::getMerchantId, merchantId)
                        .eq(BaseMerchantInfoDO::getMerchantStatus, AuthConstants.ENABLED)
                        .eq(BaseMerchantInfoDO::getDeleted, 0)
                        .last("LIMIT 1")
        );
        if (merchantInfo == null) {
            throw new ServiceException(ApiResultEnum.MERCHANT_INVALID);
        }
    }

    /**
     * 断言账号不存在。
     *
     * @param appId        系统应用ID
     * @param loginAccount 登录账号
     */
    private void assertAccountNotExists(Long appId, String loginAccount) {
        SysAccountDO exists = findAccount(appId, loginAccount, null);
        if (exists != null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "loginAccount already exists");
        }
    }

    /**
     * 查询登录账号。
     *
     * @param appId        系统应用ID
     * @param loginAccount 登录账号
     * @param merchantId   商户号，商户系统登录时必传
     * @return 登录账号实体
     */
    private SysAccountDO findAccount(Long appId, String loginAccount, String merchantId) {
        if (!StringUtils.hasText(loginAccount)) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "loginAccount is required");
        }
        return sysAccountMapper.selectOne(
                Wrappers.<SysAccountDO>lambdaQuery()
                        .eq(SysAccountDO::getAppId, appId)
                        .eq(StringUtils.hasText(merchantId), SysAccountDO::getMerchantId, merchantId)
                        .eq(SysAccountDO::getLoginAccount, loginAccount)
                        .eq(SysAccountDO::getDeleted, AuthConstants.NOT_DELETED)
                        .last("LIMIT 1")
        );
    }

    /**
     * 查询登录账号。
     *
     * <p>商户系统先按商户端用户表解析 {@code merchantId + loginAccount}，再回到 sys_account 校验密码；
     * 管理后台继续直接使用 sys_account。</p>
     *
     * @param app          系统应用
     * @param loginAccount 登录输入账号
     * @param merchantId   商户号
     * @return 登录账号实体
     */
    private SysAccountDO findLoginAccount(SysAppDO app, String loginAccount, String merchantId) {
        if (!AuthConstants.APP_MERCHANT.equals(app.getAppCode())) {
            return findAccount(app.getId(), loginAccount, null);
        }
        SysMerchantUserDO merchantUser = findMerchantUser(merchantId, loginAccount);
        if (merchantUser == null || merchantUser.getAccountId() == null) {
            return null;
        }
        SysAccountDO account = sysAccountMapper.selectById(merchantUser.getAccountId());
        if (account == null
                || !Objects.equals(account.getAppId(), app.getId())
                || !merchantId.equals(account.getMerchantId())
                || account.getDeleted() == null
                || account.getDeleted() != AuthConstants.NOT_DELETED) {
            return null;
        }
        return account;
    }

    /**
     * 查询启用商户端用户。
     *
     * @param merchantId    商户号
     * @param loginAccount  商户端登录账号
     * @return 商户端用户，未找到返回 null
     */
    private SysMerchantUserDO findMerchantUser(String merchantId, String loginAccount) {
        if (!StringUtils.hasText(loginAccount)) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "loginAccount is required");
        }
        return sysMerchantUserMapper.selectOne(
                Wrappers.<SysMerchantUserDO>lambdaQuery()
                        .eq(SysMerchantUserDO::getMerchantId, merchantId)
                        .eq(SysMerchantUserDO::getLoginAccount, loginAccount)
                        .eq(SysMerchantUserDO::getStatus, AuthConstants.ENABLED)
                        .eq(SysMerchantUserDO::getDeleted, AuthConstants.NOT_DELETED)
                        .last("LIMIT 1")
        );
    }

    /**
     * 获取启用应用。
     *
     * @param appCode 应用编码
     * @return 系统应用
     */
    private SysAppDO getEnabledApp(String appCode) {
        SysAppDO app = sysAppMapper.selectOne(
                Wrappers.<SysAppDO>lambdaQuery()
                        .eq(SysAppDO::getAppCode, appCode)
                        .eq(SysAppDO::getStatus, AuthConstants.ENABLED)
                        .eq(SysAppDO::getDeleted, AuthConstants.NOT_DELETED)
                        .last("LIMIT 1")
        );
        if (app == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "app not found:" + appCode);
        }
        return app;
    }

    /**
     * 获取启用账号。
     *
     * @param accountId 账号ID
     * @return 登录账号
     */
    private SysAccountDO getEnabledAccount(Long accountId) {
        SysAccountDO account = sysAccountMapper.selectById(accountId);
        if (account == null || account.getStatus() == null || account.getStatus() != AuthConstants.ENABLED) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED.getCode(), "account is invalid");
        }
        return account;
    }

    /**
     * 获取启用用户主体。
     *
     * @param userId 用户主体ID
     * @return 用户主体
     */
    private SysUserDO getEnabledUser(Long userId) {
        SysUserDO user = sysUserMapper.selectById(userId);
        if (user == null || user.getStatus() == null || user.getStatus() != AuthConstants.ENABLED) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED.getCode(), "user is invalid");
        }
        return user;
    }

    /**
     * 获取当前账号对应的启用商户端用户。
     *
     * @param account 登录账号
     * @return 商户端用户
     */
    private SysMerchantUserDO getEnabledMerchantUser(SysAccountDO account) {
        SysMerchantUserDO merchantUser = sysMerchantUserMapper.selectOne(
                Wrappers.<SysMerchantUserDO>lambdaQuery()
                        .eq(SysMerchantUserDO::getAccountId, account.getId())
                        .eq(SysMerchantUserDO::getMerchantId, account.getMerchantId())
                        .eq(SysMerchantUserDO::getStatus, AuthConstants.ENABLED)
                        .eq(SysMerchantUserDO::getDeleted, AuthConstants.NOT_DELETED)
                        .last("LIMIT 1")
        );
        if (merchantUser == null) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED.getCode(), "merchant user is invalid");
        }
        return merchantUser;
    }

    /**
     * 增加登录失败次数。
     *
     * @param account 登录账号
     */
    private void increaseFailedLoginCount(SysAccountDO account) {
        account.setFailedLoginCount(defaultInt(account.getFailedLoginCount()) + 1);
        account.setUpdatedAt(LocalDateTime.now());
        sysAccountMapper.updateById(account);
    }

    /**
     * 更新登录成功信息。
     *
     * @param account  登录账号
     * @param clientIp 客户端IP
     * @param now      当前时间
     */
    private void updateAccountLoginSuccess(SysAccountDO account, String clientIp, LocalDateTime now) {
        account.setLastLoginAt(now);
        account.setLastLoginIp(clientIp);
        account.setFailedLoginCount(0);
        account.setUpdatedAt(now);
        sysAccountMapper.updateById(account);
    }

    /**
     * 签发登录会话并构建完整登录响应。
     *
     * @param app          系统应用
     * @param user         用户主体
     * @param account      登录账号
     * @param loginAccount 登录输入账号
     * @param clientIp     客户端 IP
     * @param userAgent    User-Agent
     * @return 登录响应
     */
    private AuthLoginResponse issueLoginSession(SysAppDO app,
                                                SysUserDO user,
                                                SysAccountDO account,
                                                String loginAccount,
                                                String clientIp,
                                                String userAgent) {
        String token = LoginTokenUtils.generateToken();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expireAt = now.plusSeconds(AuthConstants.DEFAULT_TOKEN_TTL_SECONDS);
        saveLoginSession(app, account, token, clientIp, userAgent, now, expireAt);
        updateAccountLoginSuccess(account, clientIp, now);
        recordLoginLog(app, account, loginAccount, clientIp, userAgent, LOGIN_SUCCESS, null);
        return buildLoginResponse(app, user, account, token, expireAt);
    }

    /**
     * 保存登录会话。
     *
     * @param app       系统应用
     * @param account   登录账号
     * @param token     token 明文
     * @param clientIp  客户端IP
     * @param userAgent User-Agent
     * @param now       当前时间
     * @param expireAt  过期时间
     */
    private void saveLoginSession(SysAppDO app,
                                  SysAccountDO account,
                                  String token,
                                  String clientIp,
                                  String userAgent,
                                  LocalDateTime now,
                                  LocalDateTime expireAt) {
        SysLoginSessionDO session = new SysLoginSessionDO();
        session.setAppId(app.getId());
        session.setAccountId(account.getId());
        session.setUserId(account.getUserId());
        session.setMerchantId(account.getMerchantId());
        session.setTokenHash(LoginTokenUtils.hashToken(token));
        session.setLoginIp(clientIp);
        session.setUserAgent(userAgent);
        session.setExpireAt(expireAt);
        session.setLogout(NOT_LOGOUT);
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        sysLoginSessionMapper.insert(session);
    }

    /**
     * 查询账号 MFA 配置，老账号未初始化时返回 null 并按 OPTIONAL + NOT_ENABLED 兼容。
     *
     * @param appId     应用 ID
     * @param accountId 账号 ID
     * @return MFA 配置或 null
     */
    private SysAccountMfaDO loadMfa(Long appId, Long accountId) {
        return sysAccountMfaMapper.selectOne(
                Wrappers.<SysAccountMfaDO>lambdaQuery()
                        .eq(SysAccountMfaDO::getAppId, appId)
                        .eq(SysAccountMfaDO::getAccountId, accountId)
                        .eq(SysAccountMfaDO::getDeleted, AuthConstants.NOT_DELETED)
                        .last("LIMIT 1")
        );
    }

    /**
     * 查询账号 MFA 配置，不存在时按未启用处理并拒绝二阶段动作。
     *
     * @param account 登录账号
     * @return MFA 配置
     */
    private SysAccountMfaDO requireMfa(SysAccountDO account) {
        SysAccountMfaDO mfa = loadMfa(account.getAppId(), account.getId());
        if (mfa == null) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED.getCode(), "mfa is not enabled");
        }
        return mfa;
    }

    /**
     * 根据 MFA 策略和状态判断登录是否需要 OTP 二阶段。
     *
     * @param mfa MFA 配置
     * @param now 当前时间
     * @return 挑战类型，空表示不需要 MFA
     */
    private String resolveMfaChallenge(SysAccountMfaDO mfa, LocalDateTime now) {
        if (mfa == null
                || AuthConstants.MFA_POLICY_OPTIONAL.equals(mfa.getMfaPolicy())
                || AuthConstants.MFA_STATUS_NOT_ENABLED.equals(mfa.getMfaStatus())) {
            return null;
        }
        if (AuthConstants.MFA_POLICY_EXEMPT.equals(mfa.getMfaPolicy())) {
            if (mfa.getExemptUntil() == null || mfa.getExemptUntil().isAfter(now)) {
                return null;
            }
            mfa.setMfaPolicy(AuthConstants.MFA_POLICY_REQUIRED);
            mfa.setMfaStatus(AuthConstants.MFA_STATUS_PENDING_BIND);
            mfa.setUpdatedAt(now);
            sysAccountMfaMapper.updateById(mfa);
            return AuthConstants.MFA_CHALLENGE_BIND_REQUIRED;
        }
        if (AuthConstants.MFA_STATUS_LOCKED.equals(mfa.getMfaStatus())) {
            if (mfa.getLockedUntil() != null && mfa.getLockedUntil().isAfter(now)) {
                return AuthConstants.MFA_CHALLENGE_LOCKED;
            }
            recoverExpiredMfaLock(mfa, now);
        }
        if (AuthConstants.MFA_STATUS_PENDING_BIND.equals(mfa.getMfaStatus())) {
            return AuthConstants.MFA_CHALLENGE_BIND_REQUIRED;
        }
        if (AuthConstants.MFA_STATUS_RESET_REQUIRED.equals(mfa.getMfaStatus())) {
            return AuthConstants.MFA_CHALLENGE_RESET_BIND_REQUIRED;
        }
        if (AuthConstants.MFA_STATUS_ENABLED.equals(mfa.getMfaStatus())) {
            return AuthConstants.MFA_CHALLENGE_VERIFY_REQUIRED;
        }
        if (AuthConstants.MFA_POLICY_REQUIRED.equals(mfa.getMfaPolicy())
                && AuthConstants.MFA_STATUS_DISABLED.equals(mfa.getMfaStatus())) {
            return AuthConstants.MFA_CHALLENGE_BIND_REQUIRED;
        }
        return null;
    }

    /**
     * 连续失败锁定到期后恢复到可继续验证的状态。
     *
     * @param mfa MFA 配置
     * @param now 当前时间
     */
    private void recoverExpiredMfaLock(SysAccountMfaDO mfa, LocalDateTime now) {
        if (StringUtils.hasText(mfa.getSecretCipher())) {
            mfa.setMfaStatus(AuthConstants.MFA_STATUS_ENABLED);
        } else if (StringUtils.hasText(mfa.getPendingSecretCipher())) {
            mfa.setMfaStatus(AuthConstants.MFA_STATUS_PENDING_BIND);
        } else {
            mfa.setMfaStatus(AuthConstants.MFA_STATUS_RESET_REQUIRED);
        }
        mfa.setFailedVerifyCount(0);
        mfa.setLockedUntil(null);
        mfa.setUpdatedAt(now);
        sysAccountMfaMapper.updateById(mfa);
    }

    /**
     * 待绑定状态缺少临时密钥时生成新的 TOTP 密钥。
     *
     * @param app           系统应用
     * @param account       登录账号
     * @param mfa           MFA 配置
     * @param challengeType MFA 挑战类型
     * @param now           当前时间
     * @return 可用于绑定的 MFA 配置
     */
    private SysAccountMfaDO preparePendingMfaIfNecessary(SysAppDO app,
                                                         SysAccountDO account,
                                                         SysAccountMfaDO mfa,
                                                         String challengeType,
                                                         LocalDateTime now) {
        if (!AuthConstants.MFA_CHALLENGE_BIND_REQUIRED.equals(challengeType)
                && !AuthConstants.MFA_CHALLENGE_RESET_BIND_REQUIRED.equals(challengeType)) {
            return mfa;
        }
        SysAccountMfaDO target = mfa == null ? new SysAccountMfaDO() : mfa;
        boolean insert = target.getId() == null;
        target.setAppId(app.getId());
        target.setAccountId(account.getId());
        target.setUserId(account.getUserId());
        target.setMerchantId(account.getMerchantId());
        target.setMfaPolicy(AuthConstants.MFA_POLICY_REQUIRED);
        target.setMfaStatus(AuthConstants.MFA_CHALLENGE_RESET_BIND_REQUIRED.equals(challengeType)
                ? AuthConstants.MFA_STATUS_RESET_REQUIRED
                : AuthConstants.MFA_STATUS_PENDING_BIND);
        target.setMfaType(AuthConstants.MFA_TYPE_TOTP);
        target.setIssuer(resolveMfaIssuer(app));
        target.setAccountLabel(resolveMfaAccountLabel(app, account));
        target.setFailedVerifyCount(0);
        target.setLastSuccessTimeStep(null);
        target.setLockedUntil(null);
        target.setUpdatedAt(now);
        if (!StringUtils.hasText(target.getPendingSecretCipher())) {
            target.setPendingSecretCipher(MfaSecretCrypto.encrypt(TotpUtils.generateBase32Secret()));
        }
        if (insert) {
            target.setCreatedAt(now);
            target.setDeleted(AuthConstants.NOT_DELETED);
            sysAccountMfaMapper.insert(target);
        } else {
            sysAccountMfaMapper.updateById(target);
        }
        return target;
    }

    /**
     * 创建 MFA 登录票据，票据明文只返回给前端，数据库只保存哈希。
     *
     * @param app           系统应用
     * @param account       登录账号
     * @param challengeType 挑战类型
     * @param clientIp      客户端 IP
     * @param userAgent     User-Agent
     * @param now           当前时间
     * @return MFA 登录票据
     */
    private MfaLoginTicket createMfaLoginTicket(SysAppDO app,
                                                SysAccountDO account,
                                                String challengeType,
                                                String clientIp,
                                                String userAgent,
                                                LocalDateTime now) {
        sysAccountMfaTokenMapper.update(
                Wrappers.<SysAccountMfaTokenDO>lambdaUpdate()
                        .set(SysAccountMfaTokenDO::getUsed, AuthConstants.ENABLED)
                        .set(SysAccountMfaTokenDO::getUsedAt, now)
                        .set(SysAccountMfaTokenDO::getUpdatedAt, now)
                        .eq(SysAccountMfaTokenDO::getAppId, app.getId())
                        .eq(SysAccountMfaTokenDO::getAccountId, account.getId())
                        .eq(SysAccountMfaTokenDO::getTokenType, MFA_TOKEN_TYPE_LOGIN)
                        .eq(SysAccountMfaTokenDO::getUsed, AuthConstants.DISABLED)
                        .eq(SysAccountMfaTokenDO::getDeleted, AuthConstants.NOT_DELETED)
        );
        String rawTicket = LoginTokenUtils.generateToken();
        SysAccountMfaTokenDO token = new SysAccountMfaTokenDO();
        token.setAppId(app.getId());
        token.setAccountId(account.getId());
        token.setTokenType(MFA_TOKEN_TYPE_LOGIN);
        token.setTokenHash(LoginTokenUtils.hashToken(rawTicket));
        token.setChallengeType(challengeType);
        token.setExpireAt(now.plusSeconds(MFA_LOGIN_TICKET_TTL_SECONDS));
        token.setUsed(AuthConstants.DISABLED);
        token.setClientIp(normalizeIp(clientIp));
        token.setUserAgent(userAgent);
        token.setCreatedAt(now);
        token.setUpdatedAt(now);
        token.setDeleted(AuthConstants.NOT_DELETED);
        sysAccountMfaTokenMapper.insert(token);
        return new MfaLoginTicket(rawTicket, token);
    }

    /**
     * 校验 MFA 登录票据并加载账号。
     *
     * @param app         系统应用
     * @param loginTicket MFA 登录票据明文
     * @return 票据上下文
     */
    private MfaTicketContext requireMfaTicket(SysAppDO app, String loginTicket) {
        if (!StringUtils.hasText(loginTicket)) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "loginTicket is required");
        }
        LocalDateTime now = LocalDateTime.now();
        SysAccountMfaTokenDO ticket = sysAccountMfaTokenMapper.selectOne(
                Wrappers.<SysAccountMfaTokenDO>lambdaQuery()
                        .eq(SysAccountMfaTokenDO::getAppId, app.getId())
                        .eq(SysAccountMfaTokenDO::getTokenType, MFA_TOKEN_TYPE_LOGIN)
                        .eq(SysAccountMfaTokenDO::getTokenHash, LoginTokenUtils.hashToken(loginTicket))
                        .eq(SysAccountMfaTokenDO::getUsed, AuthConstants.DISABLED)
                        .eq(SysAccountMfaTokenDO::getDeleted, AuthConstants.NOT_DELETED)
                        .gt(SysAccountMfaTokenDO::getExpireAt, now)
                        .last("LIMIT 1")
        );
        if (ticket == null) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED.getCode(), "loginTicket is invalid or expired");
        }
        SysAccountDO account = getEnabledAccount(ticket.getAccountId());
        if (!Objects.equals(account.getAppId(), app.getId())) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED.getCode(), "loginTicket is invalid or expired");
        }
        return new MfaTicketContext(ticket, account);
    }

    /**
     * 标记 MFA 登录票据已使用。
     *
     * @param ticket 票据实体
     * @param now    当前时间
     */
    private void markMfaTicketUsed(SysAccountMfaTokenDO ticket, LocalDateTime now) {
        ticket.setUsed(AuthConstants.ENABLED);
        ticket.setUsedAt(now);
        ticket.setUpdatedAt(now);
        sysAccountMfaTokenMapper.updateById(ticket);
    }

    /**
     * 验证 TOTP；失败时累计次数、必要时临时锁定并写审计日志。
     *
     * @param app          系统应用
     * @param account      登录账号
     * @param mfa          MFA 配置
     * @param secretCipher 密钥密文
     * @param code         用户输入验证码
     * @param actionType   操作类型
     * @param clientIp     客户端 IP
     * @param userAgent    User-Agent
     * @return 命中的 TOTP 时间步
     */
    private Long verifyTotpOrRecordFailure(SysAppDO app,
                                           SysAccountDO account,
                                           SysAccountMfaDO mfa,
                                           String secretCipher,
                                           String code,
                                           String actionType,
                                           String clientIp,
                                           String userAgent) {
        LocalDateTime now = LocalDateTime.now();
        if (AuthConstants.MFA_STATUS_LOCKED.equals(mfa.getMfaStatus())
                && mfa.getLockedUntil() != null
                && mfa.getLockedUntil().isAfter(now)) {
            recordMfaLog(app, account, mfa, actionType, MFA_RESULT_FAILED, "mfa locked", null, clientIp, userAgent);
            throw new ServiceException(ApiResultEnum.TOO_MANY_REQUESTS.getCode(), "mfa is temporarily locked");
        }
        Long timeStep = TotpUtils.verify(MfaSecretCrypto.decrypt(secretCipher), code, Instant.now(), MFA_TOTP_PERIOD_SECONDS, MFA_TOTP_WINDOW);
        if (timeStep == null || (mfa.getLastSuccessTimeStep() != null && timeStep <= mfa.getLastSuccessTimeStep())) {
            increaseMfaFailedCount(app, account, mfa, actionType, clientIp, userAgent, now);
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED.getCode(), "mfa code is invalid");
        }
        return timeStep;
    }

    /**
     * 累计 OTP 失败次数，达到阈值后临时锁定。
     *
     * @param app        系统应用
     * @param account    登录账号
     * @param mfa        MFA 配置
     * @param actionType 操作类型
     * @param clientIp   客户端 IP
     * @param userAgent  User-Agent
     * @param now        当前时间
     */
    private void increaseMfaFailedCount(SysAppDO app,
                                        SysAccountDO account,
                                        SysAccountMfaDO mfa,
                                        String actionType,
                                        String clientIp,
                                        String userAgent,
                                        LocalDateTime now) {
        int failedCount = defaultInt(mfa.getFailedVerifyCount()) + 1;
        mfa.setFailedVerifyCount(failedCount);
        if (failedCount >= MFA_MAX_FAILED_ATTEMPTS) {
            mfa.setMfaStatus(AuthConstants.MFA_STATUS_LOCKED);
            mfa.setLockedUntil(now.plusMinutes(MFA_LOCK_MINUTES));
        }
        mfa.setUpdatedAt(now);
        sysAccountMfaMapper.updateById(mfa);
        recordMfaLog(app, account, mfa, actionType, MFA_RESULT_FAILED,
                failedCount >= MFA_MAX_FAILED_ATTEMPTS ? "mfa locked by retry limit" : "mfa code invalid",
                null, clientIp, userAgent);
    }

    /**
     * 构建 MFA 挑战响应，账号密码已通过但尚未签发真实登录会话。
     *
     * @param app     系统应用
     * @param user    用户主体
     * @param account 登录账号
     * @param mfa     MFA 配置
     * @param ticket  MFA 登录票据
     * @return MFA 挑战响应
     */
    private AuthLoginResponse buildMfaChallengeResponse(SysAppDO app,
                                                        SysUserDO user,
                                                        SysAccountDO account,
                                                        SysAccountMfaDO mfa,
                                                        MfaLoginTicket ticket) {
        AuthLoginResponse response = new AuthLoginResponse();
        response.setLoginStatus(AuthConstants.LOGIN_STATUS_MFA_REQUIRED);
        response.setMfaRequired(Boolean.TRUE);
        response.setMfaChallengeType(ticket.token().getChallengeType());
        response.setLoginTicket(ticket.rawTicket());
        response.setLoginTicketExpireAt(ticket.token().getExpireAt());
        response.setMfaPolicy(resolveMfaPolicy(mfa));
        response.setMfaStatus(resolveMfaStatus(mfa));
        response.setMfaLockedUntil(mfa == null ? null : mfa.getLockedUntil());
        response.setAccount(toAccountDTO(app, user, account));
        return response;
    }

    /**
     * 构建 MFA 锁定响应，不返回登录票据。
     *
     * @param app     系统应用
     * @param user    用户主体
     * @param account 登录账号
     * @param mfa     MFA 配置
     * @return MFA 锁定响应
     */
    private AuthLoginResponse buildMfaLockedResponse(SysAppDO app, SysUserDO user, SysAccountDO account, SysAccountMfaDO mfa) {
        AuthLoginResponse response = new AuthLoginResponse();
        response.setLoginStatus(AuthConstants.LOGIN_STATUS_MFA_REQUIRED);
        response.setMfaRequired(Boolean.TRUE);
        response.setMfaChallengeType(AuthConstants.MFA_CHALLENGE_LOCKED);
        response.setMfaPolicy(resolveMfaPolicy(mfa));
        response.setMfaStatus(resolveMfaStatus(mfa));
        response.setMfaLockedUntil(mfa == null ? null : mfa.getLockedUntil());
        response.setAccount(toAccountDTO(app, user, account));
        return response;
    }

    /**
     * 记录 MFA 安全审计事件。
     *
     * @param app                系统应用
     * @param account            登录账号
     * @param mfa                MFA 配置
     * @param actionType         操作类型
     * @param result             操作结果
     * @param reason             原因说明
     * @param operatorAccount    操作人
     * @param clientIp           客户端 IP
     * @param userAgent          User-Agent
     */
    private void recordMfaLog(SysAppDO app,
                              SysAccountDO account,
                              SysAccountMfaDO mfa,
                              String actionType,
                              String result,
                              String reason,
                              InternalAuthAccount operatorAccount,
                              String clientIp,
                              String userAgent) {
        SysAccountMfaLogDO log = new SysAccountMfaLogDO();
        log.setAppId(app.getId());
        log.setAccountId(account == null ? null : account.getId());
        log.setUserId(account == null ? null : account.getUserId());
        log.setMerchantId(account == null ? null : account.getMerchantId());
        log.setActionType(actionType);
        log.setResult(result);
        log.setReason(reason);
        log.setBeforePolicy(resolveMfaPolicy(mfa));
        log.setBeforeStatus(resolveMfaStatus(mfa));
        log.setAfterPolicy(resolveMfaPolicy(mfa));
        log.setAfterStatus(resolveMfaStatus(mfa));
        log.setOperatorAccountId(operatorAccount == null ? null : operatorAccount.getAccountId());
        log.setOperatorLoginAccount(operatorAccount == null ? null : operatorAccount.getLoginAccount());
        log.setClientIp(normalizeIp(clientIp));
        log.setUserAgent(userAgent);
        log.setEventTime(LocalDateTime.now());
        log.setCreatedAt(LocalDateTime.now());
        sysAccountMfaLogMapper.insert(log);
    }

    /**
     * 拼接 MFA 状态变化原因。
     *
     * @param beforePolicy 变更前策略
     * @param beforeStatus 变更前状态
     * @param mfa          变更后 MFA 配置
     * @return 状态变化说明
     */
    private String statusChangeReason(String beforePolicy, String beforeStatus, SysAccountMfaDO mfa) {
        return beforePolicy + "/" + beforeStatus + " -> " + resolveMfaPolicy(mfa) + "/" + resolveMfaStatus(mfa);
    }

    /**
     * 获取 MFA 策略展示值。
     *
     * @param mfa MFA 配置
     * @return MFA 策略
     */
    private String resolveMfaPolicy(SysAccountMfaDO mfa) {
        return mfa == null ? AuthConstants.MFA_POLICY_OPTIONAL : mfa.getMfaPolicy();
    }

    /**
     * 获取 MFA 状态展示值。
     *
     * @param mfa MFA 配置
     * @return MFA 状态
     */
    private String resolveMfaStatus(SysAccountMfaDO mfa) {
        return mfa == null ? AuthConstants.MFA_STATUS_NOT_ENABLED : mfa.getMfaStatus();
    }

    /**
     * 解析验证器发行方。
     *
     * @param app 系统应用
     * @return 发行方
     */
    private String resolveMfaIssuer(SysAppDO app) {
        return AuthConstants.APP_ADMIN.equals(app.getAppCode()) ? "Acquiring Admin" : "Acquiring Merchant";
    }

    /**
     * 解析验证器账号标签。
     *
     * @param app     系统应用
     * @param account 登录账号
     * @return 账号标签
     */
    private String resolveMfaAccountLabel(SysAppDO app, SysAccountDO account) {
        if (AuthConstants.APP_MERCHANT.equals(app.getAppCode()) && StringUtils.hasText(account.getMerchantId())) {
            return account.getMerchantId() + ":" + account.getLoginAccount();
        }
        return account.getLoginAccount();
    }

    /**
     * 脱敏登录账号。
     *
     * @param loginAccount 登录账号
     * @return 脱敏账号
     */
    private String maskLoginAccount(String loginAccount) {
        if (!StringUtils.hasText(loginAccount)) {
            return "-";
        }
        if (loginAccount.contains("@")) {
            return maskReceiver(loginAccount, "EMAIL");
        }
        if (loginAccount.length() <= 2) {
            return loginAccount.charAt(0) + "***";
        }
        return loginAccount.substring(0, 2) + "***" + loginAccount.substring(loginAccount.length() - 1);
    }

    private record MfaLoginTicket(String rawTicket, SysAccountMfaTokenDO token) {
    }

    private record MfaTicketContext(SysAccountMfaTokenDO ticket, SysAccountDO account) {
    }

    /**
     * 获取有效会话。
     *
     * @param appId 系统应用ID
     * @param token token 明文
     * @return 登录会话
     */
    private SysLoginSessionDO getActiveSession(Long appId, String token) {
        String tokenHash = LoginTokenUtils.hashToken(extractBearerToken(token));
        LocalDateTime now = LocalDateTime.now();
        SysLoginSessionDO session = sysLoginSessionMapper.selectOne(
                Wrappers.<SysLoginSessionDO>lambdaQuery()
                        .eq(SysLoginSessionDO::getAppId, appId)
                        .eq(SysLoginSessionDO::getTokenHash, tokenHash)
                        .eq(SysLoginSessionDO::getLogout, NOT_LOGOUT)
                        .gt(SysLoginSessionDO::getExpireAt, now)
                        .last("LIMIT 1")
        );
        if (session == null) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED.getCode(), "login token is invalid or expired");
        }
        LocalDateTime lastActiveAt = session.getUpdatedAt() == null ? session.getCreatedAt() : session.getUpdatedAt();
        if (lastActiveAt == null || lastActiveAt.plusMinutes(SESSION_IDLE_TIMEOUT_MINUTES).isBefore(now)) {
            expireIdleSession(session, now);
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED.getCode(), "login token is idle expired");
        }
        touchSession(session, now);
        return session;
    }

    /**
     * 将闲置超时的会话标记为退出，避免同一 token 后续继续被查询为在线。
     *
     * @param session 登录会话
     * @param now     当前时间
     */
    private void expireIdleSession(SysLoginSessionDO session, LocalDateTime now) {
        session.setLogout(LOGOUT);
        session.setLogoutAt(now);
        session.setUpdatedAt(now);
        sysLoginSessionMapper.updateById(session);
    }

    /**
     * 刷新会话最后活跃时间，用于 30 分钟无操作自动退出判定。
     *
     * @param session 登录会话
     * @param now     当前时间
     */
    private void touchSession(SysLoginSessionDO session, LocalDateTime now) {
        session.setUpdatedAt(now);
        sysLoginSessionMapper.updateById(session);
    }

    /**
     * 提取 Bearer token 明文。
     *
     * @param token 请求头 token
     * @return token 明文
     */
    private String extractBearerToken(String token) {
        if (!StringUtils.hasText(token)) {
            throw new ServiceException(ApiResultEnum.AUTHORIZATION_HEADER_MISSING);
        }
        return token.startsWith("Bearer ") ? token.substring("Bearer ".length()) : token;
    }

    /**
     * 记录登录日志。
     *
     * @param app          系统应用
     * @param account      登录账号
     * @param loginAccount 输入账号
     * @param clientIp     客户端IP
     * @param userAgent    User-Agent
     * @param status       登录状态
     * @param failReason   失败原因
     */
    private void recordLoginLog(SysAppDO app,
                                SysAccountDO account,
                                String loginAccount,
                                String clientIp,
                                String userAgent,
                                int status,
                                String failReason) {
        SysLoginLogDO loginLog = new SysLoginLogDO();
        loginLog.setAppId(app.getId());
        loginLog.setAccountId(account == null ? null : account.getId());
        loginLog.setUserId(account == null ? null : account.getUserId());
        loginLog.setMerchantId(account == null ? null : account.getMerchantId());
        loginLog.setLoginAccount(loginAccount);
        loginLog.setLoginIp(clientIp);
        loginLog.setUserAgent(userAgent);
        loginLog.setLoginStatus(status);
        loginLog.setFailReason(failReason);
        loginLog.setLoginAt(LocalDateTime.now());
        loginLog.setCreatedAt(LocalDateTime.now());
        sysLoginLogMapper.insert(loginLog);
    }

    /**
     * 构建登录响应。
     *
     * @param app      系统应用
     * @param user     用户主体
     * @param account  登录账号
     * @param token    token 明文
     * @param expireAt 过期时间
     * @return 登录响应
     */
    private AuthLoginResponse buildLoginResponse(SysAppDO app,
                                                 SysUserDO user,
                                                 SysAccountDO account,
                                                 String token,
                                                 LocalDateTime expireAt) {
        List<Long> roleIds = queryRoleIds(app, account);
        AuthLoginResponse response = new AuthLoginResponse();
        response.setAccessToken(token);
        response.setExpiresIn(AuthConstants.DEFAULT_TOKEN_TTL_SECONDS);
        response.setExpireAt(expireAt);
        response.setAccount(toAccountDTO(app, user, account, roleIds));
        response.setMenus(queryMenuTree(app, account, roleIds));
        response.setRoles(queryRoleCodes(app, roleIds));
        response.setPermissions(queryPermissionCodes(app, account, roleIds));
        SysAccountMfaDO mfa = loadMfa(app.getId(), account.getId());
        response.setLoginStatus(AuthConstants.LOGIN_STATUS_SUCCESS);
        response.setMfaRequired(Boolean.FALSE);
        response.setMfaPolicy(resolveMfaPolicy(mfa));
        response.setMfaStatus(resolveMfaStatus(mfa));
        response.setMfaLockedUntil(mfa == null ? null : mfa.getLockedUntil());
        return response;
    }

    /**
     * 转换账号响应。
     *
     * @param app     系统应用
     * @param user    用户主体
     * @param account 登录账号
     * @param roleIds 当前应用下的有效角色ID集合
     * @return 账号响应
     */
    private AuthAccountDTO toAccountDTO(SysAppDO app, SysUserDO user, SysAccountDO account) {
        return toAccountDTO(app, user, account, queryRoleIds(app, account));
    }

    /**
     * 转换账号响应。
     *
     * @param app     系统应用
     * @param user    用户主体
     * @param account 登录账号
     * @param roleIds 当前应用下的有效角色ID集合
     * @return 账号响应
     */
    private AuthAccountDTO toAccountDTO(SysAppDO app, SysUserDO user, SysAccountDO account, List<Long> roleIds) {
        AuthAccountDTO dto = new AuthAccountDTO();
        dto.setAccountId(account.getId());
        dto.setUserId(user.getId());
        dto.setAppCode(app.getAppCode());
        dto.setLoginAccount(account.getLoginAccount());
        dto.setRealName(user.getRealName());
        dto.setNickname(user.getNickname());
        dto.setMobile(firstText(user.getMobile(), account.getMobile()));
        dto.setEmail(firstText(user.getEmail(), account.getEmail()));
        dto.setRoleNames(queryRoleNames(app, roleIds));
        dto.setCreatedAt(user.getCreatedAt());
        dto.setMerchantId(account.getMerchantId());
        dto.setStatus(account.getStatus());
        if (AuthConstants.APP_MERCHANT.equals(app.getAppCode())) {
            SysMerchantUserDO merchantUser = getEnabledMerchantUser(account);
            dto.setMerchantUserId(merchantUser.getId());
            dto.setLoginAccount(merchantUser.getLoginAccount());
            dto.setRealName(StringUtils.hasText(merchantUser.getRealName()) ? merchantUser.getRealName() : user.getRealName());
            dto.setMerchantAdmin(isMerchantSuperAdmin(app, account, roleIds));
        }
        return dto;
    }

    /**
     * 返回第一个非空文本，用于用户主体资料和登录账号资料之间做兼容兜底。
     *
     * @param primary   主来源
     * @param secondary 兜底来源
     * @return 非空文本
     */
    private String firstText(String primary, String secondary) {
        return StringUtils.hasText(primary) ? primary : secondary;
    }

    /**
     * 获取必填文本并去除首尾空白。
     *
     * @param value     原始文本
     * @param fieldName 字段名
     * @return 规范化文本
     */
    private String requiredText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), fieldName + " is required");
        }
        return value.trim();
    }

    /**
     * 获取可选文本并去除首尾空白。
     *
     * @param value 原始文本
     * @return 规范化文本或 null
     */
    private String optionalText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * 查询账号角色ID。
     *
     * @param appId     系统应用ID
     * @param accountId 账号ID
     * @return 角色ID集合
     */
    private List<Long> queryRoleIds(Long appId, Long accountId) {
        return sysAccountRoleMapper.selectList(
                        Wrappers.<SysAccountRoleDO>lambdaQuery()
                                .eq(SysAccountRoleDO::getAppId, appId)
                                .eq(SysAccountRoleDO::getAccountId, accountId)
                                .eq(SysAccountRoleDO::getDeleted, AuthConstants.NOT_DELETED)
                ).stream()
                .map(SysAccountRoleDO::getRoleId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    /**
     * 查询当前登录账号在指定应用下的有效角色ID。
     *
     * <p>商户系统以商户端用户为授权主体，角色来自 sys_merchant_user_role；
     * 管理后台继续沿用 sys_account_role。</p>
     *
     * @param app     系统应用
     * @param account 登录账号
     * @return 角色ID集合
     */
    private List<Long> queryRoleIds(SysAppDO app, SysAccountDO account) {
        if (!AuthConstants.APP_MERCHANT.equals(app.getAppCode())) {
            return queryRoleIds(app.getId(), account.getId());
        }
        SysMerchantUserDO merchantUser = getEnabledMerchantUser(account);
        return sysMerchantUserRoleMapper.selectList(
                        Wrappers.<SysMerchantUserRoleDO>lambdaQuery()
                                .eq(SysMerchantUserRoleDO::getAppId, app.getId())
                                .eq(SysMerchantUserRoleDO::getMerchantUserId, merchantUser.getId())
                                .eq(SysMerchantUserRoleDO::getDeleted, AuthConstants.NOT_DELETED)
                ).stream()
                .map(SysMerchantUserRoleDO::getRoleId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    /**
     * 查询账号角色编码。
     *
     * @param app     系统应用
     * @param account 登录账号
     * @return 角色编码集合
     */
    private List<String> queryRoleCodes(SysAppDO app, SysAccountDO account) {
        return queryRoleCodes(app, queryRoleIds(app, account));
    }

    /**
     * 查询账号角色编码。
     *
     * @param app     系统应用
     * @param roleIds 角色ID集合
     * @return 角色编码集合
     */
    private List<String> queryRoleCodes(SysAppDO app, List<Long> roleIds) {
        Long appId = app.getId();
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return sysRoleMapper.selectList(
                        Wrappers.<SysRoleDO>lambdaQuery()
                                .eq(SysRoleDO::getAppId, appId)
                                .in(SysRoleDO::getId, roleIds)
                                .eq(SysRoleDO::getStatus, AuthConstants.ENABLED)
                                .eq(SysRoleDO::getDeleted, AuthConstants.NOT_DELETED)
                ).stream()
                .map(SysRoleDO::getRoleCode)
                .filter(StringUtils::hasText)
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * 查询账号角色名称。
     *
     * @param app     系统应用
     * @param roleIds 角色ID集合
     * @return 角色名称集合
     */
    private List<String> queryRoleNames(SysAppDO app, List<Long> roleIds) {
        Long appId = app.getId();
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return sysRoleMapper.selectList(
                        Wrappers.<SysRoleDO>lambdaQuery()
                                .eq(SysRoleDO::getAppId, appId)
                                .in(SysRoleDO::getId, roleIds)
                                .eq(SysRoleDO::getStatus, AuthConstants.ENABLED)
                                .eq(SysRoleDO::getDeleted, AuthConstants.NOT_DELETED)
                ).stream()
                .map(SysRoleDO::getRoleName)
                .filter(StringUtils::hasText)
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * 查询菜单树。
     *
     * @param app     系统应用
     * @param account 登录账号
     * @param roleIds 角色ID集合
     * @return 菜单树
     */
    private List<AuthMenuDTO> queryMenuTree(SysAppDO app, SysAccountDO account, List<Long> roleIds) {
        Long appId = app.getId();
        if (roleIds.isEmpty()) {
            return List.of();
        }
        Set<Long> menuIds = resolveEffectiveMenuIds(app, account, roleIds);
        if (menuIds.isEmpty()) {
            return List.of();
        }
        List<AuthMenuDTO> nodes = sysMenuMapper.selectList(
                        Wrappers.<SysMenuDO>lambdaQuery()
                                .eq(SysMenuDO::getAppId, appId)
                                .in(SysMenuDO::getId, menuIds)
                                .ne(SysMenuDO::getMenuType, "BUTTON")
                                .eq(SysMenuDO::getStatus, AuthConstants.ENABLED)
                                .eq(SysMenuDO::getVisible, AuthConstants.ENABLED)
                                .eq(SysMenuDO::getDeleted, AuthConstants.NOT_DELETED)
                                .orderByAsc(SysMenuDO::getSortNo, SysMenuDO::getId)
                ).stream()
                .filter(menu -> !isDeprecatedRiskRuleMenu(menu))
                .map(this::toMenuDTO)
                .toList();
        return buildMenuTree(nodes);
    }

    /**
     * 查询账号最终可见菜单ID。
     *
     * <p>商户系统需要把角色菜单限制在平台给商户开放的菜单范围内。已有环境可能尚未初始化平台授权，
     * 因此未配置授权记录时保持历史角色授权结果，避免升级后商户端菜单突然清空。</p>
     *
     * @param app     系统应用
     * @param account 登录账号
     * @param roleIds 角色ID集合
     * @return 最终菜单ID集合
     */
    private Set<Long> resolveEffectiveMenuIds(SysAppDO app, SysAccountDO account, List<Long> roleIds) {
        Set<Long> roleMenuIds = sysRoleMenuMapper.selectList(
                        Wrappers.<SysRoleMenuDO>lambdaQuery()
                                .eq(SysRoleMenuDO::getAppId, app.getId())
                                .in(SysRoleMenuDO::getRoleId, roleIds)
                                .eq(SysRoleMenuDO::getDeleted, AuthConstants.NOT_DELETED)
                ).stream()
                .map(SysRoleMenuDO::getMenuId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (!AuthConstants.APP_MERCHANT.equals(app.getAppCode()) || !StringUtils.hasText(account.getMerchantId())) {
            return roleMenuIds;
        }
        Set<Long> platformMenuIds = queryMerchantGrantMenuIds(app.getId(), account.getMerchantId());
        if (platformMenuIds.isEmpty()) {
            return roleMenuIds;
        }
        if (isMerchantSuperAdmin(app, account, roleIds)) {
            return platformMenuIds;
        }
        roleMenuIds.retainAll(platformMenuIds);
        return roleMenuIds;
    }

    /**
     * 查询权限编码集合。
     *
     * @param app     系统应用
     * @param account 登录账号
     * @return 权限编码集合
     */
    private List<String> queryPermissionCodes(SysAppDO app, SysAccountDO account) {
        return queryPermissionCodes(app, account, queryRoleIds(app, account));
    }

    /**
     * 查询权限编码集合。
     *
     * @param app     系统应用
     * @param account 登录账号
     * @param roleIds 角色ID集合
     * @return 权限编码集合
     */
    private List<String> queryPermissionCodes(SysAppDO app, SysAccountDO account, List<Long> roleIds) {
        Long appId = app.getId();
        if (roleIds.isEmpty()) {
            return List.of();
        }
        Set<String> permissionCodes = new java.util.TreeSet<>();
        if (isMerchantSuperAdmin(app, account, roleIds)) {
            List<String> platformCodes = queryMerchantGrantPermissionCodes(appId, account.getMerchantId());
            if (!platformCodes.isEmpty()) {
                permissionCodes.addAll(platformCodes);
                permissionCodes.addAll(queryMerchantGrantMenuPermissionCodes(appId, account.getMerchantId()));
                return List.copyOf(permissionCodes);
            }
        }
        List<String> roleMenuCodes = queryMenuPermissionCodes(appId, roleIds);
        List<String> roleApiCodes = queryApiPermissionCodes(appId, roleIds);
        if (AuthConstants.APP_MERCHANT.equals(app.getAppCode()) && StringUtils.hasText(account.getMerchantId())) {
            List<String> platformMenuCodes = queryMerchantGrantMenuPermissionCodes(appId, account.getMerchantId());
            if (!platformMenuCodes.isEmpty()) {
                roleMenuCodes = intersectCodes(roleMenuCodes, platformMenuCodes);
            }
            List<String> platformApiCodes = queryMerchantGrantPermissionCodes(appId, account.getMerchantId());
            if (!platformApiCodes.isEmpty()) {
                roleApiCodes = intersectCodes(roleApiCodes, platformApiCodes);
            }
        }
        permissionCodes.addAll(roleMenuCodes);
        permissionCodes.addAll(roleApiCodes);
        return List.copyOf(permissionCodes);
    }

    /**
     * 查询平台给商户开放的菜单ID集合。
     *
     * @param appId      应用ID
     * @param merchantId 商户号
     * @return 菜单ID集合
     */
    private Set<Long> queryMerchantGrantMenuIds(Long appId, String merchantId) {
        if (!StringUtils.hasText(merchantId)) {
            return Collections.emptySet();
        }
        try {
            return sysMerchantMenuGrantMapper.selectList(
                            Wrappers.<SysMerchantMenuGrantDO>lambdaQuery()
                                    .eq(SysMerchantMenuGrantDO::getAppId, appId)
                                    .eq(SysMerchantMenuGrantDO::getMerchantId, merchantId)
                                    .eq(SysMerchantMenuGrantDO::getStatus, AuthConstants.ENABLED)
                                    .eq(SysMerchantMenuGrantDO::getDeleted, AuthConstants.NOT_DELETED)
                    ).stream()
                    .map(SysMerchantMenuGrantDO::getMenuId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        } catch (BadSqlGrammarException ex) {
            if (isMissingMerchantGrantTable(ex)) {
                return Collections.emptySet();
            }
            throw ex;
        }
    }

    /**
     * 查询平台给商户开放的资源权限ID集合。
     *
     * @param appId      应用ID
     * @param merchantId 商户号
     * @return 权限ID集合
     */
    private Set<Long> queryMerchantGrantPermissionIds(Long appId, String merchantId) {
        if (!StringUtils.hasText(merchantId)) {
            return Collections.emptySet();
        }
        try {
            return sysMerchantPermissionGrantMapper.selectList(
                            Wrappers.<SysMerchantPermissionGrantDO>lambdaQuery()
                                    .eq(SysMerchantPermissionGrantDO::getAppId, appId)
                                    .eq(SysMerchantPermissionGrantDO::getMerchantId, merchantId)
                                    .eq(SysMerchantPermissionGrantDO::getStatus, AuthConstants.ENABLED)
                                    .eq(SysMerchantPermissionGrantDO::getDeleted, AuthConstants.NOT_DELETED)
                    ).stream()
                    .map(SysMerchantPermissionGrantDO::getPermissionId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        } catch (BadSqlGrammarException ex) {
            if (isMissingMerchantGrantTable(ex)) {
                return Collections.emptySet();
            }
            throw ex;
        }
    }

    /**
     * 判断是否为商户授权迁移表尚未创建导致的查询异常。
     *
     * @param ex SQL 语法异常
     * @return true 表示可按“未配置平台授权”兼容处理
     */
    private boolean isMissingMerchantGrantTable(BadSqlGrammarException ex) {
        String message = ex.getMostSpecificCause() == null ? ex.getMessage() : ex.getMostSpecificCause().getMessage();
        return message != null
                && (message.contains("sys_merchant_menu_grant") || message.contains("sys_merchant_permission_grant"))
                && (message.contains("doesn't exist") || message.contains("does not exist"));
    }

    /**
     * 判断当前商户账号是否为商户超级管理员。
     *
     * @param app     系统应用
     * @param account 登录账号
     * @param roleIds 角色ID集合
     * @return true 表示商户超级管理员
     */
    private boolean isMerchantSuperAdmin(SysAppDO app, SysAccountDO account, List<Long> roleIds) {
        if (!AuthConstants.APP_MERCHANT.equals(app.getAppCode()) || !StringUtils.hasText(account.getMerchantId()) || roleIds.isEmpty()) {
            return false;
        }
        Set<String> roleCodes = sysRoleMapper.selectList(
                        Wrappers.<SysRoleDO>lambdaQuery()
                                .eq(SysRoleDO::getAppId, app.getId())
                                .in(SysRoleDO::getId, roleIds)
                                .eq(SysRoleDO::getStatus, AuthConstants.ENABLED)
                                .eq(SysRoleDO::getDeleted, AuthConstants.NOT_DELETED)
                ).stream()
                .map(SysRoleDO::getRoleCode)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        return roleCodes.contains(AuthConstants.DEFAULT_MERCHANT_ROLE)
                || roleCodes.contains(AuthConstants.DEFAULT_MERCHANT_ROLE + "_" + account.getMerchantId());
    }

    /**
     * 查询平台给商户开放菜单自身携带的权限编码。
     *
     * @param appId      应用ID
     * @param merchantId 商户号
     * @return 菜单权限编码
     */
    private List<String> queryMerchantGrantMenuPermissionCodes(Long appId, String merchantId) {
        Set<Long> menuIds = queryMerchantGrantMenuIds(appId, merchantId);
        if (menuIds.isEmpty()) {
            return List.of();
        }
        return sysMenuMapper.selectList(
                        Wrappers.<SysMenuDO>lambdaQuery()
                                .eq(SysMenuDO::getAppId, appId)
                                .in(SysMenuDO::getId, menuIds)
                                .eq(SysMenuDO::getStatus, AuthConstants.ENABLED)
                                .eq(SysMenuDO::getDeleted, AuthConstants.NOT_DELETED)
                ).stream()
                .map(SysMenuDO::getPermissionCode)
                .filter(StringUtils::hasText)
                .filter(code -> !isDeprecatedRiskRulePermission(code))
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * 查询平台给商户开放的资源权限编码。
     *
     * @param appId      应用ID
     * @param merchantId 商户号
     * @return 权限编码集合
     */
    private List<String> queryMerchantGrantPermissionCodes(Long appId, String merchantId) {
        Set<Long> permissionIds = queryMerchantGrantPermissionIds(appId, merchantId);
        if (permissionIds.isEmpty()) {
            return List.of();
        }
        return sysPermissionMapper.selectList(
                        Wrappers.<SysPermissionDO>lambdaQuery()
                                .eq(SysPermissionDO::getAppId, appId)
                                .in(SysPermissionDO::getId, permissionIds)
                                .eq(SysPermissionDO::getStatus, AuthConstants.ENABLED)
                                .eq(SysPermissionDO::getDeleted, AuthConstants.NOT_DELETED)
                ).stream()
                .map(SysPermissionDO::getPermissionCode)
                .filter(StringUtils::hasText)
                .filter(code -> !isDeprecatedRiskRulePermission(code))
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * 对权限编码取交集。
     *
     * @param roleCodes     角色权限编码
     * @param platformCodes 平台授权权限编码
     * @return 交集编码
     */
    private List<String> intersectCodes(List<String> roleCodes, List<String> platformCodes) {
        Set<String> platformSet = Set.copyOf(platformCodes);
        return roleCodes.stream()
                .filter(platformSet::contains)
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * 查询角色绑定菜单上的权限编码。
     *
     * <p>兼容历史 RBAC 模型：部分后台按钮权限仍直接挂载在菜单树上，
     * 登录态需要继续下发这些权限编码，避免页面按钮与详情授权树出现误判。</p>
     *
     * @param appId   系统应用ID
     * @param roleIds 角色ID集合
     * @return 菜单权限编码集合
     */
    private List<String> queryMenuPermissionCodes(Long appId, List<Long> roleIds) {
        Set<Long> menuIds = sysRoleMenuMapper.selectList(
                        Wrappers.<SysRoleMenuDO>lambdaQuery()
                                .eq(SysRoleMenuDO::getAppId, appId)
                                .in(SysRoleMenuDO::getRoleId, roleIds)
                                .eq(SysRoleMenuDO::getDeleted, AuthConstants.NOT_DELETED)
                ).stream()
                .map(SysRoleMenuDO::getMenuId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (menuIds.isEmpty()) {
            return List.of();
        }
        return sysMenuMapper.selectList(
                        Wrappers.<SysMenuDO>lambdaQuery()
                                .eq(SysMenuDO::getAppId, appId)
                                .in(SysMenuDO::getId, menuIds)
                                .eq(SysMenuDO::getStatus, AuthConstants.ENABLED)
                                .eq(SysMenuDO::getDeleted, AuthConstants.NOT_DELETED)
                ).stream()
                .map(SysMenuDO::getPermissionCode)
                .filter(StringUtils::hasText)
                .filter(code -> !isDeprecatedRiskRulePermission(code))
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * 查询角色绑定资源权限上的权限编码。
     *
     * <p>新的接口鉴权模型基于 {@code sys_permission} 资源权限表，
     * 需要与菜单权限一并下发给登录态，确保按钮显隐和接口鉴权同时生效。</p>
     *
     * @param appId   系统应用ID
     * @param roleIds 角色ID集合
     * @return 资源权限编码集合
     */
    private List<String> queryApiPermissionCodes(Long appId, List<Long> roleIds) {
        Set<Long> permissionIds = sysRolePermissionMapper.selectList(
                        Wrappers.<SysRolePermissionDO>lambdaQuery()
                                .eq(SysRolePermissionDO::getAppId, appId)
                                .in(SysRolePermissionDO::getRoleId, roleIds)
                                .eq(SysRolePermissionDO::getDeleted, AuthConstants.NOT_DELETED)
                ).stream()
                .map(SysRolePermissionDO::getPermissionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (permissionIds.isEmpty()) {
            return List.of();
        }
        return sysPermissionMapper.selectList(
                        Wrappers.<SysPermissionDO>lambdaQuery()
                                .eq(SysPermissionDO::getAppId, appId)
                                .in(SysPermissionDO::getId, permissionIds)
                                .eq(SysPermissionDO::getStatus, AuthConstants.ENABLED)
                                .eq(SysPermissionDO::getDeleted, AuthConstants.NOT_DELETED)
                ).stream()
                .map(SysPermissionDO::getPermissionCode)
                .filter(StringUtils::hasText)
                .filter(code -> !isDeprecatedRiskRulePermission(code))
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * 判断菜单是否为已废弃的内风控规则功能。旧库或旧角色授权中可能仍存在历史菜单，
     * 登录态下发前统一拦截，避免前端继续展示不可用入口。
     *
     * @param menu 菜单实体
     * @return true 表示该菜单应从登录态中过滤
     */
    private boolean isDeprecatedRiskRuleMenu(SysMenuDO menu) {
        return DEPRECATED_RISK_RULE_MENU_CODES.contains(menu.getMenuCode())
                || DEPRECATED_RISK_RULE_ROUTE_PATHS.contains(menu.getRoutePath())
                || isDeprecatedRiskRulePermission(menu.getPermissionCode());
    }

    /**
     * 判断权限编码是否属于已废弃的内风控规则功能。
     *
     * @param permissionCode 权限编码
     * @return true 表示该权限应从登录态中过滤
     */
    private boolean isDeprecatedRiskRulePermission(String permissionCode) {
        if (!StringUtils.hasText(permissionCode)) {
            return false;
        }
        return DEPRECATED_RISK_RULE_PERMISSION_PREFIXES.stream()
                .anyMatch(prefix -> permissionCode.equals(prefix) || permissionCode.startsWith(prefix + ":"));
    }

    /**
     * 查询当前接口需要的权限编码。
     *
     * @param appId         系统应用ID
     * @param requestMethod HTTP 方法
     * @param requestPath   请求路径
     * @return 权限编码，未配置则为空
     */
    private String findRequiredPermission(Long appId, String requestMethod, String requestPath) {
        if (!StringUtils.hasText(requestPath)) {
            return null;
        }
        List<SysPermissionDO> permissions = sysPermissionMapper.selectList(
                Wrappers.<SysPermissionDO>lambdaQuery()
                        .eq(SysPermissionDO::getAppId, appId)
                        .eq(SysPermissionDO::getPermissionType, "API")
                        .eq(SysPermissionDO::getStatus, AuthConstants.ENABLED)
                        .eq(SysPermissionDO::getDeleted, AuthConstants.NOT_DELETED)
                        .isNotNull(SysPermissionDO::getResourcePath)
        );
        SysPermissionDO matchedPermission = null;
        int matchedScore = -1;
        for (SysPermissionDO permission : permissions) {
            if (!matchMethod(permission.getResourceMethod(), requestMethod)) {
                continue;
            }
            if (PATH_MATCHER.match(permission.getResourcePath(), requestPath)) {
                int score = matchScore(permission);
                if (score > matchedScore) {
                    matchedPermission = permission;
                    matchedScore = score;
                }
            }
        }
        return matchedPermission == null ? null : matchedPermission.getPermissionCode();
    }

    /**
     * 判断权限 HTTP 方法是否匹配当前请求。
     *
     * @param permissionMethod 权限配置方法
     * @param requestMethod    当前请求方法
     * @return true 表示匹配
     */
    private boolean matchMethod(String permissionMethod, String requestMethod) {
        return !StringUtils.hasText(permissionMethod)
                || "*".equals(permissionMethod)
                || permissionMethod.equalsIgnoreCase(requestMethod);
    }

    /**
     * 计算权限匹配优先级，精确 HTTP 方法优先于通配方法，路径越长越优先。
     *
     * @param permission 权限配置
     * @return 匹配分数
     */
    private int matchScore(SysPermissionDO permission) {
        int methodScore = StringUtils.hasText(permission.getResourceMethod())
                && !"*".equals(permission.getResourceMethod()) ? 1000 : 0;
        return methodScore + permission.getResourcePath().length();
    }

    /**
     * 构建当前登录上下文。
     *
     * @param app             系统应用
     * @param user            用户主体
     * @param account         登录账号
     * @param roleCodes       角色编码集合
     * @param permissionCodes 权限编码集合
     * @return 当前登录上下文
     */
    private InternalAuthAccount buildInternalAuthAccount(SysAppDO app,
                                                        SysUserDO user,
                                                        SysAccountDO account,
                                                        List<String> roleCodes,
                                                        List<String> permissionCodes) {
        InternalAuthAccount authAccount = new InternalAuthAccount();
        authAccount.setAppCode(app.getAppCode());
        authAccount.setAppId(app.getId());
        authAccount.setAccountId(account.getId());
        authAccount.setUserId(user.getId());
        authAccount.setMerchantId(account.getMerchantId());
        authAccount.setLoginAccount(account.getLoginAccount());
        authAccount.setRealName(user.getRealName());
        authAccount.setRoles(roleCodes);
        authAccount.setPermissions(permissionCodes);
        return authAccount;
    }

    /**
     * 转换菜单节点。
     *
     * @param menuDO 菜单实体
     * @return 菜单节点
     */
    private AuthMenuDTO toMenuDTO(SysMenuDO menuDO) {
        AuthMenuDTO dto = new AuthMenuDTO();
        dto.setId(menuDO.getId());
        dto.setParentId(menuDO.getParentId());
        dto.setMenuCode(menuDO.getMenuCode());
        dto.setMenuName(menuDO.getMenuName());
        dto.setMenuType(menuDO.getMenuType());
        dto.setRoutePath(menuDO.getRoutePath());
        dto.setComponentPath(menuDO.getComponentPath());
        dto.setPermissionCode(menuDO.getPermissionCode());
        dto.setIcon(menuDO.getIcon());
        dto.setSortNo(menuDO.getSortNo());
        dto.setExternalLink(menuDO.getExternalLink());
        return dto;
    }

    /**
     * 构建菜单树。
     *
     * @param nodes 平铺菜单节点
     * @return 菜单树
     */
    private List<AuthMenuDTO> buildMenuTree(List<AuthMenuDTO> nodes) {
        Map<Long, AuthMenuDTO> nodeMap = nodes.stream().collect(Collectors.toMap(AuthMenuDTO::getId, item -> item));
        List<AuthMenuDTO> roots = new ArrayList<>();
        for (AuthMenuDTO node : nodes) {
            if (node.getParentId() == null || node.getParentId() == ROOT_MENU_PARENT_ID || !nodeMap.containsKey(node.getParentId())) {
                roots.add(node);
                continue;
            }
            nodeMap.get(node.getParentId()).getChildren().add(node);
        }
        sortMenus(roots);
        return roots;
    }

    /**
     * 递归排序菜单节点。
     *
     * @param menus 菜单节点集合
     */
    private void sortMenus(List<AuthMenuDTO> menus) {
        menus.sort(Comparator.comparing(AuthMenuDTO::getSortNo, Comparator.nullsLast(Integer::compareTo)));
        for (AuthMenuDTO menu : menus) {
            sortMenus(menu.getChildren());
        }
    }

    /**
     * 获取非空整数。
     *
     * @param value 入参
     * @return 非空整数
     */
    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }
}
