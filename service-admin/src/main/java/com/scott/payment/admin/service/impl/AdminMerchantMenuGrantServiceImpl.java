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
 * @description : Admin Merchant Menu Grant Service Impl 服务实现，位于 运营后台服务，执行领域校验、配置读取、数据库更新或远程调用编排，并向上层返回明确结果。
 * @status : create
 */
public class AdminMerchantMenuGrantServiceImpl implements AdminMerchantMenuGrantService {

    /**
     * ROOT PARENT ID，用于定位 Admin Merchant Menu Grant Service Impl 关联的上游配置、渠道、账号、角色或业务记录。
     * <p>
     * 单位：无；格式：业务编号字符串；不允许为空；非敏感字段。
     * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final long ROOT_PARENT_ID = 0L;
    /**
     * GRANT SOURCE ADMIN，用于保存 Admin Merchant Menu Grant Service Impl 中与 grant来源admin 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final String GRANT_SOURCE_ADMIN = "ADMIN";

    /**
     * sys App Mapper 依赖，用于 Admin Merchant Menu Grant Service Impl 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final SysAppMapper sysAppMapper;
    /**
     * sys Menu Mapper 依赖，用于 Admin Merchant Menu Grant Service Impl 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final SysMenuMapper sysMenuMapper;
    /**
     * sys Permission Mapper 依赖，用于 Admin Merchant Menu Grant Service Impl 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final SysPermissionMapper sysPermissionMapper;
    /**
     * sys Merchant Menu Grant Mapper 依赖，用于 Admin Merchant Menu Grant Service Impl 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final SysMerchantMenuGrantMapper sysMerchantMenuGrantMapper;
    /**
     * sys Merchant Permission Grant Mapper 依赖，用于 Admin Merchant Menu Grant Service Impl 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final SysMerchantPermissionGrantMapper sysMerchantPermissionGrantMapper;
    /**
     * base Merchant Info Mapper 依赖，用于 Admin Merchant Menu Grant Service Impl 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
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
     * 查询商户菜单树，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param appId app ID 输入值，参与 appID 的查询、校验、转换、写入或日志摘要
     * @return 查询得到的业务对象、分页结果或空结果
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
     * 查询商户权限，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param appId app ID 输入值，参与 appID 的查询、校验、转换、写入或日志摘要
     * @return 查询得到的业务对象、分页结果或空结果
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
     * 查询checked菜单ID，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param appId app ID 输入值，参与 appID 的查询、校验、转换、写入或日志摘要
     * @param merchantId 商户号，用于限定数据归属、权限范围和配置读取范围
     * @return 查询得到的业务对象、分页结果或空结果
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
     * 查询checked权限，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param appId app ID 输入值，参与 appID 的查询、校验、转换、写入或日志摘要
     * @param merchantId 商户号，用于限定数据归属、权限范围和配置读取范围
     * @return 查询得到的业务对象、分页结果或空结果
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
                        .in(SysMenuDO::getId, menuIds)
                        .eq(SysMenuDO::getStatus, AuthConstants.ENABLED)
                        .eq(SysMenuDO::getDeleted, AuthConstants.NOT_DELETED)
        );
        if (count == null || count != menuIds.size()) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "menuIds contain invalid MERCHANT menu");
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
                        .in(SysPermissionDO::getId, permissionIds)
                        .eq(SysPermissionDO::getStatus, AuthConstants.ENABLED)
                        .eq(SysPermissionDO::getDeleted, AuthConstants.NOT_DELETED)
        );
        if (count == null || count != permissionIds.size()) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "permissionIds contain invalid MERCHANT permission");
        }
    }

    /**
     * 校验权限菜单scope输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 运营后台服务 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param appId app ID 输入值，参与 appID 的查询、校验、转换、写入或日志摘要
     * @param menuIds menu Ids 输入值，参与 菜单ID 的查询、校验、转换、写入或日志摘要
     * @param permissionIds permission Ids 输入值，参与 权限ID 的查询、校验、转换、写入或日志摘要
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
     * 更新softdelete菜单grants，保持业务状态、配置项或展示字段与请求意图一致。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param appId app ID 输入值，参与 appID 的查询、校验、转换、写入或日志摘要
     * @param merchantId 商户号，用于限定数据归属、权限范围和配置读取范围
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
     * 更新softdelete权限grants，保持业务状态、配置项或展示字段与请求意图一致。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param appId app ID 输入值，参与 appID 的查询、校验、转换、写入或日志摘要
     * @param merchantId 商户号，用于限定数据归属、权限范围和配置读取范围
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
     * 创建菜单grant，完成必要校验后写入或委托下游服务处理。
     * <p>
     * 前置条件：调用方已完成 运营后台服务 的身份、权限、必填字段和业务唯一性准备。
     * 该方法可能写入数据库、生成业务编号或投递后续事件；幂等键、唯一索引和事务注解共同约束重复提交。
     * 异常边界：校验失败、持久化失败或下游调用失败会中断当前写入流程，敏感字段只允许进入脱敏摘要。
     * </p>
     * @param appId app ID 输入值，参与 appID 的查询、校验、转换、写入或日志摘要
     * @param merchantId 商户号，用于限定数据归属、权限范围和配置读取范围
     * @param menuId menu ID 输入值，参与 菜单ID 的查询、校验、转换、写入或日志摘要
     * @param now now 输入值，参与 now 的查询、校验、转换、写入或日志摘要
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
     * 创建权限grant，完成必要校验后写入或委托下游服务处理。
     * <p>
     * 前置条件：调用方已完成 运营后台服务 的身份、权限、必填字段和业务唯一性准备。
     * 该方法可能写入数据库、生成业务编号或投递后续事件；幂等键、唯一索引和事务注解共同约束重复提交。
     * 异常边界：校验失败、持久化失败或下游调用失败会中断当前写入流程，敏感字段只允许进入脱敏摘要。
     * </p>
     * @param appId app ID 输入值，参与 appID 的查询、校验、转换、写入或日志摘要
     * @param merchantId 商户号，用于限定数据归属、权限范围和配置读取范围
     * @param permissionId permission ID 输入值，参与 权限ID 的查询、校验、转换、写入或日志摘要
     * @param now now 输入值，参与 now 的查询、校验、转换、写入或日志摘要
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
        return ids.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .collect(Collectors.toSet());
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
