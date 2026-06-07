package com.scott.payment.admin.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scott.payment.admin.dto.SysMenuDTO;
import com.scott.payment.admin.dto.SysPermissionDTO;
import com.scott.payment.admin.dto.SysRoleCreateRequest;
import com.scott.payment.admin.dto.SysRoleDTO;
import com.scott.payment.admin.dto.SysRoleMenuAuthDTO;
import com.scott.payment.admin.dto.SysRoleMenuGrantRequest;
import com.scott.payment.admin.dto.SysRolePermissionAuthDTO;
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
import com.scott.payment.component.db.auth.entity.SysPermissionDO;
import com.scott.payment.component.db.auth.entity.SysRoleDO;
import com.scott.payment.component.db.auth.entity.SysRoleMenuDO;
import com.scott.payment.component.db.auth.entity.SysRolePermissionDO;
import com.scott.payment.component.db.auth.mapper.SysAccountRoleMapper;
import com.scott.payment.component.db.auth.mapper.SysAppMapper;
import com.scott.payment.component.db.auth.mapper.SysMenuMapper;
import com.scott.payment.component.db.auth.mapper.SysPermissionMapper;
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
 * @description : 管理后台角色服务实现
 * @status : create
 */
@Service
public class AdminRoleServiceImpl implements AdminRoleService {

    private static final long NOT_DELETED = 0L;
    private static final String ROLE_TYPE_CUSTOM = "CUSTOM";
    private static final String ROLE_TYPE_SYSTEM = "SYSTEM";
    private static final String DATA_SCOPE_ALL = "ALL";
    private static final String DATA_SCOPE_SELF = "SELF";
    private static final String DATA_SCOPE_CUSTOM = "CUSTOM";

    private final SysAppMapper sysAppMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysRoleMenuMapper sysRoleMenuMapper;
    private final SysRolePermissionMapper sysRolePermissionMapper;
    private final SysAccountRoleMapper sysAccountRoleMapper;
    private final SysMenuMapper sysMenuMapper;
    private final SysPermissionMapper sysPermissionMapper;

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

    @Override
    @DS(DataSourceName.SLAVE)
    public SysRolePermissionAuthDTO rolePermissions(Long roleId) {
        SysAppDO app = getAdminApp();
        SysRoleDO role = getRole(app.getId(), roleId);
        SysRolePermissionAuthDTO dto = new SysRolePermissionAuthDTO();
        dto.setRoleId(role.getId());
        dto.setRoleCode(role.getRoleCode());
        dto.setRoleName(role.getRoleName());
        dto.setPermissions(loadGrantablePermissions(app.getId()).stream().map(this::toPermissionDTO).toList());
        dto.setCheckedPermissionIds(sysRolePermissionMapper.selectList(
                Wrappers.<SysRolePermissionDO>lambdaQuery()
                        .eq(SysRolePermissionDO::getAppId, app.getId())
                        .eq(SysRolePermissionDO::getRoleId, role.getId())
                        .eq(SysRolePermissionDO::getDeleted, NOT_DELETED)
        ).stream().map(SysRolePermissionDO::getPermissionId).toList());
        return dto;
    }

    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public void grantPermissions(SysRolePermissionGrantRequest request) {
        SysAppDO app = getAdminApp();
        SysRoleDO role = getRole(app.getId(), request.getRoleId());
        Set<Long> permissionIds = normalizeIds(request.getPermissionIds());
        validatePermissionIds(app.getId(), permissionIds);
        softDeleteRolePermissions(app.getId(), role.getId());
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

    private Map<Long, Long> countRoleMenus(Long roleId) {
        return Map.of(roleId, sysRoleMenuMapper.selectCount(
                Wrappers.<SysRoleMenuDO>lambdaQuery()
                        .eq(SysRoleMenuDO::getRoleId, roleId)
                        .eq(SysRoleMenuDO::getDeleted, NOT_DELETED)
        ));
    }

    private Map<Long, Long> countRolePermissions(Long roleId) {
        return Map.of(roleId, sysRolePermissionMapper.selectCount(
                Wrappers.<SysRolePermissionDO>lambdaQuery()
                        .eq(SysRolePermissionDO::getRoleId, roleId)
                        .eq(SysRolePermissionDO::getDeleted, NOT_DELETED)
        ));
    }

    private Integer validStatus(Integer status) {
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
        throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "data scope is invalid");
    }

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

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

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

    private Map<Long, Long> countRolePermissions(Page<SysRoleDO> page) {
        if (page.getRecords().isEmpty()) {
            return Collections.emptyMap();
        }
        return sysRolePermissionMapper.selectList(
                Wrappers.<SysRolePermissionDO>lambdaQuery()
                        .in(SysRolePermissionDO::getRoleId, page.getRecords().stream().map(SysRoleDO::getId).toList())
                        .eq(SysRolePermissionDO::getDeleted, NOT_DELETED)
        ).stream().collect(Collectors.groupingBy(SysRolePermissionDO::getRoleId, Collectors.counting()));
    }

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

    private List<SysMenuDO> loadGrantableMenus(Long appId) {
        return sysMenuMapper.selectList(
                Wrappers.<SysMenuDO>lambdaQuery()
                        .eq(SysMenuDO::getAppId, appId)
                        .eq(SysMenuDO::getDeleted, NOT_DELETED)
                        .eq(SysMenuDO::getStatus, AuthConstants.ENABLED)
                        .eq(SysMenuDO::getVisible, AuthConstants.ENABLED)
                        .orderByAsc(SysMenuDO::getSortNo)
                        .orderByAsc(SysMenuDO::getId)
        );
    }

    private List<SysPermissionDO> loadGrantablePermissions(Long appId) {
        return sysPermissionMapper.selectList(
                Wrappers.<SysPermissionDO>lambdaQuery()
                        .eq(SysPermissionDO::getAppId, appId)
                        .eq(SysPermissionDO::getDeleted, NOT_DELETED)
                        .eq(SysPermissionDO::getStatus, AuthConstants.ENABLED)
                        .orderByAsc(SysPermissionDO::getMenuId)
                        .orderByAsc(SysPermissionDO::getId)
        );
    }

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

    private Set<Long> normalizeIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptySet();
        }
        return ids.stream().filter(id -> id != null && id > 0).collect(Collectors.toSet());
    }

    private void validateMenuIds(Long appId, Set<Long> menuIds) {
        if (menuIds.isEmpty()) {
            return;
        }
        Long count = sysMenuMapper.selectCount(
                Wrappers.<SysMenuDO>lambdaQuery()
                        .eq(SysMenuDO::getAppId, appId)
                        .eq(SysMenuDO::getDeleted, NOT_DELETED)
                        .eq(SysMenuDO::getStatus, AuthConstants.ENABLED)
                        .eq(SysMenuDO::getVisible, AuthConstants.ENABLED)
                        .in(SysMenuDO::getId, menuIds)
        );
        if (count == null || count != menuIds.size()) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "menu ids are invalid");
        }
    }

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
}
