package com.scott.payment.admin.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scott.payment.admin.dto.SysUserAccountCreateRequest;
import com.scott.payment.admin.dto.SysUserAccountDTO;
import com.scott.payment.admin.dto.SysUserAccountQueryRequest;
import com.scott.payment.admin.dto.SysUserAccountResetPasswordRequest;
import com.scott.payment.admin.dto.SysUserAccountStatusRequest;
import com.scott.payment.admin.dto.SysUserAccountUpdateRequest;
import com.scott.payment.admin.dto.SysRoleDTO;
import com.scott.payment.admin.dto.SysUserRoleAuthDTO;
import com.scott.payment.admin.dto.SysUserRoleGrantRequest;
import com.scott.payment.admin.service.AdminUserService;
import com.scott.payment.component.core.auth.PasswordHashUtils;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.db.auth.constant.AuthConstants;
import com.scott.payment.component.db.auth.entity.SysAccountDO;
import com.scott.payment.component.db.auth.entity.SysAccountRoleDO;
import com.scott.payment.component.db.auth.entity.SysAppDO;
import com.scott.payment.component.db.auth.entity.SysLoginSessionDO;
import com.scott.payment.component.db.auth.entity.SysRoleDO;
import com.scott.payment.component.db.auth.entity.SysUserDO;
import com.scott.payment.component.db.auth.mapper.SysAccountRoleMapper;
import com.scott.payment.component.db.auth.mapper.SysAccountMapper;
import com.scott.payment.component.db.auth.mapper.SysAppMapper;
import com.scott.payment.component.db.auth.mapper.SysLoginSessionMapper;
import com.scott.payment.component.db.auth.mapper.SysRoleMapper;
import com.scott.payment.component.db.auth.mapper.SysUserMapper;
import com.scott.payment.component.db.constant.DataSourceName;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminUserServiceImpl
 * @date : 2026-06-06 00:00
 * @email : scott_x@163.com
 * @description : 管理后台用户领域服务实现
 * @status : create
 *
 * <p>负责后台用户维护、状态变更、密码重置和角色授权等核心领域规则，
 * 不承担控制器协议适配和页面交互逻辑。</p>
 */
@Service
public class AdminUserServiceImpl implements AdminUserService {

    private static final long NOT_DELETED = 0L;

    private final SysAppMapper sysAppMapper;
    private final SysAccountMapper sysAccountMapper;
    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysAccountRoleMapper sysAccountRoleMapper;
    private final SysLoginSessionMapper sysLoginSessionMapper;

    /**
     * 创建后台用户服务实现。
     *
     * @param sysAppMapper          应用 Mapper
     * @param sysAccountMapper      账号 Mapper
     * @param sysUserMapper         用户 Mapper
     * @param sysRoleMapper         角色 Mapper
     * @param sysAccountRoleMapper  账号角色 Mapper
     * @param sysLoginSessionMapper 登录会话 Mapper
     */
    public AdminUserServiceImpl(SysAppMapper sysAppMapper,
                                SysAccountMapper sysAccountMapper,
                                SysUserMapper sysUserMapper,
                                SysRoleMapper sysRoleMapper,
                                SysAccountRoleMapper sysAccountRoleMapper,
                                SysLoginSessionMapper sysLoginSessionMapper) {
        this.sysAppMapper = sysAppMapper;
        this.sysAccountMapper = sysAccountMapper;
        this.sysUserMapper = sysUserMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysAccountRoleMapper = sysAccountRoleMapper;
        this.sysLoginSessionMapper = sysLoginSessionMapper;
    }

    /**
     * 分页查询后台用户，并批量聚合用户主数据避免 N+1 查询。
     *
     * @param request 查询条件
     * @return 用户分页结果
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public PageResult<SysUserAccountDTO> pageUsers(SysUserAccountQueryRequest request) {
        SysUserAccountQueryRequest query = request == null ? new SysUserAccountQueryRequest() : request;
        SysAppDO app = getAdminApp();
        // 先查询账号分页，再按 userId 批量回填用户资料，避免列表场景出现 N+1 查询。
        Page<SysAccountDO> page = sysAccountMapper.selectPage(
                new Page<>(query.safePageNo(), query.safePageSize()),
                Wrappers.<SysAccountDO>lambdaQuery()
                        .eq(SysAccountDO::getAppId, app.getId())
                        .eq(SysAccountDO::getDeleted, NOT_DELETED)
                        .likeRight(StringUtils.hasText(query.getLoginAccount()), SysAccountDO::getLoginAccount, query.getLoginAccount())
                        .likeRight(StringUtils.hasText(query.getMobile()), SysAccountDO::getMobile, query.getMobile())
                        .likeRight(StringUtils.hasText(query.getEmail()), SysAccountDO::getEmail, query.getEmail())
                        .eq(query.getStatus() != null, SysAccountDO::getStatus, query.getStatus())
                        .orderByDesc(SysAccountDO::getUpdatedAt)
        );
        Map<Long, SysUserDO> userMap = loadUsers(page);
        return PageResult.of(
                page.getTotal(),
                page.getCurrent(),
                page.getSize(),
                page.getRecords().stream().map(account -> toDTO(account, userMap.get(account.getUserId()))).toList()
        );
    }

    @Override
    @DS(DataSourceName.SLAVE)
    public List<SysUserAccountDTO> listUsers(SysUserAccountQueryRequest request) {
        SysUserAccountQueryRequest query = request == null ? new SysUserAccountQueryRequest() : request;
        SysAppDO app = getAdminApp();
        List<SysAccountDO> accounts = sysAccountMapper.selectList(
                Wrappers.<SysAccountDO>lambdaQuery()
                        .eq(SysAccountDO::getAppId, app.getId())
                        .eq(SysAccountDO::getDeleted, NOT_DELETED)
                        .likeRight(StringUtils.hasText(query.getLoginAccount()), SysAccountDO::getLoginAccount, query.getLoginAccount())
                        .likeRight(StringUtils.hasText(query.getMobile()), SysAccountDO::getMobile, query.getMobile())
                        .likeRight(StringUtils.hasText(query.getEmail()), SysAccountDO::getEmail, query.getEmail())
                        .eq(query.getStatus() != null, SysAccountDO::getStatus, query.getStatus())
                        .orderByDesc(SysAccountDO::getUpdatedAt)
        );
        if (accounts.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, SysUserDO> userMap = sysUserMapper.selectList(
                Wrappers.<SysUserDO>lambdaQuery()
                        .in(SysUserDO::getId, accounts.stream().map(SysAccountDO::getUserId).toList())
                        .eq(SysUserDO::getDeleted, NOT_DELETED)
        ).stream().collect(Collectors.toMap(SysUserDO::getId, Function.identity(), (left, right) -> left));
        return accounts.stream()
                .map(account -> toDTO(account, userMap.get(account.getUserId())))
                .toList();
    }

    /**
     * 新增后台用户，并为新账号绑定默认管理员角色。
     *
     * @param request 新增请求
     * @return 用户详情
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public SysUserAccountDTO createUser(SysUserAccountCreateRequest request) {
        SysAppDO app = getAdminApp();
        assertAccountNotExists(app.getId(), request.getLoginAccount());
        LocalDateTime now = LocalDateTime.now();

        SysUserDO user = new SysUserDO();
        user.setUserType(AuthConstants.USER_TYPE_PLATFORM);
        user.setRealName(normalize(request.getRealName()));
        user.setMobile(normalize(request.getMobile()));
        user.setEmail(normalize(request.getEmail()));
        user.setCountryCode("CN");
        user.setLanguage("zh-CN");
        user.setTimezone("Asia/Shanghai");
        user.setStatus(AuthConstants.ENABLED);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        user.setDeleted(NOT_DELETED);
        sysUserMapper.insert(user);

        String salt = PasswordHashUtils.generateSalt();
        SysAccountDO account = new SysAccountDO();
        account.setAppId(app.getId());
        account.setUserId(user.getId());
        account.setLoginAccount(normalize(request.getLoginAccount()));
        account.setPasswordSalt(salt);
        account.setPasswordHash(PasswordHashUtils.hashPassword(request.getPassword(), salt));
        account.setPasswordAlgo(PasswordHashUtils.ALGORITHM);
        account.setMobile(normalize(request.getMobile()));
        account.setEmail(normalize(request.getEmail()));
        account.setMfaEnabled(AuthConstants.DISABLED);
        account.setPasswordExpired(AuthConstants.DISABLED);
        account.setPasswordUpdatedAt(now);
        account.setFailedLoginCount(0);
        account.setLocked(AuthConstants.DISABLED);
        account.setStatus(AuthConstants.ENABLED);
        account.setCreatedAt(now);
        account.setUpdatedAt(now);
        account.setDeleted(NOT_DELETED);
        sysAccountMapper.insert(account);

        bindDefaultRole(app, account, now);
        return toDTO(account, user);
    }

    /**
     * 更新后台用户基础信息。
     *
     * @param request 更新请求
     * @return 用户详情
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public SysUserAccountDTO updateUser(SysUserAccountUpdateRequest request) {
        SysAppDO app = getAdminApp();
        SysAccountDO account = getAccount(app.getId(), request.getAccountId());
        SysUserDO user = getUser(account.getUserId());
        LocalDateTime now = LocalDateTime.now();
        Integer status = request.getStatus() == null ? account.getStatus() : validStatus(request.getStatus());
        String realName = request.getRealName() == null ? user.getRealName() : normalize(request.getRealName());
        String mobile = request.getMobile() == null ? account.getMobile() : normalize(request.getMobile());
        String email = request.getEmail() == null ? account.getEmail() : normalize(request.getEmail());

        user.setRealName(realName);
        user.setMobile(mobile);
        user.setEmail(email);
        user.setStatus(status);
        user.setUpdatedAt(now);
        sysUserMapper.updateById(user);

        account.setMobile(mobile);
        account.setEmail(email);
        account.setStatus(status);
        account.setUpdatedAt(now);
        sysAccountMapper.updateById(account);
        if (status == AuthConstants.DISABLED) {
            logoutSessions(app.getId(), account.getId(), now);
        }
        return toDTO(account, user);
    }

    /**
     * 更新后台用户状态。
     *
     * @param request 状态请求
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(SysUserAccountStatusRequest request) {
        SysAppDO app = getAdminApp();
        SysAccountDO account = getAccount(app.getId(), request.getAccountId());
        SysUserDO user = getUser(account.getUserId());
        LocalDateTime now = LocalDateTime.now();
        Integer status = validStatus(request.getStatus());
        account.setStatus(status);
        account.setUpdatedAt(now);
        sysAccountMapper.updateById(account);
        user.setStatus(status);
        user.setUpdatedAt(now);
        sysUserMapper.updateById(user);
        if (status == AuthConstants.DISABLED) {
            logoutSessions(app.getId(), account.getId(), now);
        }
    }

    /**
     * 重置后台用户密码，并强制注销已有会话。
     *
     * @param request 重置密码请求
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(SysUserAccountResetPasswordRequest request) {
        SysAppDO app = getAdminApp();
        SysAccountDO account = getAccount(app.getId(), request.getAccountId());
        LocalDateTime now = LocalDateTime.now();
        String salt = PasswordHashUtils.generateSalt();
        account.setPasswordSalt(salt);
        account.setPasswordHash(PasswordHashUtils.hashPassword(request.getPassword(), salt));
        account.setPasswordAlgo(PasswordHashUtils.ALGORITHM);
        account.setPasswordExpired(AuthConstants.DISABLED);
        account.setPasswordUpdatedAt(now);
        account.setFailedLoginCount(0);
        account.setLocked(AuthConstants.DISABLED);
        account.setLockedAt(null);
        account.setLockedReason(null);
        account.setUpdatedAt(now);
        sysAccountMapper.updateById(account);
        logoutSessions(app.getId(), account.getId(), now);
    }

    /**
     * 查询后台用户角色授权。
     *
     * @param accountId 账号主键
     * @return 角色授权结果
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public SysUserRoleAuthDTO userRoles(Long accountId) {
        SysAppDO app = getAdminApp();
        SysAccountDO account = getAccount(app.getId(), accountId);
        SysUserRoleAuthDTO dto = new SysUserRoleAuthDTO();
        dto.setAccountId(account.getId());
        dto.setRoles(loadEnabledRoles(app.getId()).stream().map(this::toRoleDTO).toList());
        dto.setCheckedRoleIds(loadCheckedRoleIds(app.getId(), account.getId()));
        return dto;
    }

    /**
     * 保存后台用户角色授权，并使旧会话失效以便权限即时生效。
     *
     * @param request 角色授权请求
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public void grantRoles(SysUserRoleGrantRequest request) {
        SysAppDO app = getAdminApp();
        SysAccountDO account = getAccount(app.getId(), request.getAccountId());
        List<Long> roleIds = normalizeIds(request.getRoleIds());
        validateRoleIds(app.getId(), roleIds);
        LocalDateTime now = LocalDateTime.now();

        List<SysAccountRoleDO> oldRelations = sysAccountRoleMapper.selectList(
                Wrappers.<SysAccountRoleDO>lambdaQuery()
                        .eq(SysAccountRoleDO::getAppId, app.getId())
                        .eq(SysAccountRoleDO::getAccountId, account.getId())
                        .eq(SysAccountRoleDO::getDeleted, NOT_DELETED)
        );
        oldRelations.forEach(relation -> sysAccountRoleMapper.update(
                Wrappers.<SysAccountRoleDO>lambdaUpdate()
                        .set(SysAccountRoleDO::getDeleted, relation.getId())
                        .eq(SysAccountRoleDO::getId, relation.getId())
                        .eq(SysAccountRoleDO::getDeleted, NOT_DELETED)
        ));

        for (Long roleId : roleIds) {
            SysAccountRoleDO relation = new SysAccountRoleDO();
            relation.setAppId(app.getId());
            relation.setAccountId(account.getId());
            relation.setRoleId(roleId);
            relation.setCreatedAt(now);
            relation.setDeleted(NOT_DELETED);
            sysAccountRoleMapper.insert(relation);
        }
        logoutSessions(app.getId(), account.getId(), now);
    }

    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public void removeUsers(List<Long> accountIds) {
        List<Long> normalizedIds = normalizeIds(accountIds);
        if (normalizedIds.isEmpty()) {
            return;
        }
        SysAppDO app = getAdminApp();
        LocalDateTime now = LocalDateTime.now();
        for (Long accountId : normalizedIds) {
            SysAccountDO account = getAccount(app.getId(), accountId);
            SysUserDO user = getUser(account.getUserId());
            account.setDeleted(account.getId());
            account.setUpdatedAt(now);
            sysAccountMapper.updateById(account);

            user.setDeleted(user.getId());
            user.setUpdatedAt(now);
            sysUserMapper.updateById(user);

            sysAccountRoleMapper.update(
                    Wrappers.<SysAccountRoleDO>lambdaUpdate()
                            .set(SysAccountRoleDO::getDeleted, account.getId())
                            .eq(SysAccountRoleDO::getAppId, app.getId())
                            .eq(SysAccountRoleDO::getAccountId, account.getId())
                            .eq(SysAccountRoleDO::getDeleted, NOT_DELETED)
            );
            logoutSessions(app.getId(), account.getId(), now);
        }
    }

    /**
     * 获取后台管理系统对应的应用信息。
     */
    private SysAppDO getAdminApp() {
        SysAppDO app = sysAppMapper.selectOne(
                Wrappers.<SysAppDO>lambdaQuery()
                        .eq(SysAppDO::getAppCode, AuthConstants.APP_ADMIN)
                        .eq(SysAppDO::getDeleted, NOT_DELETED)
                        .last("LIMIT 1")
        );
        if (app == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "ADMIN app not found");
        }
        return app;
    }

    /**
     * 校验登录账号唯一性。
     *
     * @param appId        应用主键
     * @param loginAccount 登录账号
     */
    private void assertAccountNotExists(Long appId, String loginAccount) {
        Long count = sysAccountMapper.selectCount(
                Wrappers.<SysAccountDO>lambdaQuery()
                        .eq(SysAccountDO::getAppId, appId)
                        .eq(SysAccountDO::getLoginAccount, normalize(loginAccount))
                        .eq(SysAccountDO::getDeleted, NOT_DELETED)
        );
        if (count != null && count > 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "login account already exists");
        }
    }

    /**
     * 查询后台账号实体。
     *
     * @param appId     应用主键
     * @param accountId 账号主键
     * @return 账号实体
     */
    private SysAccountDO getAccount(Long appId, Long accountId) {
        SysAccountDO account = sysAccountMapper.selectOne(
                Wrappers.<SysAccountDO>lambdaQuery()
                        .eq(SysAccountDO::getId, accountId)
                        .eq(SysAccountDO::getAppId, appId)
                        .eq(SysAccountDO::getDeleted, NOT_DELETED)
                        .last("LIMIT 1")
        );
        if (account == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "account not found");
        }
        return account;
    }

    private SysUserDO getUser(Long userId) {
        SysUserDO user = sysUserMapper.selectOne(
                Wrappers.<SysUserDO>lambdaQuery()
                        .eq(SysUserDO::getId, userId)
                        .eq(SysUserDO::getDeleted, NOT_DELETED)
                        .last("LIMIT 1")
        );
        if (user == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "user not found");
        }
        return user;
    }

    private void bindDefaultRole(SysAppDO app, SysAccountDO account, LocalDateTime now) {
        SysRoleDO role = sysRoleMapper.selectOne(
                Wrappers.<SysRoleDO>lambdaQuery()
                        .eq(SysRoleDO::getAppId, app.getId())
                        .eq(SysRoleDO::getRoleCode, AuthConstants.DEFAULT_ADMIN_ROLE)
                        .eq(SysRoleDO::getDeleted, NOT_DELETED)
                        .last("LIMIT 1")
        );
        if (role == null || role.getStatus() == null || role.getStatus() != AuthConstants.ENABLED) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "default admin role not found");
        }
        SysAccountRoleDO relation = new SysAccountRoleDO();
        relation.setAppId(app.getId());
        relation.setAccountId(account.getId());
        relation.setRoleId(role.getId());
        relation.setCreatedAt(now);
        relation.setDeleted(NOT_DELETED);
        sysAccountRoleMapper.insert(relation);
    }

    private List<SysRoleDO> loadEnabledRoles(Long appId) {
        return sysRoleMapper.selectList(
                Wrappers.<SysRoleDO>lambdaQuery()
                        .eq(SysRoleDO::getAppId, appId)
                        .eq(SysRoleDO::getStatus, AuthConstants.ENABLED)
                        .eq(SysRoleDO::getDeleted, NOT_DELETED)
                        .orderByAsc(SysRoleDO::getSortNo)
                        .orderByAsc(SysRoleDO::getId)
        );
    }

    private List<Long> loadCheckedRoleIds(Long appId, Long accountId) {
        return sysAccountRoleMapper.selectList(
                Wrappers.<SysAccountRoleDO>lambdaQuery()
                        .select(SysAccountRoleDO::getRoleId)
                        .eq(SysAccountRoleDO::getAppId, appId)
                        .eq(SysAccountRoleDO::getAccountId, accountId)
                        .eq(SysAccountRoleDO::getDeleted, NOT_DELETED)
        ).stream().map(SysAccountRoleDO::getRoleId).toList();
    }

    private void validateRoleIds(Long appId, List<Long> roleIds) {
        if (roleIds.isEmpty()) {
            return;
        }
        Long count = sysRoleMapper.selectCount(
                Wrappers.<SysRoleDO>lambdaQuery()
                        .eq(SysRoleDO::getAppId, appId)
                        .eq(SysRoleDO::getStatus, AuthConstants.ENABLED)
                        .eq(SysRoleDO::getDeleted, NOT_DELETED)
                        .in(SysRoleDO::getId, roleIds)
        );
        if (count == null || count != roleIds.size()) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "role ids contain invalid role");
        }
    }

    private Integer validStatus(Integer status) {
        if (status == AuthConstants.ENABLED || status == AuthConstants.DISABLED) {
            return status;
        }
        throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "status is invalid");
    }

    private void logoutSessions(Long appId, Long accountId, LocalDateTime now) {
        sysLoginSessionMapper.update(
                Wrappers.<SysLoginSessionDO>lambdaUpdate()
                        .set(SysLoginSessionDO::getLogout, AuthConstants.ENABLED)
                        .set(SysLoginSessionDO::getLogoutAt, now)
                        .set(SysLoginSessionDO::getUpdatedAt, now)
                        .eq(SysLoginSessionDO::getAppId, appId)
                        .eq(SysLoginSessionDO::getAccountId, accountId)
                        .eq(SysLoginSessionDO::getLogout, AuthConstants.DISABLED)
        );
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private List<Long> normalizeIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> idSet = ids.stream()
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());
        return new ArrayList<>(idSet);
    }

    /**
     * 根据分页账号列表，批量加载对应的用户信息
     */
    private Map<Long, SysUserDO> loadUsers(Page<SysAccountDO> page) {
        if (page.getRecords().isEmpty()) {
            return Collections.emptyMap();
        }
        return sysUserMapper.selectList(
                Wrappers.<SysUserDO>lambdaQuery()
                        .in(SysUserDO::getId, page.getRecords().stream().map(SysAccountDO::getUserId).toList())
                        .eq(SysUserDO::getDeleted, NOT_DELETED)
        ).stream().collect(Collectors.toMap(SysUserDO::getId, Function.identity(), (left, right) -> left));
    }


    /**
     * 账号+用户信息转换为前端 DTO
     */
    private SysUserAccountDTO toDTO(SysAccountDO account, SysUserDO user) {
        SysUserAccountDTO dto = new SysUserAccountDTO();
        dto.setAccountId(account.getId());
        dto.setUserId(account.getUserId());
        dto.setLoginAccount(account.getLoginAccount());
        dto.setRealName(user == null ? null : user.getRealName());
        dto.setMobile(account.getMobile());
        dto.setEmail(account.getEmail());
        dto.setUserType(user == null ? null : user.getUserType());
        dto.setStatus(account.getStatus());
        dto.setLocked(account.getLocked());
        dto.setLastLoginAt(account.getLastLoginAt());
        dto.setLastLoginIp(account.getLastLoginIp());
        dto.setCreatedAt(account.getCreatedAt());
        return dto;
    }

    private SysRoleDTO toRoleDTO(SysRoleDO role) {
        SysRoleDTO dto = new SysRoleDTO();
        dto.setRoleId(role.getId());
        dto.setRoleCode(role.getRoleCode());
        dto.setRoleName(role.getRoleName());
        dto.setRoleType(role.getRoleType());
        dto.setDataScope(role.getDataScope());
        dto.setDescription(role.getDescription());
        dto.setStatus(role.getStatus());
        dto.setSortNo(role.getSortNo());
        dto.setCreatedAt(role.getCreatedAt());
        dto.setUpdatedAt(role.getUpdatedAt());
        return dto;
    }
}
