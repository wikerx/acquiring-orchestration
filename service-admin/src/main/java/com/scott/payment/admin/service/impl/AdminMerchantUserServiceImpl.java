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
 * @description : AdminMerchantUserServiceImpl 服务实现，用于执行领域规则、数据读写编排和业务异常转换，位于 运营后台服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
public class AdminMerchantUserServiceImpl implements AdminMerchantUserService {

    /**
     * ROOT PARENT ID 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final long ROOT_PARENT_ID = 0L;

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
     * sys Merchant User Role Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final SysMerchantUserRoleMapper sysMerchantUserRoleMapper;
    /**
     * sys Role Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final SysRoleMapper sysRoleMapper;
    /**
     * sys Role Menu Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final SysRoleMenuMapper sysRoleMenuMapper;
    /**
     * sys Role Permission Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final SysRolePermissionMapper sysRolePermissionMapper;
    /**
     * sys Menu Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final SysMenuMapper sysMenuMapper;
    /**
     * sys Permission Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final SysPermissionMapper sysPermissionMapper;
    /**
     * sys Merchant Dept Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final SysMerchantDeptMapper sysMerchantDeptMapper;
    /**
     * sys Merchant Post Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final SysMerchantPostMapper sysMerchantPostMapper;
    /**
     * sys Merchant Account Dept Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；敏感或可识别字段，日志输出必须脱敏。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final SysMerchantAccountDeptMapper sysMerchantAccountDeptMapper;
    /**
     * sys Merchant Account Post Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；敏感或可识别字段，日志输出必须脱敏。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final SysMerchantAccountPostMapper sysMerchantAccountPostMapper;
    /**
     * base Merchant Info Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final BaseMerchantInfoMapper baseMerchantInfoMapper;

/**
 * 创建 AdminMerchantUserServiceImpl 实例并注入其运行所需依赖。
 * <p>
 * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminMerchantUserServiceImpl 的方法签名及调用链约束。
 * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
 * </p>
 * @param sysAppMapper sys App Mapper 输入值，含义由调用方法名称和所属业务对象限定
 * @param sysAccountMapper sys Account Mapper 输入值，含义由调用方法名称和所属业务对象限定
 * @param sysMerchantUserMapper sys Merchant User Mapper 输入值，含义由调用方法名称和所属业务对象限定
 * @param sysMerchantUserRoleMapper sys Merchant User Role Mapper 输入值，含义由调用方法名称和所属业务对象限定
 * @param sysRoleMapper sys Role Mapper 输入值，含义由调用方法名称和所属业务对象限定
 * @param sysRoleMenuMapper sys Role Menu Mapper 输入值，含义由调用方法名称和所属业务对象限定
 * @param sysRolePermissionMapper sys Role Permission Mapper 输入值，含义由调用方法名称和所属业务对象限定
 * @param sysMenuMapper sys Menu Mapper 输入值，含义由调用方法名称和所属业务对象限定
 * @param sysPermissionMapper sys Permission Mapper 输入值，含义由调用方法名称和所属业务对象限定
 * @param sysMerchantDeptMapper sys Merchant Dept Mapper 输入值，含义由调用方法名称和所属业务对象限定
 * @param sysMerchantPostMapper sys Merchant Post Mapper 输入值，含义由调用方法名称和所属业务对象限定
 * @param sysMerchantAccountDeptMapper sys Merchant Account Dept Mapper 输入值，含义由调用方法名称和所属业务对象限定
 * @param sysMerchantAccountPostMapper sys Merchant Account Post Mapper 输入值，含义由调用方法名称和所属业务对象限定
 * @param baseMerchantInfoMapper base Merchant Info Mapper 输入值，含义由调用方法名称和所属业务对象限定
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
 * 执行 to List DTO 服务能力，按当前领域规则完成校验、状态读取或数据写入。
 * <p>
 * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminMerchantUserServiceImpl 的方法签名及调用链约束。
 * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
 * </p>
 * @param account account 输入值，含义由调用方法名称和所属业务对象限定
 * @param merchantUser merchant User 输入值，含义由调用方法名称和所属业务对象限定
 * @param Map Map 输入值，含义由调用方法名称和所属业务对象限定
 * @param merchantMap merchant Map 输入值，含义由调用方法名称和所属业务对象限定
 * @param roleMap role Map 输入值，含义由调用方法名称和所属业务对象限定
 * @param deptMap dept Map 输入值，含义由调用方法名称和所属业务对象限定
 * @param postMap post Map 输入值，含义由调用方法名称和所属业务对象限定
 * @return 转换或构建后的目标对象
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
     * 执行 filter Merchant Ids 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminMerchantUserServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param query query 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
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
     * 执行 filter Account Ids 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminMerchantUserServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param appId app Id 输入值，含义由调用方法名称和所属业务对象限定
     * @param query query 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
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
     * 执行 account Ids By Role 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminMerchantUserServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param appId app Id 输入值，含义由调用方法名称和所属业务对象限定
     * @param roleIds role Ids 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
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
     * 执行 account Ids By Dept 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminMerchantUserServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param deptIds dept Ids 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
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
     * 执行 account Ids By Post 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminMerchantUserServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param postIds post Ids 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
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
     * 执行 load Merchant User Map 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminMerchantUserServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param accountIds account Ids 输入值，含义由调用方法名称和所属业务对象限定
     * @return 解析或查询得到的业务值
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
     * 执行 load Merchant Map 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminMerchantUserServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param merchantIds 商户号，用于限定数据归属、幂等范围和权限边界
     * @return 解析或查询得到的业务值
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
     * 执行 load Role Map 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminMerchantUserServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param appId app Id 输入值，含义由调用方法名称和所属业务对象限定
     * @param merchantUserIds merchant User Ids 输入值，含义由调用方法名称和所属业务对象限定
     * @return 解析或查询得到的业务值
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
     * 执行 load Account Roles 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminMerchantUserServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param appId app Id 输入值，含义由调用方法名称和所属业务对象限定
     * @param accountId account Id 输入值，含义由调用方法名称和所属业务对象限定
     * @return 解析或查询得到的业务值
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
     * 执行 load Roles 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminMerchantUserServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param roleIds role Ids 输入值，含义由调用方法名称和所属业务对象限定
     * @return 解析或查询得到的业务值
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
     * 执行 load Dept Map 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminMerchantUserServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param accountIds account Ids 输入值，含义由调用方法名称和所属业务对象限定
     * @return 解析或查询得到的业务值
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
     * 执行 load Post Map 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminMerchantUserServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param accountIds account Ids 输入值，含义由调用方法名称和所属业务对象限定
     * @return 解析或查询得到的业务值
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
     * 执行 load Dept Summaries 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminMerchantUserServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param merchantId 商户号，用于限定数据归属、幂等范围和权限边界
     * @param accountId account Id 输入值，含义由调用方法名称和所属业务对象限定
     * @return 解析或查询得到的业务值
     */
    private List<AdminMerchantUserDetailDTO.DeptSummary> loadDeptSummaries(String merchantId, Long accountId) {
        return loadDeptMap(List.of(accountId)).getOrDefault(accountId, Collections.emptyList()).stream()
                .filter(dept -> Objects.equals(dept.getMerchantId(), merchantId))
                .map(this::toDeptSummary).toList();
    }

    /**
     * 执行 load Post Summaries 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminMerchantUserServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param merchantId 商户号，用于限定数据归属、幂等范围和权限边界
     * @param accountId account Id 输入值，含义由调用方法名称和所属业务对象限定
     * @return 解析或查询得到的业务值
     */
    private List<AdminMerchantUserDetailDTO.PostSummary> loadPostSummaries(String merchantId, Long accountId) {
        return loadPostMap(List.of(accountId)).getOrDefault(accountId, Collections.emptyList()).stream()
                .filter(post -> Objects.equals(post.getMerchantId(), merchantId))
                .map(this::toPostSummary).toList();
    }

    /**
     * 执行 load Depts 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminMerchantUserServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param deptIds dept Ids 输入值，含义由调用方法名称和所属业务对象限定
     * @return 解析或查询得到的业务值
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
     * 执行 load Posts 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminMerchantUserServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param postIds post Ids 输入值，含义由调用方法名称和所属业务对象限定
     * @return 解析或查询得到的业务值
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
     * 执行 load Final Menus 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminMerchantUserServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param appId app Id 输入值，含义由调用方法名称和所属业务对象限定
     * @param roles roles 输入值，含义由调用方法名称和所属业务对象限定
     * @return 解析或查询得到的业务值
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
     * 执行 load Final Permission Codes 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminMerchantUserServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param appId app Id 输入值，含义由调用方法名称和所属业务对象限定
     * @param roles roles 输入值，含义由调用方法名称和所属业务对象限定
     * @return 解析或查询得到的业务值
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
     * 执行 build Menu Tree 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminMerchantUserServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param flat flat 输入值，含义由调用方法名称和所属业务对象限定
     * @return 转换或构建后的目标对象
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
     * 执行 to Menu DTO 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminMerchantUserServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param row row 输入值，含义由调用方法名称和所属业务对象限定
     * @return 转换或构建后的目标对象
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
     * 执行 to Merchant Summary 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminMerchantUserServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param merchant merchant 输入值，含义由调用方法名称和所属业务对象限定
     * @return 转换或构建后的目标对象
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
     * 执行 to Dept Summary 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminMerchantUserServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param dept dept 输入值，含义由调用方法名称和所属业务对象限定
     * @return 转换或构建后的目标对象
     */
    private AdminMerchantUserDetailDTO.DeptSummary toDeptSummary(SysMerchantDeptDO dept) {
        AdminMerchantUserDetailDTO.DeptSummary dto = new AdminMerchantUserDetailDTO.DeptSummary();
        dto.setDeptId(dept.getId());
        dto.setDeptCode(dept.getDeptCode());
        dto.setDeptName(dept.getDeptName());
        return dto;
    }

    /**
     * 执行 to Post Summary 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminMerchantUserServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param post post 输入值，含义由调用方法名称和所属业务对象限定
     * @return 转换或构建后的目标对象
     */
    private AdminMerchantUserDetailDTO.PostSummary toPostSummary(SysMerchantPostDO post) {
        AdminMerchantUserDetailDTO.PostSummary dto = new AdminMerchantUserDetailDTO.PostSummary();
        dto.setPostId(post.getId());
        dto.setPostCode(post.getPostCode());
        dto.setPostName(post.getPostName());
        return dto;
    }

    /**
     * 执行 to Role Summary 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminMerchantUserServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param role role 输入值，含义由调用方法名称和所属业务对象限定
     * @return 转换或构建后的目标对象
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
     * 执行 merchant App 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminMerchantUserServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @return 方法签名声明的返回值，具体结构由返回类型定义
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
     * 执行 display Login Account 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminMerchantUserServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param account account 输入值，含义由调用方法名称和所属业务对象限定
     * @param merchantUser merchant User 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
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
     * 执行 intersect 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminMerchantUserServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param base base 输入值，含义由调用方法名称和所属业务对象限定
     * @param next next 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
     */
    private Set<Long> intersect(Set<Long> base, Set<Long> next) {
        if (base == null) {
            return next;
        }
        base.retainAll(next);
        return base;
    }

    /**
     * 执行 mask Mobile 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminMerchantUserServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param value 待校验或转换的原始值
     * @return 方法签名声明的返回值，具体结构由返回类型定义
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
     * 执行 mask Email 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminMerchantUserServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param value 待校验或转换的原始值
     * @return 方法签名声明的返回值，具体结构由返回类型定义
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
     * 执行 mask Ip 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminMerchantUserServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param value 待校验或转换的原始值
     * @return 方法签名声明的返回值，具体结构由返回类型定义
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
