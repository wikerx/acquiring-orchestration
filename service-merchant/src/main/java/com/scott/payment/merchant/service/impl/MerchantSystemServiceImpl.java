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
import com.scott.payment.component.db.auth.entity.SysAccountMfaDO;
import com.scott.payment.component.db.auth.entity.SysAccountMfaLogDO;
import com.scott.payment.component.db.auth.entity.SysAccountMfaTokenDO;
import com.scott.payment.component.db.auth.entity.SysAppDO;
import com.scott.payment.component.db.auth.entity.SysLoginSessionDO;
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
import com.scott.payment.component.db.auth.mapper.SysAccountMfaLogMapper;
import com.scott.payment.component.db.auth.mapper.SysAccountMfaMapper;
import com.scott.payment.component.db.auth.mapper.SysAccountMfaTokenMapper;
import com.scott.payment.component.db.auth.mapper.SysAppMapper;
import com.scott.payment.component.db.auth.mapper.SysLoginSessionMapper;
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
import com.scott.payment.component.db.auth.support.MfaSecretCrypto;
import com.scott.payment.component.db.auth.support.TotpUtils;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.AccountBaseSaveRequest;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.AccountDTO;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.AccountMfaActionRequest;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.AccountMfaExemptRequest;
import com.scott.payment.merchant.dto.system.MerchantSystemDTOs.AccountMfaStatusResponse;
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
import com.scott.payment.merchant.service.MerchantConfigService;
import com.scott.payment.merchant.service.MerchantSystemService;
import com.scott.payment.merchant.service.MerchantTemplateEmailService;
import com.scott.payment.merchant.service.MerchantTemplateEmailService.MerchantEmailSendCommand;
import lombok.extern.slf4j.Slf4j;
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
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantSystemServiceImpl
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 商户管理Merchant System Service Impl，位于 service-merchant 的服务实现层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Slf4j
@Service
public class MerchantSystemServiceImpl implements MerchantSystemService {

    /**
     * 商户管理固定配置或枚举常量，集中维护魔法值，避免业务代码散落硬编码。
     */
    private static final long ROOT_PARENT_ID = 0L;
    /**
     * 商户管理固定配置或枚举常量，集中维护魔法值，避免业务代码散落硬编码。
     */
    private static final String ROLE_TYPE_CUSTOM = "CUSTOM";
    /**
     * 商户管理固定配置或枚举常量，集中维护魔法值，避免业务代码散落硬编码。
     */
    private static final String ROLE_TYPE_SYSTEM = "SYSTEM";
    /**
     * 商户管理固定配置或枚举常量，集中维护魔法值，避免业务代码散落硬编码。
     */
    private static final String DATA_SCOPE_ALL = "ALL";
    /**
     * 商户管理固定配置或枚举常量，集中维护魔法值，避免业务代码散落硬编码。
     */
    private static final String DATA_SCOPE_SELF = "SELF";
    /**
     * 商户管理固定配置或枚举常量，集中维护魔法值，避免业务代码散落硬编码。
     */
    private static final String DATA_SCOPE_CUSTOM = "CUSTOM";
    /**
     * 商户管理固定配置或枚举常量，集中维护魔法值，避免业务代码散落硬编码。
     */
    private static final String ACCOUNT_LOGIN_SEPARATOR = "_";
    /**
     * OTP 登录票据类型。
     */
    private static final String MFA_TOKEN_TYPE_LOGIN = "LOGIN_MFA";
    /**
     * OTP 操作结果：成功。
     */
    private static final String MFA_RESULT_SUCCESS = "SUCCESS";
    /**
     * OTP 操作结果：失败。
     */
    private static final String MFA_RESULT_FAILED = "FAILED";
    /**
     * 商户 OTP 邮件场景编码。
     */
    private static final String MERCHANT_MFA_SCENE = "MERCHANT_MFA";
    /**
     * 商户 OTP 绑定邮件模板。
     */
    private static final String TEMPLATE_MFA_BIND_NOTICE = "MERCHANT_MFA_BIND_NOTICE";
    /**
     * 商户 OTP 启用邮件模板。
     */
    private static final String TEMPLATE_MFA_ENABLED_NOTICE = "MERCHANT_MFA_ENABLED_NOTICE";
    /**
     * 商户 OTP 重置邮件模板。
     */
    private static final String TEMPLATE_MFA_RESET_NOTICE = "MERCHANT_MFA_RESET_NOTICE";
    /**
     * 商户 OTP 停用邮件模板。
     */
    private static final String TEMPLATE_MFA_DISABLED_NOTICE = "MERCHANT_MFA_DISABLED_NOTICE";
    /**
     * 商户 OTP 豁免邮件模板。
     */
    private static final String TEMPLATE_MFA_EXEMPT_NOTICE = "MERCHANT_MFA_EXEMPT_NOTICE";
    /**
     * 参数管理中维护的商户系统前端地址。
     */
    private static final String MERCHANT_FRONTEND_BASE_URL_KEY = "platform.merchant.frontend-base-url";

    /**
     * 商户管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final BaseMerchantInfoMapper baseMerchantInfoMapper;
    /**
     * 商户管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final SysAppMapper sysAppMapper;
    /**
     * 商户管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final SysUserMapper sysUserMapper;
    /**
     * 商户管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final SysAccountMapper sysAccountMapper;
    /**
     * 商户员工 OTP 配置 Mapper。
     */
    private final SysAccountMfaMapper sysAccountMfaMapper;
    /**
     * 商户员工 OTP 登录票据 Mapper。
     */
    private final SysAccountMfaTokenMapper sysAccountMfaTokenMapper;
    /**
     * 商户员工 OTP 审计日志 Mapper。
     */
    private final SysAccountMfaLogMapper sysAccountMfaLogMapper;
    /**
     * 登录会话 Mapper，用于安全策略变更后强制会话失效。
     */
    private final SysLoginSessionMapper sysLoginSessionMapper;
    /**
     * 商户管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final SysRoleMapper sysRoleMapper;
    /**
     * 商户管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final SysRoleMenuMapper sysRoleMenuMapper;
    /**
     * 商户管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final SysRolePermissionMapper sysRolePermissionMapper;
    /**
     * 商户管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final SysMenuMapper sysMenuMapper;
    /**
     * 商户管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final SysPermissionMapper sysPermissionMapper;
    /**
     * 商户管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final SysMerchantDeptMapper sysMerchantDeptMapper;
    /**
     * 商户管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final SysMerchantPostMapper sysMerchantPostMapper;
    /**
     * 商户管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final SysMerchantAccountDeptMapper sysMerchantAccountDeptMapper;
    /**
     * 商户管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final SysMerchantAccountPostMapper sysMerchantAccountPostMapper;
    /**
     * 商户管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final SysMerchantMenuGrantMapper sysMerchantMenuGrantMapper;
    /**
     * 商户管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final SysMerchantPermissionGrantMapper sysMerchantPermissionGrantMapper;
    /**
     * 商户管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final SysMerchantUserMapper sysMerchantUserMapper;
    /**
     * 商户管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final SysMerchantUserRoleMapper sysMerchantUserRoleMapper;
    /**
     * 商户模板邮件服务。
     */
    private final MerchantTemplateEmailService merchantTemplateEmailService;
    /**
     * 商户系统只读参数服务，用于读取参数管理中的平台访问地址。
     */
    private final MerchantConfigService merchantConfigService;

    public MerchantSystemServiceImpl(BaseMerchantInfoMapper baseMerchantInfoMapper,
                                     SysAppMapper sysAppMapper,
                                     SysUserMapper sysUserMapper,
                                     SysAccountMapper sysAccountMapper,
                                     SysAccountMfaMapper sysAccountMfaMapper,
                                     SysAccountMfaTokenMapper sysAccountMfaTokenMapper,
                                     SysAccountMfaLogMapper sysAccountMfaLogMapper,
                                     SysLoginSessionMapper sysLoginSessionMapper,
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
                                     SysMerchantUserRoleMapper sysMerchantUserRoleMapper,
                                     MerchantTemplateEmailService merchantTemplateEmailService,
                                     MerchantConfigService merchantConfigService) {
        this.baseMerchantInfoMapper = baseMerchantInfoMapper;
        this.sysAppMapper = sysAppMapper;
        this.sysUserMapper = sysUserMapper;
        this.sysAccountMapper = sysAccountMapper;
        this.sysAccountMfaMapper = sysAccountMfaMapper;
        this.sysAccountMfaTokenMapper = sysAccountMfaTokenMapper;
        this.sysAccountMfaLogMapper = sysAccountMfaLogMapper;
        this.sysLoginSessionMapper = sysLoginSessionMapper;
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
        this.merchantTemplateEmailService = merchantTemplateEmailService;
        this.merchantConfigService = merchantConfigService;
    }

    /**
     * 查询商户管理列表或分页数据，供页面筛选和展示使用。
     * @return 处理后的业务结果或页面展示数据。
     */
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

    /**
     * 查询商户管理列表或分页数据，供页面筛选和展示使用。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
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

    /**
     * 执行商户管理相关处理，保持当前层级的职责边界和返回语义。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public List<DeptDTO> deptTree() {
        return buildDeptTree(listDepts());
    }

    /**
     * 创建或保存商户管理数据，保持请求校验、默认值和审计字段一致。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
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

    /**
     * 更新商户管理数据，保持已有记录、状态和审计字段的一致性。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
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

    /**
     * 删除商户管理数据，按业务规则处理引用校验和删除边界。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */
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

    /**
     * 查询商户管理列表或分页数据，供页面筛选和展示使用。
     * @return 处理后的业务结果或页面展示数据。
     */
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

    /**
     * 查询商户管理列表或分页数据，供页面筛选和展示使用。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
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

    /**
     * 创建或保存商户管理数据，保持请求校验、默认值和审计字段一致。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
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

    /**
     * 更新商户管理数据，保持已有记录、状态和审计字段的一致性。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
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

    /**
     * 删除商户管理数据，按业务规则处理引用校验和删除边界。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */
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

    /**
     * 查询商户管理列表或分页数据，供页面筛选和展示使用。
     * @return 处理后的业务结果或页面展示数据。
     */
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

    /**
     * 查询商户管理列表或分页数据，供页面筛选和展示使用。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
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

    /**
     * 创建或保存商户管理数据，保持请求校验、默认值和审计字段一致。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
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
        createDefaultRequiredMfa(app, account, now);
        SysMerchantUserDO merchantUser = createMerchantUser(merchantId, request.getLoginAccount(), user, account, now);
        replaceAccountRoles(app.getId(), merchantId, merchantUser, request.getRoleIds());
        replaceAccountDepts(merchantId, account.getId(), request.getDeptIds());
        replaceAccountPosts(merchantId, account.getId(), request.getPostIds());
        return toAccountDTO(app.getId(), account);
    }

    /**
     * 更新商户管理数据，保持已有记录、状态和审计字段的一致性。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
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

    /**
     * 更新商户管理数据，保持已有记录、状态和审计字段的一致性。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
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

    /**
     * 删除商户管理数据，按业务规则处理引用校验和删除边界。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */
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

    /**
     * 更新商户管理数据，保持已有记录、状态和审计字段的一致性。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param status 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */
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
        syncMfaStatusForAccountStatus(account);
        SysMerchantUserDO merchantUser = getMerchantUser(merchantId, account.getId());
        merchantUser.setStatus(validStatus(status));
        merchantUser.setUpdatedAt(LocalDateTime.now());
        merchantUser.setUpdatedBy(currentAccountId());
        sysMerchantUserMapper.updateById(merchantUser);
    }

    /**
     * 执行商户管理相关处理，保持当前层级的职责边界和返回语义。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */
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

    /**
     * 执行商户管理相关处理，保持当前层级的职责边界和返回语义。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public void assignAccountDepts(Long id, IdsRequest request) {
        SysAppDO app = merchantApp();
        String merchantId = currentMerchantId();
        getAccount(app.getId(), merchantId, id);
        replaceAccountDepts(merchantId, id, request.getIds());
    }

    /**
     * 执行商户管理相关处理，保持当前层级的职责边界和返回语义。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public void assignAccountPosts(Long id, IdsRequest request) {
        SysAppDO app = merchantApp();
        String merchantId = currentMerchantId();
        getAccount(app.getId(), merchantId, id);
        replaceAccountPosts(merchantId, id, request.getIds());
    }

    /**
     * 强制启用商户员工 OTP。
     *
     * @param id      员工账号ID
     * @param request 操作请求
     * @return OTP 状态
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public AccountMfaStatusResponse requireAccountMfa(Long id, AccountMfaActionRequest request) {
        SysAppDO app = merchantApp();
        String merchantId = currentMerchantId();
        SysAccountDO account = getAccount(app.getId(), merchantId, id);
        SysAccountMfaDO mfa = ensureMfa(app, account, LocalDateTime.now());
        String beforePolicy = mfa.getMfaPolicy();
        String beforeStatus = mfa.getMfaStatus();
        LocalDateTime now = LocalDateTime.now();
        mfa.setMfaPolicy(AuthConstants.MFA_POLICY_REQUIRED);
        mfa.setMfaStatus(account.getStatus() != null && account.getStatus() == AuthConstants.DISABLED
                ? AuthConstants.MFA_STATUS_DISABLED
                : AuthConstants.MFA_STATUS_PENDING_BIND);
        mfa.setMfaType(AuthConstants.MFA_TYPE_TOTP);
        mfa.setSecretCipher(null);
        mfa.setPendingSecretCipher(MfaSecretCrypto.encrypt(TotpUtils.generateBase32Secret()));
        mfa.setIssuer("Acquiring Merchant");
        mfa.setAccountLabel(mfaAccountLabel(account));
        mfa.setFailedVerifyCount(0);
        mfa.setLockedUntil(null);
        mfa.setLastSuccessTimeStep(null);
        mfa.setExemptReason(null);
        mfa.setExemptUntil(null);
        mfa.setRemark(normalize(request.getReason()));
        mfa.setUpdatedAt(now);
        mfa.setUpdatedBy(currentAccountId());
        sysAccountMfaMapper.updateById(mfa);
        expireOpenMfaTokens(app.getId(), account.getId(), now);
        logoutSessions(app.getId(), account.getId(), now);
        recordMfaLog(app, account, mfa, "REQUIRE", MFA_RESULT_SUCCESS, request.getReason(), beforePolicy, beforeStatus, currentOperator(), null);
        sendMfaNotice(app, account, TEMPLATE_MFA_ENABLED_NOTICE, request.getReason(), null);
        sendMfaNotice(app, account, TEMPLATE_MFA_BIND_NOTICE, request.getReason(), null);
        return toMfaStatusResponse(account, mfa);
    }

    /**
     * 重置商户员工 OTP。
     *
     * @param id      员工账号ID
     * @param request 操作请求
     * @return OTP 状态
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public AccountMfaStatusResponse resetAccountMfa(Long id, AccountMfaActionRequest request) {
        SysAppDO app = merchantApp();
        String merchantId = currentMerchantId();
        SysAccountDO account = getAccount(app.getId(), merchantId, id);
        assertNotSelf(account.getId(), "不能重置当前登录账号自己的 OTP");
        SysAccountMfaDO mfa = ensureMfa(app, account, LocalDateTime.now());
        String beforePolicy = mfa.getMfaPolicy();
        String beforeStatus = mfa.getMfaStatus();
        LocalDateTime now = LocalDateTime.now();
        mfa.setMfaPolicy(AuthConstants.MFA_POLICY_REQUIRED);
        mfa.setMfaStatus(AuthConstants.MFA_STATUS_RESET_REQUIRED);
        mfa.setMfaType(AuthConstants.MFA_TYPE_TOTP);
        mfa.setSecretCipher(null);
        mfa.setPendingSecretCipher(MfaSecretCrypto.encrypt(TotpUtils.generateBase32Secret()));
        mfa.setIssuer("Acquiring Merchant");
        mfa.setAccountLabel(mfaAccountLabel(account));
        mfa.setResetTime(now);
        mfa.setFailedVerifyCount(0);
        mfa.setLockedUntil(null);
        mfa.setLastSuccessTimeStep(null);
        mfa.setRemark(normalize(request.getReason()));
        mfa.setUpdatedAt(now);
        mfa.setUpdatedBy(currentAccountId());
        sysAccountMfaMapper.updateById(mfa);
        expireOpenMfaTokens(app.getId(), account.getId(), now);
        logoutSessions(app.getId(), account.getId(), now);
        recordMfaLog(app, account, mfa, "RESET", MFA_RESULT_SUCCESS, request.getReason(), beforePolicy, beforeStatus, currentOperator(), null);
        sendMfaNotice(app, account, TEMPLATE_MFA_RESET_NOTICE, request.getReason(), null);
        sendMfaNotice(app, account, TEMPLATE_MFA_BIND_NOTICE, request.getReason(), null);
        return toMfaStatusResponse(account, mfa);
    }

    /**
     * 豁免商户员工 OTP。
     *
     * @param id      员工账号ID
     * @param request 豁免请求
     * @return OTP 状态
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public AccountMfaStatusResponse exemptAccountMfa(Long id, AccountMfaExemptRequest request) {
        SysAppDO app = merchantApp();
        String merchantId = currentMerchantId();
        SysAccountDO account = getAccount(app.getId(), merchantId, id);
        assertNotSelf(account.getId(), "不能豁免当前登录账号自己的 OTP");
        SysAccountMfaDO mfa = ensureMfa(app, account, LocalDateTime.now());
        String beforePolicy = mfa.getMfaPolicy();
        String beforeStatus = mfa.getMfaStatus();
        LocalDateTime now = LocalDateTime.now();
        mfa.setMfaPolicy(AuthConstants.MFA_POLICY_EXEMPT);
        mfa.setMfaStatus(AuthConstants.MFA_STATUS_EXEMPT);
        mfa.setSecretCipher(null);
        mfa.setPendingSecretCipher(null);
        mfa.setFailedVerifyCount(0);
        mfa.setLockedUntil(null);
        mfa.setLastSuccessTimeStep(null);
        mfa.setExemptReason(normalize(request.getReason()));
        mfa.setExemptUntil(request.getExemptUntil());
        mfa.setRemark(normalize(request.getReason()));
        mfa.setUpdatedAt(now);
        mfa.setUpdatedBy(currentAccountId());
        sysAccountMfaMapper.updateById(mfa);
        expireOpenMfaTokens(app.getId(), account.getId(), now);
        logoutSessions(app.getId(), account.getId(), now);
        recordMfaLog(app, account, mfa, "EXEMPT", MFA_RESULT_SUCCESS, request.getReason(), beforePolicy, beforeStatus, currentOperator(), null);
        sendMfaNotice(app, account, TEMPLATE_MFA_EXEMPT_NOTICE, request.getReason(), request.getExemptUntil());
        return toMfaStatusResponse(account, mfa);
    }

    /**
     * 停用商户员工 OTP。
     *
     * @param id      员工账号ID
     * @param request 操作请求
     * @return OTP 状态
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public AccountMfaStatusResponse disableAccountMfa(Long id, AccountMfaActionRequest request) {
        SysAppDO app = merchantApp();
        String merchantId = currentMerchantId();
        SysAccountDO account = getAccount(app.getId(), merchantId, id);
        assertNotSelf(account.getId(), "不能停用当前登录账号自己的 OTP");
        SysAccountMfaDO mfa = ensureMfa(app, account, LocalDateTime.now());
        String beforePolicy = mfa.getMfaPolicy();
        String beforeStatus = mfa.getMfaStatus();
        LocalDateTime now = LocalDateTime.now();
        mfa.setMfaPolicy(AuthConstants.MFA_POLICY_OPTIONAL);
        mfa.setMfaStatus(AuthConstants.MFA_STATUS_NOT_ENABLED);
        mfa.setSecretCipher(null);
        mfa.setPendingSecretCipher(null);
        mfa.setFailedVerifyCount(0);
        mfa.setLockedUntil(null);
        mfa.setLastSuccessTimeStep(null);
        mfa.setRemark(normalize(request.getReason()));
        mfa.setUpdatedAt(now);
        mfa.setUpdatedBy(currentAccountId());
        sysAccountMfaMapper.updateById(mfa);
        expireOpenMfaTokens(app.getId(), account.getId(), now);
        logoutSessions(app.getId(), account.getId(), now);
        recordMfaLog(app, account, mfa, "DISABLE", MFA_RESULT_SUCCESS, request.getReason(), beforePolicy, beforeStatus, currentOperator(), null);
        sendMfaNotice(app, account, TEMPLATE_MFA_DISABLED_NOTICE, request.getReason(), null);
        return toMfaStatusResponse(account, mfa);
    }

    /**
     * 解锁商户员工 OTP。
     *
     * @param id      员工账号ID
     * @param request 操作请求
     * @return OTP 状态
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public AccountMfaStatusResponse unlockAccountMfa(Long id, AccountMfaActionRequest request) {
        SysAppDO app = merchantApp();
        String merchantId = currentMerchantId();
        SysAccountDO account = getAccount(app.getId(), merchantId, id);
        SysAccountMfaDO mfa = ensureMfa(app, account, LocalDateTime.now());
        String beforePolicy = mfa.getMfaPolicy();
        String beforeStatus = mfa.getMfaStatus();
        LocalDateTime now = LocalDateTime.now();
        if (AuthConstants.MFA_STATUS_LOCKED.equals(mfa.getMfaStatus())) {
            mfa.setMfaStatus(StringUtils.hasText(mfa.getSecretCipher())
                    ? AuthConstants.MFA_STATUS_ENABLED
                    : AuthConstants.MFA_STATUS_PENDING_BIND);
        }
        mfa.setFailedVerifyCount(0);
        mfa.setLockedUntil(null);
        mfa.setRemark(normalize(request.getReason()));
        mfa.setUpdatedAt(now);
        mfa.setUpdatedBy(currentAccountId());
        sysAccountMfaMapper.updateById(mfa);
        recordMfaLog(app, account, mfa, "UNLOCK", MFA_RESULT_SUCCESS, request.getReason(), beforePolicy, beforeStatus, currentOperator(), null);
        return toMfaStatusResponse(account, mfa);
    }

    /**
     * 重发商户员工 OTP 绑定邮件。
     *
     * @param id      员工账号ID
     * @param request 操作请求
     * @return OTP 状态
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public AccountMfaStatusResponse resendAccountMfaBindMail(Long id, AccountMfaActionRequest request) {
        SysAppDO app = merchantApp();
        String merchantId = currentMerchantId();
        SysAccountDO account = getAccount(app.getId(), merchantId, id);
        SysAccountMfaDO mfa = ensureMfa(app, account, LocalDateTime.now());
        if (!AuthConstants.MFA_STATUS_PENDING_BIND.equals(mfa.getMfaStatus())
                && !AuthConstants.MFA_STATUS_RESET_REQUIRED.equals(mfa.getMfaStatus())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "OTP 绑定邮件只能对待绑定或需重绑用户重发");
        }
        String beforePolicy = mfa.getMfaPolicy();
        String beforeStatus = mfa.getMfaStatus();
        LocalDateTime now = LocalDateTime.now();
        if (!StringUtils.hasText(mfa.getPendingSecretCipher())) {
            mfa.setPendingSecretCipher(MfaSecretCrypto.encrypt(TotpUtils.generateBase32Secret()));
        }
        mfa.setUpdatedAt(now);
        mfa.setUpdatedBy(currentAccountId());
        sysAccountMfaMapper.updateById(mfa);
        expireOpenMfaTokens(app.getId(), account.getId(), now);
        recordMfaLog(app, account, mfa, "RESEND_BIND_MAIL", MFA_RESULT_SUCCESS, request.getReason(), beforePolicy, beforeStatus, currentOperator(), null);
        sendMfaNotice(app, account, TEMPLATE_MFA_BIND_NOTICE, request.getReason(), null);
        return toMfaStatusResponse(account, mfa);
    }

    /**
     * 查询商户管理列表或分页数据，供页面筛选和展示使用。
     * @return 处理后的业务结果或页面展示数据。
     */
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

    /**
     * 查询商户管理列表或分页数据，供页面筛选和展示使用。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
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

    /**
     * 获取商户管理明细数据，并在不存在或不满足条件时按业务边界处理。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public RoleDTO getRole(Long id) {
        SysAppDO app = merchantApp();
        String merchantId = currentMerchantId();
        return toRoleDTO(getRole(app.getId(), merchantId, id));
    }

    /**
     * 创建或保存商户管理数据，保持请求校验、默认值和审计字段一致。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
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

    /**
     * 更新商户管理数据，保持已有记录、状态和审计字段的一致性。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
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

    /**
     * 删除商户管理数据，按业务规则处理引用校验和删除边界。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */
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

    /**
     * 更新商户管理数据，保持已有记录、状态和审计字段的一致性。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param status 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */
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

    /**
     * 执行商户管理相关处理，保持当前层级的职责边界和返回语义。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public RoleGrantTreeDTO roleGrantTreeTemplate() {
        SysAppDO app = merchantApp();
        String merchantId = currentMerchantId();
        RoleGrantTreeDTO dto = new RoleGrantTreeDTO();
        dto.setTree(buildGrantTree(loadGrantedMenuTree(app.getId(), merchantId), grantedPermissions()));
        return dto;
    }

    /**
     * 执行商户管理相关处理，保持当前层级的职责边界和返回语义。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
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

    /**
     * 执行商户管理相关处理，保持当前层级的职责边界和返回语义。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */
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

    /**
     * 执行商户管理相关处理，保持当前层级的职责边界和返回语义。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
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

    /**
     * 执行商户管理相关处理，保持当前层级的职责边界和返回语义。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */
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

    /**
     * 执行商户管理相关处理，保持当前层级的职责边界和返回语义。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
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

    /**
     * 执行商户管理相关处理，保持当前层级的职责边界和返回语义。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */
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

    /**
     * 执行商户管理相关处理，保持当前层级的职责边界和返回语义。
     * @return 处理后的业务结果或页面展示数据。
     */
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

    /**
     * 为新增商户员工创建默认强制 OTP 配置。
     *
     * @param app     商户应用
     * @param account 登录账号
     * @param now     当前时间
     */
    private void createDefaultRequiredMfa(SysAppDO app, SysAccountDO account, LocalDateTime now) {
        SysAccountMfaDO mfa = new SysAccountMfaDO();
        mfa.setAppId(app.getId());
        mfa.setAccountId(account.getId());
        mfa.setUserId(account.getUserId());
        mfa.setMerchantId(account.getMerchantId());
        mfa.setMfaPolicy(AuthConstants.MFA_POLICY_REQUIRED);
        mfa.setMfaStatus(account.getStatus() != null && account.getStatus() == AuthConstants.DISABLED
                ? AuthConstants.MFA_STATUS_DISABLED
                : AuthConstants.MFA_STATUS_PENDING_BIND);
        mfa.setMfaType(AuthConstants.MFA_TYPE_TOTP);
        mfa.setIssuer("Acquiring Merchant");
        mfa.setAccountLabel(mfaAccountLabel(account));
        mfa.setFailedVerifyCount(0);
        mfa.setCreatedAt(now);
        mfa.setUpdatedAt(now);
        mfa.setCreatedBy(currentAccountId());
        mfa.setUpdatedBy(currentAccountId());
        mfa.setDeleted(AuthConstants.NOT_DELETED);
        sysAccountMfaMapper.insert(mfa);
    }

    /**
     * 账号停用时同步 OTP 状态，账号启用时保留原 OTP 策略等待管理员明确处理。
     *
     * @param account 登录账号
     */
    private void syncMfaStatusForAccountStatus(SysAccountDO account) {
        if (account.getStatus() == null || account.getStatus() != AuthConstants.DISABLED) {
            return;
        }
        SysAccountMfaDO mfa = loadMfa(account.getAppId(), account.getId());
        if (mfa == null) {
            return;
        }
        mfa.setMfaStatus(AuthConstants.MFA_STATUS_DISABLED);
        mfa.setFailedVerifyCount(0);
        mfa.setLockedUntil(null);
        mfa.setUpdatedAt(LocalDateTime.now());
        mfa.setUpdatedBy(currentAccountId());
        sysAccountMfaMapper.updateById(mfa);
    }

    private SysAccountMfaDO loadMfa(Long appId, Long accountId) {
        return sysAccountMfaMapper.selectOne(Wrappers.<SysAccountMfaDO>lambdaQuery()
                .eq(SysAccountMfaDO::getAppId, appId)
                .eq(SysAccountMfaDO::getAccountId, accountId)
                .eq(SysAccountMfaDO::getDeleted, AuthConstants.NOT_DELETED)
                .last("LIMIT 1"));
    }

    private SysAccountMfaDO ensureMfa(SysAppDO app, SysAccountDO account, LocalDateTime now) {
        SysAccountMfaDO mfa = loadMfa(app.getId(), account.getId());
        if (mfa != null) {
            return mfa;
        }
        SysAccountMfaDO created = new SysAccountMfaDO();
        created.setAppId(app.getId());
        created.setAccountId(account.getId());
        created.setUserId(account.getUserId());
        created.setMerchantId(account.getMerchantId());
        created.setMfaPolicy(AuthConstants.MFA_POLICY_OPTIONAL);
        created.setMfaStatus(AuthConstants.MFA_STATUS_NOT_ENABLED);
        created.setMfaType(AuthConstants.MFA_TYPE_TOTP);
        created.setIssuer("Acquiring Merchant");
        created.setAccountLabel(mfaAccountLabel(account));
        created.setFailedVerifyCount(0);
        created.setCreatedAt(now);
        created.setUpdatedAt(now);
        created.setCreatedBy(currentAccountId());
        created.setUpdatedBy(currentAccountId());
        created.setDeleted(AuthConstants.NOT_DELETED);
        sysAccountMfaMapper.insert(created);
        return created;
    }

    private void expireOpenMfaTokens(Long appId, Long accountId, LocalDateTime now) {
        sysAccountMfaTokenMapper.update(
                Wrappers.<SysAccountMfaTokenDO>lambdaUpdate()
                        .set(SysAccountMfaTokenDO::getUsed, AuthConstants.ENABLED)
                        .set(SysAccountMfaTokenDO::getUsedAt, now)
                        .set(SysAccountMfaTokenDO::getUpdatedAt, now)
                        .eq(SysAccountMfaTokenDO::getAppId, appId)
                        .eq(SysAccountMfaTokenDO::getAccountId, accountId)
                        .eq(SysAccountMfaTokenDO::getTokenType, MFA_TOKEN_TYPE_LOGIN)
                        .eq(SysAccountMfaTokenDO::getUsed, AuthConstants.DISABLED)
                        .eq(SysAccountMfaTokenDO::getDeleted, AuthConstants.NOT_DELETED)
        );
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

    private void recordMfaLog(SysAppDO app,
                              SysAccountDO account,
                              SysAccountMfaDO mfa,
                              String actionType,
                              String result,
                              String reason,
                              String beforePolicy,
                              String beforeStatus,
                              InternalAuthAccount operator,
                              String userAgent) {
        SysAccountMfaLogDO logRow = new SysAccountMfaLogDO();
        logRow.setAppId(app.getId());
        logRow.setAccountId(account.getId());
        logRow.setUserId(account.getUserId());
        logRow.setMerchantId(account.getMerchantId());
        logRow.setActionType(actionType);
        logRow.setResult(result);
        logRow.setReason(normalize(reason));
        logRow.setBeforePolicy(beforePolicy);
        logRow.setBeforeStatus(beforeStatus);
        logRow.setAfterPolicy(mfa.getMfaPolicy());
        logRow.setAfterStatus(mfa.getMfaStatus());
        logRow.setOperatorAccountId(operator == null ? null : operator.getAccountId());
        logRow.setOperatorLoginAccount(operator == null ? null : operator.getLoginAccount());
        logRow.setClientIp("-");
        logRow.setUserAgent(userAgent);
        logRow.setEventTime(LocalDateTime.now());
        logRow.setCreatedAt(LocalDateTime.now());
        sysAccountMfaLogMapper.insert(logRow);
    }

    private void sendMfaNotice(SysAppDO app, SysAccountDO account, String templateCode, String reason, LocalDateTime exemptUntil) {
        if (!StringUtils.hasText(account.getEmail())) {
            return;
        }
        try {
            merchantTemplateEmailService.sendByTemplate(new MerchantEmailSendCommand(
                    app.getAppCode(),
                    account.getMerchantId(),
                    account.getMerchantId(),
                    merchantName(account.getMerchantId()),
                    templateCode,
                    MERCHANT_MFA_SCENE,
                    "zh-CN",
                    List.of(account.getEmail()),
                    mfaEmailVariables(account, reason, exemptUntil),
                    MERCHANT_MFA_SCENE,
                    String.valueOf(account.getId())
            ));
        } catch (RuntimeException exception) {
            log.warn("merchant mfa notice send failed, accountId={}, templateCode={}", account.getId(), templateCode, exception);
            SysAccountMfaDO mfa = ensureMfa(app, account, LocalDateTime.now());
            recordMfaLog(app, account, mfa, "SEND_NOTICE", MFA_RESULT_FAILED, exception.getMessage(),
                    mfa.getMfaPolicy(), mfa.getMfaStatus(), currentOperator(), null);
        }
    }

    private Map<String, Object> mfaEmailVariables(SysAccountDO account, String reason, LocalDateTime exemptUntil) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("loginAccount", displayLoginAccount(account));
        variables.put("merchantId", account.getMerchantId());
        variables.put("merchantName", merchantName(account.getMerchantId()));
        variables.put("email", account.getEmail());
        variables.put("reason", StringUtils.hasText(reason) ? reason : "-");
        variables.put("exemptUntil", exemptUntil == null ? "长期有效" : exemptUntil.toString().replace("T", " "));
        String merchantSystemBaseUrl = merchantSystemBaseUrl();
        variables.put("merchantSystemBaseUrl", merchantSystemBaseUrl);
        variables.put("bindUrl", merchantLoginUrl(merchantSystemBaseUrl));
        return variables;
    }

    private String merchantSystemBaseUrl() {
        return merchantConfigService.enabledConfigValue(MERCHANT_FRONTEND_BASE_URL_KEY)
                .map(this::trimTrailingSlash)
                .orElse("");
    }

    private String merchantLoginUrl(String merchantSystemBaseUrl) {
        if (!StringUtils.hasText(merchantSystemBaseUrl)) {
            return "/login";
        }
        return trimTrailingSlash(merchantSystemBaseUrl) + "/login";
    }

    private String trimTrailingSlash(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.trim();
        while (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private String merchantName(String merchantId) {
        if (!StringUtils.hasText(merchantId)) {
            return "-";
        }
        BaseMerchantInfoDO merchant = baseMerchantInfoMapper.selectOne(Wrappers.<BaseMerchantInfoDO>lambdaQuery()
                .eq(BaseMerchantInfoDO::getMerchantId, merchantId)
                .eq(BaseMerchantInfoDO::getDeleted, 0)
                .last("LIMIT 1"));
        if (merchant == null || !StringUtils.hasText(merchant.getMerchantName())) {
            return merchantId;
        }
        return merchant.getMerchantName();
    }

    private AccountMfaStatusResponse toMfaStatusResponse(SysAccountDO account, SysAccountMfaDO mfa) {
        AccountMfaStatusResponse response = new AccountMfaStatusResponse();
        response.setAccountId(account.getId());
        response.setLoginAccount(displayLoginAccount(account));
        response.setMfaPolicy(mfa.getMfaPolicy());
        response.setMfaStatus(mfa.getMfaStatus());
        response.setBindTime(mfa.getBindTime());
        response.setLastVerifyTime(mfa.getLastVerifyTime());
        response.setLockedUntil(mfa.getLockedUntil());
        response.setExemptUntil(mfa.getExemptUntil());
        return response;
    }

    private void fillMfaStatus(SysAccountDO account, AccountDTO dto) {
        SysAccountMfaDO mfa = loadMfa(account.getAppId(), account.getId());
        dto.setMfaPolicy(mfa == null ? AuthConstants.MFA_POLICY_OPTIONAL : mfa.getMfaPolicy());
        dto.setMfaStatus(mfa == null ? AuthConstants.MFA_STATUS_NOT_ENABLED : mfa.getMfaStatus());
        dto.setMfaBindTime(mfa == null ? null : mfa.getBindTime());
        dto.setMfaLastVerifyTime(mfa == null ? null : mfa.getLastVerifyTime());
        dto.setMfaExemptUntil(mfa == null ? null : mfa.getExemptUntil());
        dto.setMfaLockedUntil(mfa == null ? null : mfa.getLockedUntil());
    }

    private void assertNotSelf(Long targetAccountId, String message) {
        InternalAuthAccount operator = currentOperator();
        if (operator != null && Objects.equals(operator.getAccountId(), targetAccountId)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), message);
        }
    }

    private InternalAuthAccount currentOperator() {
        return InternalAuthContextHolder.get();
    }

    private String mfaAccountLabel(SysAccountDO account) {
        return StringUtils.hasText(account.getMerchantId())
                ? account.getMerchantId() + ":" + account.getLoginAccount()
                : account.getLoginAccount();
    }

    private String displayLoginAccount(SysAccountDO account) {
        if (account == null || !StringUtils.hasText(account.getLoginAccount())) {
            return "-";
        }
        String suffix = ACCOUNT_LOGIN_SEPARATOR + account.getMerchantId();
        if (StringUtils.hasText(account.getMerchantId()) && account.getLoginAccount().endsWith(suffix)) {
            return account.getLoginAccount().substring(0, account.getLoginAccount().length() - suffix.length());
        }
        return account.getLoginAccount();
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
        fillMfaStatus(account, dto);
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
