package com.scott.payment.component.db.auth.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
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
import com.scott.payment.component.db.auth.entity.BaseMerchantInfoDO;
import com.scott.payment.component.db.auth.entity.SysAccountDO;
import com.scott.payment.component.db.auth.entity.SysAccountRoleDO;
import com.scott.payment.component.db.auth.entity.SysAppDO;
import com.scott.payment.component.db.auth.entity.SysLoginLogDO;
import com.scott.payment.component.db.auth.entity.SysLoginSessionDO;
import com.scott.payment.component.db.auth.entity.SysMenuDO;
import com.scott.payment.component.db.auth.entity.SysPermissionDO;
import com.scott.payment.component.db.auth.entity.SysRoleDO;
import com.scott.payment.component.db.auth.entity.SysRoleMenuDO;
import com.scott.payment.component.db.auth.entity.SysRolePermissionDO;
import com.scott.payment.component.db.auth.entity.SysUserDO;
import com.scott.payment.component.db.auth.mapper.BaseMerchantInfoMapper;
import com.scott.payment.component.db.auth.mapper.SysAccountMapper;
import com.scott.payment.component.db.auth.mapper.SysAccountRoleMapper;
import com.scott.payment.component.db.auth.mapper.SysAppMapper;
import com.scott.payment.component.db.auth.mapper.SysLoginLogMapper;
import com.scott.payment.component.db.auth.mapper.SysLoginSessionMapper;
import com.scott.payment.component.db.auth.mapper.SysMenuMapper;
import com.scott.payment.component.db.auth.mapper.SysPermissionMapper;
import com.scott.payment.component.db.auth.mapper.SysRoleMapper;
import com.scott.payment.component.db.auth.mapper.SysRoleMenuMapper;
import com.scott.payment.component.db.auth.mapper.SysRolePermissionMapper;
import com.scott.payment.component.db.auth.mapper.SysUserMapper;
import com.scott.payment.component.db.auth.service.SystemAuthService;
import com.scott.payment.component.db.constant.DataSourceName;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
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

    private final SysAppMapper sysAppMapper;
    private final SysUserMapper sysUserMapper;
    private final SysAccountMapper sysAccountMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysAccountRoleMapper sysAccountRoleMapper;
    private final SysRoleMenuMapper sysRoleMenuMapper;
    private final SysMenuMapper sysMenuMapper;
    private final SysRolePermissionMapper sysRolePermissionMapper;
    private final SysPermissionMapper sysPermissionMapper;
    private final SysLoginLogMapper sysLoginLogMapper;
    private final SysLoginSessionMapper sysLoginSessionMapper;
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
     * @param sysMenuMapper           菜单 Mapper
     * @param sysRolePermissionMapper 角色权限 Mapper
     * @param sysPermissionMapper     权限 Mapper
     * @param sysLoginLogMapper       登录日志 Mapper
     * @param sysLoginSessionMapper   登录会话 Mapper
     * @param baseMerchantInfoMapper  商户基础信息 Mapper
     */
    public SystemAuthServiceImpl(SysAppMapper sysAppMapper,
                                 SysUserMapper sysUserMapper,
                                 SysAccountMapper sysAccountMapper,
                                 SysRoleMapper sysRoleMapper,
                                 SysAccountRoleMapper sysAccountRoleMapper,
                                 SysRoleMenuMapper sysRoleMenuMapper,
                                 SysMenuMapper sysMenuMapper,
                                 SysRolePermissionMapper sysRolePermissionMapper,
                                 SysPermissionMapper sysPermissionMapper,
                                 SysLoginLogMapper sysLoginLogMapper,
                                 SysLoginSessionMapper sysLoginSessionMapper,
                                 BaseMerchantInfoMapper baseMerchantInfoMapper) {
        this.sysAppMapper = sysAppMapper;
        this.sysUserMapper = sysUserMapper;
        this.sysAccountMapper = sysAccountMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysAccountRoleMapper = sysAccountRoleMapper;
        this.sysRoleMenuMapper = sysRoleMenuMapper;
        this.sysMenuMapper = sysMenuMapper;
        this.sysRolePermissionMapper = sysRolePermissionMapper;
        this.sysPermissionMapper = sysPermissionMapper;
        this.sysLoginLogMapper = sysLoginLogMapper;
        this.sysLoginSessionMapper = sysLoginSessionMapper;
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

        SysRoleDO role = getRegisterRole(app, request.getRoleCode());
        bindRole(app, account, role, now);
        return toAccountDTO(app, user, account);
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
        SysAccountDO account = findAccount(app.getId(), request.getLoginAccount());
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
        SysAccountRoleDO relation = new SysAccountRoleDO();
        relation.setAppId(app.getId());
        relation.setAccountId(account.getId());
        relation.setRoleId(role.getId());
        relation.setCreatedAt(now);
        relation.setDeleted(AuthConstants.NOT_DELETED);
        sysAccountRoleMapper.insert(relation);
    }

    /**
     * 查询注册角色。
     *
     * @param app      系统应用
     * @param roleCode 指定角色编码
     * @return 角色
     */
    private SysRoleDO getRegisterRole(SysAppDO app, String roleCode) {
        String targetRole = StringUtils.hasText(roleCode)
                ? roleCode
                : defaultRoleCode(app.getAppCode());
        SysRoleDO role = sysRoleMapper.selectOne(
                Wrappers.<SysRoleDO>lambdaQuery()
                        .eq(SysRoleDO::getAppId, app.getId())
                        .eq(SysRoleDO::getRoleCode, targetRole)
                        .eq(SysRoleDO::getStatus, AuthConstants.ENABLED)
                        .eq(SysRoleDO::getDeleted, AuthConstants.NOT_DELETED)
                        .last("LIMIT 1")
        );
        if (role == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "role not found:" + targetRole);
        }
        return role;
    }

    /**
     * 获取应用默认角色编码。
     *
     * @param appCode 应用编码
     * @return 默认角色编码
     */
    private String defaultRoleCode(String appCode) {
        return AuthConstants.APP_MERCHANT.equals(appCode)
                ? AuthConstants.DEFAULT_MERCHANT_ROLE
                : AuthConstants.DEFAULT_ADMIN_ROLE;
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
     * 断言账号不存在。
     *
     * @param appId        系统应用ID
     * @param loginAccount 登录账号
     */
    private void assertAccountNotExists(Long appId, String loginAccount) {
        SysAccountDO exists = findAccount(appId, loginAccount);
        if (exists != null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "loginAccount already exists");
        }
    }

    /**
     * 查询登录账号。
     *
     * @param appId        系统应用ID
     * @param loginAccount 登录账号
     * @return 登录账号实体
     */
    private SysAccountDO findAccount(Long appId, String loginAccount) {
        if (!StringUtils.hasText(loginAccount)) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "loginAccount is required");
        }
        return sysAccountMapper.selectOne(
                Wrappers.<SysAccountDO>lambdaQuery()
                        .eq(SysAccountDO::getAppId, appId)
                        .eq(SysAccountDO::getLoginAccount, loginAccount)
                        .eq(SysAccountDO::getDeleted, AuthConstants.NOT_DELETED)
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
        response.setMenus(queryMenuTree(app.getId(), account.getId()));
        response.setPermissions(queryPermissionCodes(app.getId(), account.getId()));
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
     * 查询菜单树。
     *
     * @param appId     系统应用ID
     * @param accountId 账号ID
     * @return 菜单树
     */
    private List<AuthMenuDTO> queryMenuTree(Long appId, Long accountId) {
        List<Long> roleIds = queryRoleIds(appId, accountId);
        if (roleIds.isEmpty()) {
            return List.of();
        }
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
        List<AuthMenuDTO> nodes = sysMenuMapper.selectList(
                        Wrappers.<SysMenuDO>lambdaQuery()
                                .eq(SysMenuDO::getAppId, appId)
                                .in(SysMenuDO::getId, menuIds)
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
     * 查询权限编码集合。
     *
     * @param appId     系统应用ID
     * @param accountId 账号ID
     * @return 权限编码集合
     */
    private List<String> queryPermissionCodes(Long appId, Long accountId) {
        List<Long> roleIds = queryRoleIds(appId, accountId);
        if (roleIds.isEmpty()) {
            return List.of();
        }
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
