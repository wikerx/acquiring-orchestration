package com.scott.payment.merchant.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.auth.PasswordHashUtils;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.db.auth.constant.AuthConstants;
import com.scott.payment.component.db.auth.dto.AuthMenuDTO;
import com.scott.payment.component.db.auth.entity.BaseMerchantInfoDO;
import com.scott.payment.component.db.auth.entity.SysAccountDO;
import com.scott.payment.component.db.auth.entity.SysAppDO;
import com.scott.payment.component.db.auth.entity.SysMerchantAccountDeptDO;
import com.scott.payment.component.db.auth.entity.SysMerchantAccountPostDO;
import com.scott.payment.component.db.auth.entity.SysMerchantDeptDO;
import com.scott.payment.component.db.auth.entity.SysMerchantMenuGrantDO;
import com.scott.payment.component.db.auth.entity.SysMerchantPermissionGrantDO;
import com.scott.payment.component.db.auth.entity.SysMerchantPostDO;
import com.scott.payment.component.db.auth.entity.SysMerchantUserDO;
import com.scott.payment.component.db.auth.entity.SysMerchantUserRoleDO;
import com.scott.payment.component.db.auth.entity.SysMenuDO;
import com.scott.payment.component.db.auth.entity.SysPermissionDO;
import com.scott.payment.component.db.auth.entity.SysRoleDO;
import com.scott.payment.component.db.auth.entity.SysRoleMenuDO;
import com.scott.payment.component.db.auth.entity.SysRolePermissionDO;
import com.scott.payment.component.db.auth.entity.SysUserDO;
import com.scott.payment.component.db.auth.mapper.BaseMerchantInfoMapper;
import com.scott.payment.component.db.auth.mapper.SysAccountMapper;
import com.scott.payment.component.db.auth.mapper.SysAppMapper;
import com.scott.payment.component.db.auth.mapper.SysMerchantAccountDeptMapper;
import com.scott.payment.component.db.auth.mapper.SysMerchantAccountPostMapper;
import com.scott.payment.component.db.auth.mapper.SysMerchantDeptMapper;
import com.scott.payment.component.db.auth.mapper.SysMerchantMenuGrantMapper;
import com.scott.payment.component.db.auth.mapper.SysMerchantPermissionGrantMapper;
import com.scott.payment.component.db.auth.mapper.SysMerchantPostMapper;
import com.scott.payment.component.db.auth.mapper.SysMerchantUserMapper;
import com.scott.payment.component.db.auth.mapper.SysMerchantUserRoleMapper;
import com.scott.payment.component.db.auth.mapper.SysMenuMapper;
import com.scott.payment.component.db.auth.mapper.SysPermissionMapper;
import com.scott.payment.component.db.auth.mapper.SysRoleMapper;
import com.scott.payment.component.db.auth.mapper.SysRoleMenuMapper;
import com.scott.payment.component.db.auth.mapper.SysRolePermissionMapper;
import com.scott.payment.component.db.auth.mapper.SysUserMapper;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.AccountBaseSaveRequest;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.AccountDTO;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.AccountQueryRequest;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.AccountSaveRequest;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.AuthGrantNodeDTO;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.DeptDTO;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.DeptQueryRequest;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.DeptSaveRequest;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.IdsRequest;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.PermissionDTO;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.PostDTO;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.PostQueryRequest;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.PostSaveRequest;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.RoleDTO;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.RoleGrantTreeDTO;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.RoleGrantTreeSaveRequest;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.RoleMenuAuthDTO;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.RolePermissionAuthDTO;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.RoleQueryRequest;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.RoleSaveRequest;
import com.scott.payment.merchant.service.MerchantSystemService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 商户系统基础管理领域服务实现。
 *
 * <p>所有数据访问都从当前登录上下文获取商户号，避免前端传参造成跨商户越权。</p>
 */
@Service
public class MerchantSystemServiceImpl implements MerchantSystemService {

    private static final long ROOT_PARENT_ID = 0L;
    private static final String ROLE_TYPE_CUSTOM = "CUSTOM";
    private static final String ROLE_TYPE_SYSTEM = "SYSTEM";
    private static final String DATA_SCOPE_ALL = "ALL";
    private static final String DATA_SCOPE_SELF = "SELF";
    private static final String DATA_SCOPE_CUSTOM = "CUSTOM";
    private static final String ACCOUNT_LOGIN_SEPARATOR = "_";

    private final BaseMerchantInfoMapper baseMerchantInfoMapper;
    private final SysAppMapper sysAppMapper;
    private final SysUserMapper sysUserMapper;
    private final SysAccountMapper sysAccountMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysRoleMenuMapper sysRoleMenuMapper;
    private final SysRolePermissionMapper sysRolePermissionMapper;
    private final SysMenuMapper sysMenuMapper;
    private final SysPermissionMapper sysPermissionMapper;
    private final SysMerchantDeptMapper sysMerchantDeptMapper;
    private final SysMerchantPostMapper sysMerchantPostMapper;
    private final SysMerchantAccountDeptMapper sysMerchantAccountDeptMapper;
    private final SysMerchantAccountPostMapper sysMerchantAccountPostMapper;
    private final SysMerchantMenuGrantMapper sysMerchantMenuGrantMapper;
    private final SysMerchantPermissionGrantMapper sysMerchantPermissionGrantMapper;
    private final SysMerchantUserMapper sysMerchantUserMapper;
    private final SysMerchantUserRoleMapper sysMerchantUserRoleMapper;

    public MerchantSystemServiceImpl(BaseMerchantInfoMapper baseMerchantInfoMapper,
                                     SysAppMapper sysAppMapper,
                                     SysUserMapper sysUserMapper,
                                     SysAccountMapper sysAccountMapper,
                                     SysRoleMapper sysRoleMapper,
                                     SysRoleMenuMapper sysRoleMenuMapper,
                                     SysRolePermissionMapper sysRolePermissionMapper,
                                     SysMenuMapper sysMenuMapper,
                                     SysPermissionMapper sysPermissionMapper,
                                     SysMerchantDeptMapper sysMerchantDeptMapper,
                                     SysMerchantPostMapper sysMerchantPostMapper,
                                     SysMerchantAccountDeptMapper sysMerchantAccountDeptMapper,
                                     SysMerchantAccountPostMapper sysMerchantAccountPostMapper,
                                     SysMerchantMenuGrantMapper sysMerchantMenuGrantMapper,
                                     SysMerchantPermissionGrantMapper sysMerchantPermissionGrantMapper,
                                     SysMerchantUserMapper sysMerchantUserMapper,
                                     SysMerchantUserRoleMapper sysMerchantUserRoleMapper) {
        this.baseMerchantInfoMapper = baseMerchantInfoMapper;
        this.sysAppMapper = sysAppMapper;
        this.sysUserMapper = sysUserMapper;
        this.sysAccountMapper = sysAccountMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysRoleMenuMapper = sysRoleMenuMapper;
        this.sysRolePermissionMapper = sysRolePermissionMapper;
        this.sysMenuMapper = sysMenuMapper;
        this.sysPermissionMapper = sysPermissionMapper;
        this.sysMerchantDeptMapper = sysMerchantDeptMapper;
        this.sysMerchantPostMapper = sysMerchantPostMapper;
        this.sysMerchantAccountDeptMapper = sysMerchantAccountDeptMapper;
        this.sysMerchantAccountPostMapper = sysMerchantAccountPostMapper;
        this.sysMerchantMenuGrantMapper = sysMerchantMenuGrantMapper;
        this.sysMerchantPermissionGrantMapper = sysMerchantPermissionGrantMapper;
        this.sysMerchantUserMapper = sysMerchantUserMapper;
        this.sysMerchantUserRoleMapper = sysMerchantUserRoleMapper;
    }

    @Override
    @DS(DataSourceName.SLAVE)
    public List<DeptDTO> listDepts() {
        String merchantId = currentMerchantId();
        return sysMerchantDeptMapper.selectList(Wrappers.<SysMerchantDeptDO>lambdaQuery()
                        .eq(SysMerchantDeptDO::getMerchantId, merchantId)
                        .eq(SysMerchantDeptDO::getDeleted, AuthConstants.NOT_DELETED)
                        .orderByAsc(SysMerchantDeptDO::getSortNo, SysMerchantDeptDO::getId))
                .stream().map(this::toDeptDTO).toList();
    }

    @Override
    @DS(DataSourceName.SLAVE)
    public PageResult<DeptDTO> pageDepts(DeptQueryRequest request) {
        DeptQueryRequest query = request == null ? new DeptQueryRequest() : request;
        String keyword = normalize(query.getKeyword());
        Page<SysMerchantDeptDO> page = sysMerchantDeptMapper.selectPage(new Page<>(query.safePageNo(), query.safePageSize()),
                Wrappers.<SysMerchantDeptDO>lambdaQuery()
                        .eq(SysMerchantDeptDO::getMerchantId, currentMerchantId())
                        .eq(SysMerchantDeptDO::getDeleted, AuthConstants.NOT_DELETED)
                        .eq(query.getStatus() != null, SysMerchantDeptDO::getStatus, query.getStatus())
                        .and(StringUtils.hasText(keyword), wrapper -> wrapper
                                .like(SysMerchantDeptDO::getDeptName, keyword)
                                .or()
                                .like(SysMerchantDeptDO::getDeptCode, keyword))
                        .orderByAsc(SysMerchantDeptDO::getSortNo, SysMerchantDeptDO::getId));
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords().stream().map(this::toDeptDTO).toList());
    }

    @Override
    @DS(DataSourceName.SLAVE)
    public List<DeptDTO> deptTree() {
        return buildDeptTree(listDepts());
    }

    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public DeptDTO createDept(DeptSaveRequest request) {
        String merchantId = currentMerchantId();
        validateDeptParent(merchantId, request.getParentId(), null);
        assertDeptCodeAvailable(merchantId, request.getDeptCode(), null);
        SysMerchantDeptDO dept = new SysMerchantDeptDO();
        applyDept(dept, request);
        LocalDateTime now = LocalDateTime.now();
        dept.setMerchantId(merchantId);
        dept.setCreatedAt(now);
        dept.setUpdatedAt(now);
        dept.setCreatedBy(currentAccountId());
        dept.setUpdatedBy(currentAccountId());
        dept.setDeleted(AuthConstants.NOT_DELETED);
        sysMerchantDeptMapper.insert(dept);
        return toDeptDTO(dept);
    }

    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public DeptDTO updateDept(Long id, DeptSaveRequest request) {
        String merchantId = currentMerchantId();
        SysMerchantDeptDO dept = getDept(merchantId, id);
        validateDeptParent(merchantId, request.getParentId(), id);
        assertDeptCodeAvailable(merchantId, request.getDeptCode(), id);
        applyDept(dept, request);
        dept.setUpdatedAt(LocalDateTime.now());
        dept.setUpdatedBy(currentAccountId());
        sysMerchantDeptMapper.updateById(dept);
        return toDeptDTO(dept);
    }

    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public void deleteDept(Long id) {
        String merchantId = currentMerchantId();
        SysMerchantDeptDO dept = getDept(merchantId, id);
        Long childCount = sysMerchantDeptMapper.selectCount(Wrappers.<SysMerchantDeptDO>lambdaQuery()
                .eq(SysMerchantDeptDO::getMerchantId, merchantId)
                .eq(SysMerchantDeptDO::getParentId, id)
                .eq(SysMerchantDeptDO::getDeleted, AuthConstants.NOT_DELETED));
        if (childCount != null && childCount > 0) {
            throw new ServiceException(ApiResultEnum.BAD_REQUEST.getCode(), "dept has children");
        }
        Long bindCount = sysMerchantAccountDeptMapper.selectCount(Wrappers.<SysMerchantAccountDeptDO>lambdaQuery()
                .eq(SysMerchantAccountDeptDO::getMerchantId, merchantId)
                .eq(SysMerchantAccountDeptDO::getDeptId, id));
        if (bindCount != null && bindCount > 0) {
            throw new ServiceException(ApiResultEnum.BAD_REQUEST.getCode(), "dept is assigned to accounts");
        }
        dept.setDeleted(dept.getId());
        dept.setStatus(AuthConstants.DISABLED);
        dept.setUpdatedAt(LocalDateTime.now());
        sysMerchantDeptMapper.updateById(dept);
    }

    @Override
    @DS(DataSourceName.SLAVE)
    public List<PostDTO> listPosts() {
        String merchantId = currentMerchantId();
        return sysMerchantPostMapper.selectList(Wrappers.<SysMerchantPostDO>lambdaQuery()
                        .eq(SysMerchantPostDO::getMerchantId, merchantId)
                        .eq(SysMerchantPostDO::getDeleted, AuthConstants.NOT_DELETED)
                        .orderByAsc(SysMerchantPostDO::getSortNo, SysMerchantPostDO::getId))
                .stream().map(this::toPostDTO).toList();
    }

    @Override
    @DS(DataSourceName.SLAVE)
    public PageResult<PostDTO> pagePosts(PostQueryRequest request) {
        PostQueryRequest query = request == null ? new PostQueryRequest() : request;
        String keyword = normalize(query.getKeyword());
        Page<SysMerchantPostDO> page = sysMerchantPostMapper.selectPage(new Page<>(query.safePageNo(), query.safePageSize()),
                Wrappers.<SysMerchantPostDO>lambdaQuery()
                        .eq(SysMerchantPostDO::getMerchantId, currentMerchantId())
                        .eq(SysMerchantPostDO::getDeleted, AuthConstants.NOT_DELETED)
                        .eq(query.getStatus() != null, SysMerchantPostDO::getStatus, query.getStatus())
                        .and(StringUtils.hasText(keyword), wrapper -> wrapper
                                .like(SysMerchantPostDO::getPostName, keyword)
                                .or()
                                .like(SysMerchantPostDO::getPostCode, keyword))
                        .orderByAsc(SysMerchantPostDO::getSortNo, SysMerchantPostDO::getId));
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords().stream().map(this::toPostDTO).toList());
    }

    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public PostDTO createPost(PostSaveRequest request) {
        String merchantId = currentMerchantId();
        assertPostCodeAvailable(merchantId, request.getPostCode(), null);
        SysMerchantPostDO post = new SysMerchantPostDO();
        applyPost(post, request);
        LocalDateTime now = LocalDateTime.now();
        post.setMerchantId(merchantId);
        post.setCreatedAt(now);
        post.setUpdatedAt(now);
        post.setCreatedBy(currentAccountId());
        post.setUpdatedBy(currentAccountId());
        post.setDeleted(AuthConstants.NOT_DELETED);
        sysMerchantPostMapper.insert(post);
        return toPostDTO(post);
    }

    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public PostDTO updatePost(Long id, PostSaveRequest request) {
        String merchantId = currentMerchantId();
        SysMerchantPostDO post = getPost(merchantId, id);
        assertPostCodeAvailable(merchantId, request.getPostCode(), id);
        applyPost(post, request);
        post.setUpdatedAt(LocalDateTime.now());
        post.setUpdatedBy(currentAccountId());
        sysMerchantPostMapper.updateById(post);
        return toPostDTO(post);
    }

    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public void deletePost(Long id) {
        String merchantId = currentMerchantId();
        SysMerchantPostDO post = getPost(merchantId, id);
        Long bindCount = sysMerchantAccountPostMapper.selectCount(Wrappers.<SysMerchantAccountPostDO>lambdaQuery()
                .eq(SysMerchantAccountPostDO::getMerchantId, merchantId)
                .eq(SysMerchantAccountPostDO::getPostId, id));
        if (bindCount != null && bindCount > 0) {
            throw new ServiceException(ApiResultEnum.BAD_REQUEST.getCode(), "post is assigned to accounts");
        }
        post.setDeleted(post.getId());
        post.setStatus(AuthConstants.DISABLED);
        post.setUpdatedAt(LocalDateTime.now());
        sysMerchantPostMapper.updateById(post);
    }

    @Override
    @DS(DataSourceName.SLAVE)
    public List<AccountDTO> listAccounts() {
        SysAppDO app = merchantApp();
        String merchantId = currentMerchantId();
        List<SysMerchantUserDO> users = sysMerchantUserMapper.selectList(Wrappers.<SysMerchantUserDO>lambdaQuery()
                .eq(SysMerchantUserDO::getMerchantId, merchantId)
                .eq(SysMerchantUserDO::getDeleted, AuthConstants.NOT_DELETED)
                .orderByAsc(SysMerchantUserDO::getId));
        return users.stream().map(user -> toAccountDTO(app.getId(), user)).toList();
    }

    @Override
    @DS(DataSourceName.SLAVE)
    public PageResult<AccountDTO> pageAccounts(AccountQueryRequest request) {
        AccountQueryRequest query = request == null ? new AccountQueryRequest() : request;
        SysAppDO app = merchantApp();
        String merchantId = currentMerchantId();
        Set<Long> merchantUserIds = merchantUserIdsByRole(app.getId(), query.getRoleId());
        if (query.getRoleId() != null && merchantUserIds.isEmpty()) {
            return PageResult.of(0, query.safePageNo(), query.safePageSize(), Collections.emptyList());
        }
        String keyword = normalize(query.getKeyword());
        Set<Long> accountIdsByKeyword = accountIdsByKeyword(app.getId(), merchantId, keyword);
        Page<SysMerchantUserDO> page = sysMerchantUserMapper.selectPage(new Page<>(query.safePageNo(), query.safePageSize()),
                Wrappers.<SysMerchantUserDO>lambdaQuery()
                        .eq(SysMerchantUserDO::getMerchantId, merchantId)
                        .eq(SysMerchantUserDO::getDeleted, AuthConstants.NOT_DELETED)
                        .eq(query.getStatus() != null, SysMerchantUserDO::getStatus, query.getStatus())
                        .in(query.getRoleId() != null, SysMerchantUserDO::getId, merchantUserIds)
                        .and(StringUtils.hasText(keyword), wrapper -> wrapper
                                .like(SysMerchantUserDO::getLoginAccount, keyword)
                                .or()
                                .like(SysMerchantUserDO::getRealName, keyword)
                                .or(!accountIdsByKeyword.isEmpty())
                                .in(!accountIdsByKeyword.isEmpty(), SysMerchantUserDO::getAccountId, accountIdsByKeyword))
                        .orderByDesc(SysMerchantUserDO::getCreatedAt)
                        .orderByAsc(SysMerchantUserDO::getId));
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(),
                page.getRecords().stream().map(user -> toAccountDTO(app.getId(), user)).toList());
    }

    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public AccountDTO createAccount(AccountSaveRequest request) {
        SysAppDO app = merchantApp();
        String merchantId = currentMerchantId();
        assertAccountAvailable(merchantId, request.getLoginAccount(), null);
        LocalDateTime now = LocalDateTime.now();
        SysUserDO user = new SysUserDO();
        user.setUserType(AuthConstants.USER_TYPE_MERCHANT);
        user.setRealName(required(request.getRealName(), "realName"));
        user.setMobile(normalize(request.getMobile()));
        user.setEmail(normalize(request.getEmail()));
        user.setStatus(validStatus(request.getStatus()));
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        user.setCreatedBy(currentAccountId());
        user.setUpdatedBy(currentAccountId());
        user.setDeleted(AuthConstants.NOT_DELETED);
        sysUserMapper.insert(user);
        String salt = PasswordHashUtils.generateSalt();
        SysAccountDO account = new SysAccountDO();
        account.setAppId(app.getId());
        account.setUserId(user.getId());
        account.setMerchantId(merchantId);
        account.setLoginAccount(toAccountLoginName(merchantId, request.getLoginAccount()));
        account.setPasswordSalt(salt);
        account.setPasswordHash(PasswordHashUtils.hashPassword(required(request.getPassword(), "password"), salt));
        account.setPasswordAlgo(PasswordHashUtils.ALGORITHM);
        account.setMobile(user.getMobile());
        account.setEmail(user.getEmail());
        account.setMfaEnabled(AuthConstants.DISABLED);
        account.setPasswordExpired(AuthConstants.DISABLED);
        account.setPasswordUpdatedAt(now);
        account.setFailedLoginCount(0);
        account.setLocked(AuthConstants.DISABLED);
        account.setStatus(user.getStatus());
        account.setCreatedAt(now);
        account.setUpdatedAt(now);
        account.setCreatedBy(currentAccountId());
        account.setUpdatedBy(currentAccountId());
        account.setDeleted(AuthConstants.NOT_DELETED);
        sysAccountMapper.insert(account);
        SysMerchantUserDO merchantUser = createMerchantUser(merchantId, request.getLoginAccount(), user, account, now);
        replaceAccountRoles(app.getId(), merchantId, merchantUser, request.getRoleIds());
        replaceAccountDepts(merchantId, account.getId(), request.getDeptIds());
        replaceAccountPosts(merchantId, account.getId(), request.getPostIds());
        return toAccountDTO(app.getId(), account);
    }

    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public AccountDTO updateAccount(Long id, AccountSaveRequest request) {
        SysAppDO app = merchantApp();
        String merchantId = currentMerchantId();
        SysAccountDO account = getAccount(app.getId(), merchantId, id);
        SysMerchantUserDO merchantUser = getMerchantUser(merchantId, account.getId());
        updateAccountBaseFields(app.getId(), merchantId, account, request);
        replaceAccountRoles(app.getId(), merchantId, merchantUser, request.getRoleIds());
        replaceAccountDepts(merchantId, account.getId(), request.getDeptIds());
        replaceAccountPosts(merchantId, account.getId(), request.getPostIds());
        return toAccountDTO(app.getId(), account);
    }

    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public AccountDTO updateAccountBase(Long id, AccountBaseSaveRequest request) {
        SysAppDO app = merchantApp();
        String merchantId = currentMerchantId();
        SysAccountDO account = getAccount(app.getId(), merchantId, id);
        SysMerchantUserDO merchantUser = getMerchantUser(merchantId, account.getId());
        updateAccountBaseFields(app.getId(), merchantId, account, request);
        merchantUser.setLoginAccount(required(request.getLoginAccount(), "loginAccount"));
        merchantUser.setRealName(required(request.getRealName(), "realName"));
        merchantUser.setStatus(validStatus(request.getStatus()));
        merchantUser.setUpdatedAt(LocalDateTime.now());
        merchantUser.setUpdatedBy(currentAccountId());
        sysMerchantUserMapper.updateById(merchantUser);
        return toAccountDTO(app.getId(), account);
    }

    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public void deleteAccount(Long id) {
        SysAppDO app = merchantApp();
        String merchantId = currentMerchantId();
        if (Objects.equals(id, currentAccountId())) {
            throw new ServiceException(ApiResultEnum.BAD_REQUEST.getCode(), "can not delete current account");
        }
        SysAccountDO account = getAccount(app.getId(), merchantId, id);
        account.setDeleted(account.getId());
        account.setStatus(AuthConstants.DISABLED);
        account.setUpdatedAt(LocalDateTime.now());
        sysAccountMapper.updateById(account);
        SysMerchantUserDO merchantUser = getMerchantUser(merchantId, account.getId());
        merchantUser.setDeleted(merchantUser.getId());
        merchantUser.setStatus(AuthConstants.DISABLED);
        merchantUser.setUpdatedAt(LocalDateTime.now());
        merchantUser.setUpdatedBy(currentAccountId());
        sysMerchantUserMapper.updateById(merchantUser);
    }

    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public void updateAccountStatus(Long id, Integer status) {
        SysAppDO app = merchantApp();
        String merchantId = currentMerchantId();
        if (Objects.equals(id, currentAccountId()) && validStatus(status) == AuthConstants.DISABLED) {
            throw new ServiceException(ApiResultEnum.BAD_REQUEST.getCode(), "can not disable current account");
        }
        SysAccountDO account = getAccount(app.getId(), merchantId, id);
        account.setStatus(validStatus(status));
        account.setUpdatedAt(LocalDateTime.now());
        sysAccountMapper.updateById(account);
        SysMerchantUserDO merchantUser = getMerchantUser(merchantId, account.getId());
        merchantUser.setStatus(validStatus(status));
        merchantUser.setUpdatedAt(LocalDateTime.now());
        merchantUser.setUpdatedBy(currentAccountId());
        sysMerchantUserMapper.updateById(merchantUser);
    }

    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public void assignAccountRoles(Long id, IdsRequest request) {
        SysAppDO app = merchantApp();
        String merchantId = currentMerchantId();
        SysAccountDO account = getAccount(app.getId(), merchantId, id);
        SysMerchantUserDO merchantUser = getMerchantUser(merchantId, account.getId());
        replaceAccountRoles(app.getId(), merchantId, merchantUser, request.getIds());
    }

    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public void assignAccountDepts(Long id, IdsRequest request) {
        SysAppDO app = merchantApp();
        String merchantId = currentMerchantId();
        getAccount(app.getId(), merchantId, id);
        replaceAccountDepts(merchantId, id, request.getIds());
    }

    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public void assignAccountPosts(Long id, IdsRequest request) {
        SysAppDO app = merchantApp();
        String merchantId = currentMerchantId();
        getAccount(app.getId(), merchantId, id);
        replaceAccountPosts(merchantId, id, request.getIds());
    }

    @Override
    @DS(DataSourceName.SLAVE)
    public List<RoleDTO> listRoles() {
        SysAppDO app = merchantApp();
        String merchantId = currentMerchantId();
        return sysRoleMapper.selectList(Wrappers.<SysRoleDO>lambdaQuery()
                        .eq(SysRoleDO::getAppId, app.getId())
                        .eq(SysRoleDO::getMerchantId, merchantId)
                        .eq(SysRoleDO::getDeleted, AuthConstants.NOT_DELETED)
                        .orderByAsc(SysRoleDO::getSortNo, SysRoleDO::getId))
                .stream().map(this::toRoleDTO).toList();
    }

    @Override
    @DS(DataSourceName.SLAVE)
    public PageResult<RoleDTO> pageRoles(RoleQueryRequest request) {
        RoleQueryRequest query = request == null ? new RoleQueryRequest() : request;
        SysAppDO app = merchantApp();
        String merchantId = currentMerchantId();
        LocalDateTime startTime = parseQueryDateTime(query.getCreatedStartTime(), false);
        LocalDateTime endTime = parseQueryDateTime(query.getCreatedEndTime(), true);
        Page<SysRoleDO> page = sysRoleMapper.selectPage(new Page<>(query.safePageNo(), query.safePageSize()),
                Wrappers.<SysRoleDO>lambdaQuery()
                        .eq(SysRoleDO::getAppId, app.getId())
                        .eq(SysRoleDO::getMerchantId, merchantId)
                        .eq(SysRoleDO::getDeleted, AuthConstants.NOT_DELETED)
                        .like(StringUtils.hasText(query.getRoleName()), SysRoleDO::getRoleName, normalize(query.getRoleName()))
                        .like(StringUtils.hasText(query.getRoleCode()), SysRoleDO::getRoleCode, normalize(query.getRoleCode()))
                        .eq(query.getStatus() != null, SysRoleDO::getStatus, query.getStatus())
                        .ge(startTime != null, SysRoleDO::getCreatedAt, startTime)
                        .le(endTime != null, SysRoleDO::getCreatedAt, endTime)
                        .orderByAsc(SysRoleDO::getSortNo, SysRoleDO::getId));
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords().stream().map(this::toRoleDTO).toList());
    }

    @Override
    @DS(DataSourceName.SLAVE)
    public RoleDTO getRole(Long id) {
        SysAppDO app = merchantApp();
        String merchantId = currentMerchantId();
        return toRoleDTO(getRole(app.getId(), merchantId, id));
    }

    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public RoleDTO createRole(RoleSaveRequest request) {
        SysAppDO app = merchantApp();
        String merchantId = currentMerchantId();
        assertRoleCodeAvailable(app.getId(), merchantId, request.getRoleCode(), null);
        LocalDateTime now = LocalDateTime.now();
        SysRoleDO role = new SysRoleDO();
        role.setAppId(app.getId());
        role.setMerchantId(merchantId);
        role.setRoleCode(required(request.getRoleCode(), "roleCode"));
        role.setRoleName(required(request.getRoleName(), "roleName"));
        role.setRoleType(ROLE_TYPE_CUSTOM);
        role.setDataScope(resolveDataScope(request.getDataScope()));
        role.setDescription(normalize(request.getDescription()));
        role.setStatus(validStatus(request.getStatus()));
        role.setSortNo(request.getSortNo() == null ? 100 : request.getSortNo());
        role.setCreatedAt(now);
        role.setUpdatedAt(now);
        role.setCreatedBy(currentAccountId());
        role.setUpdatedBy(currentAccountId());
        role.setDeleted(AuthConstants.NOT_DELETED);
        sysRoleMapper.insert(role);
        replaceRoleGrants(app.getId(), merchantId, role.getId(), request.getMenuIds(), request.getPermissionIds());
        return toRoleDTO(role);
    }

    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public RoleDTO updateRole(Long id, RoleSaveRequest request) {
        SysAppDO app = merchantApp();
        String merchantId = currentMerchantId();
        SysRoleDO role = getRole(app.getId(), merchantId, id);
        if (isSystemRole(role)) {
            if (StringUtils.hasText(request.getRoleCode()) && !Objects.equals(role.getRoleCode(), request.getRoleCode().trim())) {
                throw new ServiceException(ApiResultEnum.FORBIDDEN.getCode(), "system role code can not be changed");
            }
        } else {
            assertRoleCodeAvailable(app.getId(), merchantId, request.getRoleCode(), id);
            role.setRoleCode(required(request.getRoleCode(), "roleCode"));
        }
        role.setRoleName(required(request.getRoleName(), "roleName"));
        role.setDataScope(resolveDataScope(request.getDataScope()));
        role.setDescription(normalize(request.getDescription()));
        role.setStatus(validStatus(request.getStatus()));
        role.setSortNo(request.getSortNo() == null ? 100 : request.getSortNo());
        role.setUpdatedAt(LocalDateTime.now());
        role.setUpdatedBy(currentAccountId());
        sysRoleMapper.updateById(role);
        replaceRoleGrants(app.getId(), merchantId, id, request.getMenuIds(), request.getPermissionIds());
        return toRoleDTO(role);
    }

    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(Long id) {
        SysAppDO app = merchantApp();
        String merchantId = currentMerchantId();
        SysRoleDO role = getRole(app.getId(), merchantId, id);
        if (isSystemRole(role)) {
            throw new ServiceException(ApiResultEnum.FORBIDDEN.getCode(), "system role can not be deleted");
        }
        Long bindCount = sysMerchantUserRoleMapper.selectCount(Wrappers.<SysMerchantUserRoleDO>lambdaQuery()
                .eq(SysMerchantUserRoleDO::getAppId, app.getId())
                .eq(SysMerchantUserRoleDO::getRoleId, id)
                .eq(SysMerchantUserRoleDO::getDeleted, AuthConstants.NOT_DELETED));
        if (bindCount != null && bindCount > 0) {
            throw new ServiceException(ApiResultEnum.BAD_REQUEST.getCode(), "role is assigned to accounts");
        }
        role.setDeleted(role.getId());
        role.setStatus(AuthConstants.DISABLED);
        role.setUpdatedAt(LocalDateTime.now());
        sysRoleMapper.updateById(role);
    }

    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public void updateRoleStatus(Long id, Integer status) {
        SysAppDO app = merchantApp();
        String merchantId = currentMerchantId();
        SysRoleDO role = getRole(app.getId(), merchantId, id);
        role.setStatus(validStatus(status));
        role.setUpdatedAt(LocalDateTime.now());
        role.setUpdatedBy(currentAccountId());
        sysRoleMapper.updateById(role);
    }

    @Override
    @DS(DataSourceName.SLAVE)
    public RoleGrantTreeDTO roleGrantTreeTemplate() {
        SysAppDO app = merchantApp();
        String merchantId = currentMerchantId();
        RoleGrantTreeDTO dto = new RoleGrantTreeDTO();
        dto.setTree(buildGrantTree(loadGrantedMenuTree(app.getId(), merchantId), grantedPermissions()));
        return dto;
    }

    @Override
    @DS(DataSourceName.SLAVE)
    public RoleGrantTreeDTO roleGrantTree(Long id) {
        SysAppDO app = merchantApp();
        String merchantId = currentMerchantId();
        SysRoleDO role = getRole(app.getId(), merchantId, id);
        RoleGrantTreeDTO dto = new RoleGrantTreeDTO();
        dto.setRoleId(id);
        dto.setRole(toRoleDTO(role));
        dto.setTree(buildGrantTree(loadGrantedMenuTree(app.getId(), merchantId), grantedPermissions()));
        dto.setCheckedMenuIds(sysRoleMenuMapper.selectList(Wrappers.<SysRoleMenuDO>lambdaQuery()
                        .eq(SysRoleMenuDO::getAppId, app.getId())
                        .eq(SysRoleMenuDO::getRoleId, id)
                        .eq(SysRoleMenuDO::getDeleted, AuthConstants.NOT_DELETED))
                .stream().map(SysRoleMenuDO::getMenuId).toList());
        dto.setCheckedPermissionIds(sysRolePermissionMapper.selectList(Wrappers.<SysRolePermissionDO>lambdaQuery()
                        .eq(SysRolePermissionDO::getAppId, app.getId())
                        .eq(SysRolePermissionDO::getRoleId, id)
                        .eq(SysRolePermissionDO::getDeleted, AuthConstants.NOT_DELETED))
                .stream().map(SysRolePermissionDO::getPermissionId).toList());
        return dto;
    }

    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public void grantRoleTree(Long id, RoleGrantTreeSaveRequest request) {
        SysAppDO app = merchantApp();
        String merchantId = currentMerchantId();
        getRole(app.getId(), merchantId, id);
        replaceRoleGrants(app.getId(), merchantId, id,
                request == null ? null : request.getMenuIds(),
                request == null ? null : request.getPermissionIds());
    }

    @Override
    @DS(DataSourceName.SLAVE)
    public RoleMenuAuthDTO roleMenus(Long id) {
        SysAppDO app = merchantApp();
        String merchantId = currentMerchantId();
        getRole(app.getId(), merchantId, id);
        RoleMenuAuthDTO dto = new RoleMenuAuthDTO();
        dto.setRoleId(id);
        dto.setMenus(loadGrantedMenuTree(app.getId(), merchantId));
        dto.setCheckedMenuIds(sysRoleMenuMapper.selectList(Wrappers.<SysRoleMenuDO>lambdaQuery()
                        .eq(SysRoleMenuDO::getAppId, app.getId())
                        .eq(SysRoleMenuDO::getRoleId, id)
                        .eq(SysRoleMenuDO::getDeleted, AuthConstants.NOT_DELETED))
                .stream().map(SysRoleMenuDO::getMenuId).toList());
        return dto;
    }

    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public void grantRoleMenus(Long id, IdsRequest request) {
        SysAppDO app = merchantApp();
        String merchantId = currentMerchantId();
        getRole(app.getId(), merchantId, id);
        Set<Long> ids = normalizeIds(request.getIds());
        if (!loadGrantedMenuIds(app.getId(), merchantId).containsAll(ids)) {
            throw new ServiceException(ApiResultEnum.FORBIDDEN.getCode(), "menuIds exceed merchant grant");
        }
        softDeleteRoleMenus(app.getId(), id);
        LocalDateTime now = LocalDateTime.now();
        ids.forEach(menuId -> {
            SysRoleMenuDO relation = new SysRoleMenuDO();
            relation.setAppId(app.getId());
            relation.setRoleId(id);
            relation.setMenuId(menuId);
            relation.setCreatedAt(now);
            relation.setCreatedBy(currentAccountId());
            relation.setDeleted(AuthConstants.NOT_DELETED);
            sysRoleMenuMapper.insert(relation);
        });
    }

    @Override
    @DS(DataSourceName.SLAVE)
    public RolePermissionAuthDTO rolePermissions(Long id) {
        SysAppDO app = merchantApp();
        String merchantId = currentMerchantId();
        getRole(app.getId(), merchantId, id);
        RolePermissionAuthDTO dto = new RolePermissionAuthDTO();
        dto.setRoleId(id);
        dto.setPermissions(grantedPermissions());
        dto.setCheckedPermissionIds(sysRolePermissionMapper.selectList(Wrappers.<SysRolePermissionDO>lambdaQuery()
                        .eq(SysRolePermissionDO::getAppId, app.getId())
                        .eq(SysRolePermissionDO::getRoleId, id)
                        .eq(SysRolePermissionDO::getDeleted, AuthConstants.NOT_DELETED))
                .stream().map(SysRolePermissionDO::getPermissionId).toList());
        return dto;
    }

    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public void grantRolePermissions(Long id, IdsRequest request) {
        SysAppDO app = merchantApp();
        String merchantId = currentMerchantId();
        getRole(app.getId(), merchantId, id);
        Set<Long> ids = normalizeIds(request.getIds());
        if (!loadGrantedPermissionIds(app.getId(), merchantId).containsAll(ids)) {
            throw new ServiceException(ApiResultEnum.FORBIDDEN.getCode(), "permissionIds exceed merchant grant");
        }
        softDeleteRolePermissions(app.getId(), id);
        LocalDateTime now = LocalDateTime.now();
        ids.forEach(permissionId -> {
            SysRolePermissionDO relation = new SysRolePermissionDO();
            relation.setAppId(app.getId());
            relation.setRoleId(id);
            relation.setPermissionId(permissionId);
            relation.setCreatedAt(now);
            relation.setCreatedBy(currentAccountId());
            relation.setDeleted(AuthConstants.NOT_DELETED);
            sysRolePermissionMapper.insert(relation);
        });
    }

    @Override
    @DS(DataSourceName.SLAVE)
    public List<PermissionDTO> grantedPermissions() {
        SysAppDO app = merchantApp();
        String merchantId = currentMerchantId();
        Set<Long> ids = loadGrantedPermissionIds(app.getId(), merchantId);
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        return sysPermissionMapper.selectList(Wrappers.<SysPermissionDO>lambdaQuery()
                        .eq(SysPermissionDO::getAppId, app.getId())
                        .in(SysPermissionDO::getId, ids)
                        .eq(SysPermissionDO::getStatus, AuthConstants.ENABLED)
                        .eq(SysPermissionDO::getDeleted, AuthConstants.NOT_DELETED)
                        .orderByAsc(SysPermissionDO::getMenuId, SysPermissionDO::getId))
                .stream().map(this::toPermissionDTO).toList();
    }

    private Set<Long> merchantUserIdsByRole(Long appId, Long roleId) {
        if (roleId == null || roleId <= 0) {
            return Collections.emptySet();
        }
        String merchantId = currentMerchantId();
        Long roleCount = sysRoleMapper.selectCount(Wrappers.<SysRoleDO>lambdaQuery()
                .eq(SysRoleDO::getAppId, appId)
                .eq(SysRoleDO::getMerchantId, merchantId)
                .eq(SysRoleDO::getId, roleId)
                .eq(SysRoleDO::getDeleted, AuthConstants.NOT_DELETED));
        if (roleCount == null || roleCount == 0) {
            return Collections.emptySet();
        }
        return sysMerchantUserRoleMapper.selectList(Wrappers.<SysMerchantUserRoleDO>lambdaQuery()
                        .eq(SysMerchantUserRoleDO::getAppId, appId)
                        .eq(SysMerchantUserRoleDO::getRoleId, roleId)
                        .eq(SysMerchantUserRoleDO::getDeleted, AuthConstants.NOT_DELETED))
                .stream().map(SysMerchantUserRoleDO::getMerchantUserId).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private Set<Long> accountIdsByKeyword(Long appId, String merchantId, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return Collections.emptySet();
        }
        return sysAccountMapper.selectList(Wrappers.<SysAccountDO>lambdaQuery()
                        .eq(SysAccountDO::getAppId, appId)
                        .eq(SysAccountDO::getMerchantId, merchantId)
                        .eq(SysAccountDO::getDeleted, AuthConstants.NOT_DELETED)
                        .and(wrapper -> wrapper
                                .like(SysAccountDO::getMobile, keyword)
                                .or()
                                .like(SysAccountDO::getEmail, keyword)))
                .stream().map(SysAccountDO::getId).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private void applyDept(SysMerchantDeptDO dept, DeptSaveRequest request) {
        dept.setParentId(request.getParentId() == null ? ROOT_PARENT_ID : request.getParentId());
        dept.setDeptCode(required(request.getDeptCode(), "deptCode"));
        dept.setDeptName(required(request.getDeptName(), "deptName"));
        dept.setLeaderAccountId(request.getLeaderAccountId());
        dept.setPhone(normalize(request.getPhone()));
        dept.setEmail(normalize(request.getEmail()));
        dept.setSortNo(request.getSortNo() == null ? 0 : request.getSortNo());
        dept.setStatus(validStatus(request.getStatus()));
        dept.setRemark(normalize(request.getRemark()));
    }

    private void applyPost(SysMerchantPostDO post, PostSaveRequest request) {
        post.setPostCode(required(request.getPostCode(), "postCode"));
        post.setPostName(required(request.getPostName(), "postName"));
        post.setSortNo(request.getSortNo() == null ? 0 : request.getSortNo());
        post.setStatus(validStatus(request.getStatus()));
        post.setRemark(normalize(request.getRemark()));
    }

    private void updateAccountBaseFields(Long appId, String merchantId, SysAccountDO account, AccountBaseSaveRequest request) {
        SysMerchantUserDO merchantUser = getMerchantUser(merchantId, account.getId());
        assertAccountAvailable(merchantId, request.getLoginAccount(), merchantUser.getId());
        SysUserDO user = sysUserMapper.selectById(account.getUserId());
        user.setRealName(required(request.getRealName(), "realName"));
        user.setMobile(normalize(request.getMobile()));
        user.setEmail(normalize(request.getEmail()));
        user.setStatus(validStatus(request.getStatus()));
        user.setUpdatedAt(LocalDateTime.now());
        user.setUpdatedBy(currentAccountId());
        sysUserMapper.updateById(user);
        account.setLoginAccount(toAccountLoginName(merchantId, request.getLoginAccount()));
        account.setMobile(user.getMobile());
        account.setEmail(user.getEmail());
        account.setStatus(user.getStatus());
        account.setUpdatedAt(LocalDateTime.now());
        account.setUpdatedBy(currentAccountId());
        sysAccountMapper.updateById(account);
    }

    private SysMerchantUserDO createMerchantUser(String merchantId, String loginAccount, SysUserDO user, SysAccountDO account, LocalDateTime now) {
        SysMerchantUserDO merchantUser = new SysMerchantUserDO();
        merchantUser.setMerchantInfoId(currentMerchantInfoId(merchantId));
        merchantUser.setMerchantId(merchantId);
        merchantUser.setUserId(user.getId());
        merchantUser.setAccountId(account.getId());
        merchantUser.setLoginAccount(required(loginAccount, "loginAccount"));
        merchantUser.setRealName(user.getRealName());
        merchantUser.setStatus(account.getStatus());
        merchantUser.setCreatedAt(now);
        merchantUser.setUpdatedAt(now);
        merchantUser.setCreatedBy(currentAccountId());
        merchantUser.setUpdatedBy(currentAccountId());
        merchantUser.setDeleted(AuthConstants.NOT_DELETED);
        sysMerchantUserMapper.insert(merchantUser);
        return merchantUser;
    }

    private Long currentMerchantInfoId(String merchantId) {
        BaseMerchantInfoDO merchant = baseMerchantInfoMapper.selectOne(Wrappers.<BaseMerchantInfoDO>lambdaQuery()
                .eq(BaseMerchantInfoDO::getMerchantId, merchantId)
                .eq(BaseMerchantInfoDO::getDeleted, 0)
                .last("LIMIT 1"));
        if (merchant == null) {
            throw new ServiceException(ApiResultEnum.MERCHANT_INVALID);
        }
        return merchant.getId();
    }

    private void replaceAccountRoles(Long appId, String merchantId, SysMerchantUserDO merchantUser, List<Long> roleIds) {
        Set<Long> ids = normalizeIds(roleIds);
        if (!ids.isEmpty()) {
            Set<Long> available = sysRoleMapper.selectList(Wrappers.<SysRoleDO>lambdaQuery()
                            .eq(SysRoleDO::getAppId, appId)
                            .eq(SysRoleDO::getMerchantId, merchantId)
                            .in(SysRoleDO::getId, ids)
                            .eq(SysRoleDO::getStatus, AuthConstants.ENABLED)
                            .eq(SysRoleDO::getDeleted, AuthConstants.NOT_DELETED))
                    .stream().map(SysRoleDO::getId).collect(Collectors.toSet());
            if (!available.containsAll(ids)) {
                throw new ServiceException(ApiResultEnum.FORBIDDEN.getCode(), "roleIds exceed merchant roles");
            }
        }
        sysMerchantUserRoleMapper.selectList(Wrappers.<SysMerchantUserRoleDO>lambdaQuery()
                .eq(SysMerchantUserRoleDO::getAppId, appId)
                .eq(SysMerchantUserRoleDO::getMerchantUserId, merchantUser.getId())
                .eq(SysMerchantUserRoleDO::getDeleted, AuthConstants.NOT_DELETED)).forEach(relation -> {
            relation.setDeleted(relation.getId());
            sysMerchantUserRoleMapper.updateById(relation);
        });
        LocalDateTime now = LocalDateTime.now();
        ids.forEach(roleId -> {
            SysMerchantUserRoleDO relation = new SysMerchantUserRoleDO();
            relation.setAppId(appId);
            relation.setMerchantInfoId(merchantUser.getMerchantInfoId());
            relation.setMerchantUserId(merchantUser.getId());
            relation.setRoleId(roleId);
            relation.setCreatedAt(now);
            relation.setCreatedBy(currentAccountId());
            relation.setDeleted(AuthConstants.NOT_DELETED);
            sysMerchantUserRoleMapper.insert(relation);
        });
    }

    private void replaceAccountDepts(String merchantId, Long accountId, List<Long> deptIds) {
        Set<Long> ids = normalizeIds(deptIds);
        if (!ids.isEmpty() && !listDepts().stream().map(DeptDTO::getDeptId).collect(Collectors.toSet()).containsAll(ids)) {
            throw new ServiceException(ApiResultEnum.FORBIDDEN.getCode(), "deptIds exceed merchant depts");
        }
        sysMerchantAccountDeptMapper.delete(Wrappers.<SysMerchantAccountDeptDO>lambdaQuery()
                .eq(SysMerchantAccountDeptDO::getMerchantId, merchantId)
                .eq(SysMerchantAccountDeptDO::getAccountId, accountId));
        LocalDateTime now = LocalDateTime.now();
        ids.forEach(deptId -> {
            SysMerchantAccountDeptDO relation = new SysMerchantAccountDeptDO();
            relation.setMerchantId(merchantId);
            relation.setAccountId(accountId);
            relation.setDeptId(deptId);
            relation.setCreatedAt(now);
            relation.setCreatedBy(currentAccountId());
            sysMerchantAccountDeptMapper.insert(relation);
        });
    }

    private void replaceAccountPosts(String merchantId, Long accountId, List<Long> postIds) {
        Set<Long> ids = normalizeIds(postIds);
        if (!ids.isEmpty() && !listPosts().stream().map(PostDTO::getPostId).collect(Collectors.toSet()).containsAll(ids)) {
            throw new ServiceException(ApiResultEnum.FORBIDDEN.getCode(), "postIds exceed merchant posts");
        }
        sysMerchantAccountPostMapper.delete(Wrappers.<SysMerchantAccountPostDO>lambdaQuery()
                .eq(SysMerchantAccountPostDO::getMerchantId, merchantId)
                .eq(SysMerchantAccountPostDO::getAccountId, accountId));
        LocalDateTime now = LocalDateTime.now();
        ids.forEach(postId -> {
            SysMerchantAccountPostDO relation = new SysMerchantAccountPostDO();
            relation.setMerchantId(merchantId);
            relation.setAccountId(accountId);
            relation.setPostId(postId);
            relation.setCreatedAt(now);
            relation.setCreatedBy(currentAccountId());
            sysMerchantAccountPostMapper.insert(relation);
        });
    }

    private void replaceRoleGrants(Long appId, String merchantId, Long roleId, List<Long> menuIds, List<Long> permissionIds) {
        if (menuIds == null && permissionIds == null) {
            return;
        }
        Set<Long> safeMenuIds = normalizeIds(menuIds);
        Set<Long> safePermissionIds = normalizeIds(permissionIds);
        if (!currentAccountCanGrantRole()) {
            throw new ServiceException(ApiResultEnum.FORBIDDEN.getCode(), "role grant permission required");
        }
        if (!loadGrantedMenuIds(appId, merchantId).containsAll(safeMenuIds)) {
            throw new ServiceException(ApiResultEnum.FORBIDDEN.getCode(), "menuIds exceed merchant grant");
        }
        if (!loadGrantedPermissionIds(appId, merchantId).containsAll(safePermissionIds)) {
            throw new ServiceException(ApiResultEnum.FORBIDDEN.getCode(), "permissionIds exceed merchant grant");
        }
        softDeleteRoleMenus(appId, roleId);
        softDeleteRolePermissions(appId, roleId);
        LocalDateTime now = LocalDateTime.now();
        safeMenuIds.forEach(menuId -> {
            SysRoleMenuDO relation = new SysRoleMenuDO();
            relation.setAppId(appId);
            relation.setRoleId(roleId);
            relation.setMenuId(menuId);
            relation.setCreatedAt(now);
            relation.setCreatedBy(currentAccountId());
            relation.setDeleted(AuthConstants.NOT_DELETED);
            sysRoleMenuMapper.insert(relation);
        });
        safePermissionIds.forEach(permissionId -> {
            SysRolePermissionDO relation = new SysRolePermissionDO();
            relation.setAppId(appId);
            relation.setRoleId(roleId);
            relation.setPermissionId(permissionId);
            relation.setCreatedAt(now);
            relation.setCreatedBy(currentAccountId());
            relation.setDeleted(AuthConstants.NOT_DELETED);
            sysRolePermissionMapper.insert(relation);
        });
    }

    private Set<Long> loadGrantedMenuIds(Long appId, String merchantId) {
        return sysMerchantMenuGrantMapper.selectList(Wrappers.<SysMerchantMenuGrantDO>lambdaQuery()
                        .eq(SysMerchantMenuGrantDO::getAppId, appId)
                        .eq(SysMerchantMenuGrantDO::getMerchantId, merchantId)
                        .eq(SysMerchantMenuGrantDO::getStatus, AuthConstants.ENABLED)
                        .eq(SysMerchantMenuGrantDO::getDeleted, AuthConstants.NOT_DELETED))
                .stream().map(SysMerchantMenuGrantDO::getMenuId).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private Set<Long> loadGrantedPermissionIds(Long appId, String merchantId) {
        return sysMerchantPermissionGrantMapper.selectList(Wrappers.<SysMerchantPermissionGrantDO>lambdaQuery()
                        .eq(SysMerchantPermissionGrantDO::getAppId, appId)
                        .eq(SysMerchantPermissionGrantDO::getMerchantId, merchantId)
                        .eq(SysMerchantPermissionGrantDO::getStatus, AuthConstants.ENABLED)
                        .eq(SysMerchantPermissionGrantDO::getDeleted, AuthConstants.NOT_DELETED))
                .stream().map(SysMerchantPermissionGrantDO::getPermissionId).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    private List<AuthMenuDTO> loadGrantedMenuTree(Long appId, String merchantId) {
        Set<Long> ids = loadGrantedMenuIds(appId, merchantId);
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<AuthMenuDTO> nodes = sysMenuMapper.selectList(Wrappers.<SysMenuDO>lambdaQuery()
                        .eq(SysMenuDO::getAppId, appId)
                        .in(SysMenuDO::getId, ids)
                        .eq(SysMenuDO::getStatus, AuthConstants.ENABLED)
                        .eq(SysMenuDO::getDeleted, AuthConstants.NOT_DELETED)
                        .orderByAsc(SysMenuDO::getSortNo, SysMenuDO::getId))
                .stream().map(this::toAuthMenuDTO).toList();
        return buildMenuTree(nodes);
    }

    private void softDeleteRoleMenus(Long appId, Long roleId) {
        sysRoleMenuMapper.selectList(Wrappers.<SysRoleMenuDO>lambdaQuery()
                .eq(SysRoleMenuDO::getAppId, appId)
                .eq(SysRoleMenuDO::getRoleId, roleId)
                .eq(SysRoleMenuDO::getDeleted, AuthConstants.NOT_DELETED)).forEach(relation -> {
            relation.setDeleted(relation.getId());
            sysRoleMenuMapper.updateById(relation);
        });
    }

    private void softDeleteRolePermissions(Long appId, Long roleId) {
        sysRolePermissionMapper.selectList(Wrappers.<SysRolePermissionDO>lambdaQuery()
                .eq(SysRolePermissionDO::getAppId, appId)
                .eq(SysRolePermissionDO::getRoleId, roleId)
                .eq(SysRolePermissionDO::getDeleted, AuthConstants.NOT_DELETED)).forEach(relation -> {
            relation.setDeleted(relation.getId());
            sysRolePermissionMapper.updateById(relation);
        });
    }

    private SysMerchantDeptDO getDept(String merchantId, Long id) {
        SysMerchantDeptDO dept = sysMerchantDeptMapper.selectOne(Wrappers.<SysMerchantDeptDO>lambdaQuery()
                .eq(SysMerchantDeptDO::getMerchantId, merchantId)
                .eq(SysMerchantDeptDO::getId, id)
                .eq(SysMerchantDeptDO::getDeleted, AuthConstants.NOT_DELETED)
                .last("LIMIT 1"));
        if (dept == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "dept not found");
        }
        return dept;
    }

    private SysMerchantPostDO getPost(String merchantId, Long id) {
        SysMerchantPostDO post = sysMerchantPostMapper.selectOne(Wrappers.<SysMerchantPostDO>lambdaQuery()
                .eq(SysMerchantPostDO::getMerchantId, merchantId)
                .eq(SysMerchantPostDO::getId, id)
                .eq(SysMerchantPostDO::getDeleted, AuthConstants.NOT_DELETED)
                .last("LIMIT 1"));
        if (post == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "post not found");
        }
        return post;
    }

    private SysAccountDO getAccount(Long appId, String merchantId, Long id) {
        SysAccountDO account = sysAccountMapper.selectOne(Wrappers.<SysAccountDO>lambdaQuery()
                .eq(SysAccountDO::getAppId, appId)
                .eq(SysAccountDO::getMerchantId, merchantId)
                .eq(SysAccountDO::getId, id)
                .eq(SysAccountDO::getDeleted, AuthConstants.NOT_DELETED)
                .last("LIMIT 1"));
        if (account == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "account not found");
        }
        return account;
    }

    private SysMerchantUserDO getMerchantUser(String merchantId, Long accountId) {
        SysMerchantUserDO merchantUser = sysMerchantUserMapper.selectOne(Wrappers.<SysMerchantUserDO>lambdaQuery()
                .eq(SysMerchantUserDO::getMerchantId, merchantId)
                .eq(SysMerchantUserDO::getAccountId, accountId)
                .eq(SysMerchantUserDO::getDeleted, AuthConstants.NOT_DELETED)
                .last("LIMIT 1"));
        if (merchantUser == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "merchant user not found");
        }
        return merchantUser;
    }

    private SysRoleDO getRole(Long appId, String merchantId, Long id) {
        SysRoleDO role = sysRoleMapper.selectOne(Wrappers.<SysRoleDO>lambdaQuery()
                .eq(SysRoleDO::getAppId, appId)
                .eq(SysRoleDO::getMerchantId, merchantId)
                .eq(SysRoleDO::getId, id)
                .eq(SysRoleDO::getDeleted, AuthConstants.NOT_DELETED)
                .last("LIMIT 1"));
        if (role == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "role not found");
        }
        return role;
    }

    private void validateDeptParent(String merchantId, Long parentId, Long currentId) {
        if (parentId == null || parentId == ROOT_PARENT_ID) {
            return;
        }
        if (Objects.equals(parentId, currentId)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "parentId is invalid");
        }
        getDept(merchantId, parentId);
    }

    private void assertDeptCodeAvailable(String merchantId, String code, Long currentId) {
        Long count = sysMerchantDeptMapper.selectCount(Wrappers.<SysMerchantDeptDO>lambdaQuery()
                .eq(SysMerchantDeptDO::getMerchantId, merchantId)
                .eq(SysMerchantDeptDO::getDeptCode, required(code, "deptCode"))
                .ne(currentId != null, SysMerchantDeptDO::getId, currentId)
                .eq(SysMerchantDeptDO::getDeleted, AuthConstants.NOT_DELETED));
        if (count != null && count > 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "deptCode already exists");
        }
    }

    private void assertPostCodeAvailable(String merchantId, String code, Long currentId) {
        Long count = sysMerchantPostMapper.selectCount(Wrappers.<SysMerchantPostDO>lambdaQuery()
                .eq(SysMerchantPostDO::getMerchantId, merchantId)
                .eq(SysMerchantPostDO::getPostCode, required(code, "postCode"))
                .ne(currentId != null, SysMerchantPostDO::getId, currentId)
                .eq(SysMerchantPostDO::getDeleted, AuthConstants.NOT_DELETED));
        if (count != null && count > 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "postCode already exists");
        }
    }

    private void assertAccountAvailable(String merchantId, String loginAccount, Long currentMerchantUserId) {
        Long count = sysMerchantUserMapper.selectCount(Wrappers.<SysMerchantUserDO>lambdaQuery()
                .eq(SysMerchantUserDO::getMerchantId, merchantId)
                .eq(SysMerchantUserDO::getLoginAccount, required(loginAccount, "loginAccount"))
                .ne(currentMerchantUserId != null, SysMerchantUserDO::getId, currentMerchantUserId)
                .eq(SysMerchantUserDO::getDeleted, AuthConstants.NOT_DELETED));
        if (count != null && count > 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "loginAccount already exists");
        }
    }

    private void assertRoleCodeAvailable(Long appId, String merchantId, String roleCode, Long currentId) {
        Long count = sysRoleMapper.selectCount(Wrappers.<SysRoleDO>lambdaQuery()
                .eq(SysRoleDO::getAppId, appId)
                .eq(SysRoleDO::getMerchantId, merchantId)
                .eq(SysRoleDO::getRoleCode, required(roleCode, "roleCode"))
                .ne(currentId != null, SysRoleDO::getId, currentId)
                .eq(SysRoleDO::getDeleted, AuthConstants.NOT_DELETED));
        if (count != null && count > 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "roleCode already exists");
        }
    }

    private DeptDTO toDeptDTO(SysMerchantDeptDO dept) {
        DeptDTO dto = new DeptDTO();
        dto.setDeptId(dept.getId());
        dto.setParentId(dept.getParentId());
        dto.setDeptCode(dept.getDeptCode());
        dto.setDeptName(dept.getDeptName());
        dto.setLeaderAccountId(dept.getLeaderAccountId());
        dto.setPhone(dept.getPhone());
        dto.setEmail(dept.getEmail());
        dto.setSortNo(dept.getSortNo());
        dto.setStatus(dept.getStatus());
        dto.setRemark(dept.getRemark());
        dto.setCreatedAt(dept.getCreatedAt());
        dto.setUpdatedAt(dept.getUpdatedAt());
        return dto;
    }

    private PostDTO toPostDTO(SysMerchantPostDO post) {
        PostDTO dto = new PostDTO();
        dto.setPostId(post.getId());
        dto.setPostCode(post.getPostCode());
        dto.setPostName(post.getPostName());
        dto.setSortNo(post.getSortNo());
        dto.setStatus(post.getStatus());
        dto.setRemark(post.getRemark());
        dto.setCreatedAt(post.getCreatedAt());
        dto.setUpdatedAt(post.getUpdatedAt());
        return dto;
    }

    private AccountDTO toAccountDTO(Long appId, SysAccountDO account) {
        return toAccountDTO(appId, getMerchantUser(account.getMerchantId(), account.getId()));
    }

    private AccountDTO toAccountDTO(Long appId, SysMerchantUserDO merchantUser) {
        SysAccountDO account = sysAccountMapper.selectById(merchantUser.getAccountId());
        AccountDTO dto = new AccountDTO();
        SysUserDO user = sysUserMapper.selectById(account.getUserId());
        dto.setAccountId(account.getId());
        dto.setUserId(account.getUserId());
        dto.setMerchantUserId(merchantUser.getId());
        dto.setLoginAccount(merchantUser.getLoginAccount());
        dto.setRealName(StringUtils.hasText(merchantUser.getRealName()) ? merchantUser.getRealName() : user == null ? null : user.getRealName());
        dto.setMobile(account.getMobile());
        dto.setEmail(account.getEmail());
        dto.setStatus(merchantUser.getStatus());
        dto.setLocked(account.getLocked());
        dto.setLastLoginAt(account.getLastLoginAt());
        dto.setCreatedAt(merchantUser.getCreatedAt());
        List<Long> roleIds = sysMerchantUserRoleMapper.selectList(Wrappers.<SysMerchantUserRoleDO>lambdaQuery()
                        .eq(SysMerchantUserRoleDO::getAppId, appId)
                        .eq(SysMerchantUserRoleDO::getMerchantUserId, merchantUser.getId())
                        .eq(SysMerchantUserRoleDO::getDeleted, AuthConstants.NOT_DELETED))
                .stream().map(SysMerchantUserRoleDO::getRoleId).toList();
        dto.setRoleIds(roleIds);
        if (!roleIds.isEmpty()) {
            dto.setRoleNames(sysRoleMapper.selectList(Wrappers.<SysRoleDO>lambdaQuery().in(SysRoleDO::getId, roleIds))
                    .stream().map(SysRoleDO::getRoleName).toList());
        }
        dto.setDeptIds(sysMerchantAccountDeptMapper.selectList(Wrappers.<SysMerchantAccountDeptDO>lambdaQuery()
                        .eq(SysMerchantAccountDeptDO::getMerchantId, account.getMerchantId())
                        .eq(SysMerchantAccountDeptDO::getAccountId, account.getId()))
                .stream().map(SysMerchantAccountDeptDO::getDeptId).toList());
        dto.setPostIds(sysMerchantAccountPostMapper.selectList(Wrappers.<SysMerchantAccountPostDO>lambdaQuery()
                        .eq(SysMerchantAccountPostDO::getMerchantId, account.getMerchantId())
                        .eq(SysMerchantAccountPostDO::getAccountId, account.getId()))
                .stream().map(SysMerchantAccountPostDO::getPostId).toList());
        return dto;
    }

    private RoleDTO toRoleDTO(SysRoleDO role) {
        RoleDTO dto = new RoleDTO();
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

    private PermissionDTO toPermissionDTO(SysPermissionDO permission) {
        PermissionDTO dto = new PermissionDTO();
        dto.setPermissionId(permission.getId());
        dto.setMenuId(permission.getMenuId());
        dto.setPermissionCode(permission.getPermissionCode());
        dto.setPermissionName(permission.getPermissionName());
        dto.setPermissionType(permission.getPermissionType());
        dto.setResourceMethod(permission.getResourceMethod());
        dto.setResourcePath(permission.getResourcePath());
        return dto;
    }

    private List<AuthGrantNodeDTO> buildGrantTree(List<AuthMenuDTO> menus, List<PermissionDTO> permissions) {
        Map<Long, AuthMenuDTO> menuMap = flattenAuthMenus(menus).stream()
                .collect(Collectors.toMap(AuthMenuDTO::getId, item -> item, (left, right) -> left, LinkedHashMap::new));
        Map<Long, List<PermissionDTO>> permissionMap = new LinkedHashMap<>();
        permissions.forEach(permission -> {
            Long displayMenuId = resolvePermissionDisplayMenuId(permission, menuMap);
            if (displayMenuId == null) {
                return;
            }
            permissionMap.computeIfAbsent(displayMenuId, ignored -> new ArrayList<>()).add(permission);
        });
        return menus.stream()
                .filter(this::isGrantDisplayMenu)
                .map(menu -> toGrantNode(menu, permissionMap))
                .toList();
    }

    private AuthGrantNodeDTO toGrantNode(AuthMenuDTO menu, Map<Long, List<PermissionDTO>> permissionMap) {
        AuthGrantNodeDTO node = new AuthGrantNodeDTO();
        node.setId("m_" + menu.getId());
        node.setNodeId(menu.getId());
        node.setMenuId(menu.getId());
        node.setNodeType(resolveGrantNodeType(menu.getMenuType()));
        node.setName(menu.getMenuName());
        node.setCode(menu.getPermissionCode());
        List<AuthGrantNodeDTO> children = new ArrayList<>();
        if (menu.getChildren() != null && !menu.getChildren().isEmpty()) {
            children.addAll(menu.getChildren().stream()
                    .filter(this::isGrantDisplayMenu)
                    .map(child -> toGrantNode(child, permissionMap))
                    .toList());
        }
        permissionMap.getOrDefault(menu.getId(), Collections.emptyList()).forEach(permission -> {
            AuthGrantNodeDTO permissionNode = new AuthGrantNodeDTO();
            permissionNode.setId("p_" + permission.getPermissionId());
            permissionNode.setNodeId(permission.getPermissionId());
            permissionNode.setPermissionId(permission.getPermissionId());
            permissionNode.setMenuId(permission.getMenuId());
            permissionNode.setNodeType("BTN");
            permissionNode.setName(permission.getPermissionName());
            permissionNode.setCode(permission.getPermissionCode());
            children.add(permissionNode);
        });
        node.setChildren(children);
        return node;
    }

    private Long resolvePermissionDisplayMenuId(PermissionDTO permission, Map<Long, AuthMenuDTO> menuMap) {
        if (permission.getMenuId() == null) {
            return null;
        }
        AuthMenuDTO menu = menuMap.get(permission.getMenuId());
        if (menu == null) {
            return null;
        }
        if (isGrantDisplayMenu(menu)) {
            return menu.getId();
        }
        if (!"BUTTON".equals(menu.getMenuType())) {
            return null;
        }
        Long parentId = menu.getParentId();
        while (parentId != null && parentId > ROOT_PARENT_ID) {
            AuthMenuDTO parent = menuMap.get(parentId);
            if (parent == null) {
                return null;
            }
            if (isGrantDisplayMenu(parent)) {
                return parent.getId();
            }
            if (!"BUTTON".equals(parent.getMenuType())) {
                return null;
            }
            parentId = parent.getParentId();
        }
        return null;
    }

    private boolean isGrantDisplayMenu(AuthMenuDTO menu) {
        return menu != null
                && !"BUTTON".equals(menu.getMenuType())
                && AuthConstants.ENABLED == (menu.getVisible() == null ? AuthConstants.ENABLED : menu.getVisible());
    }

    private List<AuthMenuDTO> flattenAuthMenus(List<AuthMenuDTO> menus) {
        List<AuthMenuDTO> result = new ArrayList<>();
        flattenAuthMenus(menus, result);
        return result;
    }

    private void flattenAuthMenus(List<AuthMenuDTO> menus, List<AuthMenuDTO> result) {
        if (menus == null || menus.isEmpty()) {
            return;
        }
        menus.forEach(menu -> {
            result.add(menu);
            flattenAuthMenus(menu.getChildren(), result);
        });
    }

    private String resolveGrantNodeType(String menuType) {
        if ("CATALOG".equals(menuType)) {
            return "DIR";
        }
        if ("BUTTON".equals(menuType)) {
            return "BTN";
        }
        return "MENU";
    }

    private AuthMenuDTO toAuthMenuDTO(SysMenuDO menu) {
        AuthMenuDTO dto = new AuthMenuDTO();
        dto.setId(menu.getId());
        dto.setParentId(menu.getParentId());
        dto.setMenuCode(menu.getMenuCode());
        dto.setMenuName(menu.getMenuName());
        dto.setMenuType(menu.getMenuType());
        dto.setRoutePath(menu.getRoutePath());
        dto.setComponentPath(menu.getComponentPath());
        dto.setPermissionCode(menu.getPermissionCode());
        dto.setIcon(menu.getIcon());
        dto.setVisible(menu.getVisible());
        dto.setSortNo(menu.getSortNo());
        dto.setExternalLink(menu.getExternalLink());
        return dto;
    }

    private List<AuthMenuDTO> buildMenuTree(List<AuthMenuDTO> nodes) {
        Map<Long, AuthMenuDTO> nodeMap = nodes.stream().collect(Collectors.toMap(AuthMenuDTO::getId, item -> item, (left, right) -> left, LinkedHashMap::new));
        List<AuthMenuDTO> roots = new ArrayList<>();
        for (AuthMenuDTO node : nodes) {
            if (node.getParentId() == null || node.getParentId() == ROOT_PARENT_ID || !nodeMap.containsKey(node.getParentId())) {
                roots.add(node);
            } else {
                nodeMap.get(node.getParentId()).getChildren().add(node);
            }
        }
        return roots;
    }

    private List<DeptDTO> buildDeptTree(List<DeptDTO> nodes) {
        Map<Long, DeptDTO> nodeMap = nodes.stream().collect(Collectors.toMap(DeptDTO::getDeptId, item -> item, (left, right) -> left, LinkedHashMap::new));
        List<DeptDTO> roots = new ArrayList<>();
        for (DeptDTO node : nodes) {
            if (node.getParentId() == null || node.getParentId() == ROOT_PARENT_ID || !nodeMap.containsKey(node.getParentId())) {
                roots.add(node);
            } else {
                nodeMap.get(node.getParentId()).getChildren().add(node);
            }
        }
        return roots;
    }

    private Set<Long> normalizeIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptySet();
        }
        return ids.stream().filter(Objects::nonNull).filter(id -> id > 0).collect(Collectors.toSet());
    }

    private Integer validStatus(Integer status) {
        if (status == null) {
            return AuthConstants.ENABLED;
        }
        if (status == AuthConstants.ENABLED || status == AuthConstants.DISABLED) {
            return status;
        }
        throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "status is invalid");
    }

    private String resolveDataScope(String dataScope) {
        if (!StringUtils.hasText(dataScope)) {
            return DATA_SCOPE_SELF;
        }
        String normalized = dataScope.trim().toUpperCase();
        if (DATA_SCOPE_ALL.equals(normalized) || DATA_SCOPE_SELF.equals(normalized) || DATA_SCOPE_CUSTOM.equals(normalized)) {
            return normalized;
        }
        throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "dataScope is invalid");
    }

    private boolean isSystemRole(SysRoleDO role) {
        return role != null && ROLE_TYPE_SYSTEM.equals(role.getRoleType());
    }

    private String required(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), field + " is required");
        }
        return value.trim();
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private LocalDateTime parseQueryDateTime(String value, boolean endOfDay) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        try {
            if (normalized.length() == 10) {
                return LocalDate.parse(normalized).atTime(endOfDay ? LocalTime.MAX : LocalTime.MIN);
            }
            return LocalDateTime.parse(normalized.replace(' ', 'T'));
        } catch (DateTimeParseException ex) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "created time is invalid");
        }
    }

    private String toAccountLoginName(String merchantId, String loginAccount) {
        return required(loginAccount, "loginAccount") + ACCOUNT_LOGIN_SEPARATOR + merchantId;
    }

    private SysAppDO merchantApp() {
        SysAppDO app = sysAppMapper.selectOne(Wrappers.<SysAppDO>lambdaQuery()
                .eq(SysAppDO::getAppCode, AuthConstants.APP_MERCHANT)
                .eq(SysAppDO::getDeleted, AuthConstants.NOT_DELETED)
                .last("LIMIT 1"));
        if (app == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "MERCHANT app not found");
        }
        return app;
    }

    private String currentMerchantId() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null || !StringUtils.hasText(account.getMerchantId())) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED.getCode(), "merchant context missing");
        }
        return account.getMerchantId();
    }

    private Long currentAccountId() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        return account == null ? null : account.getAccountId();
    }

    private boolean currentAccountCanGrantRole() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null || account.getPermissions() == null) {
            return false;
        }
        return account.getPermissions().contains("*:*:*")
                || account.getPermissions().contains("merchant:system:role:grant")
                || account.getPermissions().contains("merchant:system:role:grantMenu")
                || account.getPermissions().contains("merchant:system:role:grantPermission");
    }
}
