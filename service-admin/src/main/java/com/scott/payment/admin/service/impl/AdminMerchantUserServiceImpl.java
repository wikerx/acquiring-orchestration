package com.scott.payment.admin.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scott.payment.admin.dto.SysMenuDTO;
import com.scott.payment.admin.dto.merchant.AdminMerchantUserDetailDTO;
import com.scott.payment.admin.dto.merchant.AdminMerchantUserListDTO;
import com.scott.payment.admin.dto.merchant.AdminMerchantUserQueryRequest;
import com.scott.payment.admin.service.AdminMerchantUserService;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.db.auth.constant.AuthConstants;
import com.scott.payment.component.db.auth.entity.BaseMerchantInfoDO;
import com.scott.payment.component.db.auth.entity.SysAccountDO;
import com.scott.payment.component.db.auth.entity.SysAppDO;
import com.scott.payment.component.db.auth.entity.SysMerchantAccountDeptDO;
import com.scott.payment.component.db.auth.entity.SysMerchantAccountPostDO;
import com.scott.payment.component.db.auth.entity.SysMerchantDeptDO;
import com.scott.payment.component.db.auth.entity.SysMerchantPostDO;
import com.scott.payment.component.db.auth.entity.SysMerchantUserDO;
import com.scott.payment.component.db.auth.entity.SysMerchantUserRoleDO;
import com.scott.payment.component.db.auth.entity.SysMenuDO;
import com.scott.payment.component.db.auth.entity.SysPermissionDO;
import com.scott.payment.component.db.auth.entity.SysRoleDO;
import com.scott.payment.component.db.auth.entity.SysRoleMenuDO;
import com.scott.payment.component.db.auth.entity.SysRolePermissionDO;
import com.scott.payment.component.db.auth.mapper.BaseMerchantInfoMapper;
import com.scott.payment.component.db.auth.mapper.SysAccountMapper;
import com.scott.payment.component.db.auth.mapper.SysAppMapper;
import com.scott.payment.component.db.auth.mapper.SysMerchantAccountDeptMapper;
import com.scott.payment.component.db.auth.mapper.SysMerchantAccountPostMapper;
import com.scott.payment.component.db.auth.mapper.SysMerchantDeptMapper;
import com.scott.payment.component.db.auth.mapper.SysMerchantPostMapper;
import com.scott.payment.component.db.auth.mapper.SysMerchantUserMapper;
import com.scott.payment.component.db.auth.mapper.SysMerchantUserRoleMapper;
import com.scott.payment.component.db.auth.mapper.SysMenuMapper;
import com.scott.payment.component.db.auth.mapper.SysPermissionMapper;
import com.scott.payment.component.db.auth.mapper.SysRoleMapper;
import com.scott.payment.component.db.auth.mapper.SysRoleMenuMapper;
import com.scott.payment.component.db.auth.mapper.SysRolePermissionMapper;
import com.scott.payment.component.db.constant.DataSourceName;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantUserServiceImpl
 * @date : 2026-06-23 12:55
 * @email : scott_x@163.com
 * @description : Admin Merchant User Service Impl 服务实现，位于 运营后台服务，执行领域校验、配置读取、数据库更新或远程调用编排，并向上层返回明确结果。
 * @status : create
 */
public class AdminMerchantUserServiceImpl implements AdminMerchantUserService {

    /**
     * ROOT PARENT ID，用于定位 Admin Merchant User Service Impl 关联的上游配置、渠道、账号、角色或业务记录。
     * <p>
     * 单位：无；格式：业务编号字符串；不允许为空；非敏感字段。
     * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final long ROOT_PARENT_ID = 0L;

    /**
     * sys App Mapper 依赖，用于 Admin Merchant User Service Impl 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final SysAppMapper sysAppMapper;
    /**
     * sys Account Mapper，表示当前统计、分页、扫描或重试场景中的数量。
     * <p>
     * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final SysAccountMapper sysAccountMapper;
    /**
     * sys Merchant User Mapper 依赖，用于 Admin Merchant User Service Impl 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final SysMerchantUserMapper sysMerchantUserMapper;
    /**
     * sys Merchant User Role Mapper 依赖，用于 Admin Merchant User Service Impl 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final SysMerchantUserRoleMapper sysMerchantUserRoleMapper;
    /**
     * sys Role Mapper 依赖，用于 Admin Merchant User Service Impl 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final SysRoleMapper sysRoleMapper;
    /**
     * sys Role Menu Mapper 依赖，用于 Admin Merchant User Service Impl 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final SysRoleMenuMapper sysRoleMenuMapper;
    /**
     * sys Role Permission Mapper 依赖，用于 Admin Merchant User Service Impl 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final SysRolePermissionMapper sysRolePermissionMapper;
    /**
     * sys Menu Mapper 依赖，用于 Admin Merchant User Service Impl 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final SysMenuMapper sysMenuMapper;
    /**
     * sys Permission Mapper 依赖，用于 Admin Merchant User Service Impl 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final SysPermissionMapper sysPermissionMapper;
    /**
     * sys Merchant Dept Mapper 依赖，用于 Admin Merchant User Service Impl 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final SysMerchantDeptMapper sysMerchantDeptMapper;
    /**
     * sys Merchant Post Mapper 依赖，用于 Admin Merchant User Service Impl 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final SysMerchantPostMapper sysMerchantPostMapper;
    /**
     * sys Merchant Account Dept Mapper，表示当前统计、分页、扫描或重试场景中的数量。
     * <p>
     * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final SysMerchantAccountDeptMapper sysMerchantAccountDeptMapper;
    /**
     * sys Merchant Account Post Mapper，表示当前统计、分页、扫描或重试场景中的数量。
     * <p>
     * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final SysMerchantAccountPostMapper sysMerchantAccountPostMapper;
    /**
     * base Merchant Info Mapper 依赖，用于 Admin Merchant User Service Impl 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final BaseMerchantInfoMapper baseMerchantInfoMapper;

/**
 * 整理admin商户用户serviceimpl，返回当前业务步骤需要的规范化结果。
 * <p>
 * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
 * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
 * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
 * </p>
 * @param sysAppMapper sys App Mapper 输入值，参与 sysapp映射器 的查询、校验、转换、写入或日志摘要
 * @param sysAccountMapper sys Account Mapper 输入值，参与 sys账号映射器 的查询、校验、转换、写入或日志摘要
 * @param sysMerchantUserMapper sys Merchant User Mapper 输入值，参与 sys商户用户映射器 的查询、校验、转换、写入或日志摘要
 * @param sysMerchantUserRoleMapper sys Merchant User Role Mapper 输入值，参与 sys商户用户角色映射器 的查询、校验、转换、写入或日志摘要
 * @param sysRoleMapper sys Role Mapper 输入值，参与 sys角色映射器 的查询、校验、转换、写入或日志摘要
 * @param sysRoleMenuMapper sys Role Menu Mapper 输入值，参与 sys角色菜单映射器 的查询、校验、转换、写入或日志摘要
 * @param sysRolePermissionMapper sys Role Permission Mapper 输入值，参与 sys角色权限映射器 的查询、校验、转换、写入或日志摘要
 * @param sysMenuMapper sys Menu Mapper 输入值，参与 sys菜单映射器 的查询、校验、转换、写入或日志摘要
 * @param sysPermissionMapper sys Permission Mapper 输入值，参与 sys权限映射器 的查询、校验、转换、写入或日志摘要
 * @param sysMerchantDeptMapper sys Merchant Dept Mapper 输入值，参与 sys商户部门映射器 的查询、校验、转换、写入或日志摘要
 * @param sysMerchantPostMapper sys Merchant Post Mapper 输入值，参与 sys商户岗位映射器 的查询、校验、转换、写入或日志摘要
 * @param sysMerchantAccountDeptMapper sys Merchant Account Dept Mapper 输入值，参与 sys商户账号部门映射器 的查询、校验、转换、写入或日志摘要
 * @param sysMerchantAccountPostMapper sys Merchant Account Post Mapper 输入值，参与 sys商户账号岗位映射器 的查询、校验、转换、写入或日志摘要
 * @param baseMerchantInfoMapper base Merchant Info Mapper 输入值，参与 基础商户信息映射器 的查询、校验、转换、写入或日志摘要
 */
    public AdminMerchantUserServiceImpl(SysAppMapper sysAppMapper,
                                        SysAccountMapper sysAccountMapper,
                                        SysMerchantUserMapper sysMerchantUserMapper,
                                        SysMerchantUserRoleMapper sysMerchantUserRoleMapper,
                                        SysRoleMapper sysRoleMapper,
                                        SysRoleMenuMapper sysRoleMenuMapper,
                                        SysRolePermissionMapper sysRolePermissionMapper,
                                        SysMenuMapper sysMenuMapper,
                                        SysPermissionMapper sysPermissionMapper,
                                        SysMerchantDeptMapper sysMerchantDeptMapper,
                                        SysMerchantPostMapper sysMerchantPostMapper,
                                        SysMerchantAccountDeptMapper sysMerchantAccountDeptMapper,
                                        SysMerchantAccountPostMapper sysMerchantAccountPostMapper,
                                        BaseMerchantInfoMapper baseMerchantInfoMapper) {
        this.sysAppMapper = sysAppMapper;
        this.sysAccountMapper = sysAccountMapper;
        this.sysMerchantUserMapper = sysMerchantUserMapper;
        this.sysMerchantUserRoleMapper = sysMerchantUserRoleMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysRoleMenuMapper = sysRoleMenuMapper;
        this.sysRolePermissionMapper = sysRolePermissionMapper;
        this.sysMenuMapper = sysMenuMapper;
        this.sysPermissionMapper = sysPermissionMapper;
        this.sysMerchantDeptMapper = sysMerchantDeptMapper;
        this.sysMerchantPostMapper = sysMerchantPostMapper;
        this.sysMerchantAccountDeptMapper = sysMerchantAccountDeptMapper;
        this.sysMerchantAccountPostMapper = sysMerchantAccountPostMapper;
        this.baseMerchantInfoMapper = baseMerchantInfoMapper;
    }

    @Override
    @DS(DataSourceName.SLAVE)
    public PageResult<AdminMerchantUserListDTO> pageMerchantUsers(AdminMerchantUserQueryRequest request) {
        AdminMerchantUserQueryRequest query = request == null ? new AdminMerchantUserQueryRequest() : request;
        SysAppDO app = merchantApp();
        Set<String> merchantIds = filterMerchantIds(query);
        if (merchantIds != null && merchantIds.isEmpty()) {
            return PageResult.of(0, query.safePageNo(), query.safePageSize(), Collections.emptyList());
        }
        Set<Long> accountIds = filterAccountIds(app.getId(), query);
        if (accountIds != null && accountIds.isEmpty()) {
            return PageResult.of(0, query.safePageNo(), query.safePageSize(), Collections.emptyList());
        }
        Page<SysAccountDO> page = sysAccountMapper.selectPage(
                new Page<>(query.safePageNo(), query.safePageSize()),
                Wrappers.<SysAccountDO>lambdaQuery()
                        .eq(SysAccountDO::getAppId, app.getId())
                        .eq(SysAccountDO::getDeleted, AuthConstants.NOT_DELETED)
                        .in(merchantIds != null, SysAccountDO::getMerchantId, merchantIds)
                        .in(accountIds != null, SysAccountDO::getId, accountIds)
                        .like(StringUtils.hasText(query.getLoginAccount()), SysAccountDO::getLoginAccount, query.getLoginAccount())
                        .like(StringUtils.hasText(query.getMobile()), SysAccountDO::getMobile, query.getMobile())
                        .like(StringUtils.hasText(query.getEmail()), SysAccountDO::getEmail, query.getEmail())
                        .eq(query.getStatus() != null, SysAccountDO::getStatus, query.getStatus())
                        .ge(query.getCreatedStartTime() != null, SysAccountDO::getCreatedAt, query.getCreatedStartTime())
                        .le(query.getCreatedEndTime() != null, SysAccountDO::getCreatedAt, query.getCreatedEndTime())
                        .orderByDesc(SysAccountDO::getCreatedAt)
        );
        return PageResult.of(
                page.getTotal(),
                page.getCurrent(),
                page.getSize(),
                buildListRows(app.getId(), page.getRecords())
        );
    }

    @Override
    @DS(DataSourceName.SLAVE)
    public AdminMerchantUserDetailDTO getMerchantUser(Long accountId) {
        SysAppDO app = merchantApp();
        SysAccountDO account = sysAccountMapper.selectOne(Wrappers.<SysAccountDO>lambdaQuery()
                .eq(SysAccountDO::getAppId, app.getId())
                .eq(SysAccountDO::getId, accountId)
                .eq(SysAccountDO::getDeleted, AuthConstants.NOT_DELETED)
                .last("LIMIT 1"));
        if (account == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "merchant account not found");
        }
        AdminMerchantUserDetailDTO detail = new AdminMerchantUserDetailDTO();
        detail.setAccount(buildListRows(app.getId(), List.of(account)).stream().findFirst().orElse(null));
        BaseMerchantInfoDO merchant = loadMerchantMap(List.of(account.getMerchantId())).get(account.getMerchantId());
        detail.setMerchant(toMerchantSummary(merchant));
        detail.setDepts(loadDeptSummaries(account.getMerchantId(), account.getId()));
        detail.setPosts(loadPostSummaries(account.getMerchantId(), account.getId()));
        List<SysRoleDO> roles = loadAccountRoles(app.getId(), account.getId());
        detail.setRoles(roles.stream().map(this::toRoleSummary).toList());
        detail.setMenus(loadFinalMenus(app.getId(), roles));
        detail.setPermissions(loadFinalPermissionCodes(app.getId(), roles));
        return detail;
    }

    private List<AdminMerchantUserListDTO> buildListRows(Long appId, List<SysAccountDO> accounts) {
        if (accounts == null || accounts.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> accountIds = accounts.stream().map(SysAccountDO::getId).toList();
        Map<Long, SysMerchantUserDO> merchantUserMap = loadMerchantUserMap(accountIds);
        Map<String, BaseMerchantInfoDO> merchantMap = loadMerchantMap(accounts.stream().map(SysAccountDO::getMerchantId).toList());
        Map<Long, List<SysRoleDO>> roleMap = loadRoleMap(appId, merchantUserMap.values().stream().map(SysMerchantUserDO::getId).toList());
        Map<Long, List<SysMerchantDeptDO>> deptMap = loadDeptMap(accountIds);
        Map<Long, List<SysMerchantPostDO>> postMap = loadPostMap(accountIds);
        return accounts.stream()
                .map(account -> toListDTO(account, merchantUserMap.get(account.getId()), merchantMap, roleMap, deptMap, postMap))
                .toList();
    }

/**
 * 构造listdto对象，完成字段复制、格式标准化和敏感数据处理。
 * <p>
 * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
 * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
 * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
 * </p>
 * @param account account 输入值，参与 账号 的查询、校验、转换、写入或日志摘要
 * @param merchantUser merchant User 输入值，参与 商户用户 的查询、校验、转换、写入或日志摘要
 * @param Map Map 输入值，参与 map 的查询、校验、转换、写入或日志摘要
 * @param merchantMap merchant Map 输入值，参与 商户map 的查询、校验、转换、写入或日志摘要
 * @param roleMap role Map 输入值，参与 角色map 的查询、校验、转换、写入或日志摘要
 * @param deptMap dept Map 输入值，参与 部门map 的查询、校验、转换、写入或日志摘要
 * @param postMap post Map 输入值，参与 岗位map 的查询、校验、转换、写入或日志摘要
 * @return 构造、转换或解析后的业务值
 */
    private AdminMerchantUserListDTO toListDTO(SysAccountDO account,
                                               SysMerchantUserDO merchantUser,
                                               Map<String, BaseMerchantInfoDO> merchantMap,
                                               Map<Long, List<SysRoleDO>> roleMap,
                                               Map<Long, List<SysMerchantDeptDO>> deptMap,
                                               Map<Long, List<SysMerchantPostDO>> postMap) {
        AdminMerchantUserListDTO dto = new AdminMerchantUserListDTO();
        dto.setAccountId(account.getId());
        dto.setUserId(account.getUserId());
        dto.setMerchantId(account.getMerchantId());
        BaseMerchantInfoDO merchant = merchantMap.get(account.getMerchantId());
        dto.setMerchantName(merchant == null ? null : merchant.getMerchantName());
        dto.setLoginAccount(displayLoginAccount(account, merchantUser));
        dto.setRealName(merchantUser != null && StringUtils.hasText(merchantUser.getRealName())
                ? merchantUser.getRealName() : null);
        dto.setMobile(maskMobile(account.getMobile()));
        dto.setEmail(maskEmail(account.getEmail()));
        dto.setDeptNames(deptMap.getOrDefault(account.getId(), Collections.emptyList()).stream()
                .map(SysMerchantDeptDO::getDeptName).filter(StringUtils::hasText).distinct().toList());
        dto.setPostNames(postMap.getOrDefault(account.getId(), Collections.emptyList()).stream()
                .map(SysMerchantPostDO::getPostName).filter(StringUtils::hasText).distinct().toList());
        List<SysRoleDO> roles = merchantUser == null ? Collections.emptyList() : roleMap.getOrDefault(merchantUser.getId(), Collections.emptyList());
        dto.setRoleNames(roles.stream().map(SysRoleDO::getRoleName).filter(StringUtils::hasText).distinct().toList());
        dto.setMerchantAdmin(roles.stream().anyMatch(role -> role.getRoleCode() != null && role.getRoleCode().startsWith("MERCHANT_ADMIN_")));
        dto.setStatus(account.getStatus());
        dto.setLastLoginAt(account.getLastLoginAt());
        dto.setLastLoginIp(maskIp(account.getLastLoginIp()));
        dto.setCreatedAt(account.getCreatedAt());
        return dto;
    }

    /**
     * 整理筛选商户ID，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private Set<String> filterMerchantIds(AdminMerchantUserQueryRequest query) {
        if (!StringUtils.hasText(query.getMerchantName())) {
            return StringUtils.hasText(query.getMerchantId()) ? Set.of(query.getMerchantId().trim()) : null;
        }
        return baseMerchantInfoMapper.selectList(Wrappers.<BaseMerchantInfoDO>lambdaQuery()
                        .eq(BaseMerchantInfoDO::getDeleted, 0)
                        .like(StringUtils.hasText(query.getMerchantId()), BaseMerchantInfoDO::getMerchantId, query.getMerchantId())
                        .like(BaseMerchantInfoDO::getMerchantName, query.getMerchantName().trim()))
                .stream().map(BaseMerchantInfoDO::getMerchantId).collect(Collectors.toCollection(HashSet::new));
    }

    /**
     * 整理筛选账号ID，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param appId app ID 输入值，参与 appID 的查询、校验、转换、写入或日志摘要
     * @param query 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private Set<Long> filterAccountIds(Long appId, AdminMerchantUserQueryRequest query) {
        Set<Long> result = null;
        if (StringUtils.hasText(query.getRealName())) {
            result = intersect(result, sysMerchantUserMapper.selectList(Wrappers.<SysMerchantUserDO>lambdaQuery()
                            .like(SysMerchantUserDO::getRealName, query.getRealName().trim())
                            .eq(SysMerchantUserDO::getDeleted, AuthConstants.NOT_DELETED))
                    .stream().map(SysMerchantUserDO::getAccountId).filter(Objects::nonNull).collect(Collectors.toSet()));
        }
        if (StringUtils.hasText(query.getRoleName())) {
            List<SysRoleDO> roles = sysRoleMapper.selectList(Wrappers.<SysRoleDO>lambdaQuery()
                    .eq(SysRoleDO::getAppId, appId)
                    .like(SysRoleDO::getRoleName, query.getRoleName().trim())
                    .eq(SysRoleDO::getDeleted, AuthConstants.NOT_DELETED));
            result = intersect(result, accountIdsByRole(appId, roles.stream().map(SysRoleDO::getId).toList()));
        }
        if (StringUtils.hasText(query.getDeptName())) {
            List<SysMerchantDeptDO> depts = sysMerchantDeptMapper.selectList(Wrappers.<SysMerchantDeptDO>lambdaQuery()
                    .like(SysMerchantDeptDO::getDeptName, query.getDeptName().trim())
                    .eq(SysMerchantDeptDO::getDeleted, AuthConstants.NOT_DELETED));
            result = intersect(result, accountIdsByDept(depts.stream().map(SysMerchantDeptDO::getId).toList()));
        }
        if (StringUtils.hasText(query.getPostName())) {
            List<SysMerchantPostDO> posts = sysMerchantPostMapper.selectList(Wrappers.<SysMerchantPostDO>lambdaQuery()
                    .like(SysMerchantPostDO::getPostName, query.getPostName().trim())
                    .eq(SysMerchantPostDO::getDeleted, AuthConstants.NOT_DELETED));
            result = intersect(result, accountIdsByPost(posts.stream().map(SysMerchantPostDO::getId).toList()));
        }
        return result;
    }

    /**
     * 整理账号ID按角色，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param appId app ID 输入值，参与 appID 的查询、校验、转换、写入或日志摘要
     * @param roleIds role Ids 输入值，参与 角色ID 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private Set<Long> accountIdsByRole(Long appId, List<Long> roleIds) {
        if (roleIds.isEmpty()) {
            return Collections.emptySet();
        }
        List<Long> merchantUserIds = sysMerchantUserRoleMapper.selectList(Wrappers.<SysMerchantUserRoleDO>lambdaQuery()
                        .eq(SysMerchantUserRoleDO::getAppId, appId)
                        .in(SysMerchantUserRoleDO::getRoleId, roleIds)
                        .eq(SysMerchantUserRoleDO::getDeleted, AuthConstants.NOT_DELETED))
                .stream().map(SysMerchantUserRoleDO::getMerchantUserId).distinct().toList();
        if (merchantUserIds.isEmpty()) {
            return Collections.emptySet();
        }
        return sysMerchantUserMapper.selectList(Wrappers.<SysMerchantUserDO>lambdaQuery()
                        .in(SysMerchantUserDO::getId, merchantUserIds)
                        .eq(SysMerchantUserDO::getDeleted, AuthConstants.NOT_DELETED))
                .stream().map(SysMerchantUserDO::getAccountId).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    /**
     * 整理账号ID按部门，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param deptIds dept Ids 输入值，参与 部门ID 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private Set<Long> accountIdsByDept(List<Long> deptIds) {
        if (deptIds.isEmpty()) {
            return Collections.emptySet();
        }
        return sysMerchantAccountDeptMapper.selectList(Wrappers.<SysMerchantAccountDeptDO>lambdaQuery()
                        .in(SysMerchantAccountDeptDO::getDeptId, deptIds))
                .stream().map(SysMerchantAccountDeptDO::getAccountId).collect(Collectors.toSet());
    }

    /**
     * 整理账号ID按岗位，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param postIds post Ids 输入值，参与 岗位ID 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private Set<Long> accountIdsByPost(List<Long> postIds) {
        if (postIds.isEmpty()) {
            return Collections.emptySet();
        }
        return sysMerchantAccountPostMapper.selectList(Wrappers.<SysMerchantAccountPostDO>lambdaQuery()
                        .in(SysMerchantAccountPostDO::getPostId, postIds))
                .stream().map(SysMerchantAccountPostDO::getAccountId).collect(Collectors.toSet());
    }

    /**
     * 查询商户用户map，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param accountIds account Ids 输入值，参与 账号ID 的查询、校验、转换、写入或日志摘要
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private Map<Long, SysMerchantUserDO> loadMerchantUserMap(List<Long> accountIds) {
        if (accountIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return sysMerchantUserMapper.selectList(Wrappers.<SysMerchantUserDO>lambdaQuery()
                        .in(SysMerchantUserDO::getAccountId, accountIds)
                        .eq(SysMerchantUserDO::getDeleted, AuthConstants.NOT_DELETED))
                .stream().collect(Collectors.toMap(SysMerchantUserDO::getAccountId, Function.identity(), (left, right) -> left));
    }

    /**
     * 查询商户map，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param merchantIds 商户号，用于限定数据归属、权限范围和配置读取范围
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private Map<String, BaseMerchantInfoDO> loadMerchantMap(List<String> merchantIds) {
        List<String> ids = merchantIds.stream().filter(StringUtils::hasText).distinct().toList();
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return baseMerchantInfoMapper.selectList(Wrappers.<BaseMerchantInfoDO>lambdaQuery()
                        .in(BaseMerchantInfoDO::getMerchantId, ids)
                        .eq(BaseMerchantInfoDO::getDeleted, 0))
                .stream().collect(Collectors.toMap(BaseMerchantInfoDO::getMerchantId, Function.identity(), (left, right) -> left));
    }

    /**
     * 查询角色map，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param appId app ID 输入值，参与 appID 的查询、校验、转换、写入或日志摘要
     * @param merchantUserIds merchant User Ids 输入值，参与 商户用户ID 的查询、校验、转换、写入或日志摘要
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private Map<Long, List<SysRoleDO>> loadRoleMap(Long appId, List<Long> merchantUserIds) {
        if (merchantUserIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<SysMerchantUserRoleDO> relations = sysMerchantUserRoleMapper.selectList(Wrappers.<SysMerchantUserRoleDO>lambdaQuery()
                .eq(SysMerchantUserRoleDO::getAppId, appId)
                .in(SysMerchantUserRoleDO::getMerchantUserId, merchantUserIds)
                .eq(SysMerchantUserRoleDO::getDeleted, AuthConstants.NOT_DELETED));
        Map<Long, SysRoleDO> roleById = loadRoles(relations.stream().map(SysMerchantUserRoleDO::getRoleId).toList())
                .stream().collect(Collectors.toMap(SysRoleDO::getId, Function.identity(), (left, right) -> left));
        return relations.stream().collect(Collectors.groupingBy(
                SysMerchantUserRoleDO::getMerchantUserId,
                LinkedHashMap::new,
                Collectors.mapping(relation -> roleById.get(relation.getRoleId()),
                        Collectors.filtering(Objects::nonNull, Collectors.toList()))
        ));
    }

    /**
     * 查询账号角色，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param appId app ID 输入值，参与 appID 的查询、校验、转换、写入或日志摘要
     * @param accountId account ID 输入值，参与 账号ID 的查询、校验、转换、写入或日志摘要
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private List<SysRoleDO> loadAccountRoles(Long appId, Long accountId) {
        SysMerchantUserDO merchantUser = sysMerchantUserMapper.selectOne(Wrappers.<SysMerchantUserDO>lambdaQuery()
                .eq(SysMerchantUserDO::getAccountId, accountId)
                .eq(SysMerchantUserDO::getDeleted, AuthConstants.NOT_DELETED)
                .last("LIMIT 1"));
        if (merchantUser == null) {
            return Collections.emptyList();
        }
        return loadRoleMap(appId, List.of(merchantUser.getId())).getOrDefault(merchantUser.getId(), Collections.emptyList());
    }

    /**
     * 查询角色，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param roleIds role Ids 输入值，参与 角色ID 的查询、校验、转换、写入或日志摘要
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private List<SysRoleDO> loadRoles(List<Long> roleIds) {
        List<Long> ids = roleIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        return sysRoleMapper.selectList(Wrappers.<SysRoleDO>lambdaQuery()
                .in(SysRoleDO::getId, ids)
                .eq(SysRoleDO::getDeleted, AuthConstants.NOT_DELETED));
    }

    /**
     * 查询部门map，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param accountIds account Ids 输入值，参与 账号ID 的查询、校验、转换、写入或日志摘要
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private Map<Long, List<SysMerchantDeptDO>> loadDeptMap(List<Long> accountIds) {
        if (accountIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<SysMerchantAccountDeptDO> relations = sysMerchantAccountDeptMapper.selectList(Wrappers.<SysMerchantAccountDeptDO>lambdaQuery()
                .in(SysMerchantAccountDeptDO::getAccountId, accountIds));
        Map<Long, SysMerchantDeptDO> deptById = loadDepts(relations.stream().map(SysMerchantAccountDeptDO::getDeptId).toList())
                .stream().collect(Collectors.toMap(SysMerchantDeptDO::getId, Function.identity(), (left, right) -> left));
        return relations.stream().collect(Collectors.groupingBy(
                SysMerchantAccountDeptDO::getAccountId,
                LinkedHashMap::new,
                Collectors.mapping(relation -> deptById.get(relation.getDeptId()),
                        Collectors.filtering(Objects::nonNull, Collectors.toList()))
        ));
    }

    /**
     * 查询岗位map，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param accountIds account Ids 输入值，参与 账号ID 的查询、校验、转换、写入或日志摘要
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private Map<Long, List<SysMerchantPostDO>> loadPostMap(List<Long> accountIds) {
        if (accountIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<SysMerchantAccountPostDO> relations = sysMerchantAccountPostMapper.selectList(Wrappers.<SysMerchantAccountPostDO>lambdaQuery()
                .in(SysMerchantAccountPostDO::getAccountId, accountIds));
        Map<Long, SysMerchantPostDO> postById = loadPosts(relations.stream().map(SysMerchantAccountPostDO::getPostId).toList())
                .stream().collect(Collectors.toMap(SysMerchantPostDO::getId, Function.identity(), (left, right) -> left));
        return relations.stream().collect(Collectors.groupingBy(
                SysMerchantAccountPostDO::getAccountId,
                LinkedHashMap::new,
                Collectors.mapping(relation -> postById.get(relation.getPostId()),
                        Collectors.filtering(Objects::nonNull, Collectors.toList()))
        ));
    }

    /**
     * 查询部门summaries，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param merchantId 商户号，用于限定数据归属、权限范围和配置读取范围
     * @param accountId account ID 输入值，参与 账号ID 的查询、校验、转换、写入或日志摘要
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private List<AdminMerchantUserDetailDTO.DeptSummary> loadDeptSummaries(String merchantId, Long accountId) {
        return loadDeptMap(List.of(accountId)).getOrDefault(accountId, Collections.emptyList()).stream()
                .filter(dept -> Objects.equals(dept.getMerchantId(), merchantId))
                .map(this::toDeptSummary).toList();
    }

    /**
     * 查询岗位summaries，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param merchantId 商户号，用于限定数据归属、权限范围和配置读取范围
     * @param accountId account ID 输入值，参与 账号ID 的查询、校验、转换、写入或日志摘要
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private List<AdminMerchantUserDetailDTO.PostSummary> loadPostSummaries(String merchantId, Long accountId) {
        return loadPostMap(List.of(accountId)).getOrDefault(accountId, Collections.emptyList()).stream()
                .filter(post -> Objects.equals(post.getMerchantId(), merchantId))
                .map(this::toPostSummary).toList();
    }

    /**
     * 查询部门，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param deptIds dept Ids 输入值，参与 部门ID 的查询、校验、转换、写入或日志摘要
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private List<SysMerchantDeptDO> loadDepts(List<Long> deptIds) {
        List<Long> ids = deptIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        return sysMerchantDeptMapper.selectList(Wrappers.<SysMerchantDeptDO>lambdaQuery()
                .in(SysMerchantDeptDO::getId, ids)
                .eq(SysMerchantDeptDO::getDeleted, AuthConstants.NOT_DELETED));
    }

    /**
     * 查询岗位，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param postIds post Ids 输入值，参与 岗位ID 的查询、校验、转换、写入或日志摘要
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private List<SysMerchantPostDO> loadPosts(List<Long> postIds) {
        List<Long> ids = postIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        return sysMerchantPostMapper.selectList(Wrappers.<SysMerchantPostDO>lambdaQuery()
                .in(SysMerchantPostDO::getId, ids)
                .eq(SysMerchantPostDO::getDeleted, AuthConstants.NOT_DELETED));
    }

    /**
     * 查询final菜单，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param appId app ID 输入值，参与 appID 的查询、校验、转换、写入或日志摘要
     * @param roles roles 输入值，参与 角色 的查询、校验、转换、写入或日志摘要
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private List<SysMenuDTO> loadFinalMenus(Long appId, List<SysRoleDO> roles) {
        List<Long> roleIds = roles.stream().map(SysRoleDO::getId).toList();
        if (roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> menuIds = sysRoleMenuMapper.selectList(Wrappers.<SysRoleMenuDO>lambdaQuery()
                        .eq(SysRoleMenuDO::getAppId, appId)
                        .in(SysRoleMenuDO::getRoleId, roleIds)
                        .eq(SysRoleMenuDO::getDeleted, AuthConstants.NOT_DELETED))
                .stream().map(SysRoleMenuDO::getMenuId).filter(Objects::nonNull).distinct().toList();
        if (menuIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<SysMenuDTO> nodes = sysMenuMapper.selectList(Wrappers.<SysMenuDO>lambdaQuery()
                        .eq(SysMenuDO::getAppId, appId)
                        .in(SysMenuDO::getId, menuIds)
                        .eq(SysMenuDO::getDeleted, AuthConstants.NOT_DELETED)
                        .orderByAsc(SysMenuDO::getSortNo, SysMenuDO::getId))
                .stream().map(this::toMenuDTO).toList();
        return buildMenuTree(nodes);
    }

    /**
     * 查询final权限codes，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param appId app ID 输入值，参与 appID 的查询、校验、转换、写入或日志摘要
     * @param roles roles 输入值，参与 角色 的查询、校验、转换、写入或日志摘要
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private List<String> loadFinalPermissionCodes(Long appId, List<SysRoleDO> roles) {
        List<Long> roleIds = roles.stream().map(SysRoleDO::getId).toList();
        if (roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> permissionIds = sysRolePermissionMapper.selectList(Wrappers.<SysRolePermissionDO>lambdaQuery()
                        .eq(SysRolePermissionDO::getAppId, appId)
                        .in(SysRolePermissionDO::getRoleId, roleIds)
                        .eq(SysRolePermissionDO::getDeleted, AuthConstants.NOT_DELETED))
                .stream().map(SysRolePermissionDO::getPermissionId).filter(Objects::nonNull).distinct().toList();
        if (permissionIds.isEmpty()) {
            return Collections.emptyList();
        }
        return sysPermissionMapper.selectList(Wrappers.<SysPermissionDO>lambdaQuery()
                        .eq(SysPermissionDO::getAppId, appId)
                        .in(SysPermissionDO::getId, permissionIds)
                        .eq(SysPermissionDO::getDeleted, AuthConstants.NOT_DELETED))
                .stream().map(SysPermissionDO::getPermissionCode).filter(StringUtils::hasText).sorted().toList();
    }

    /**
     * 构造菜单树对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param flat flat 输入值，参与 flat 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private List<SysMenuDTO> buildMenuTree(List<SysMenuDTO> flat) {
        Map<Long, SysMenuDTO> nodeMap = flat.stream()
                .collect(Collectors.toMap(SysMenuDTO::getMenuId, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        List<SysMenuDTO> roots = new ArrayList<>();
        nodeMap.values().forEach(node -> {
            if (node.getParentId() == null || node.getParentId() == ROOT_PARENT_ID || !nodeMap.containsKey(node.getParentId())) {
                roots.add(node);
                return;
            }
            nodeMap.get(node.getParentId()).getChildren().add(node);
        });
        return roots;
    }

    /**
     * 构造菜单dto对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param row 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @return 构造、转换或解析后的业务值
     */
    private SysMenuDTO toMenuDTO(SysMenuDO row) {
        SysMenuDTO dto = new SysMenuDTO();
        dto.setMenuId(row.getId());
        dto.setParentId(row.getParentId());
        dto.setMenuCode(row.getMenuCode());
        dto.setMenuName(row.getMenuName());
        dto.setMenuType(row.getMenuType());
        dto.setRoutePath(row.getRoutePath());
        dto.setComponentPath(row.getComponentPath());
        dto.setPermissionCode(row.getPermissionCode());
        dto.setIcon(row.getIcon());
        dto.setRedirect(row.getRedirect());
        dto.setVisible(row.getVisible());
        dto.setKeepAlive(row.getKeepAlive());
        dto.setExternalLink(row.getExternalLink());
        dto.setSortNo(row.getSortNo());
        dto.setStatus(row.getStatus());
        return dto;
    }

    /**
     * 构造商户汇总对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param merchant merchant 输入值，参与 商户 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private AdminMerchantUserDetailDTO.MerchantSummary toMerchantSummary(BaseMerchantInfoDO merchant) {
        if (merchant == null) {
            return null;
        }
        AdminMerchantUserDetailDTO.MerchantSummary dto = new AdminMerchantUserDetailDTO.MerchantSummary();
        dto.setMerchantId(merchant.getMerchantId());
        dto.setMerchantName(merchant.getMerchantName());
        dto.setMerchantShortName(merchant.getMerchantShortName());
        dto.setMerchantStatus(merchant.getMerchantStatus());
        return dto;
    }

    /**
     * 构造部门汇总对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param dept dept 输入值，参与 部门 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private AdminMerchantUserDetailDTO.DeptSummary toDeptSummary(SysMerchantDeptDO dept) {
        AdminMerchantUserDetailDTO.DeptSummary dto = new AdminMerchantUserDetailDTO.DeptSummary();
        dto.setDeptId(dept.getId());
        dto.setDeptCode(dept.getDeptCode());
        dto.setDeptName(dept.getDeptName());
        return dto;
    }

    /**
     * 构造岗位汇总对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param post post 输入值，参与 岗位 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private AdminMerchantUserDetailDTO.PostSummary toPostSummary(SysMerchantPostDO post) {
        AdminMerchantUserDetailDTO.PostSummary dto = new AdminMerchantUserDetailDTO.PostSummary();
        dto.setPostId(post.getId());
        dto.setPostCode(post.getPostCode());
        dto.setPostName(post.getPostName());
        return dto;
    }

    /**
     * 构造角色汇总对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param role role 输入值，参与 角色 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private AdminMerchantUserDetailDTO.RoleSummary toRoleSummary(SysRoleDO role) {
        AdminMerchantUserDetailDTO.RoleSummary dto = new AdminMerchantUserDetailDTO.RoleSummary();
        dto.setRoleId(role.getId());
        dto.setRoleCode(role.getRoleCode());
        dto.setRoleName(role.getRoleName());
        dto.setRoleType(role.getRoleType());
        dto.setStatus(role.getStatus());
        return dto;
    }

    /**
     * 整理商户app，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
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

    /**
     * 整理展示登录账号，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param account account 输入值，参与 账号 的查询、校验、转换、写入或日志摘要
     * @param merchantUser merchant User 输入值，参与 商户用户 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String displayLoginAccount(SysAccountDO account, SysMerchantUserDO merchantUser) {
        if (merchantUser != null && StringUtils.hasText(merchantUser.getLoginAccount())) {
            return merchantUser.getLoginAccount();
        }
        String raw = account.getLoginAccount();
        String suffix = "_" + account.getMerchantId();
        return raw != null && raw.endsWith(suffix) ? raw.substring(0, raw.length() - suffix.length()) : raw;
    }

    /**
     * 规范化intersect，返回当前业务步骤需要的业务值。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param base base 输入值，参与 基础 的查询、校验、转换、写入或日志摘要
     * @param next next 输入值，参与 next 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private Set<Long> intersect(Set<Long> base, Set<Long> next) {
        if (base == null) {
            return next;
        }
        base.retainAll(next);
        return base;
    }

    /**
     * 脱敏mobile，返回可安全写入日志或展示的摘要文本。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String maskMobile(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String text = value.trim();
        if (text.length() <= 4) {
            return "****";
        }
        return text.substring(0, Math.min(3, text.length())) + "****" + text.substring(text.length() - 4);
    }

    /**
     * 脱敏email，返回可安全写入日志或展示的摘要文本。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String maskEmail(String value) {
        if (!StringUtils.hasText(value) || !value.contains("@")) {
            return value;
        }
        String[] parts = value.split("@", 2);
        String name = parts[0];
        String maskedName = name.length() <= 2 ? name.charAt(0) + "***" : name.substring(0, 2) + "***";
        return maskedName + "@" + parts[1];
    }

    /**
     * 脱敏ip，返回可安全写入日志或展示的摘要文本。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String maskIp(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        int lastDot = value.lastIndexOf('.');
        if (lastDot <= 0) {
            return "***";
        }
        return value.substring(0, lastDot + 1) + "*";
    }
}
