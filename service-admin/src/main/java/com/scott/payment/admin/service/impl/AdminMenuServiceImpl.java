package com.scott.payment.admin.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scott.payment.admin.converter.MenuConverter;
import com.scott.payment.admin.dto.SysMenuCreateRequest;
import com.scott.payment.admin.dto.SysMenuDeleteRequest;
import com.scott.payment.admin.dto.SysMenuDTO;
import com.scott.payment.admin.dto.SysMenuQueryRequest;
import com.scott.payment.admin.dto.SysMenuStatusRequest;
import com.scott.payment.admin.dto.SysMenuUpdateRequest;
import com.scott.payment.admin.service.AdminMenuService;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.auth.constant.AuthConstants;
import com.scott.payment.component.db.auth.entity.SysAppDO;
import com.scott.payment.component.db.auth.entity.SysMenuDO;
import com.scott.payment.component.db.auth.entity.SysPermissionDO;
import com.scott.payment.component.db.auth.entity.SysRoleMenuDO;
import com.scott.payment.component.db.auth.entity.SysRolePermissionDO;
import com.scott.payment.component.db.auth.mapper.SysAppMapper;
import com.scott.payment.component.db.auth.mapper.SysMenuMapper;
import com.scott.payment.component.db.auth.mapper.SysPermissionMapper;
import com.scott.payment.component.db.auth.mapper.SysRoleMenuMapper;
import com.scott.payment.component.db.auth.mapper.SysRolePermissionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMenuServiceImpl
 * @date : 2026-06-19 22:22
 * @email : scott_x@163.com
 * @description : 管理后台菜单领域服务实现
 * @status : create
 *
 * <p>负责菜单树组装、父子层级校验、菜单编码唯一性校验和菜单状态维护，
 * 不承担控制器协议适配或页面交互逻辑。</p>
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMenuServiceImpl
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Admin Menu Service Impl，位于 service-admin 的服务实现层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Service
public class AdminMenuServiceImpl implements AdminMenuService {

    /**
     * 收单支付固定配置或枚举常量，集中维护魔法值，避免业务代码散落硬编码。
     */
    private static final long ROOT_PARENT_ID = 0L;
    /**
     * 收单支付固定配置或枚举常量，集中维护魔法值，避免业务代码散落硬编码。
     */
    private static final long NOT_DELETED = 0L;
    /**
     * 收单支付固定配置或枚举常量，集中维护魔法值，避免业务代码散落硬编码。
     */
    private static final int DEFAULT_SORT_NO = 100;
    private static final Set<String> MENU_TYPES = Set.of("CATALOG", "MENU", "BUTTON", "LINK");

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final SysAppMapper sysAppMapper;
    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final SysMenuMapper sysMenuMapper;
    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final SysPermissionMapper sysPermissionMapper;
    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final SysRoleMenuMapper sysRoleMenuMapper;
    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final SysRolePermissionMapper sysRolePermissionMapper;

    /**
     * 后台菜单对象转换器。
     */
    private final MenuConverter menuConverter;

    /**
     * 创建后台菜单服务实现。
     *
     * @param sysAppMapper            应用 Mapper
     * @param sysMenuMapper           菜单 Mapper
     * @param sysPermissionMapper     权限 Mapper
     * @param sysRoleMenuMapper       角色菜单 Mapper
     * @param sysRolePermissionMapper 角色权限 Mapper
     * @param menuConverter           后台菜单对象转换器
     */
    public AdminMenuServiceImpl(SysAppMapper sysAppMapper,
                                SysMenuMapper sysMenuMapper,
                                SysPermissionMapper sysPermissionMapper,
                                SysRoleMenuMapper sysRoleMenuMapper,
                                SysRolePermissionMapper sysRolePermissionMapper,
                                MenuConverter menuConverter) {
        this.sysAppMapper = sysAppMapper;
        this.sysMenuMapper = sysMenuMapper;
        this.sysPermissionMapper = sysPermissionMapper;
        this.sysRoleMenuMapper = sysRoleMenuMapper;
        this.sysRolePermissionMapper = sysRolePermissionMapper;
        this.menuConverter = menuConverter;
    }

    /**
     * 查询后台菜单树，并根据查询条件过滤返回节点。
     *
     * @param request 查询条件
     * @return 菜单树
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    public List<SysMenuDTO> treeMenus(SysMenuQueryRequest request) {
        return treeMenus(AuthConstants.APP_ADMIN, request);
    }

    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param appCode 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    public List<SysMenuDTO> treeMenus(String appCode, SysMenuQueryRequest request) {
        SysMenuQueryRequest query = request == null ? new SysMenuQueryRequest() : request;
        SysAppDO app = getApp(appCode);
        boolean defaultVisibleMenus = query.getStatus() == null && query.getVisible() == null;
        Integer statusFilter = query.getStatus();
        Integer visibleFilter = query.getVisible();
        if (defaultVisibleMenus) {
            statusFilter = AuthConstants.ENABLED;
            // 菜单管理页默认不过滤 visible，避免按钮类型菜单被隐藏
        }
        List<SysMenuDO> menus = sysMenuMapper.selectList(
                Wrappers.<SysMenuDO>lambdaQuery()
                        .eq(SysMenuDO::getAppId, app.getId())
                        .eq(SysMenuDO::getDeleted, NOT_DELETED)
                        .like(StringUtils.hasText(query.getMenuName()), SysMenuDO::getMenuName, query.getMenuName())
                        .eq(StringUtils.hasText(query.getMenuType()), SysMenuDO::getMenuType, query.getMenuType())
                        .eq(statusFilter != null, SysMenuDO::getStatus, statusFilter)
                        .eq(visibleFilter != null, SysMenuDO::getVisible, visibleFilter)
                        .orderByAsc(SysMenuDO::getSortNo)
                        .orderByAsc(SysMenuDO::getId)
        );
        return buildTree(menus);
    }

    /**
     * 新增后台菜单。
     *
     * @param request 新增请求
     * @return 菜单详情
     */
    /**
     * 创建或保存收单支付数据，保持请求校验、默认值和审计字段一致。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysMenuDTO createMenu(SysMenuCreateRequest request) {
        return createMenu(AuthConstants.APP_ADMIN, request);
    }

    /**
     * 创建或保存收单支付数据，保持请求校验、默认值和审计字段一致。
     * @param appCode 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysMenuDTO createMenu(String appCode, SysMenuCreateRequest request) {
        SysAppDO app = getApp(appCode);
        validateParent(app.getId(), request.getParentId(), null);
        assertMenuCodeNotExists(app.getId(), normalizeRequired(request.getMenuCode()));

        SysMenuDO menu = new SysMenuDO();
        menu.setAppId(app.getId());
        menu.setParentId(request.getParentId());
        menu.setMenuCode(normalizeRequired(request.getMenuCode()));
        menu.setMenuName(normalizeRequired(request.getMenuName()));
        menu.setMenuType(validMenuType(request.getMenuType()));
        applyEditableFields(menu, request.getRoutePath(), request.getComponentPath(), request.getPermissionCode(),
                request.getIcon(), request.getRedirect(), request.getVisible(), request.getKeepAlive(),
                request.getExternalLink(), request.getSortNo(), request.getStatus());
        menu.setDeleted(NOT_DELETED);
        sysMenuMapper.insert(menu);
        if (AuthConstants.APP_MERCHANT.equals(appCode)) {
            syncPermission(app.getId(), menu);
        }
        return toDTO(menu);
    }

    /**
     * 更新后台菜单。
     *
     * @param request 更新请求
     * @return 菜单详情
     */
    /**
     * 更新收单支付数据，保持已有记录、状态和审计字段的一致性。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysMenuDTO updateMenu(SysMenuUpdateRequest request) {
        return updateMenu(AuthConstants.APP_ADMIN, request);
    }

    /**
     * 更新收单支付数据，保持已有记录、状态和审计字段的一致性。
     * @param appCode 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysMenuDTO updateMenu(String appCode, SysMenuUpdateRequest request) {
        SysAppDO app = getApp(appCode);
        SysMenuDO menu = getMenu(app.getId(), request.getMenuId());
        validateParent(app.getId(), request.getParentId(), menu.getId());

        menu.setParentId(request.getParentId());
        menu.setMenuName(normalizeRequired(request.getMenuName()));
        menu.setMenuType(validMenuType(request.getMenuType()));
        applyEditableFields(menu, request.getRoutePath(), request.getComponentPath(), request.getPermissionCode(),
                request.getIcon(), request.getRedirect(), request.getVisible(), request.getKeepAlive(),
                request.getExternalLink(), request.getSortNo(), request.getStatus());
        sysMenuMapper.updateById(menu);
        if (AuthConstants.APP_MERCHANT.equals(appCode)) {
            syncPermission(app.getId(), menu);
        }
        return toDTO(menu);
    }

    /**
     * 更新后台菜单状态。
     *
     * @param request 状态请求
     */
    /**
     * 更新收单支付数据，保持已有记录、状态和审计字段的一致性。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(SysMenuStatusRequest request) {
        updateStatus(AuthConstants.APP_ADMIN, request);
    }

    /**
     * 更新收单支付数据，保持已有记录、状态和审计字段的一致性。
     * @param appCode 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(String appCode, SysMenuStatusRequest request) {
        SysAppDO app = getApp(appCode);
        SysMenuDO menu = getMenu(app.getId(), request.getMenuId());
        menu.setStatus(validStatus(request.getStatus()));
        sysMenuMapper.updateById(menu);
        if (AuthConstants.APP_MERCHANT.equals(appCode)) {
            syncPermission(app.getId(), menu);
        }
    }

    /**
     * 逻辑删除指定应用菜单。
     *
     * @param appCode 应用编码
     * @param request 删除请求
     */
    /**
     * 删除收单支付数据，按业务规则处理引用校验和删除边界。
     * @param appCode 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMenu(String appCode, SysMenuDeleteRequest request) {
        SysAppDO app = getApp(appCode);
        SysMenuDO menu = getMenu(app.getId(), request.getMenuId());
        Long childCount = sysMenuMapper.selectCount(
                Wrappers.<SysMenuDO>lambdaQuery()
                        .eq(SysMenuDO::getAppId, app.getId())
                        .eq(SysMenuDO::getParentId, menu.getId())
                        .eq(SysMenuDO::getDeleted, NOT_DELETED)
        );
        if (childCount != null && childCount > 0) {
            throw new ServiceException(ApiResultEnum.BAD_REQUEST.getCode(), "menu has child nodes");
        }
        menu.setStatus(AuthConstants.DISABLED);
        menu.setDeleted(menu.getId());
        sysMenuMapper.updateById(menu);
        softDeleteRoleMenus(app.getId(), menu.getId());
        softDeleteMenuPermissions(app.getId(), menu.getId());
    }

    /**
     * 查询 admin 应用，确保菜单始终挂载在管理后台应用之下。
     *
     * @return admin 应用实体
     */
    private SysAppDO getApp(String appCode) {
        SysAppDO app = sysAppMapper.selectOne(
                Wrappers.<SysAppDO>lambdaQuery()
                        .eq(SysAppDO::getAppCode, appCode)
                        .eq(SysAppDO::getDeleted, NOT_DELETED)
                        .last("LIMIT 1")
        );
        if (app == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), appCode + " app not found");
        }
        return app;
    }

    /**
     * 查询指定菜单，避免跨应用或已删除菜单被误操作。
     *
     * @param appId  应用主键
     * @param menuId 菜单主键
     * @return 菜单实体
     */
    private SysMenuDO getMenu(Long appId, Long menuId) {
        SysMenuDO menu = sysMenuMapper.selectOne(
                Wrappers.<SysMenuDO>lambdaQuery()
                        .eq(SysMenuDO::getId, menuId)
                        .eq(SysMenuDO::getAppId, appId)
                        .eq(SysMenuDO::getDeleted, NOT_DELETED)
                        .last("LIMIT 1")
        );
        if (menu == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "menu not found");
        }
        return menu;
    }

    /**
     * 校验父菜单是否合法，避免自引用或形成循环层级。
     *
     * @param appId         应用主键
     * @param parentId      父菜单主键
     * @param currentMenuId 当前菜单主键，新增时为空
     */
    private void validateParent(Long appId, Long parentId, Long currentMenuId) {
        if (parentId == null || parentId == ROOT_PARENT_ID) {
            return;
        }
        if (currentMenuId != null && parentId.equals(currentMenuId)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "parent menu is invalid");
        }
        SysMenuDO parent = getMenu(appId, parentId);
        Long nextParentId = parent.getParentId();
        while (currentMenuId != null && nextParentId != null && nextParentId != ROOT_PARENT_ID) {
            if (currentMenuId.equals(nextParentId)) {
                throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "parent menu is invalid");
            }
            nextParentId = getMenu(appId, nextParentId).getParentId();
        }
    }

    /**
     * 校验菜单编码在当前应用内唯一。
     *
     * @param appId    应用主键
     * @param menuCode 菜单编码
     */
    private void assertMenuCodeNotExists(Long appId, String menuCode) {
        Long count = sysMenuMapper.selectCount(
                Wrappers.<SysMenuDO>lambdaQuery()
                        .eq(SysMenuDO::getAppId, appId)
                        .eq(SysMenuDO::getMenuCode, menuCode)
                        .eq(SysMenuDO::getDeleted, NOT_DELETED)
        );
        if (count != null && count > 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "menu code already exists");
        }
    }

    /**
     * 将可编辑字段统一写回菜单实体，避免新增和编辑逻辑散落。
     *
     * @param menu         菜单实体
     * @param routePath    路由地址
     * @param componentPath 组件地址
     * @param permissionCode 权限标识
     * @param icon         图标
     * @param redirect     重定向地址
     * @param visible      是否显示
     * @param keepAlive    是否缓存
     * @param externalLink 是否外链
     * @param sortNo       排序号
     * @param status       状态
     */
    private void applyEditableFields(SysMenuDO menu, String routePath, String componentPath, String permissionCode,
                                     String icon, String redirect, Integer visible, Integer keepAlive,
                                     Integer externalLink, Integer sortNo, Integer status) {
        menu.setRoutePath(normalize(routePath));
        menu.setComponentPath(normalize(componentPath));
        menu.setPermissionCode(normalize(permissionCode));
        menu.setIcon(normalize(icon));
        menu.setRedirect(normalize(redirect));
        menu.setVisible(visible == null ? AuthConstants.ENABLED : validStatus(visible));
        menu.setKeepAlive(keepAlive == null ? AuthConstants.DISABLED : validStatus(keepAlive));
        menu.setExternalLink(externalLink == null ? AuthConstants.DISABLED : validStatus(externalLink));
        menu.setSortNo(sortNo == null ? DEFAULT_SORT_NO : sortNo);
        menu.setStatus(status == null ? AuthConstants.ENABLED : validStatus(status));
    }

    /**
     * 校验菜单类型是否合法。
     *
     * @param menuType 菜单类型
     * @return 规范化后的菜单类型
     */
    private String validMenuType(String menuType) {
        String normalized = normalizeRequired(menuType);
        if (MENU_TYPES.contains(normalized)) {
            return normalized;
        }
        throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "menu type is invalid");
    }

    /**
     * 校验开关类状态值是否合法。
     *
     * @param status 状态值
     * @return 合法状态值
     */
    private Integer validStatus(Integer status) {
        if (status == AuthConstants.ENABLED || status == AuthConstants.DISABLED) {
            return status;
        }
        throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "status is invalid");
    }

    /**
     * 去除首尾空格并校验必填字符串。
     *
     * @param value 原始值
     * @return 规范化后的非空字符串
     */
    private String normalizeRequired(String value) {
        if (!StringUtils.hasText(value)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "required field is blank");
        }
        return value.trim();
    }

    /**
     * 去除首尾空格，空白字符串统一归一为 null。
     *
     * @param value 原始值
     * @return 规范化后的可空字符串
     */
    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private void syncPermission(Long appId, SysMenuDO menu) {
        String permissionCode = normalize(menu.getPermissionCode());
        SysPermissionDO existing = sysPermissionMapper.selectOne(
                Wrappers.<SysPermissionDO>lambdaQuery()
                        .eq(SysPermissionDO::getAppId, appId)
                        .eq(SysPermissionDO::getMenuId, menu.getId())
                        .eq(SysPermissionDO::getDeleted, NOT_DELETED)
                        .last("LIMIT 1")
        );
        if (!StringUtils.hasText(permissionCode)) {
            if (existing != null) {
                existing.setDeleted(existing.getId());
                existing.setStatus(AuthConstants.DISABLED);
                sysPermissionMapper.updateById(existing);
            }
            return;
        }
        SysPermissionDO sameCode = sysPermissionMapper.selectOne(
                Wrappers.<SysPermissionDO>lambdaQuery()
                        .eq(SysPermissionDO::getAppId, appId)
                        .eq(SysPermissionDO::getPermissionCode, permissionCode)
                        .eq(SysPermissionDO::getDeleted, NOT_DELETED)
                        .last("LIMIT 1")
        );
        if (sameCode != null && !sameCode.getId().equals(existing == null ? null : existing.getId())
                && !Objects.equals(sameCode.getMenuId(), menu.getId())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "permission code already exists");
        }
        SysPermissionDO permission = existing == null ? sameCode : existing;
        if (permission == null) {
            permission = new SysPermissionDO();
            permission.setAppId(appId);
            permission.setMenuId(menu.getId());
            permission.setPermissionCode(permissionCode);
            applyPermissionFields(permission, menu);
            permission.setDeleted(NOT_DELETED);
            sysPermissionMapper.insert(permission);
            return;
        }
        permission.setMenuId(menu.getId());
        permission.setPermissionCode(permissionCode);
        applyPermissionFields(permission, menu);
        sysPermissionMapper.updateById(permission);
    }

    private void applyPermissionFields(SysPermissionDO permission, SysMenuDO menu) {
        permission.setPermissionName(menu.getMenuName());
        permission.setPermissionType("BUTTON".equals(menu.getMenuType()) ? "BUTTON" : "MENU");
        permission.setResourceMethod(defaultResourceMethod(menu.getMenuType()));
        permission.setResourcePath(normalize(menu.getRoutePath()));
        permission.setStatus(validStatus(menu.getStatus()));
    }

    private String defaultResourceMethod(String menuType) {
        return "BUTTON".equals(menuType) ? "*" : "GET";
    }

    private void softDeleteRoleMenus(Long appId, Long menuId) {
        List<SysRoleMenuDO> grants = sysRoleMenuMapper.selectList(
                Wrappers.<SysRoleMenuDO>lambdaQuery()
                        .eq(SysRoleMenuDO::getAppId, appId)
                        .eq(SysRoleMenuDO::getMenuId, menuId)
                        .eq(SysRoleMenuDO::getDeleted, NOT_DELETED)
        );
        grants.forEach(grant -> {
            grant.setDeleted(grant.getId());
            sysRoleMenuMapper.updateById(grant);
        });
    }

    private void softDeleteMenuPermissions(Long appId, Long menuId) {
        List<SysPermissionDO> permissions = sysPermissionMapper.selectList(
                Wrappers.<SysPermissionDO>lambdaQuery()
                        .eq(SysPermissionDO::getAppId, appId)
                        .eq(SysPermissionDO::getMenuId, menuId)
                        .eq(SysPermissionDO::getDeleted, NOT_DELETED)
        );
        permissions.forEach(permission -> {
            softDeleteRolePermissions(appId, permission.getId());
            permission.setStatus(AuthConstants.DISABLED);
            permission.setDeleted(permission.getId());
            sysPermissionMapper.updateById(permission);
        });
    }

    private void softDeleteRolePermissions(Long appId, Long permissionId) {
        List<SysRolePermissionDO> grants = sysRolePermissionMapper.selectList(
                Wrappers.<SysRolePermissionDO>lambdaQuery()
                        .eq(SysRolePermissionDO::getAppId, appId)
                        .eq(SysRolePermissionDO::getPermissionId, permissionId)
                        .eq(SysRolePermissionDO::getDeleted, NOT_DELETED)
        );
        grants.forEach(grant -> {
            grant.setDeleted(grant.getId());
            sysRolePermissionMapper.updateById(grant);
        });
    }

    private List<SysMenuDTO> buildTree(List<SysMenuDO> menus) {
        Map<Long, SysMenuDTO> menuMap = new LinkedHashMap<>();
        menus.forEach(menu -> menuMap.put(menu.getId(), toDTO(menu)));
        List<SysMenuDTO> roots = new ArrayList<>();
        menuMap.values().forEach(menu -> {
            SysMenuDTO parent = menuMap.get(menu.getParentId());
            if (parent == null || ROOT_PARENT_ID == menu.getParentId()) {
                roots.add(menu);
                return;
            }
            parent.getChildren().add(menu);
        });
        return roots;
    }

    private SysMenuDTO toDTO(SysMenuDO menu) {
        return menuConverter.toDTO(menu);
    }
}
