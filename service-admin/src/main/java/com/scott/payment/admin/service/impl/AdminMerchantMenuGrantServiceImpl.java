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

@Service
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantMenuGrantServiceImpl
 * @date : 2026-06-23 12:55
 * @email : scott_x@163.com
 * @description : AdminMerchantMenuGrantServiceImpl 服务实现，用于执行领域规则、数据读写编排和业务异常转换，位于 运营后台服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
public class AdminMerchantMenuGrantServiceImpl implements AdminMerchantMenuGrantService {

    /**
     * ROOT PARENT ID 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final long ROOT_PARENT_ID = 0L;
    /**
     * GRANT SOURCE ADMIN 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final String GRANT_SOURCE_ADMIN = "ADMIN";

    /**
     * sys App Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final SysAppMapper sysAppMapper;
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
     * sys Merchant Menu Grant Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final SysMerchantMenuGrantMapper sysMerchantMenuGrantMapper;
    /**
     * sys Merchant Permission Grant Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final SysMerchantPermissionGrantMapper sysMerchantPermissionGrantMapper;
    /**
     * base Merchant Info Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
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

    /**
     * 执行 load Merchant Menu Tree 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminMerchantMenuGrantServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param appId app Id 输入值，含义由调用方法名称和所属业务对象限定
     * @return 解析或查询得到的业务值
     */
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

    /**
     * 执行 load Merchant Permissions 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminMerchantMenuGrantServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param appId app Id 输入值，含义由调用方法名称和所属业务对象限定
     * @return 解析或查询得到的业务值
     */
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

    /**
     * 执行 load Checked Menu Ids 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminMerchantMenuGrantServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param appId app Id 输入值，含义由调用方法名称和所属业务对象限定
     * @param merchantId 商户号，用于限定数据归属、幂等范围和权限边界
     * @return 解析或查询得到的业务值
     */
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

    /**
     * 执行 load Checked Permissions 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminMerchantMenuGrantServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param appId app Id 输入值，含义由调用方法名称和所属业务对象限定
     * @param merchantId 商户号，用于限定数据归属、幂等范围和权限边界
     * @return 解析或查询得到的业务值
     */
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

    /**
     * 执行 validate Menu Ids 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminMerchantMenuGrantServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param appId app Id 输入值，含义由调用方法名称和所属业务对象限定
     * @param menuIds menu Ids 输入值，含义由调用方法名称和所属业务对象限定
     */
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

    /**
     * 执行 validate Permission Ids 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminMerchantMenuGrantServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param appId app Id 输入值，含义由调用方法名称和所属业务对象限定
     * @param permissionIds permission Ids 输入值，含义由调用方法名称和所属业务对象限定
     */
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

    /**
     * 执行 validate Permission Menu Scope 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminMerchantMenuGrantServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param appId app Id 输入值，含义由调用方法名称和所属业务对象限定
     * @param menuIds menu Ids 输入值，含义由调用方法名称和所属业务对象限定
     * @param permissionIds permission Ids 输入值，含义由调用方法名称和所属业务对象限定
     */
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

    /**
     * 执行 soft Delete Menu Grants 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminMerchantMenuGrantServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param appId app Id 输入值，含义由调用方法名称和所属业务对象限定
     * @param merchantId 商户号，用于限定数据归属、幂等范围和权限边界
     */
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

    /**
     * 执行 soft Delete Permission Grants 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminMerchantMenuGrantServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param appId app Id 输入值，含义由调用方法名称和所属业务对象限定
     * @param merchantId 商户号，用于限定数据归属、幂等范围和权限边界
     */
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

    /**
     * 执行 insert Menu Grant 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminMerchantMenuGrantServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param appId app Id 输入值，含义由调用方法名称和所属业务对象限定
     * @param merchantId 商户号，用于限定数据归属、幂等范围和权限边界
     * @param menuId menu Id 输入值，含义由调用方法名称和所属业务对象限定
     * @param now now 输入值，含义由调用方法名称和所属业务对象限定
     */
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

    /**
     * 执行 insert Permission Grant 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminMerchantMenuGrantServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param appId app Id 输入值，含义由调用方法名称和所属业务对象限定
     * @param merchantId 商户号，用于限定数据归属、幂等范围和权限边界
     * @param permissionId permission Id 输入值，含义由调用方法名称和所属业务对象限定
     * @param now now 输入值，含义由调用方法名称和所属业务对象限定
     */
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

    /**
     * 执行 normalize Ids 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminMerchantMenuGrantServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param ids ids 输入值，含义由调用方法名称和所属业务对象限定
     * @return 标准化后的业务字段值
     */
    private Set<Long> normalizeIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptySet();
        }
        return ids.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .collect(Collectors.toSet());
    }

    /**
     * 执行 to Menu DTO 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminMerchantMenuGrantServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param menu menu 输入值，含义由调用方法名称和所属业务对象限定
     * @return 转换或构建后的目标对象
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
     * 执行 to Permission DTO 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminMerchantMenuGrantServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param permission permission 输入值，含义由调用方法名称和所属业务对象限定
     * @return 转换或构建后的目标对象
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

    /**
     * 执行 build Menu Tree 服务能力，按当前领域规则完成校验、状态读取或数据写入。
     * <p>
     * 层级边界：运营后台服务层；输入来源、输出结构和异常语义由 AdminMerchantMenuGrantServiceImpl 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param menus menus 输入值，含义由调用方法名称和所属业务对象限定
     * @return 转换或构建后的目标对象
     */
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
