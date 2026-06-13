package com.scott.payment.admin.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scott.payment.admin.converter.MenuConverter;
import com.scott.payment.admin.dto.SysMenuCreateRequest;
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
import com.scott.payment.component.db.auth.mapper.SysAppMapper;
import com.scott.payment.component.db.auth.mapper.SysMenuMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMenuServiceImpl
 * @date : 2026-06-07 00:00
 * @description : 管理后台菜单服务实现
 * @status : create
 */
@Service
public class AdminMenuServiceImpl implements AdminMenuService {

    private static final long ROOT_PARENT_ID = 0L;
    private static final long NOT_DELETED = 0L;
    private static final int DEFAULT_SORT_NO = 100;
    private static final Set<String> MENU_TYPES = Set.of("CATALOG", "MENU", "BUTTON", "LINK");

    private final SysAppMapper sysAppMapper;
    private final SysMenuMapper sysMenuMapper;

    public AdminMenuServiceImpl(SysAppMapper sysAppMapper, SysMenuMapper sysMenuMapper) {
        this.sysAppMapper = sysAppMapper;
        this.sysMenuMapper = sysMenuMapper;
    }

    @Override
    public List<SysMenuDTO> treeMenus(SysMenuQueryRequest request) {
        SysMenuQueryRequest query = request == null ? new SysMenuQueryRequest() : request;
        SysAppDO app = getAdminApp();
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysMenuDTO createMenu(SysMenuCreateRequest request) {
        SysAppDO app = getAdminApp();
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
        return toDTO(menu);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysMenuDTO updateMenu(SysMenuUpdateRequest request) {
        SysAppDO app = getAdminApp();
        SysMenuDO menu = getMenu(app.getId(), request.getMenuId());
        validateParent(app.getId(), request.getParentId(), menu.getId());

        menu.setParentId(request.getParentId());
        menu.setMenuName(normalizeRequired(request.getMenuName()));
        menu.setMenuType(validMenuType(request.getMenuType()));
        applyEditableFields(menu, request.getRoutePath(), request.getComponentPath(), request.getPermissionCode(),
                request.getIcon(), request.getRedirect(), request.getVisible(), request.getKeepAlive(),
                request.getExternalLink(), request.getSortNo(), request.getStatus());
        sysMenuMapper.updateById(menu);
        return toDTO(menu);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(SysMenuStatusRequest request) {
        SysAppDO app = getAdminApp();
        SysMenuDO menu = getMenu(app.getId(), request.getMenuId());
        menu.setStatus(validStatus(request.getStatus()));
        sysMenuMapper.updateById(menu);
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

    private String validMenuType(String menuType) {
        String normalized = normalizeRequired(menuType);
        if (MENU_TYPES.contains(normalized)) {
            return normalized;
        }
        throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "menu type is invalid");
    }

    private Integer validStatus(Integer status) {
        if (status == AuthConstants.ENABLED || status == AuthConstants.DISABLED) {
            return status;
        }
        throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "status is invalid");
    }

    private String normalizeRequired(String value) {
        if (!StringUtils.hasText(value)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "required field is blank");
        }
        return value.trim();
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
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
        return MenuConverter.INSTANCE.toDTO(menu);
    }
}
