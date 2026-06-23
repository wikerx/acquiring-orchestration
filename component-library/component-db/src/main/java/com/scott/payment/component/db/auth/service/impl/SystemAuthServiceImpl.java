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
import com.scott.payment.component.db.auth.dto.AuthMenuDTO;
import com.scott.payment.component.db.auth.dto.AuthRegisterRequest;
import com.scott.payment.component.db.auth.dto.AuthVerifyCodeSendRequest;
import com.scott.payment.component.db.auth.dto.AuthVerifyCodeSendResponse;
import com.scott.payment.component.db.auth.entity.BaseMerchantInfoDO;
import com.scott.payment.component.db.auth.entity.SysAccountDO;
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
import com.scott.payment.component.db.constant.DataSourceName;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
     * 本地开发验证码发送渠道。
     */
    private static final String VERIFY_CODE_SEND_CHANNEL = "LOCAL_DEV";

    /**
     * 验证码随机数生成器。
     */
    private static final SecureRandom VERIFY_CODE_RANDOM = new SecureRandom();

    /**
     * 接口资源通配路径匹配器。
     */
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final SysAppMapper sysAppMapper;
    private final SysUserMapper sysUserMapper;
    private final SysAccountMapper sysAccountMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysAccountRoleMapper sysAccountRoleMapper;
    private final SysRoleMenuMapper sysRoleMenuMapper;
    private final SysRolePermissionMapper sysRolePermissionMapper;
    private final SysMenuMapper sysMenuMapper;
    private final SysPermissionMapper sysPermissionMapper;
    private final SysMerchantMenuGrantMapper sysMerchantMenuGrantMapper;
    private final SysMerchantPermissionGrantMapper sysMerchantPermissionGrantMapper;
    private final SysMerchantUserMapper sysMerchantUserMapper;
    private final SysMerchantUserRoleMapper sysMerchantUserRoleMapper;
    private final SysLoginLogMapper sysLoginLogMapper;
    private final SysLoginSessionMapper sysLoginSessionMapper;
    private final SysVerifyCodeMapper sysVerifyCodeMapper;
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
        this.baseMerchantInfoMapper = baseMerchantInfoMapper;
    }

    /**
     * 注册系统账号。
     *
     * @param appCode 系统应用编码
     * @param request 注册请求
     * @return 注册后的账号信息
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
     * 发送登录动态验证码。
     *
     * @param appCode  系统应用编码
     * @param request  验证码发送请求
     * @param clientIp 客户端IP
     * @return 验证码发送响应
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
        validateMerchantLogin(appCode, request.getMerchantId());
        SysAccountDO account = findLoginAccount(app, request.getLoginAccount(), request.getMerchantId());
        if (account == null || account.getStatus() == null || account.getStatus() != AuthConstants.ENABLED) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED.getCode(), "account disabled or not found");
        }
        if (StringUtils.hasText(request.getMerchantId()) && !request.getMerchantId().equals(account.getMerchantId())) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED.getCode(), "merchant mismatch");
        }

        LocalDateTime now = LocalDateTime.now();
        String code = generateVerifyCode();
        String salt = PasswordHashUtils.generateSalt();
        SysVerifyCodeDO verifyCode = new SysVerifyCodeDO();
        verifyCode.setAppId(app.getId());
        verifyCode.setScene(VERIFY_SCENE_LOGIN);
        verifyCode.setReceiverType(resolveReceiverType(account));
        verifyCode.setReceiver(resolveReceiver(account));
        verifyCode.setCodeSalt(salt);
        verifyCode.setCodeHash(PasswordHashUtils.hashPassword(code, salt));
        verifyCode.setExpireAt(now.plusSeconds(VERIFY_CODE_TTL_SECONDS));
        verifyCode.setUsed(AuthConstants.DISABLED);
        verifyCode.setVerifyCount(0);
        verifyCode.setSendIp(clientIp);
        verifyCode.setSendChannel(VERIFY_CODE_SEND_CHANNEL);
        verifyCode.setSendStatus(AuthConstants.ENABLED);
        verifyCode.setCreatedAt(now);
        sysVerifyCodeMapper.insert(verifyCode);

        AuthVerifyCodeSendResponse response = new AuthVerifyCodeSendResponse();
        response.setVerifyCodeId(String.valueOf(verifyCode.getId()));
        response.setReceiverType(verifyCode.getReceiverType());
        response.setMaskedReceiver(maskReceiver(verifyCode.getReceiver(), verifyCode.getReceiverType()));
        response.setExpireSeconds(VERIFY_CODE_TTL_SECONDS);
        response.setDevCode(code);
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
        validateLoginVerifyCode(app, account, request);
        if (!PasswordHashUtils.matches(request.getPassword(), account.getPasswordSalt(), account.getPasswordHash())) {
            increaseFailedLoginCount(account);
            recordLoginLog(app, account, request.getLoginAccount(), clientIp, userAgent, LOGIN_FAILED, "password mismatch");
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED.getCode(), "account or password is invalid");
        }

        SysUserDO user = getEnabledUser(account.getUserId());
        String token = LoginTokenUtils.generateToken();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expireAt = now.plusSeconds(AuthConstants.DEFAULT_TOKEN_TTL_SECONDS);
        saveLoginSession(app, account, token, clientIp, userAgent, now, expireAt);
        updateAccountLoginSuccess(account, clientIp, now);
        recordLoginLog(app, account, request.getLoginAccount(), clientIp, userAgent, LOGIN_SUCCESS, null);
        return buildLoginResponse(app, user, account, token, expireAt);
    }

    /**
     * 根据 token 查询当前登录账号。
     *
     * @param appCode 系统应用编码
     * @param token   登录 token
     * @return 当前账号和权限信息
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public AuthLoginResponse currentUser(String appCode, String token) {
        SysAppDO app = getEnabledApp(appCode);
        SysLoginSessionDO session = getActiveSession(app.getId(), token);
        SysAccountDO account = getEnabledAccount(session.getAccountId());
        SysUserDO user = getEnabledUser(session.getUserId());
        return buildLoginResponse(app, user, account, null, session.getExpireAt());
    }

    /**
     * 退出登录。
     *
     * @param appCode 系统应用编码
     * @param token   登录 token
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
    @Override
    @DS(DataSourceName.SLAVE)
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
    private void validateLoginVerifyCode(SysAppDO app, SysAccountDO account, AuthLoginRequest request) {
        if (!StringUtils.hasText(request.getVerifyCodeId()) || !StringUtils.hasText(request.getVerifyCode())) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED.getCode(), "verify code is required");
        }
        Long verifyCodeId = parseVerifyCodeId(request.getVerifyCodeId());
        SysVerifyCodeDO verifyCode = sysVerifyCodeMapper.selectById(verifyCodeId);
        if (verifyCode == null
                || !Objects.equals(verifyCode.getAppId(), app.getId())
                || !VERIFY_SCENE_LOGIN.equals(verifyCode.getScene())
                || verifyCode.getUsed() == null
                || verifyCode.getUsed() == AuthConstants.ENABLED
                || verifyCode.getExpireAt() == null
                || verifyCode.getExpireAt().isBefore(LocalDateTime.now())
                || !resolveReceiver(account).equals(verifyCode.getReceiver())) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED.getCode(), "verify code is invalid or expired");
        }
        if (defaultInt(verifyCode.getVerifyCount()) >= VERIFY_CODE_MAX_ATTEMPTS) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED.getCode(), "verify code retry limit exceeded");
        }
        verifyCode.setVerifyCount(defaultInt(verifyCode.getVerifyCount()) + 1);
        if (!PasswordHashUtils.matches(request.getVerifyCode(), verifyCode.getCodeSalt(), verifyCode.getCodeHash())) {
            sysVerifyCodeMapper.updateById(verifyCode);
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED.getCode(), "verify code is invalid or expired");
        }
        verifyCode.setUsed(AuthConstants.ENABLED);
        verifyCode.setUsedAt(LocalDateTime.now());
        sysVerifyCodeMapper.updateById(verifyCode);
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
     * 生成 6 位数字验证码。
     *
     * @return 验证码
     */
    private String generateVerifyCode() {
        return String.format("%06d", VERIFY_CODE_RANDOM.nextInt(1_000_000));
    }

    /**
     * 解析验证码接收方式。
     *
     * @param account 登录账号
     * @return 接收方式
     */
    private String resolveReceiverType(SysAccountDO account) {
        if (StringUtils.hasText(account.getEmail())) {
            return "EMAIL";
        }
        if (StringUtils.hasText(account.getMobile())) {
            return "SMS";
        }
        return "TOTP";
    }

    /**
     * 解析验证码接收人。
     *
     * @param account 登录账号
     * @return 接收人
     */
    private String resolveReceiver(SysAccountDO account) {
        if (StringUtils.hasText(account.getEmail())) {
            return account.getEmail();
        }
        if (StringUtils.hasText(account.getMobile())) {
            return account.getMobile();
        }
        return account.getLoginAccount();
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
     * 获取有效会话。
     *
     * @param appId 系统应用ID
     * @param token token 明文
     * @return 登录会话
     */
    private SysLoginSessionDO getActiveSession(Long appId, String token) {
        String tokenHash = LoginTokenUtils.hashToken(extractBearerToken(token));
        SysLoginSessionDO session = sysLoginSessionMapper.selectOne(
                Wrappers.<SysLoginSessionDO>lambdaQuery()
                        .eq(SysLoginSessionDO::getAppId, appId)
                        .eq(SysLoginSessionDO::getTokenHash, tokenHash)
                        .eq(SysLoginSessionDO::getLogout, NOT_LOGOUT)
                        .gt(SysLoginSessionDO::getExpireAt, LocalDateTime.now())
                        .last("LIMIT 1")
        );
        if (session == null) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED.getCode(), "login token is invalid or expired");
        }
        return session;
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
        AuthLoginResponse response = new AuthLoginResponse();
        response.setAccessToken(token);
        response.setExpiresIn(AuthConstants.DEFAULT_TOKEN_TTL_SECONDS);
        response.setExpireAt(expireAt);
        response.setAccount(toAccountDTO(app, user, account));
        response.setMenus(queryMenuTree(app, account));
        response.setRoles(queryRoleCodes(app, account));
        response.setPermissions(queryPermissionCodes(app, account));
        return response;
    }

    /**
     * 转换账号响应。
     *
     * @param app     系统应用
     * @param user    用户主体
     * @param account 登录账号
     * @return 账号响应
     */
    private AuthAccountDTO toAccountDTO(SysAppDO app, SysUserDO user, SysAccountDO account) {
        AuthAccountDTO dto = new AuthAccountDTO();
        dto.setAccountId(account.getId());
        dto.setUserId(user.getId());
        dto.setAppCode(app.getAppCode());
        dto.setLoginAccount(account.getLoginAccount());
        dto.setRealName(user.getRealName());
        dto.setMerchantId(account.getMerchantId());
        dto.setStatus(account.getStatus());
        if (AuthConstants.APP_MERCHANT.equals(app.getAppCode())) {
            SysMerchantUserDO merchantUser = getEnabledMerchantUser(account);
            dto.setMerchantUserId(merchantUser.getId());
            dto.setLoginAccount(merchantUser.getLoginAccount());
            dto.setRealName(StringUtils.hasText(merchantUser.getRealName()) ? merchantUser.getRealName() : user.getRealName());
            dto.setMerchantAdmin(isMerchantSuperAdmin(app, account, queryRoleIds(app, account)));
        }
        return dto;
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
     * @param appId     系统应用ID
     * @param accountId 账号ID
     * @return 角色编码集合
     */
    private List<String> queryRoleCodes(SysAppDO app, SysAccountDO account) {
        Long appId = app.getId();
        List<Long> roleIds = queryRoleIds(app, account);
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
     * 查询菜单树。
     *
     * @param appId     系统应用ID
     * @param accountId 账号ID
     * @return 菜单树
     */
    private List<AuthMenuDTO> queryMenuTree(SysAppDO app, SysAccountDO account) {
        Long appId = app.getId();
        List<Long> roleIds = queryRoleIds(app, account);
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
     * @param appId     系统应用ID
     * @param accountId 账号ID
     * @return 权限编码集合
     */
    private List<String> queryPermissionCodes(SysAppDO app, SysAccountDO account) {
        Long appId = app.getId();
        List<Long> roleIds = queryRoleIds(app, account);
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
                .distinct()
                .sorted()
                .toList();
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
