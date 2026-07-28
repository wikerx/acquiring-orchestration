package com.scott.payment.admin.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scott.payment.admin.dto.SysMenuDTO;
import com.scott.payment.admin.dto.SysRoleCreateRequest;
import com.scott.payment.admin.dto.SysRoleDTO;
import com.scott.payment.admin.dto.SysRoleMenuAuthDTO;
import com.scott.payment.admin.dto.SysRoleMenuGrantRequest;
import com.scott.payment.admin.dto.SysRolePermissionAuthDTO;
import com.scott.payment.admin.dto.SysPermissionDTO;
import com.scott.payment.admin.dto.SysRolePermissionGrantRequest;
import com.scott.payment.admin.dto.SysRoleQueryRequest;
import com.scott.payment.admin.dto.SysRoleStatusRequest;
import com.scott.payment.admin.dto.SysRoleUpdateRequest;
import com.scott.payment.admin.service.AdminRoleService;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.db.auth.constant.AuthConstants;
import com.scott.payment.component.db.auth.entity.SysAccountRoleDO;
import com.scott.payment.component.db.auth.entity.SysAppDO;
import com.scott.payment.component.db.auth.entity.SysMenuDO;
import com.scott.payment.component.db.auth.entity.SysRoleDO;
import com.scott.payment.component.db.auth.entity.SysPermissionDO;
import com.scott.payment.component.db.auth.entity.SysRoleMenuDO;
import com.scott.payment.component.db.auth.entity.SysRolePermissionDO;
import com.scott.payment.component.db.auth.mapper.SysAccountRoleMapper;
import com.scott.payment.component.db.auth.mapper.SysPermissionMapper;
import com.scott.payment.component.db.auth.mapper.SysAppMapper;
import com.scott.payment.component.db.auth.mapper.SysMenuMapper;
import com.scott.payment.component.db.auth.mapper.SysRoleMapper;
import com.scott.payment.component.db.auth.mapper.SysRoleMenuMapper;
import com.scott.payment.component.db.auth.mapper.SysRolePermissionMapper;
import com.scott.payment.component.db.constant.DataSourceName;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminRoleServiceImpl
 * @date : 2026-06-07 00:00
 * @email : scott_x@163.com
 * @description : 管理后台角色领域服务实现
 * @status : create
 *
 * <p>负责后台角色维护、状态切换、菜单授权和权限授权等核心领域规则，
 * 不承担控制器协议适配和页面交互逻辑。</p>
 */
@Service
public class AdminRoleServiceImpl implements AdminRoleService {

    /**
     * NOT DELETED，用于保存 Admin Role Service Impl 中与 notdeleted 相关的业务属性。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final long NOT_DELETED = 0L;
    /**
     * ROLE TYPE CUSTOM，用于区分 Admin Role Service Impl 记录的处理类别、配置维度或外部协议枚举。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；不允许为空；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String ROLE_TYPE_CUSTOM = "CUSTOM";
    /**
     * ROLE TYPE SYSTEM，用于区分 Admin Role Service Impl 记录的处理类别、配置维度或外部协议枚举。
     * <p>
     * 单位：无；格式：枚举编码或受控字符串；不允许为空；非敏感字段。
     * 取值范围：取值必须来自对应枚举、字典或渠道协议；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String ROLE_TYPE_SYSTEM = "SYSTEM";
    /**
     * DATA SCOPE ALL，用于保存 Admin Role Service Impl 中与 datascopeall 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String DATA_SCOPE_ALL = "ALL";
    /**
     * DATA SCOPE SELF，用于保存 Admin Role Service Impl 中与 datascopeself 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String DATA_SCOPE_SELF = "SELF";
    /**
     * DATA SCOPE CUSTOM，用于保存 Admin Role Service Impl 中与 datascopecustom 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String DATA_SCOPE_CUSTOM = "CUSTOM";

    /**
     * sys App Mapper 依赖，用于 Admin Role Service Impl 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final SysAppMapper sysAppMapper;
    /**
     * sys Role Mapper 依赖，用于 Admin Role Service Impl 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final SysRoleMapper sysRoleMapper;
    /**
     * sys Role Menu Mapper 依赖，用于 Admin Role Service Impl 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final SysRoleMenuMapper sysRoleMenuMapper;
    /**
     * sys Role Permission Mapper 依赖，用于 Admin Role Service Impl 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final SysRolePermissionMapper sysRolePermissionMapper;
    /**
     * sys Account Role Mapper，表示当前统计、分页、扫描或重试场景中的数量。
     * <p>
     * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final SysAccountRoleMapper sysAccountRoleMapper;
    /**
     * sys Menu Mapper 依赖，用于 Admin Role Service Impl 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final SysMenuMapper sysMenuMapper;
    /**
     * sys Permission Mapper 依赖，用于 Admin Role Service Impl 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final SysPermissionMapper sysPermissionMapper;

    /**
     * 创建后台角色服务实现。
     *
     * @param sysAppMapper             应用 Mapper
     * @param sysRoleMapper            角色 Mapper
     * @param sysRoleMenuMapper        角色菜单 Mapper
     * @param sysRolePermissionMapper  角色权限 Mapper
     * @param sysAccountRoleMapper     账号角色 Mapper
     * @param sysMenuMapper            菜单 Mapper
     * @param sysPermissionMapper      权限 Mapper
     */
    public AdminRoleServiceImpl(SysAppMapper sysAppMapper,
                                SysRoleMapper sysRoleMapper,
                                SysRoleMenuMapper sysRoleMenuMapper,
                                SysRolePermissionMapper sysRolePermissionMapper,
                                SysAccountRoleMapper sysAccountRoleMapper,
                                SysMenuMapper sysMenuMapper,
                                SysPermissionMapper sysPermissionMapper) {
        this.sysAppMapper = sysAppMapper;
        this.sysRoleMapper = sysRoleMapper;
        this.sysRoleMenuMapper = sysRoleMenuMapper;
        this.sysRolePermissionMapper = sysRolePermissionMapper;
        this.sysAccountRoleMapper = sysAccountRoleMapper;
        this.sysMenuMapper = sysMenuMapper;
        this.sysPermissionMapper = sysPermissionMapper;
    }

    /**
     * 分页查询后台角色，并聚合菜单与权限数量用于列表展示。
     *
     * @param request 查询条件
     * @return 角色分页结果
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public PageResult<SysRoleDTO> pageRoles(SysRoleQueryRequest request) {
        SysRoleQueryRequest query = request == null ? new SysRoleQueryRequest() : request;
        SysAppDO app = getAdminApp();
        Page<SysRoleDO> page = sysRoleMapper.selectPage(
                new Page<>(query.safePageNo(), query.safePageSize()),
                Wrappers.<SysRoleDO>lambdaQuery()
                        .eq(SysRoleDO::getAppId, app.getId())
                        .eq(SysRoleDO::getDeleted, NOT_DELETED)
                        .likeRight(StringUtils.hasText(query.getRoleCode()), SysRoleDO::getRoleCode, query.getRoleCode())
                        .like(StringUtils.hasText(query.getRoleName()), SysRoleDO::getRoleName, query.getRoleName())
                        .eq(query.getStatus() != null, SysRoleDO::getStatus, query.getStatus())
                        .orderByAsc(SysRoleDO::getSortNo)
                        .orderByAsc(SysRoleDO::getId)
        );
        Map<Long, Long> menuCountMap = countRoleMenus(page);
        Map<Long, Long> permissionCountMap = countRolePermissions(page);
        return PageResult.of(
                page.getTotal(),
                page.getCurrent(),
                page.getSize(),
                page.getRecords().stream()
                        .map(role -> toDTO(role, menuCountMap, permissionCountMap))
                        .toList()
        );
    }

    /**
     * 新增后台角色。
     *
     * @param request 新增请求
     * @return 角色详情
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public SysRoleDTO createRole(SysRoleCreateRequest request) {
        SysAppDO app = getAdminApp();
        assertRoleCodeNotExists(app.getId(), request.getRoleCode());
        LocalDateTime now = LocalDateTime.now();
        SysRoleDO role = new SysRoleDO();
        role.setAppId(app.getId());
        role.setRoleCode(normalize(request.getRoleCode()));
        role.setRoleName(normalize(request.getRoleName()));
        role.setRoleType(ROLE_TYPE_CUSTOM);
        role.setDataScope(resolveDataScope(request.getDataScope()));
        role.setDescription(normalize(request.getDescription()));
        role.setStatus(AuthConstants.ENABLED);
        role.setSortNo(request.getSortNo() == null ? 100 : request.getSortNo());
        role.setCreatedAt(now);
        role.setUpdatedAt(now);
        role.setDeleted(NOT_DELETED);
        sysRoleMapper.insert(role);
        return toDTO(role, Collections.emptyMap(), Collections.emptyMap());
    }

    /**
     * 更新后台角色。
     *
     * @param request 更新请求
     * @return 角色详情
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public SysRoleDTO updateRole(SysRoleUpdateRequest request) {
        SysAppDO app = getAdminApp();
        SysRoleDO role = getRole(app.getId(), request.getRoleId());
        LocalDateTime now = LocalDateTime.now();
        if (request.getRoleName() != null) {
            role.setRoleName(normalize(request.getRoleName()));
        }
        if (request.getDataScope() != null) {
            role.setDataScope(resolveDataScope(request.getDataScope()));
        }
        if (request.getDescription() != null) {
            role.setDescription(normalize(request.getDescription()));
        }
        if (request.getStatus() != null) {
            role.setStatus(validStatus(request.getStatus()));
        }
        if (request.getSortNo() != null) {
            role.setSortNo(request.getSortNo());
        }
        role.setUpdatedAt(now);
        sysRoleMapper.updateById(role);
        return toDTO(role, countRoleMenus(role.getId()), countRolePermissions(role.getId()));
    }

    /**
     * 更新后台角色状态。
     *
     * @param request 状态请求
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(SysRoleStatusRequest request) {
        SysAppDO app = getAdminApp();
        SysRoleDO role = getRole(app.getId(), request.getRoleId());
        role.setStatus(validStatus(request.getStatus()));
        role.setUpdatedAt(LocalDateTime.now());
        sysRoleMapper.updateById(role);
    }

    /**
     * 删除后台角色，并同步逻辑删除菜单授权与权限授权关系。
     *
     * @param roleId 角色主键
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(Long roleId) {
        SysAppDO app = getAdminApp();
        SysRoleDO role = getRole(app.getId(), roleId);
        if (ROLE_TYPE_SYSTEM.equals(role.getRoleType())) {
            throw new ServiceException(ApiResultEnum.BAD_REQUEST.getCode(), "system role can not be deleted");
        }
        Long accountCount = sysAccountRoleMapper.selectCount(
                Wrappers.<SysAccountRoleDO>lambdaQuery()
                        .eq(SysAccountRoleDO::getAppId, app.getId())
                        .eq(SysAccountRoleDO::getRoleId, role.getId())
                        .eq(SysAccountRoleDO::getDeleted, NOT_DELETED)
        );
        if (accountCount != null && accountCount > 0) {
            throw new ServiceException(ApiResultEnum.BAD_REQUEST.getCode(), "role is assigned to accounts");
        }
        LocalDateTime now = LocalDateTime.now();
        role.setStatus(AuthConstants.DISABLED);
        role.setDeleted(role.getId());
        role.setUpdatedAt(now);
        sysRoleMapper.updateById(role);
        softDeleteRoleMenus(app.getId(), role.getId());
        softDeleteRolePermissions(app.getId(), role.getId());
    }

    /**
     * 查询角色菜单授权树。
     *
     * @param roleId 角色主键
     * @return 菜单授权信息
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public SysRoleMenuAuthDTO roleMenus(Long roleId) {
        SysAppDO app = getAdminApp();
        SysRoleDO role = getRole(app.getId(), roleId);
        SysRoleMenuAuthDTO dto = new SysRoleMenuAuthDTO();
        dto.setRoleId(role.getId());
        dto.setRoleCode(role.getRoleCode());
        dto.setRoleName(role.getRoleName());
        dto.setMenus(buildMenuTree(loadGrantableMenus(app.getId())));
        dto.setCheckedMenuIds(sysRoleMenuMapper.selectList(
                Wrappers.<SysRoleMenuDO>lambdaQuery()
                        .eq(SysRoleMenuDO::getAppId, app.getId())
                        .eq(SysRoleMenuDO::getRoleId, role.getId())
                        .eq(SysRoleMenuDO::getDeleted, NOT_DELETED)
        ).stream().map(SysRoleMenuDO::getMenuId).toList());
        return dto;
    }

    /**
     * 保存角色菜单授权。
     *
     * @param request 菜单授权请求
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public void grantMenus(SysRoleMenuGrantRequest request) {
        SysAppDO app = getAdminApp();
        SysRoleDO role = getRole(app.getId(), request.getRoleId());
        Set<Long> menuIds = normalizeIds(request.getMenuIds());
        validateMenuIds(app.getId(), menuIds);
        softDeleteRoleMenus(app.getId(), role.getId());
        LocalDateTime now = LocalDateTime.now();
        menuIds.forEach(menuId -> {
            SysRoleMenuDO relation = new SysRoleMenuDO();
            relation.setAppId(app.getId());
            relation.setRoleId(role.getId());
            relation.setMenuId(menuId);
            relation.setCreatedAt(now);
            relation.setDeleted(NOT_DELETED);
            sysRoleMenuMapper.insert(relation);
        });
    }

    /**
     * 查询角色权限授权。
     *
     * @param roleId 角色主键
     * @return 权限授权信息
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public SysRolePermissionAuthDTO rolePermissions(Long roleId) {
        SysAppDO app = getAdminApp();
        SysRolePermissionAuthDTO dto = new SysRolePermissionAuthDTO();
        List<SysPermissionDO> allPermissions = sysPermissionMapper.selectList(
                Wrappers.<SysPermissionDO>lambdaQuery()
                        .eq(SysPermissionDO::getAppId, app.getId())
                        .eq(SysPermissionDO::getDeleted, NOT_DELETED)
                        .eq(SysPermissionDO::getStatus, AuthConstants.ENABLED)
                        .gt(SysPermissionDO::getMenuId, 0L)
                        .ne(SysPermissionDO::getPermissionCode, "*:*:*")
                        .orderByAsc(SysPermissionDO::getId)
        );
        List<SysPermissionDTO> permissionDTOs = allPermissions.stream()
                .map(this::toPermissionDTO)
                .toList();
        if (roleId == null || roleId <= 0) {
            dto.setRoleId(0L);
            dto.setPermissions(permissionDTOs);
            dto.setCheckedPermissionIds(List.of());
            return dto;
        }
        SysRoleDO role = getRole(app.getId(), roleId);
        dto.setRoleId(role.getId());
        dto.setRoleCode(role.getRoleCode());
        dto.setRoleName(role.getRoleName());
        dto.setPermissions(permissionDTOs);
        List<Long> checkedIds = sysRolePermissionMapper.selectList(
                Wrappers.<SysRolePermissionDO>lambdaQuery()
                        .eq(SysRolePermissionDO::getAppId, app.getId())
                        .eq(SysRolePermissionDO::getRoleId, role.getId())
                        .eq(SysRolePermissionDO::getDeleted, NOT_DELETED)
        ).stream().map(SysRolePermissionDO::getPermissionId).toList();
        dto.setCheckedPermissionIds(checkedIds);
        return dto;
    }

    /**
     * 保存角色权限授权。
     *
     * @param request 权限授权请求
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public void grantPermissions(SysRolePermissionGrantRequest request) {
        SysAppDO app = getAdminApp();
        SysRoleDO role = getRole(app.getId(), request.getRoleId());
        Set<Long> permissionIds = normalizeIds(request.getPermissionIds());
        validatePermissionIds(app.getId(), permissionIds);
        softDeleteRolePermissions(app.getId(), role.getId());
        if (permissionIds.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        permissionIds.forEach(permissionId -> {
            SysRolePermissionDO relation = new SysRolePermissionDO();
            relation.setAppId(app.getId());
            relation.setRoleId(role.getId());
            relation.setPermissionId(permissionId);
            relation.setCreatedAt(now);
            relation.setDeleted(NOT_DELETED);
            sysRolePermissionMapper.insert(relation);
        });
    }

    /**
     * 查询 admin 应用实体，保证角色数据作用域固定在管理后台。
     *
     * @return admin 应用实体
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
     * 校验角色编码唯一性，避免同一应用下出现重复角色标识。
     *
     * @param appId    应用主键
     * @param roleCode 角色编码
     */
    private void assertRoleCodeNotExists(Long appId, String roleCode) {
        Long count = sysRoleMapper.selectCount(
                Wrappers.<SysRoleDO>lambdaQuery()
                        .eq(SysRoleDO::getAppId, appId)
                        .eq(SysRoleDO::getRoleCode, normalize(roleCode))
                        .eq(SysRoleDO::getDeleted, NOT_DELETED)
        );
        if (count != null && count > 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "role code already exists");
        }
    }

    /**
     * 查询指定角色，并限制为当前 admin 应用下的有效角色。
     *
     * @param appId  应用主键
     * @param roleId 角色主键
     * @return 角色实体
     */
    private SysRoleDO getRole(Long appId, Long roleId) {
        SysRoleDO role = sysRoleMapper.selectOne(
                Wrappers.<SysRoleDO>lambdaQuery()
                        .eq(SysRoleDO::getAppId, appId)
                        .eq(SysRoleDO::getId, roleId)
                        .eq(SysRoleDO::getDeleted, NOT_DELETED)
                        .last("LIMIT 1")
        );
        if (role == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "role not found");
        }
        return role;
    }

    /**
     * 统计角色菜单，返回分页、扫描或报表汇总所需的数量结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param roleId role ID 输入值，参与 角色ID 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private Map<Long, Long> countRoleMenus(Long roleId) {
        return Map.of(roleId, sysRoleMenuMapper.selectCount(
                Wrappers.<SysRoleMenuDO>lambdaQuery()
                        .eq(SysRoleMenuDO::getRoleId, roleId)
                        .eq(SysRoleMenuDO::getDeleted, NOT_DELETED)
        ));
    }

    /**
     * 统计角色权限，返回分页、扫描或报表汇总所需的数量结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param roleId role ID 输入值，参与 角色ID 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private Map<Long, Long> countRolePermissions(Long roleId) {
        List<Long> menuIds = sysRoleMenuMapper.selectList(
                        Wrappers.<SysRoleMenuDO>lambdaQuery()
                                .eq(SysRoleMenuDO::getRoleId, roleId)
                                .eq(SysRoleMenuDO::getDeleted, NOT_DELETED)
                ).stream()
                .map(SysRoleMenuDO::getMenuId)
                .toList();
        if (menuIds.isEmpty()) {
            return Map.of(roleId, 0L);
        }
        Long count = sysMenuMapper.selectCount(
                Wrappers.<SysMenuDO>lambdaQuery()
                        .in(SysMenuDO::getId, menuIds)
                        .eq(SysMenuDO::getDeleted, NOT_DELETED)
                        .isNotNull(SysMenuDO::getPermissionCode)
        );
        return Map.of(roleId, count == null ? 0L : count);
    }

    /**
     * 整理有效状态，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param status 状态编码，取值必须来自对应枚举、字典或渠道协议
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private Integer validStatus(Integer status) {
        if (status == AuthConstants.ENABLED || status == AuthConstants.DISABLED) {
            return status;
        }
        throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "status is invalid");
    }

    /**
     * 解析resolvedatascope，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 运营后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param dataScope data Scope 输入值，参与 datascope 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private String resolveDataScope(String dataScope) {
        if (!StringUtils.hasText(dataScope)) {
            return DATA_SCOPE_SELF;
        }
        String normalized = dataScope.trim().toUpperCase();
        if (DATA_SCOPE_ALL.equals(normalized) || DATA_SCOPE_SELF.equals(normalized) || DATA_SCOPE_CUSTOM.equals(normalized)) {
            return normalized;
        }
        throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "data scope is invalid");
    }

    /**
     * 更新softdelete角色菜单，保持业务状态、配置项或展示字段与请求意图一致。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param appId app ID 输入值，参与 appID 的查询、校验、转换、写入或日志摘要
     * @param roleId role ID 输入值，参与 角色ID 的查询、校验、转换、写入或日志摘要
     */
    private void softDeleteRoleMenus(Long appId, Long roleId) {
        sysRoleMenuMapper.selectList(
                Wrappers.<SysRoleMenuDO>lambdaQuery()
                        .eq(SysRoleMenuDO::getAppId, appId)
                        .eq(SysRoleMenuDO::getRoleId, roleId)
                        .eq(SysRoleMenuDO::getDeleted, NOT_DELETED)
        ).forEach(relation -> {
            relation.setDeleted(relation.getId());
            sysRoleMenuMapper.updateById(relation);
        });
    }

    /**
     * 更新softdelete角色权限，保持业务状态、配置项或展示字段与请求意图一致。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param appId app ID 输入值，参与 appID 的查询、校验、转换、写入或日志摘要
     * @param roleId role ID 输入值，参与 角色ID 的查询、校验、转换、写入或日志摘要
     */
    private void softDeleteRolePermissions(Long appId, Long roleId) {
        sysRolePermissionMapper.selectList(
                Wrappers.<SysRolePermissionDO>lambdaQuery()
                        .eq(SysRolePermissionDO::getAppId, appId)
                        .eq(SysRolePermissionDO::getRoleId, roleId)
                        .eq(SysRolePermissionDO::getDeleted, NOT_DELETED)
        ).forEach(relation -> {
            relation.setDeleted(relation.getId());
            sysRolePermissionMapper.updateById(relation);
        });
    }

    /**
     * 解析normalize，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 运营后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 构造、转换或解析后的业务值
     */
    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    /**
     * 统计角色菜单，返回分页、扫描或报表汇总所需的数量结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param page page 输入值，参与 page 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private Map<Long, Long> countRoleMenus(Page<SysRoleDO> page) {
        if (page.getRecords().isEmpty()) {
            return Collections.emptyMap();
        }
        return sysRoleMenuMapper.selectList(
                Wrappers.<SysRoleMenuDO>lambdaQuery()
                        .in(SysRoleMenuDO::getRoleId, page.getRecords().stream().map(SysRoleDO::getId).toList())
                        .eq(SysRoleMenuDO::getDeleted, NOT_DELETED)
        ).stream().collect(Collectors.groupingBy(SysRoleMenuDO::getRoleId, Collectors.counting()));
    }

    /**
     * 统计角色权限，返回分页、扫描或报表汇总所需的数量结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param page page 输入值，参与 page 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private Map<Long, Long> countRolePermissions(Page<SysRoleDO> page) {
        if (page.getRecords().isEmpty()) {
            return Collections.emptyMap();
        }
        List<SysRoleMenuDO> roleMenus = sysRoleMenuMapper.selectList(
                Wrappers.<SysRoleMenuDO>lambdaQuery()
                        .in(SysRoleMenuDO::getRoleId, page.getRecords().stream().map(SysRoleDO::getId).toList())
                        .eq(SysRoleMenuDO::getDeleted, NOT_DELETED)
        );
        Set<Long> menuIds = roleMenus.stream()
                .map(SysRoleMenuDO::getMenuId)
                .collect(Collectors.toSet());
        if (menuIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<Long> permissionMenuIds = sysMenuMapper.selectList(
                        Wrappers.<SysMenuDO>lambdaQuery()
                                .in(SysMenuDO::getId, menuIds)
                                .eq(SysMenuDO::getDeleted, NOT_DELETED)
                                .isNotNull(SysMenuDO::getPermissionCode)
                ).stream()
                .map(SysMenuDO::getId)
                .collect(Collectors.toSet());
        return roleMenus.stream()
                .filter(item -> permissionMenuIds.contains(item.getMenuId()))
                .collect(Collectors.groupingBy(SysRoleMenuDO::getRoleId, Collectors.counting()));
    }

    /**
     * 构造dto对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param role role 输入值，参与 角色 的查询、校验、转换、写入或日志摘要
     * @param Map Map 输入值，参与 map 的查询、校验、转换、写入或日志摘要
     * @param menuCountMap menu Count Map 输入值，参与 菜单countmap 的查询、校验、转换、写入或日志摘要
     * @param permissionCountMap permission Count Map 输入值，参与 权限countmap 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private SysRoleDTO toDTO(SysRoleDO role, Map<Long, Long> menuCountMap, Map<Long, Long> permissionCountMap) {
        SysRoleDTO dto = new SysRoleDTO();
        dto.setRoleId(role.getId());
        dto.setRoleCode(role.getRoleCode());
        dto.setRoleName(role.getRoleName());
        dto.setRoleType(role.getRoleType());
        dto.setDataScope(role.getDataScope());
        dto.setDescription(role.getDescription());
        dto.setStatus(role.getStatus());
        dto.setSortNo(role.getSortNo());
        dto.setMenuCount(menuCountMap.getOrDefault(role.getId(), 0L));
        dto.setPermissionCount(permissionCountMap.getOrDefault(role.getId(), 0L));
        dto.setCreatedAt(role.getCreatedAt());
        dto.setUpdatedAt(role.getUpdatedAt());
        return dto;
    }

    /**
     * 查询grantable菜单，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param appId app ID 输入值，参与 appID 的查询、校验、转换、写入或日志摘要
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private List<SysMenuDO> loadGrantableMenus(Long appId) {
        return sysMenuMapper.selectList(
                Wrappers.<SysMenuDO>lambdaQuery()
                        .eq(SysMenuDO::getAppId, appId)
                        .eq(SysMenuDO::getDeleted, NOT_DELETED)
                        .eq(SysMenuDO::getStatus, AuthConstants.ENABLED)
                        .orderByAsc(SysMenuDO::getSortNo)
                        .orderByAsc(SysMenuDO::getId)
        );
    }

    /**
     * 构造菜单树对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param menus menus 输入值，参与 菜单 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private List<SysMenuDTO> buildMenuTree(List<SysMenuDO> menus) {
        Map<Long, SysMenuDTO> menuMap = new LinkedHashMap<>();
        menus.forEach(menu -> menuMap.put(menu.getId(), toMenuDTO(menu)));
        List<SysMenuDTO> roots = new ArrayList<>();
        menuMap.values().forEach(menu -> {
            SysMenuDTO parent = menuMap.get(menu.getParentId());
            if (parent == null || menu.getParentId() == null || menu.getParentId() == 0L) {
                roots.add(menu);
                return;
            }
            parent.getChildren().add(menu);
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
     * @param menu menu 输入值，参与 菜单 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private SysMenuDTO toMenuDTO(SysMenuDO menu) {
        SysMenuDTO dto = new SysMenuDTO();
        dto.setMenuId(menu.getId());
        dto.setParentId(menu.getParentId());
        dto.setMenuCode(menu.getMenuCode());
        dto.setMenuName(menu.getMenuName());
        dto.setMenuType(menu.getMenuType());
        dto.setRoutePath(menu.getRoutePath());
        dto.setComponentPath(menu.getComponentPath());
        dto.setPermissionCode(menu.getPermissionCode());
        dto.setIcon(menu.getIcon());
        dto.setRedirect(menu.getRedirect());
        dto.setVisible(menu.getVisible());
        dto.setKeepAlive(menu.getKeepAlive());
        dto.setExternalLink(menu.getExternalLink());
        dto.setSortNo(menu.getSortNo());
        dto.setStatus(menu.getStatus());
        return dto;
    }

    /**
     * 解析normalizeID，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 运营后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param ids 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @return 构造、转换或解析后的业务值
     */
    private Set<Long> normalizeIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptySet();
        }
        return ids.stream().filter(id -> id != null && id > 0).collect(Collectors.toSet());
    }

    /**
     * 校验菜单ID输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 运营后台服务 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param appId app ID 输入值，参与 appID 的查询、校验、转换、写入或日志摘要
     * @param menuIds menu Ids 输入值，参与 菜单ID 的查询、校验、转换、写入或日志摘要
     */
    private void validateMenuIds(Long appId, Set<Long> menuIds) {
        if (menuIds.isEmpty()) {
            return;
        }
        Long count = sysMenuMapper.selectCount(
                Wrappers.<SysMenuDO>lambdaQuery()
                        .eq(SysMenuDO::getAppId, appId)
                        .eq(SysMenuDO::getDeleted, NOT_DELETED)
                        .eq(SysMenuDO::getStatus, AuthConstants.ENABLED)
                        .in(SysMenuDO::getId, menuIds)
        );
        if (count == null || count != menuIds.size()) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "menu ids are invalid");
        }
    }

    /**
     * 校验权限ID输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 运营后台服务 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param appId app ID 输入值，参与 appID 的查询、校验、转换、写入或日志摘要
     * @param permissionIds permission Ids 输入值，参与 权限ID 的查询、校验、转换、写入或日志摘要
     */
    private void validatePermissionIds(Long appId, Set<Long> permissionIds) {
        if (permissionIds.isEmpty()) {
            return;
        }
        Long count = sysPermissionMapper.selectCount(
                Wrappers.<SysPermissionDO>lambdaQuery()
                        .eq(SysPermissionDO::getAppId, appId)
                        .eq(SysPermissionDO::getDeleted, NOT_DELETED)
                        .eq(SysPermissionDO::getStatus, AuthConstants.ENABLED)
                        .in(SysPermissionDO::getId, permissionIds)
        );
        if (count == null || count != permissionIds.size()) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "permission ids are invalid");
        }
    }

    /**
     * 构造权限dto对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param permission permission 输入值，参与 权限 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private SysPermissionDTO toPermissionDTO(SysPermissionDO permission) {
        SysPermissionDTO dto = new SysPermissionDTO();
        dto.setPermissionId(permission.getId());
        dto.setMenuId(permission.getMenuId());
        dto.setPermissionCode(permission.getPermissionCode());
        dto.setPermissionName(permission.getPermissionName());
        dto.setPermissionType(permission.getPermissionType());
        dto.setResourceMethod(permission.getResourceMethod());
        dto.setResourcePath(permission.getResourcePath());
        dto.setStatus(permission.getStatus());
        return dto;
    }

}
