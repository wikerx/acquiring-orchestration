package com.scott.payment.admin.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scott.payment.admin.dto.SysUserAccountCreateRequest;
import com.scott.payment.admin.dto.SysUserAccountDTO;
import com.scott.payment.admin.dto.AdminUserProfileDTO;
import com.scott.payment.admin.dto.SysUserAccountQueryRequest;
import com.scott.payment.admin.dto.SysUserAccountResetPasswordRequest;
import com.scott.payment.admin.dto.SysUserAccountStatusRequest;
import com.scott.payment.admin.dto.SysUserAccountUpdateRequest;
import com.scott.payment.admin.dto.SysRoleDTO;
import com.scott.payment.admin.dto.SysUserRoleAuthDTO;
import com.scott.payment.admin.dto.SysUserRoleGrantRequest;
import com.scott.payment.admin.constant.SystemConfigKeys;
import com.scott.payment.admin.dto.email.EmailDTOs.EmailSendRequest;
import com.scott.payment.admin.service.AdminConfigService;
import com.scott.payment.admin.service.AdminEmailService;
import com.scott.payment.admin.service.AdminUserService;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.auth.PasswordHashUtils;
import com.scott.payment.component.core.cache.PaymentCacheNames;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.db.auth.constant.AuthConstants;
import com.scott.payment.component.db.auth.entity.SysAccountDO;
import com.scott.payment.component.db.auth.entity.SysAccountMfaDO;
import com.scott.payment.component.db.auth.entity.SysAccountRoleDO;
import com.scott.payment.component.db.auth.entity.SysAppDO;
import com.scott.payment.component.db.auth.entity.SysDeptDO;
import com.scott.payment.component.db.auth.entity.SysLoginSessionDO;
import com.scott.payment.component.db.auth.entity.SysMenuDO;
import com.scott.payment.component.db.auth.entity.SysPostDO;
import com.scott.payment.component.db.auth.entity.SysPermissionDO;
import com.scott.payment.component.db.auth.entity.SysRoleDO;
import com.scott.payment.component.db.auth.entity.SysRoleMenuDO;
import com.scott.payment.component.db.auth.entity.SysRolePermissionDO;
import com.scott.payment.component.db.auth.entity.SysUserDO;
import com.scott.payment.component.db.auth.entity.SysUserPostDO;
import com.scott.payment.component.db.auth.mapper.SysAccountRoleMapper;
import com.scott.payment.component.db.auth.mapper.SysAccountMapper;
import com.scott.payment.component.db.auth.mapper.SysAccountMfaMapper;
import com.scott.payment.component.db.auth.mapper.SysAppMapper;
import com.scott.payment.component.db.auth.mapper.SysDeptMapper;
import com.scott.payment.component.db.auth.mapper.SysLoginSessionMapper;
import com.scott.payment.component.db.auth.mapper.SysMenuMapper;
import com.scott.payment.component.db.auth.mapper.SysPermissionMapper;
import com.scott.payment.component.db.auth.mapper.SysPostMapper;
import com.scott.payment.component.db.auth.mapper.SysRoleMapper;
import com.scott.payment.component.db.auth.mapper.SysRoleMenuMapper;
import com.scott.payment.component.db.auth.mapper.SysRolePermissionMapper;
import com.scott.payment.component.db.auth.mapper.SysUserMapper;
import com.scott.payment.component.db.auth.mapper.SysUserPostMapper;
import com.scott.payment.component.db.constant.DataSourceName;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;


@Slf4j
@Service
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminUserServiceImpl
 * @date : 2026-06-07 08:26
 * @email : scott_x@163.com
 * @description : Admin User Service Impl 服务实现，位于 运营后台服务，执行领域校验、配置读取、数据库更新或远程调用编排，并向上层返回明确结果。
 * @status : create
 */
public class AdminUserServiceImpl implements AdminUserService {

    /**
     * 未删除标识。
     */
    private static final long NOT_DELETED = 0L;
    /**
     * 系统超级权限编码。
     */
    private static final String SUPER_PERMISSION = "*:*:*";
    /** 管理员重置后台账号密码后的安全通知模板。 */
    private static final String PASSWORD_CHANGED_TEMPLATE = "ADMIN_PASSWORD_CHANGED_BY_ADMIN";
    /** 密码被管理员修改通知场景。 */
    private static final String PASSWORD_CHANGED_SCENE = "PASSWORD_CHANGED";
    /** 邮件中统一展示到秒的操作时间。 */
    private static final DateTimeFormatter EMAIL_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 应用数据访问接口。
     */
    private final SysAppMapper sysAppMapper;
    /**
     * 登录账号数据访问接口。
     */
    private final SysAccountMapper sysAccountMapper;
    /**
     * MFA 配置数据访问接口。
     */
    private final SysAccountMfaMapper sysAccountMfaMapper;
    /**
     * 用户主体数据访问接口。
     */
    private final SysUserMapper sysUserMapper;
    /**
     * 部门数据访问接口。
     */
    private final SysDeptMapper sysDeptMapper;
    /**
     * 岗位数据访问接口。
     */
    private final SysPostMapper sysPostMapper;
    /**
     * 用户岗位关联数据访问接口。
     */
    private final SysUserPostMapper sysUserPostMapper;
    /**
     * 角色数据访问接口。
     */
    private final SysRoleMapper sysRoleMapper;
    /**
     * 菜单数据访问接口。
     */
    private final SysMenuMapper sysMenuMapper;
    /**
     * 账号角色关联数据访问接口。
     */
    private final SysAccountRoleMapper sysAccountRoleMapper;
    /**
     * 角色菜单关联数据访问接口。
     */
    private final SysRoleMenuMapper sysRoleMenuMapper;
    /**
     * 角色权限关联数据访问接口。
     */
    private final SysRolePermissionMapper sysRolePermissionMapper;
    /**
     * 权限资源数据访问接口。
     */
    private final SysPermissionMapper sysPermissionMapper;
    /**
     * 登录会话数据访问接口。
     */
    private final SysLoginSessionMapper sysLoginSessionMapper;
    /**
     * 邮件发送服务，用于账号开户通知。
     */
    private final AdminEmailService adminEmailService;
    /**
     * 系统参数服务，用于读取管理系统访问地址。
     */
    private final AdminConfigService adminConfigService;
    /**
     * 创建后台用户服务实现，显式注入用户、组织、角色和会话相关数据访问接口。
     *
     * @param sysAppMapper          应用 Mapper
     * @param sysAccountMapper      账号 Mapper
     * @param sysAccountMfaMapper   MFA 配置 Mapper
     * @param sysUserMapper         用户 Mapper
     * @param sysDeptMapper         部门 Mapper
     * @param sysPostMapper         岗位 Mapper
     * @param sysUserPostMapper     用户岗位关联 Mapper
     * @param sysRoleMapper         角色 Mapper
     * @param sysMenuMapper         菜单 Mapper
     * @param sysAccountRoleMapper  账号角色 Mapper
     * @param sysRoleMenuMapper     角色菜单 Mapper
     * @param sysRolePermissionMapper 角色权限 Mapper
     * @param sysPermissionMapper   权限 Mapper
     * @param sysLoginSessionMapper 登录会话 Mapper
     * @param adminEmailService     邮件服务
     * @param adminConfigService    系统参数服务
     */
    public AdminUserServiceImpl(SysAppMapper sysAppMapper,
                                SysAccountMapper sysAccountMapper,
                                SysAccountMfaMapper sysAccountMfaMapper,
                                SysUserMapper sysUserMapper,
                                SysDeptMapper sysDeptMapper,
                                SysPostMapper sysPostMapper,
                                SysUserPostMapper sysUserPostMapper,
                                SysRoleMapper sysRoleMapper,
                                SysMenuMapper sysMenuMapper,
                                SysAccountRoleMapper sysAccountRoleMapper,
                                SysRoleMenuMapper sysRoleMenuMapper,
                                SysRolePermissionMapper sysRolePermissionMapper,
                                SysPermissionMapper sysPermissionMapper,
                                SysLoginSessionMapper sysLoginSessionMapper,
                                AdminEmailService adminEmailService,
                                AdminConfigService adminConfigService) {
        this.sysAppMapper = sysAppMapper;
        this.sysAccountMapper = sysAccountMapper;
        this.sysAccountMfaMapper = sysAccountMfaMapper;
        this.sysUserMapper = sysUserMapper;
        this.sysDeptMapper = sysDeptMapper;
        this.sysPostMapper = sysPostMapper;
        this.sysUserPostMapper = sysUserPostMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysMenuMapper = sysMenuMapper;
        this.sysAccountRoleMapper = sysAccountRoleMapper;
        this.sysRoleMenuMapper = sysRoleMenuMapper;
        this.sysRolePermissionMapper = sysRolePermissionMapper;
        this.sysPermissionMapper = sysPermissionMapper;
        this.sysLoginSessionMapper = sysLoginSessionMapper;
        this.adminEmailService = adminEmailService;
        this.adminConfigService = adminConfigService;
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
        List<Long> filteredUserIds = loadUserIdsByDept(query.getDeptId());
        if (query.getDeptId() != null && filteredUserIds.isEmpty()) {
            return PageResult.of(0, query.safePageNo(), query.safePageSize(), Collections.emptyList());
        }
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
                        .in(query.getDeptId() != null, SysAccountDO::getUserId, filteredUserIds)
                        .orderByDesc(SysAccountDO::getUpdatedAt)
        );
        Map<Long, SysUserDO> userMap = loadUsers(page);
        List<Long> userIds = page.getRecords().stream().map(SysAccountDO::getUserId).toList();
        List<Long> accountIds = page.getRecords().stream().map(SysAccountDO::getId).toList();
        Map<Long, String> deptNameMap = loadDeptNameMap(userMap.values().stream().map(SysUserDO::getDeptId).toList());
        Map<Long, List<SysPostDO>> postMap = loadUserPostMap(userIds);
        Map<Long, List<SysRoleDO>> roleMap = loadAccountRoleMap(app.getId(), accountIds);
        return PageResult.of(
                page.getTotal(),
                page.getCurrent(),
                page.getSize(),
                page.getRecords().stream()
                        .map(account -> toDTO(account, userMap.get(account.getUserId()), deptNameMap, postMap, roleMap))
                        .toList()
        );
    }

    /**
     * 按条件查询后台用户列表，用于导出。
     *
     * @param request 查询条件
     * @return 用户列表
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public List<SysUserAccountDTO> listUsers(SysUserAccountQueryRequest request) {
        SysUserAccountQueryRequest query = request == null ? new SysUserAccountQueryRequest() : request;
        SysAppDO app = getAdminApp();
        List<Long> filteredUserIds = loadUserIdsByDept(query.getDeptId());
        if (query.getDeptId() != null && filteredUserIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<SysAccountDO> accounts = sysAccountMapper.selectList(
                Wrappers.<SysAccountDO>lambdaQuery()
                        .eq(SysAccountDO::getAppId, app.getId())
                        .eq(SysAccountDO::getDeleted, NOT_DELETED)
                        .likeRight(StringUtils.hasText(query.getLoginAccount()), SysAccountDO::getLoginAccount, query.getLoginAccount())
                        .likeRight(StringUtils.hasText(query.getMobile()), SysAccountDO::getMobile, query.getMobile())
                        .likeRight(StringUtils.hasText(query.getEmail()), SysAccountDO::getEmail, query.getEmail())
                        .eq(query.getStatus() != null, SysAccountDO::getStatus, query.getStatus())
                        .in(query.getDeptId() != null, SysAccountDO::getUserId, filteredUserIds)
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
        Map<Long, String> deptNameMap = loadDeptNameMap(userMap.values().stream().map(SysUserDO::getDeptId).toList());
        Map<Long, List<SysPostDO>> postMap = loadUserPostMap(accounts.stream().map(SysAccountDO::getUserId).toList());
        Map<Long, List<SysRoleDO>> roleMap = loadAccountRoleMap(app.getId(), accounts.stream().map(SysAccountDO::getId).toList());
        return accounts.stream()
                .map(account -> toDTO(account, userMap.get(account.getUserId()), deptNameMap, postMap, roleMap))
                .toList();
    }

    /**
     * 按账号主键查询后台用户维护资料，数据库不存在时返回标准未找到异常。
     *
     * @param accountId 后台账号主键
     * @return 用户维护资料
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Cacheable(
            cacheNames = PaymentCacheNames.ADMIN_USER_PROFILE,
            key = "#p0",
            condition = "#p0 != null and #p0 > 0"
    )
    public AdminUserProfileDTO getUserProfile(Long accountId) {
        if (accountId == null || accountId <= 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "accountId is invalid");
        }
        SysAppDO app = getAdminApp();
        SysAccountDO account = getAccount(app.getId(), accountId);
        SysUserDO user = getUser(account.getUserId());
        AdminUserProfileDTO profile = new AdminUserProfileDTO();
        profile.setAccountId(account.getId());
        profile.setUserId(user.getId());
        profile.setDeptId(user.getDeptId());
        profile.setPostIds(new ArrayList<>(loadUserPostMap(List.of(user.getId()))
                .getOrDefault(user.getId(), List.of())
                .stream()
                .map(SysPostDO::getId)
                .toList()));
        profile.setRoleIds(new ArrayList<>(loadAccountRoleMap(app.getId(), List.of(account.getId()))
                .getOrDefault(account.getId(), List.of())
                .stream()
                .map(SysRoleDO::getId)
                .toList()));
        profile.setLoginAccount(account.getLoginAccount());
        profile.setRealName(user.getRealName());
        profile.setMobile(account.getMobile());
        profile.setEmail(account.getEmail());
        profile.setUserType(user.getUserType());
        profile.setStatus(account.getStatus());
        profile.setRemark(account.getRemark());
        profile.setCreatedAt(account.getCreatedAt());
        return profile;
    }

    /**
     * 新增后台用户。新账号不自动绑定角色，角色必须通过“分配角色”单独授权。
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
        Integer status = request.getStatus() == null ? AuthConstants.ENABLED : validStatus(request.getStatus());
        String email = requireEmail(request.getEmail());
        String remark = normalize(request.getRemark());

        SysUserDO user = new SysUserDO();
        user.setUserType(AuthConstants.USER_TYPE_PLATFORM);
        user.setRealName(normalize(request.getRealName()));
        user.setDeptId(validateDept(app.getId(), request.getDeptId()));
        user.setMobile(normalize(request.getMobile()));
        user.setEmail(email);
        user.setCountryCode("CN");
        user.setLanguage("zh-CN");
        user.setTimezone("Asia/Shanghai");
        user.setStatus(status);
        user.setRemark(remark);
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
        account.setEmail(email);
        account.setMfaEnabled(AuthConstants.DISABLED);
        account.setPasswordExpired(AuthConstants.DISABLED);
        account.setPasswordUpdatedAt(now);
        account.setFailedLoginCount(0);
        account.setLocked(AuthConstants.DISABLED);
        account.setStatus(status);
        account.setRemark(remark);
        account.setCreatedAt(now);
        account.setUpdatedAt(now);
        account.setDeleted(NOT_DELETED);
        sysAccountMapper.insert(account);

        createDefaultRequiredMfa(app, account, now);

        bindUserPosts(app.getId(), user.getId(), request.getPostIds(), now);
        sendAccountCreatedNoticeAfterCommit(account, user, request.getPassword());
        return toDTO(account, user, loadDeptNameMap(Collections.singletonList(user.getDeptId())),
                loadUserPostMap(List.of(user.getId())), loadAccountRoleMap(app.getId(), List.of(account.getId())));
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
    @CacheEvict(cacheNames = PaymentCacheNames.ADMIN_USER_PROFILE, key = "#p0.accountId")
    public SysUserAccountDTO updateUser(SysUserAccountUpdateRequest request) {
        SysAppDO app = getAdminApp();
        SysAccountDO account = getAccount(app.getId(), request.getAccountId());
        SysUserDO user = getUser(account.getUserId());
        LocalDateTime now = LocalDateTime.now();
        Integer status = request.getStatus() == null ? account.getStatus() : validStatus(request.getStatus());
        String realName = request.getRealName() == null ? user.getRealName() : normalize(request.getRealName());
        String mobile = request.getMobile() == null ? account.getMobile() : normalize(request.getMobile());
        String email = requireEmail(request.getEmail());
        String remark = normalize(request.getRemark());
        user.setRealName(realName);
        user.setDeptId(validateDept(app.getId(), request.getDeptId()));
        user.setMobile(mobile);
        user.setEmail(email);
        user.setStatus(status);
        user.setRemark(remark);
        user.setUpdatedAt(now);
        sysUserMapper.updateById(user);

        account.setMobile(mobile);
        account.setEmail(email);
        account.setStatus(status);
        account.setRemark(remark);
        account.setUpdatedAt(now);
        sysAccountMapper.updateById(account);
        if (status == AuthConstants.DISABLED) {
            logoutSessions(app.getId(), account.getId(), now);
        }
        bindUserPosts(app.getId(), user.getId(), request.getPostIds(), now);
        return toDTO(account, user, loadDeptNameMap(Collections.singletonList(user.getDeptId())),
                loadUserPostMap(List.of(user.getId())), loadAccountRoleMap(app.getId(), List.of(account.getId())));
    }

    /**
     * 更新后台用户状态。
     *
     * @param request 状态请求
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = PaymentCacheNames.ADMIN_USER_PROFILE, key = "#p0.accountId")
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
        sendPasswordChangedNoticeAfterCommit(account, getUser(account.getUserId()), request.getPassword(), now);
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
        List<SysRoleDO> roles = loadEnabledRoles(app.getId());
        Set<Long> assignableRoleIds = loadAssignableRoleIds(app.getId(), roles);
        SysUserRoleAuthDTO dto = new SysUserRoleAuthDTO();
        dto.setAccountId(account.getId());
        dto.setRoles(roles.stream()
                .map(role -> toRoleDTO(role, assignableRoleIds.contains(role.getId())))
                .toList());
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
    @CacheEvict(cacheNames = PaymentCacheNames.ADMIN_USER_PROFILE, key = "#p0.accountId")
    public void grantRoles(SysUserRoleGrantRequest request) {
        SysAppDO app = getAdminApp();
        SysAccountDO account = getAccount(app.getId(), request.getAccountId());
        List<Long> roleIds = normalizeIds(request.getRoleIds());
        validateRoleIds(app.getId(), roleIds);
        assertAssignableRoles(app.getId(), roleIds);
        LocalDateTime now = LocalDateTime.now();

        List<SysAccountRoleDO> oldRelations = sysAccountRoleMapper.selectList(
                Wrappers.<SysAccountRoleDO>lambdaQuery()
                        .eq(SysAccountRoleDO::getAppId, app.getId())
                        .eq(SysAccountRoleDO::getAccountId, account.getId())
                        .eq(SysAccountRoleDO::getDeleted, NOT_DELETED)
        );
        assertAssignableRoles(app.getId(), oldRelations.stream().map(SysAccountRoleDO::getRoleId).toList());
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

    /**
     * 批量逻辑删除后台账号及用户，清理角色/岗位关系并注销现有会话。
     *
     * <p>全部操作在主库事务中执行；空主键集合按幂等成功处理，
     * 防止已删除账号继续通过旧会话访问管理端。</p>
     *
     * @param accountIds 待删除的后台账号主键集合
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(cacheNames = PaymentCacheNames.ADMIN_USER_PROFILE, allEntries = true)
    public void removeUsers(List<Long> accountIds) {
        List<Long> normalizedIds = normalizeIds(accountIds);
        if (normalizedIds.isEmpty()) {
            return;
        }
        SysAppDO app = getAdminApp();
        LocalDateTime now = LocalDateTime.now();
        List<UserRemovalTarget> targets = new ArrayList<>(normalizedIds.size());
        for (Long accountId : normalizedIds) {
            SysAccountDO account = getAccount(app.getId(), accountId);
            SysUserDO user = getUser(account.getUserId());
            targets.add(new UserRemovalTarget(account, user));
        }
        for (UserRemovalTarget target : targets) {
            SysAccountDO account = target.account();
            SysUserDO user = target.user();
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
            sysUserPostMapper.delete(
                    Wrappers.<SysUserPostDO>lambdaQuery()
                            .eq(SysUserPostDO::getUserId, user.getId())
            );
            logoutSessions(app.getId(), account.getId(), now);
        }
    }

    /**
     * 获取后台管理系统对应的应用信息。
     *
     * @return 后台应用实体
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

    /** 已完成存在性校验、等待在同一事务内逻辑删除的账号和用户。 */
    private record UserRemovalTarget(SysAccountDO account, SysUserDO user) {
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

    /**
     * 查询用户主体实体。
     *
     * @param userId 用户主体主键
     * @return 用户主体实体
     */
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

    /**
     * 校验当前操作人只能下放自己已经拥有的权限。
     *
     * @param appId   应用主键
     * @param roleIds 待授权角色ID集合
     */
    private void assertAssignableRoles(Long appId, List<Long> roleIds) {
        List<Long> normalizedRoleIds = normalizeIds(roleIds);
        if (normalizedRoleIds.isEmpty()) {
            return;
        }
        Set<String> operatorPermissions = currentOperatorPermissions();
        if (operatorPermissions.contains(SUPER_PERMISSION)) {
            return;
        }
        Set<String> requiredPermissions = loadRolePermissionCodes(appId, normalizedRoleIds);
        if (!operatorPermissions.containsAll(requiredPermissions)) {
            throw new ServiceException(ApiResultEnum.FORBIDDEN.getCode(), "不能分配超出当前账号权限范围的角色");
        }
    }

    /**
     * 根据当前操作人权限计算可授权角色ID集合。
     *
     * @param appId 应用主键
     * @param roles 候选角色集合
     * @return 可授权角色ID集合
     */
    private Set<Long> loadAssignableRoleIds(Long appId, List<SysRoleDO> roles) {
        if (roles.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> operatorPermissions = currentOperatorPermissions();
        if (operatorPermissions.contains(SUPER_PERMISSION)) {
            return roles.stream().map(SysRoleDO::getId).collect(Collectors.toSet());
        }
        Map<Long, Set<String>> rolePermissionMap = loadRolePermissionCodeMap(appId, roles.stream().map(SysRoleDO::getId).toList());
        return roles.stream()
                .filter(role -> operatorPermissions.containsAll(rolePermissionMap.getOrDefault(role.getId(), Collections.emptySet())))
                .map(SysRoleDO::getId)
                .collect(Collectors.toSet());
    }

    /**
     * 读取当前登录账号拥有的权限编码。
     *
     * @return 当前账号权限编码集合
     */
    private Set<String> currentOperatorPermissions() {
        InternalAuthAccount operator = InternalAuthContextHolder.get();
        if (operator == null || operator.getPermissions() == null) {
            return Collections.emptySet();
        }
        return operator.getPermissions().stream()
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
    }

    /**
     * 查询角色绑定的菜单权限和按钮/API 权限编码。
     *
     * @param appId   应用主键
     * @param roleIds 角色ID集合
     * @return 角色拥有的权限编码集合
     */
    private Set<String> loadRolePermissionCodes(Long appId, List<Long> roleIds) {
        Map<Long, Set<String>> rolePermissionMap = loadRolePermissionCodeMap(appId, roleIds);
        return rolePermissionMap.values().stream()
                .flatMap(Set::stream)
                .collect(Collectors.toSet());
    }

    /**
     * 按角色分组查询权限编码，覆盖 sys_role_menu 和 sys_role_permission 两类授权关系。
     *
     * @param appId   应用主键
     * @param roleIds 角色ID集合
     * @return 角色ID到权限编码集合的映射
     */
    private Map<Long, Set<String>> loadRolePermissionCodeMap(Long appId, List<Long> roleIds) {
        List<Long> normalizedRoleIds = normalizeIds(roleIds);
        if (normalizedRoleIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Set<String>> result = new HashMap<>();
        normalizedRoleIds.forEach(roleId -> result.put(roleId, new HashSet<>()));
        fillRoleMenuPermissionCodes(appId, normalizedRoleIds, result);
        fillRoleResourcePermissionCodes(appId, normalizedRoleIds, result);
        return result;
    }

    /**
     * 回填角色绑定菜单上的权限编码。
     *
     * @param appId   应用主键
     * @param roleIds 角色ID集合
     * @param result  角色权限映射
     */
    private void fillRoleMenuPermissionCodes(Long appId, List<Long> roleIds, Map<Long, Set<String>> result) {
        List<SysRoleMenuDO> roleMenus = sysRoleMenuMapper.selectList(
                Wrappers.<SysRoleMenuDO>lambdaQuery()
                        .eq(SysRoleMenuDO::getAppId, appId)
                        .in(SysRoleMenuDO::getRoleId, roleIds)
                        .eq(SysRoleMenuDO::getDeleted, NOT_DELETED)
        );
        if (roleMenus.isEmpty()) {
            return;
        }
        List<Long> menuIds = roleMenus.stream().map(SysRoleMenuDO::getMenuId).filter(Objects::nonNull).distinct().toList();
        if (menuIds.isEmpty()) {
            return;
        }
        Map<Long, String> menuPermissionMap = sysMenuMapper.selectList(
                Wrappers.<SysMenuDO>lambdaQuery()
                        .eq(SysMenuDO::getAppId, appId)
                        .in(SysMenuDO::getId, menuIds)
                        .eq(SysMenuDO::getStatus, AuthConstants.ENABLED)
                        .eq(SysMenuDO::getDeleted, NOT_DELETED)
        ).stream()
                .filter(menu -> StringUtils.hasText(menu.getPermissionCode()))
                .collect(Collectors.toMap(SysMenuDO::getId, SysMenuDO::getPermissionCode, (left, right) -> left));
        for (SysRoleMenuDO relation : roleMenus) {
            String permissionCode = menuPermissionMap.get(relation.getMenuId());
            if (StringUtils.hasText(permissionCode)) {
                result.computeIfAbsent(relation.getRoleId(), key -> new HashSet<>()).add(permissionCode);
            }
        }
    }

    /**
     * 回填角色绑定资源权限表上的权限编码。
     *
     * @param appId   应用主键
     * @param roleIds 角色ID集合
     * @param result  角色权限映射
     */
    private void fillRoleResourcePermissionCodes(Long appId, List<Long> roleIds, Map<Long, Set<String>> result) {
        List<SysRolePermissionDO> rolePermissions = sysRolePermissionMapper.selectList(
                Wrappers.<SysRolePermissionDO>lambdaQuery()
                        .eq(SysRolePermissionDO::getAppId, appId)
                        .in(SysRolePermissionDO::getRoleId, roleIds)
                        .eq(SysRolePermissionDO::getDeleted, NOT_DELETED)
        );
        if (rolePermissions.isEmpty()) {
            return;
        }
        List<Long> permissionIds = rolePermissions.stream().map(SysRolePermissionDO::getPermissionId).filter(Objects::nonNull).distinct().toList();
        if (permissionIds.isEmpty()) {
            return;
        }
        Map<Long, String> permissionCodeMap = sysPermissionMapper.selectList(
                Wrappers.<SysPermissionDO>lambdaQuery()
                        .eq(SysPermissionDO::getAppId, appId)
                        .in(SysPermissionDO::getId, permissionIds)
                        .eq(SysPermissionDO::getStatus, AuthConstants.ENABLED)
                        .eq(SysPermissionDO::getDeleted, NOT_DELETED)
        ).stream().collect(Collectors.toMap(SysPermissionDO::getId, SysPermissionDO::getPermissionCode, (left, right) -> left));
        for (SysRolePermissionDO relation : rolePermissions) {
            String permissionCode = permissionCodeMap.get(relation.getPermissionId());
            if (StringUtils.hasText(permissionCode)) {
                result.computeIfAbsent(relation.getRoleId(), key -> new HashSet<>()).add(permissionCode);
            }
        }
    }

    /**
     * 加载后台已启用角色列表。
     *
     * @param appId 应用主键
     * @return 已启用角色列表
     */
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

    /**
     * 加载账号当前已授权角色主键。
     *
     * @param appId     应用主键
     * @param accountId 账号主键
     * @return 已授权角色主键集合
     */
    private List<Long> loadCheckedRoleIds(Long appId, Long accountId) {
        return sysAccountRoleMapper.selectList(
                Wrappers.<SysAccountRoleDO>lambdaQuery()
                        .select(SysAccountRoleDO::getRoleId)
                        .eq(SysAccountRoleDO::getAppId, appId)
                        .eq(SysAccountRoleDO::getAccountId, accountId)
                        .eq(SysAccountRoleDO::getDeleted, NOT_DELETED)
        ).stream().map(SysAccountRoleDO::getRoleId).toList();
    }

    /**
     * 校验角色主键均属于当前后台应用且处于启用状态。
     *
     * @param appId   应用主键
     * @param roleIds 角色主键集合
     */
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

    /**
     * 校验部门存在且启用，允许用户暂不归属部门。
     *
     * @param appId  应用主键
     * @param deptId 部门主键
     * @return 合法部门主键
     */
    private Long validateDept(Long appId, Long deptId) {
        if (deptId == null) {
            return null;
        }
        SysDeptDO dept = sysDeptMapper.selectOne(
                Wrappers.<SysDeptDO>lambdaQuery()
                        .eq(SysDeptDO::getId, deptId)
                        .eq(SysDeptDO::getAppId, appId)
                        .eq(SysDeptDO::getStatus, AuthConstants.ENABLED)
                        .eq(SysDeptDO::getDeleted, NOT_DELETED)
                        .last("LIMIT 1")
        );
        if (dept == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "department not found or disabled");
        }
        return deptId;
    }

    /**
     * 校验并重建用户岗位关联。
     *
     * @param appId   应用主键
     * @param userId  用户主键
     * @param postIds 岗位主键列表
     * @param now     当前时间
     */
    private void bindUserPosts(Long appId, Long userId, List<Long> postIds, LocalDateTime now) {
        List<Long> normalizedPostIds = normalizeIds(postIds);
        validatePostIds(appId, normalizedPostIds);
        sysUserPostMapper.delete(
                Wrappers.<SysUserPostDO>lambdaQuery()
                        .eq(SysUserPostDO::getUserId, userId)
        );
        for (Long postId : normalizedPostIds) {
            SysUserPostDO relation = new SysUserPostDO();
            relation.setUserId(userId);
            relation.setPostId(postId);
            relation.setCreateTime(now);
            sysUserPostMapper.insert(relation);
        }
    }

    /**
     * 校验岗位存在且启用。
     *
     * @param appId   应用主键
     * @param postIds 岗位主键列表
     */
    private void validatePostIds(Long appId, List<Long> postIds) {
        if (postIds.isEmpty()) {
            return;
        }
        Long count = sysPostMapper.selectCount(
                Wrappers.<SysPostDO>lambdaQuery()
                        .eq(SysPostDO::getAppId, appId)
                        .eq(SysPostDO::getStatus, AuthConstants.ENABLED)
                        .eq(SysPostDO::getDeleted, NOT_DELETED)
                        .in(SysPostDO::getId, postIds)
        );
        if (count == null || count != postIds.size()) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "post ids contain invalid or disabled post");
        }
    }

    /**
     * 校验用户状态只允许启用或停用。
     *
     * @param status 用户状态
     * @return 合法状态
     */
    private Integer validStatus(Integer status) {
        if (status == AuthConstants.ENABLED || status == AuthConstants.DISABLED) {
            return status;
        }
        throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "status is invalid");
    }

    /**
     * 规范化并校验后台用户邮箱，避免绕过 Bean Validation 的调用写入空邮箱。
     *
     * @param email 原始邮箱
     * @return 规范化后的邮箱
     */
    private String requireEmail(String email) {
        String normalizedEmail = normalize(email);
        if (!StringUtils.hasText(normalizedEmail)) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "请输入邮箱");
        }
        return normalizedEmail;
    }

    /**
     * 注销账号当前有效会话，用于密码、状态和授权变更后触发权限即时刷新。
     *
     * @param appId     应用主键
     * @param accountId 账号主键
     * @param now       当前业务时间
     */
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

    /**
     * 去除用户输入两端空格，空白字符串统一转换为 null。
     *
     * @param value 用户输入
     * @return 规范化后的字符串
     */
    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    /**
     * 规范化主键集合，过滤空值和非正数并去重。
     *
     * @param ids 原始主键集合
     * @return 规范化主键集合
     */
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
     * 根据分页账号列表，批量加载对应的用户信息。
     *
     * @param page 账号分页结果
     * @return 用户主键到用户实体的映射
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
     * 按部门查询用户主键集合，用于用户列表部门筛选。
     *
     * @param deptId 部门主键
     * @return 用户主键集合
     */
    private List<Long> loadUserIdsByDept(Long deptId) {
        if (deptId == null) {
            return Collections.emptyList();
        }
        return sysUserMapper.selectList(
                Wrappers.<SysUserDO>lambdaQuery()
                        .select(SysUserDO::getId)
                        .eq(SysUserDO::getDeptId, deptId)
                        .eq(SysUserDO::getDeleted, NOT_DELETED)
        ).stream().map(SysUserDO::getId).toList();
    }

    /**
     * 批量加载部门名称。
     *
     * @param deptIds 部门主键集合
     * @return 部门主键到名称的映射
     */
    private Map<Long, String> loadDeptNameMap(List<Long> deptIds) {
        List<Long> normalizedDeptIds = normalizeIds(deptIds);
        if (normalizedDeptIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return sysDeptMapper.selectList(
                Wrappers.<SysDeptDO>lambdaQuery()
                        .select(SysDeptDO::getId, SysDeptDO::getDeptName)
                        .in(SysDeptDO::getId, normalizedDeptIds)
                        .eq(SysDeptDO::getDeleted, NOT_DELETED)
        ).stream().collect(Collectors.toMap(SysDeptDO::getId, SysDeptDO::getDeptName, (left, right) -> left));
    }

    /**
     * 批量加载用户岗位关系和岗位摘要。
     *
     * @param userIds 用户主键集合
     * @return 用户主键到岗位列表的映射
     */
    private Map<Long, List<SysPostDO>> loadUserPostMap(List<Long> userIds) {
        List<Long> normalizedUserIds = normalizeIds(userIds);
        if (normalizedUserIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<SysUserPostDO> relations = sysUserPostMapper.selectList(
                Wrappers.<SysUserPostDO>lambdaQuery()
                        .in(SysUserPostDO::getUserId, normalizedUserIds)
        );
        if (relations.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, SysPostDO> postMap = sysPostMapper.selectList(
                Wrappers.<SysPostDO>lambdaQuery()
                        .in(SysPostDO::getId, relations.stream().map(SysUserPostDO::getPostId).toList())
                        .eq(SysPostDO::getDeleted, NOT_DELETED)
        ).stream().collect(Collectors.toMap(SysPostDO::getId, Function.identity(), (left, right) -> left));
        return relations.stream()
                .filter(relation -> postMap.containsKey(relation.getPostId()))
                .collect(Collectors.groupingBy(
                        SysUserPostDO::getUserId,
                        Collectors.mapping(relation -> postMap.get(relation.getPostId()), Collectors.toList())
                ));
    }

    /**
     * 批量加载账号已绑定角色。
     *
     * @param appId      应用主键
     * @param accountIds 账号ID集合
     * @return 账号ID到角色列表的映射
     */
    private Map<Long, List<SysRoleDO>> loadAccountRoleMap(Long appId, List<Long> accountIds) {
        List<Long> normalizedAccountIds = normalizeIds(accountIds);
        if (normalizedAccountIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<SysAccountRoleDO> relations = sysAccountRoleMapper.selectList(
                Wrappers.<SysAccountRoleDO>lambdaQuery()
                        .eq(SysAccountRoleDO::getAppId, appId)
                        .in(SysAccountRoleDO::getAccountId, normalizedAccountIds)
                        .eq(SysAccountRoleDO::getDeleted, NOT_DELETED)
        );
        if (relations.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> roleIds = relations.stream().map(SysAccountRoleDO::getRoleId).filter(Objects::nonNull).distinct().toList();
        if (roleIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, SysRoleDO> roleMap = sysRoleMapper.selectList(
                Wrappers.<SysRoleDO>lambdaQuery()
                        .eq(SysRoleDO::getAppId, appId)
                        .in(SysRoleDO::getId, roleIds)
                        .eq(SysRoleDO::getDeleted, NOT_DELETED)
        ).stream().collect(Collectors.toMap(SysRoleDO::getId, Function.identity(), (left, right) -> left));
        return relations.stream()
                .filter(relation -> roleMap.containsKey(relation.getRoleId()))
                .collect(Collectors.groupingBy(
                        SysAccountRoleDO::getAccountId,
                        Collectors.mapping(relation -> roleMap.get(relation.getRoleId()), Collectors.toList())
                ));
    }

    /**
     * 账号和用户资料转换为前端 DTO。
     *
     * @param account     账号实体
     * @param user        用户主体实体
     * @param deptNameMap 部门名称映射
     * @param postMap     用户岗位映射
     * @param roleMap     账号角色映射
     * @return 用户账号 DTO
     */
    private SysUserAccountDTO toDTO(SysAccountDO account,
                                    SysUserDO user,
                                    Map<Long, String> deptNameMap,
                                    Map<Long, List<SysPostDO>> postMap,
                                    Map<Long, List<SysRoleDO>> roleMap) {
        SysUserAccountDTO dto = new SysUserAccountDTO();
        dto.setAccountId(account.getId());
        dto.setUserId(account.getUserId());
        dto.setDeptId(user == null ? null : user.getDeptId());
        dto.setDeptName(user == null ? null : deptNameMap.get(user.getDeptId()));
        List<SysPostDO> posts = postMap.getOrDefault(account.getUserId(), Collections.emptyList());
        dto.setPostIds(posts.stream().map(SysPostDO::getId).toList());
        dto.setPostNames(posts.stream().map(SysPostDO::getPostName).toList());
        List<SysRoleDO> roles = roleMap.getOrDefault(account.getId(), Collections.emptyList());
        dto.setRoleIds(roles.stream().map(SysRoleDO::getId).toList());
        dto.setRoleNames(roles.stream().map(SysRoleDO::getRoleName).toList());
        dto.setLoginAccount(account.getLoginAccount());
        dto.setRealName(user == null ? null : user.getRealName());
        dto.setMobile(account.getMobile());
        dto.setEmail(account.getEmail());
        dto.setUserType(user == null ? null : user.getUserType());
        dto.setStatus(account.getStatus());
        dto.setLocked(account.getLocked());
        dto.setLastLoginAt(account.getLastLoginAt());
        dto.setLastLoginIp(account.getLastLoginIp());
        fillMfaStatus(account, dto);
        dto.setRemark(account.getRemark());
        dto.setCreatedAt(account.getCreatedAt());
        return dto;
    }

    /**
     * 为新增后台用户创建默认强制 OTP 配置。
     *
     * @param app     后台应用
     * @param account 登录账号
     * @param now     当前时间
     */
    private void createDefaultRequiredMfa(SysAppDO app, SysAccountDO account, LocalDateTime now) {
        SysAccountMfaDO mfa = new SysAccountMfaDO();
        mfa.setAppId(app.getId());
        mfa.setAccountId(account.getId());
        mfa.setUserId(account.getUserId());
        mfa.setMfaPolicy(AuthConstants.MFA_POLICY_REQUIRED);
        mfa.setMfaStatus(account.getStatus() != null && account.getStatus() == AuthConstants.DISABLED
                ? AuthConstants.MFA_STATUS_DISABLED
                : AuthConstants.MFA_STATUS_PENDING_BIND);
        mfa.setMfaType(AuthConstants.MFA_TYPE_TOTP);
        mfa.setIssuer("Acquiring Admin");
        mfa.setAccountLabel(account.getLoginAccount());
        mfa.setFailedVerifyCount(0);
        mfa.setCreatedAt(now);
        mfa.setUpdatedAt(now);
        mfa.setDeleted(NOT_DELETED);
        sysAccountMfaMapper.insert(mfa);
    }

    /**
     * 事务提交后发送管理系统账号开户通知，避免账号创建回滚后误发邮件。
     *
     * @param account         登录账号
     * @param user            用户主体
     * @param initialPassword 初始密码，仅作为敏感模板变量传入邮件服务
     */
    private void sendAccountCreatedNoticeAfterCommit(SysAccountDO account, SysUserDO user, String initialPassword) {
        Runnable task = () -> sendAccountCreatedNotice(account, user, initialPassword);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            task.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                task.run();
            }
        });
    }

    /**
     * 发送管理系统开户通知。发送失败仅记录告警，不影响账号创建结果。
     *
     * @param account         登录账号
     * @param user            用户主体
     * @param initialPassword 初始密码，仅作为敏感模板变量传入邮件服务
     */
    private void sendAccountCreatedNotice(SysAccountDO account, SysUserDO user, String initialPassword) {
        if (!StringUtils.hasText(account.getEmail())) {
            return;
        }
        try {
            EmailSendRequest request = new EmailSendRequest();
            request.setAppCode(AuthConstants.APP_ADMIN);
            request.setTemplateCode("ADMIN_ACCOUNT_CREATED");
            request.setSceneCode("ACCOUNT_CREATED");
            request.setLocale("zh-CN");
            request.getToEmails().add(account.getEmail());
            request.setBizType("ACCOUNT_CREATED");
            request.setBizNo(String.valueOf(account.getId()));
            Map<String, Object> variables = new HashMap<>();
            variables.put("systemName", "管理系统");
            variables.put("userName", StringUtils.hasText(user.getRealName()) ? user.getRealName() : account.getLoginAccount());
            variables.put("loginAccount", account.getLoginAccount());
            variables.put("initialPassword", initialPassword);
            variables.put("loginUrl", adminLoginUrl());
            variables.put("mfaGuide", "首次登录时请按页面提示完成多因素认证（MFA）绑定。绑定二维码和手动密钥只会在登录页身份校验通过后展示，邮件不会包含 MFA 密钥。");
            variables.put("verifyCodeGuide", "登录页会自动加载图形验证码，请输入图片中的验证码后继续登录。");
            request.setVariables(variables);
            adminEmailService.sendByTemplate(request);
        } catch (RuntimeException exception) {
            log.warn("admin account created notice send failed, accountId: {}, exceptionType: {}",
                    account.getId(), exception.getClass().getSimpleName());
        }
    }

    /** 事务提交后发送管理员重置密码通知，避免数据库回滚后误发。 */
    private void sendPasswordChangedNoticeAfterCommit(SysAccountDO account,
                                                      SysUserDO user,
                                                      String temporaryPassword,
                                                      LocalDateTime operationTime) {
        Runnable task = () -> sendPasswordChangedNotice(account, user, temporaryPassword, operationTime);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            task.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                task.run();
            }
        });
    }

    /** 发送管理员重置后台账号密码通知，发送失败不回滚已完成的密码更新。 */
    private void sendPasswordChangedNotice(SysAccountDO account,
                                           SysUserDO user,
                                           String temporaryPassword,
                                           LocalDateTime operationTime) {
        if (!StringUtils.hasText(account.getEmail())) {
            return;
        }
        try {
            EmailSendRequest request = new EmailSendRequest();
            request.setAppCode(AuthConstants.APP_ADMIN);
            request.setTemplateCode(PASSWORD_CHANGED_TEMPLATE);
            request.setSceneCode(PASSWORD_CHANGED_SCENE);
            request.setLocale("zh-CN");
            request.setToEmails(List.of(account.getEmail()));
            request.setBizType(PASSWORD_CHANGED_SCENE);
            request.setBizNo(String.valueOf(account.getId()));
            Map<String, Object> variables = new HashMap<>();
            variables.put("systemName", "Vexra Admin");
            variables.put("userName", StringUtils.hasText(user.getRealName()) ? user.getRealName() : account.getLoginAccount());
            variables.put("loginAccount", account.getLoginAccount());
            variables.put("temporaryPassword", temporaryPassword);
            variables.put("operatorName", currentOperatorName());
            variables.put("operationTime", EMAIL_TIME_FORMATTER.format(operationTime));
            variables.put("loginUrl", adminLoginUrl());
            request.setVariables(variables);
            adminEmailService.sendByTemplate(request);
        } catch (RuntimeException exception) {
            log.warn("admin password changed notice send failed, accountId: {}, exceptionType: {}",
                    account.getId(), exception.getClass().getSimpleName());
        }
    }

    /** 返回当前后台操作人的可展示名称，不在通知中暴露内部身份上下文。 */
    private String currentOperatorName() {
        InternalAuthAccount operator = InternalAuthContextHolder.get();
        if (operator == null) {
            return "System Administrator";
        }
        if (StringUtils.hasText(operator.getRealName())) {
            return operator.getRealName();
        }
        return StringUtils.hasText(operator.getLoginAccount()) ? operator.getLoginAccount() : "System Administrator";
    }

    /**
     * 获取管理系统登录地址，优先使用参数管理配置。
     *
     * @return 管理系统登录页地址
     */
    private String adminLoginUrl() {
        Map<String, String> configValues = adminConfigService.enabledConfigValues(Set.of(SystemConfigKeys.ADMIN_FRONTEND_BASE_URL));
        String baseUrl = configValues.get(SystemConfigKeys.ADMIN_FRONTEND_BASE_URL);
        if (!StringUtils.hasText(baseUrl)) {
            return "http://127.0.0.1:5173/login";
        }
        return baseUrl.replaceAll("/+$", "") + "/login";
    }

    /**
     * 回填用户列表 MFA 状态，老账号无记录时按 OPTIONAL + NOT_ENABLED 展示。
     *
     * @param account 账号实体
     * @param dto     用户 DTO
     */
    private void fillMfaStatus(SysAccountDO account, SysUserAccountDTO dto) {
        SysAccountMfaDO mfa = sysAccountMfaMapper.selectOne(
                Wrappers.<SysAccountMfaDO>lambdaQuery()
                        .eq(SysAccountMfaDO::getAppId, account.getAppId())
                        .eq(SysAccountMfaDO::getAccountId, account.getId())
                        .eq(SysAccountMfaDO::getDeleted, NOT_DELETED)
                        .last("LIMIT 1")
        );
        dto.setMfaPolicy(mfa == null ? AuthConstants.MFA_POLICY_OPTIONAL : mfa.getMfaPolicy());
        dto.setMfaStatus(mfa == null ? AuthConstants.MFA_STATUS_NOT_ENABLED : mfa.getMfaStatus());
        dto.setMfaBindTime(mfa == null ? null : mfa.getBindTime());
        dto.setMfaLastVerifyTime(mfa == null ? null : mfa.getLastVerifyTime());
        dto.setMfaExemptUntil(mfa == null ? null : mfa.getExemptUntil());
        dto.setMfaLockedUntil(mfa == null ? null : mfa.getLockedUntil());
    }

    /**
     * 构造角色dto对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param role role 输入值，参与 角色 的查询、校验、转换、写入或日志摘要
     * @param assignable assignable 输入值，参与 assignable 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private SysRoleDTO toRoleDTO(SysRoleDO role, boolean assignable) {
        SysRoleDTO dto = new SysRoleDTO();
        dto.setRoleId(role.getId());
        dto.setRoleCode(role.getRoleCode());
        dto.setRoleName(role.getRoleName());
        dto.setRoleType(role.getRoleType());
        dto.setDataScope(role.getDataScope());
        dto.setDescription(role.getDescription());
        dto.setStatus(role.getStatus());
        dto.setSortNo(role.getSortNo());
        dto.setAssignable(assignable);
        dto.setCreatedAt(role.getCreatedAt());
        dto.setUpdatedAt(role.getUpdatedAt());
        return dto;
    }
}
