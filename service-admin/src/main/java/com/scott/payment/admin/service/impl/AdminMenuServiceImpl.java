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
@Service
public class AdminMenuServiceImpl implements AdminMenuService {

    /**
     * ROOT PARENT ID，用于定位 Admin Menu Service Impl 关联的上游配置、渠道、账号、角色或业务记录。
     * <p>
     * 单位：无；格式：业务编号字符串；不允许为空；非敏感字段。
     * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final long ROOT_PARENT_ID = 0L;
    /**
     * NOT DELETED，用于保存 Admin Menu Service Impl 中与 notdeleted 相关的业务属性。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final long NOT_DELETED = 0L;
    /**
     * DEFAULT SORT NO，用于控制列表展示或规则匹配时的排序优先级。
     * <p>
     * 单位：无；格式：业务编号字符串；不允许为空；非敏感字段。
     * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final int DEFAULT_SORT_NO = 100;
    private static final Set<String> MENU_TYPES = Set.of("CATALOG", "MENU", "BUTTON", "LINK");

    /**
     * sys App Mapper 依赖，用于 Admin Menu Service Impl 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final SysAppMapper sysAppMapper;
    /**
     * sys Menu Mapper 依赖，用于 Admin Menu Service Impl 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final SysMenuMapper sysMenuMapper;
    /**
     * sys Permission Mapper 依赖，用于 Admin Menu Service Impl 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final SysPermissionMapper sysPermissionMapper;
    /**
     * sys Role Menu Mapper 依赖，用于 Admin Menu Service Impl 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final SysRoleMenuMapper sysRoleMenuMapper;
    /**
     * sys Role Permission Mapper 依赖，用于 Admin Menu Service Impl 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
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
    @Override
    public List<SysMenuDTO> treeMenus(SysMenuQueryRequest request) {
        return treeMenus(AuthConstants.APP_ADMIN, request);
    }

    /**
     * 查询指定应用的菜单树；未指定状态时默认只返回启用菜单，但保留按钮节点。
     *
     * @param appCode 应用编码
     * @param request 菜单名称、类型、状态和可见性等可选条件
     * @return 指定应用的菜单树
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
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysMenuDTO createMenu(SysMenuCreateRequest request) {
        return createMenu(AuthConstants.APP_ADMIN, request);
    }

    /**
     * 在指定应用创建菜单，并为商户应用同步对应权限记录。
     *
     * @param appCode 应用编码
     * @param request 菜单创建请求
     * @return 创建后的菜单详情
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
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysMenuDTO updateMenu(SysMenuUpdateRequest request) {
        return updateMenu(AuthConstants.APP_ADMIN, request);
    }

    /**
     * 更新指定应用菜单并校验父子层级，商户应用同时同步权限记录。
     *
     * @param appCode 应用编码
     * @param request 菜单更新请求
     * @return 更新后的菜单详情
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
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(SysMenuStatusRequest request) {
        updateStatus(AuthConstants.APP_ADMIN, request);
    }

    /**
     * 更新指定应用菜单状态，商户应用同时同步权限启停状态。
     *
     * @param appCode 应用编码
     * @param request 菜单主键和目标状态
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

    /**
     * 整理sync权限，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param appId app ID 输入值，参与 appID 的查询、校验、转换、写入或日志摘要
     * @param menu menu 输入值，参与 菜单 的查询、校验、转换、写入或日志摘要
     */
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

    /**
     * 应用应用权限fields，把校验后的配置、金额、状态或字段值写入目标对象。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param permission permission 输入值，参与 权限 的查询、校验、转换、写入或日志摘要
     * @param menu menu 输入值，参与 菜单 的查询、校验、转换、写入或日志摘要
     */
    private void applyPermissionFields(SysPermissionDO permission, SysMenuDO menu) {
        permission.setPermissionName(menu.getMenuName());
        permission.setPermissionType("BUTTON".equals(menu.getMenuType()) ? "BUTTON" : "MENU");
        permission.setResourceMethod(defaultResourceMethod(menu.getMenuType()));
        permission.setResourcePath(normalize(menu.getRoutePath()));
        permission.setStatus(validStatus(menu.getStatus()));
    }

    /**
     * 整理默认resourcemethod，返回后续查询、通知或响应组装可直接使用的标准值。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param menuType menu Type 输入值，参与 菜单type 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String defaultResourceMethod(String menuType) {
        return "BUTTON".equals(menuType) ? "*" : "GET";
    }

    /**
     * 更新softdelete角色菜单，保持业务状态、配置项或展示字段与请求意图一致。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param appId app ID 输入值，参与 appID 的查询、校验、转换、写入或日志摘要
     * @param menuId menu ID 输入值，参与 菜单ID 的查询、校验、转换、写入或日志摘要
     */
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

    /**
     * 更新softdelete菜单权限，保持业务状态、配置项或展示字段与请求意图一致。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param appId app ID 输入值，参与 appID 的查询、校验、转换、写入或日志摘要
     * @param menuId menu ID 输入值，参与 菜单ID 的查询、校验、转换、写入或日志摘要
     */
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

    /**
     * 更新softdelete角色权限，保持业务状态、配置项或展示字段与请求意图一致。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param appId app ID 输入值，参与 appID 的查询、校验、转换、写入或日志摘要
     * @param permissionId permission ID 输入值，参与 权限ID 的查询、校验、转换、写入或日志摘要
     */
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

    /**
     * 构造树对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param menus menus 输入值，参与 菜单 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
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

    /**
     * 构造dto对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param menu menu 输入值，参与 菜单 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private SysMenuDTO toDTO(SysMenuDO menu) {
        return menuConverter.toDTO(menu);
    }
}
