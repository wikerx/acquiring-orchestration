package com.scott.payment.admin.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scott.payment.admin.dto.SysMenuDTO;
import com.scott.payment.admin.dto.SysPermissionDTO;
import com.scott.payment.admin.dto.merchant.AdminMerchantMenuGrantQueryResponse;
import com.scott.payment.admin.dto.merchant.AdminMerchantMenuGrantSaveRequest;
import com.scott.payment.admin.service.AdminMerchantMenuGrantService;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.db.auth.constant.AuthConstants;
import com.scott.payment.component.db.auth.entity.BaseMerchantInfoDO;
import com.scott.payment.component.db.auth.entity.SysAppDO;
import com.scott.payment.component.db.auth.entity.SysMerchantMenuGrantDO;
import com.scott.payment.component.db.auth.entity.SysMerchantPermissionGrantDO;
import com.scott.payment.component.db.auth.entity.SysMenuDO;
import com.scott.payment.component.db.auth.entity.SysPermissionDO;
import com.scott.payment.component.db.auth.mapper.BaseMerchantInfoMapper;
import com.scott.payment.component.db.auth.mapper.SysAppMapper;
import com.scott.payment.component.db.auth.mapper.SysMerchantMenuGrantMapper;
import com.scott.payment.component.db.auth.mapper.SysMerchantPermissionGrantMapper;
import com.scott.payment.component.db.auth.mapper.SysMenuMapper;
import com.scott.payment.component.db.auth.mapper.SysPermissionMapper;
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
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantMenuGrantServiceImpl
 * @date : 2026-06-23 12:55
 * @email : scott_x@163.com
 * @description : admin商户菜单授权服务实现，位于 运营后台服务，执行该业务的规则校验和数据读写，并保持现有事务与异常边界。
 * @status : create
 */
@Service
public class AdminMerchantMenuGrantServiceImpl implements AdminMerchantMenuGrantService {

    /**
     * {@code ROOT_PARENT_ID}，用于定位 {@code AdminMerchantMenuGrantServiceImpl} 关联的上游配置、渠道、账号、角色或业务记录。
     * <p>
     * 单位：无；格式：业务编号字符串；不允许为空；非敏感字段。
     * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final long ROOT_PARENT_ID = 0L;
    /**
     * {@code GRANT_SOURCE_ADMIN}常量，统一 {@code AdminMerchantMenuGrantServiceImpl} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final String GRANT_SOURCE_ADMIN = "ADMIN";

    private final SysAppMapper sysAppMapper;
    private final SysMenuMapper sysMenuMapper;
    private final SysPermissionMapper sysPermissionMapper;
    private final SysMerchantMenuGrantMapper sysMerchantMenuGrantMapper;
    private final SysMerchantPermissionGrantMapper sysMerchantPermissionGrantMapper;
    private final BaseMerchantInfoMapper baseMerchantInfoMapper;

    /**
     * 创建商户菜单授权领域服务。
     *
     * @param sysAppMapper 系统应用 Mapper
     * @param sysMenuMapper 菜单 Mapper
     * @param sysPermissionMapper 权限 Mapper
     * @param sysMerchantMenuGrantMapper 商户菜单授权 Mapper
     * @param sysMerchantPermissionGrantMapper 商户权限授权 Mapper
     * @param baseMerchantInfoMapper 商户资料 Mapper
     */
    public AdminMerchantMenuGrantServiceImpl(SysAppMapper sysAppMapper,
                                             SysMenuMapper sysMenuMapper,
                                             SysPermissionMapper sysPermissionMapper,
                                             SysMerchantMenuGrantMapper sysMerchantMenuGrantMapper,
                                             SysMerchantPermissionGrantMapper sysMerchantPermissionGrantMapper,
                                             BaseMerchantInfoMapper baseMerchantInfoMapper) {
        this.sysAppMapper = sysAppMapper;
        this.sysMenuMapper = sysMenuMapper;
        this.sysPermissionMapper = sysPermissionMapper;
        this.sysMerchantMenuGrantMapper = sysMerchantMenuGrantMapper;
        this.sysMerchantPermissionGrantMapper = sysMerchantPermissionGrantMapper;
        this.baseMerchantInfoMapper = baseMerchantInfoMapper;
    }

    /**
     * 查询商户菜单授权。
     *
     * @param merchantId 商户号
     * @return 授权查询响应
     */
    @Override
    @DS(DataSourceName.SLAVE)
    public AdminMerchantMenuGrantQueryResponse queryGrant(String merchantId) {
        String normalizedMerchantId = validateMerchant(merchantId);
        SysAppDO merchantApp = getMerchantApp();
        AdminMerchantMenuGrantQueryResponse response = new AdminMerchantMenuGrantQueryResponse();
        response.setMerchantId(normalizedMerchantId);
        response.setMenus(loadMerchantMenuTree(merchantApp.getId()));
        response.setPermissions(loadMerchantPermissions(merchantApp.getId()));
        response.setCheckedMenuIds(loadCheckedMenuIds(merchantApp.getId(), normalizedMerchantId));
        List<SysPermissionDO> checkedPermissions = loadCheckedPermissions(merchantApp.getId(), normalizedMerchantId);
        response.setCheckedPermissionIds(checkedPermissions.stream().map(SysPermissionDO::getId).toList());
        response.setCheckedPermissionCodes(checkedPermissions.stream()
                .map(SysPermissionDO::getPermissionCode)
                .filter(StringUtils::hasText)
                .sorted()
                .toList());
        return response;
    }

    /**
     * 覆盖保存商户菜单授权。
     *
     * @param merchantId 商户号
     * @param request    保存请求
     */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public void saveGrant(String merchantId, AdminMerchantMenuGrantSaveRequest request) {
        String normalizedMerchantId = validateMerchant(merchantId);
        AdminMerchantMenuGrantSaveRequest saveRequest = request == null ? new AdminMerchantMenuGrantSaveRequest() : request;
        SysAppDO merchantApp = getMerchantApp();
        Set<Long> menuIds = normalizeIds(saveRequest.getMenuIds());
        Set<Long> permissionIds = normalizeIds(saveRequest.getPermissionIds());
        validateMenuIds(merchantApp.getId(), menuIds);
        validatePermissionIds(merchantApp.getId(), permissionIds);
        validatePermissionMenuScope(merchantApp.getId(), menuIds, permissionIds);
        softDeleteMenuGrants(merchantApp.getId(), normalizedMerchantId);
        softDeletePermissionGrants(merchantApp.getId(), normalizedMerchantId);
        LocalDateTime now = LocalDateTime.now();
        menuIds.forEach(menuId -> insertMenuGrant(merchantApp.getId(), normalizedMerchantId, menuId, now));
        permissionIds.forEach(permissionId -> insertPermissionGrant(merchantApp.getId(), normalizedMerchantId, permissionId, now));
    }

    private String validateMerchant(String merchantId) {
        if (!StringUtils.hasText(merchantId)) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "merchantId is required");
        }
        String normalized = merchantId.trim();
        BaseMerchantInfoDO merchant = baseMerchantInfoMapper.selectOne(
                Wrappers.<BaseMerchantInfoDO>lambdaQuery()
                        .eq(BaseMerchantInfoDO::getMerchantId, normalized)
                        .eq(BaseMerchantInfoDO::getDeleted, 0)
                        .last("LIMIT 1")
        );
        if (merchant == null) {
            throw new ServiceException(ApiResultEnum.MERCHANT_INVALID);
        }
        return normalized;
    }

    /**
     * 查询未删除的商户后台应用，确保菜单和权限授权使用正确应用边界。
     *
     * @return 商户后台应用记录
     * @throws ServiceException 商户应用未配置时抛出
     */
    private SysAppDO getMerchantApp() {
        SysAppDO app = sysAppMapper.selectOne(
                Wrappers.<SysAppDO>lambdaQuery()
                        .eq(SysAppDO::getAppCode, AuthConstants.APP_MERCHANT)
                        .eq(SysAppDO::getDeleted, AuthConstants.NOT_DELETED)
                        .last("LIMIT 1")
        );
        if (app == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "MERCHANT app not found");
        }
        return app;
    }

    private List<SysMenuDTO> loadMerchantMenuTree(Long appId) {
        List<SysMenuDTO> menus = sysMenuMapper.selectList(
                        Wrappers.<SysMenuDO>lambdaQuery()
                                .eq(SysMenuDO::getAppId, appId)
                                .eq(SysMenuDO::getStatus, AuthConstants.ENABLED)
                                .eq(SysMenuDO::getDeleted, AuthConstants.NOT_DELETED)
                                .orderByAsc(SysMenuDO::getSortNo, SysMenuDO::getId)
                ).stream()
                .map(this::toMenuDTO)
                .toList();
        return buildMenuTree(menus);
    }

    private List<SysPermissionDTO> loadMerchantPermissions(Long appId) {
        return sysPermissionMapper.selectList(
                        Wrappers.<SysPermissionDO>lambdaQuery()
                                .eq(SysPermissionDO::getAppId, appId)
                                .eq(SysPermissionDO::getStatus, AuthConstants.ENABLED)
                                .eq(SysPermissionDO::getDeleted, AuthConstants.NOT_DELETED)
                                .orderByAsc(SysPermissionDO::getMenuId, SysPermissionDO::getId)
                ).stream()
                .filter(permission -> !"*:*:*".equals(permission.getPermissionCode()))
                .map(this::toPermissionDTO)
                .toList();
    }

    private List<Long> loadCheckedMenuIds(Long appId, String merchantId) {
        return sysMerchantMenuGrantMapper.selectList(
                        Wrappers.<SysMerchantMenuGrantDO>lambdaQuery()
                                .eq(SysMerchantMenuGrantDO::getAppId, appId)
                                .eq(SysMerchantMenuGrantDO::getMerchantId, merchantId)
                                .eq(SysMerchantMenuGrantDO::getStatus, AuthConstants.ENABLED)
                                .eq(SysMerchantMenuGrantDO::getDeleted, AuthConstants.NOT_DELETED)
                ).stream()
                .map(SysMerchantMenuGrantDO::getMenuId)
                .filter(Objects::nonNull)
                .toList();
    }

    private List<SysPermissionDO> loadCheckedPermissions(Long appId, String merchantId) {
        List<Long> permissionIds = sysMerchantPermissionGrantMapper.selectList(
                        Wrappers.<SysMerchantPermissionGrantDO>lambdaQuery()
                                .eq(SysMerchantPermissionGrantDO::getAppId, appId)
                                .eq(SysMerchantPermissionGrantDO::getMerchantId, merchantId)
                                .eq(SysMerchantPermissionGrantDO::getStatus, AuthConstants.ENABLED)
                                .eq(SysMerchantPermissionGrantDO::getDeleted, AuthConstants.NOT_DELETED)
                ).stream()
                .map(SysMerchantPermissionGrantDO::getPermissionId)
                .filter(Objects::nonNull)
                .toList();
        if (permissionIds.isEmpty()) {
            return Collections.emptyList();
        }
        return sysPermissionMapper.selectList(
                Wrappers.<SysPermissionDO>lambdaQuery()
                        .eq(SysPermissionDO::getAppId, appId)
                        .in(SysPermissionDO::getId, permissionIds)
                        .eq(SysPermissionDO::getDeleted, AuthConstants.NOT_DELETED)
        );
    }

    private void validateMenuIds(Long appId, Set<Long> menuIds) {
        if (menuIds.isEmpty()) {
            return;
        }
        Long count = sysMenuMapper.selectCount(
                Wrappers.<SysMenuDO>lambdaQuery()
                        .eq(SysMenuDO::getAppId, appId)
                        .in(SysMenuDO::getId, menuIds)
                        .eq(SysMenuDO::getStatus, AuthConstants.ENABLED)
                        .eq(SysMenuDO::getDeleted, AuthConstants.NOT_DELETED)
        );
        if (count == null || count != menuIds.size()) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "menuIds contain invalid MERCHANT menu");
        }
    }

    private void validatePermissionIds(Long appId, Set<Long> permissionIds) {
        if (permissionIds.isEmpty()) {
            return;
        }
        Long count = sysPermissionMapper.selectCount(
                Wrappers.<SysPermissionDO>lambdaQuery()
                        .eq(SysPermissionDO::getAppId, appId)
                        .in(SysPermissionDO::getId, permissionIds)
                        .eq(SysPermissionDO::getStatus, AuthConstants.ENABLED)
                        .eq(SysPermissionDO::getDeleted, AuthConstants.NOT_DELETED)
        );
        if (count == null || count != permissionIds.size()) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "permissionIds contain invalid MERCHANT permission");
        }
    }

    private void validatePermissionMenuScope(Long appId, Set<Long> menuIds, Set<Long> permissionIds) {
        if (permissionIds.isEmpty()) {
            return;
        }
        List<SysPermissionDO> permissions = sysPermissionMapper.selectList(
                Wrappers.<SysPermissionDO>lambdaQuery()
                        .eq(SysPermissionDO::getAppId, appId)
                        .in(SysPermissionDO::getId, permissionIds)
                        .eq(SysPermissionDO::getStatus, AuthConstants.ENABLED)
                        .eq(SysPermissionDO::getDeleted, AuthConstants.NOT_DELETED)
        );
        boolean hasPermissionOutsideMenus = permissions.stream()
                .map(SysPermissionDO::getMenuId)
                .filter(Objects::nonNull)
                .anyMatch(menuId -> !menuIds.contains(menuId));
        if (hasPermissionOutsideMenus) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "permissionIds exceed selected menus");
        }
    }

    private void softDeleteMenuGrants(Long appId, String merchantId) {
        sysMerchantMenuGrantMapper.selectList(
                Wrappers.<SysMerchantMenuGrantDO>lambdaQuery()
                        .eq(SysMerchantMenuGrantDO::getAppId, appId)
                        .eq(SysMerchantMenuGrantDO::getMerchantId, merchantId)
                        .eq(SysMerchantMenuGrantDO::getDeleted, AuthConstants.NOT_DELETED)
        ).forEach(grant -> {
            grant.setDeleted(grant.getId());
            grant.setStatus(AuthConstants.DISABLED);
            grant.setUpdatedAt(LocalDateTime.now());
            sysMerchantMenuGrantMapper.updateById(grant);
        });
    }

    private void softDeletePermissionGrants(Long appId, String merchantId) {
        sysMerchantPermissionGrantMapper.selectList(
                Wrappers.<SysMerchantPermissionGrantDO>lambdaQuery()
                        .eq(SysMerchantPermissionGrantDO::getAppId, appId)
                        .eq(SysMerchantPermissionGrantDO::getMerchantId, merchantId)
                        .eq(SysMerchantPermissionGrantDO::getDeleted, AuthConstants.NOT_DELETED)
        ).forEach(grant -> {
            grant.setDeleted(grant.getId());
            grant.setStatus(AuthConstants.DISABLED);
            grant.setUpdatedAt(LocalDateTime.now());
            sysMerchantPermissionGrantMapper.updateById(grant);
        });
    }

    private void insertMenuGrant(Long appId, String merchantId, Long menuId, LocalDateTime now) {
        SysMerchantMenuGrantDO grant = new SysMerchantMenuGrantDO();
        grant.setMerchantId(merchantId);
        grant.setAppId(appId);
        grant.setMenuId(menuId);
        grant.setGrantSource(GRANT_SOURCE_ADMIN);
        grant.setStatus(AuthConstants.ENABLED);
        grant.setCreatedAt(now);
        grant.setUpdatedAt(now);
        grant.setDeleted(AuthConstants.NOT_DELETED);
        sysMerchantMenuGrantMapper.insert(grant);
    }

    private void insertPermissionGrant(Long appId, String merchantId, Long permissionId, LocalDateTime now) {
        SysMerchantPermissionGrantDO grant = new SysMerchantPermissionGrantDO();
        grant.setMerchantId(merchantId);
        grant.setAppId(appId);
        grant.setPermissionId(permissionId);
        grant.setGrantSource(GRANT_SOURCE_ADMIN);
        grant.setStatus(AuthConstants.ENABLED);
        grant.setCreatedAt(now);
        grant.setUpdatedAt(now);
        grant.setDeleted(AuthConstants.NOT_DELETED);
        sysMerchantPermissionGrantMapper.insert(grant);
    }

    private Set<Long> normalizeIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptySet();
        }
        return ids.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .collect(Collectors.toSet());
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

    private List<SysMenuDTO> buildMenuTree(List<SysMenuDTO> menus) {
        Map<Long, SysMenuDTO> nodeMap = menus.stream()
                .collect(Collectors.toMap(SysMenuDTO::getMenuId, item -> item, (left, right) -> left, LinkedHashMap::new));
        List<SysMenuDTO> roots = new ArrayList<>();
        for (SysMenuDTO menu : menus) {
            if (menu.getParentId() == null || menu.getParentId() == ROOT_PARENT_ID || !nodeMap.containsKey(menu.getParentId())) {
                roots.add(menu);
                continue;
            }
            nodeMap.get(menu.getParentId()).getChildren().add(menu);
        }
        return roots;
    }
}
