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
 * @date : 2026-06-19 22:22
 * @email : scott_x@163.com
 * @description : 管理后台菜单领域服务实现
 * @status : create
 *
 * <p>负责菜单树组装、父子层级校验、菜单编码唯一性校验和菜单状态维护，
 * 不承担控制器协议适配或页面交互逻辑。</p>
 */
@Service
public class AdminMenuServiceImpl implements AdminMenuService {

    private static final long ROOT_PARENT_ID = 0L;
    private static final long NOT_DELETED = 0L;
    private static final int DEFAULT_SORT_NO = 100;
    private static final Set<String> MENU_TYPES = Set.of("CATALOG", "MENU", "BUTTON", "LINK");

    private final SysAppMapper sysAppMapper;
    private final SysMenuMapper sysMenuMapper;

    /**
     * 创建后台菜单服务实现。
     *
     * @param sysAppMapper  应用 Mapper
     * @param sysMenuMapper 菜单 Mapper
     */
    public AdminMenuServiceImpl(SysAppMapper sysAppMapper, SysMenuMapper sysMenuMapper) {
        this.sysAppMapper = sysAppMapper;
        this.sysMenuMapper = sysMenuMapper;
    }

    /**
     * 查询后台菜单树，并根据查询条件过滤返回节点。
     *
     * @param request 查询条件
     * @return 菜单树
     */
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

    /**
     * 新增后台菜单。
     *
     * @param request 新增请求
     * @return 菜单详情
     */
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

    /**
     * 更新后台菜单。
     *
     * @param request 更新请求
     * @return 菜单详情
     */
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

    /**
     * 更新后台菜单状态。
     *
     * @param request 状态请求
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(SysMenuStatusRequest request) {
        SysAppDO app = getAdminApp();
        SysMenuDO menu = getMenu(app.getId(), request.getMenuId());
        menu.setStatus(validStatus(request.getStatus()));
        sysMenuMapper.updateById(menu);
    }

    /**
     * 查询 admin 应用，确保菜单始终挂载在管理后台应用之下。
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
