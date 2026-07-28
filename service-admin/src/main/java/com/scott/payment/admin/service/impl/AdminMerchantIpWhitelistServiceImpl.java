package com.scott.payment.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scott.payment.admin.dto.merchant.AdminMerchantIpWhitelistDTOs.MerchantIpWhitelistConfigRequest;
import com.scott.payment.admin.dto.merchant.AdminMerchantIpWhitelistDTOs.MerchantIpWhitelistCreateRequest;
import com.scott.payment.admin.dto.merchant.AdminMerchantIpWhitelistDTOs.MerchantIpWhitelistItem;
import com.scott.payment.admin.dto.merchant.AdminMerchantIpWhitelistDTOs.MerchantIpWhitelistQuery;
import com.scott.payment.admin.dto.merchant.AdminMerchantIpWhitelistDTOs.MerchantIpWhitelistResponse;
import com.scott.payment.admin.dto.merchant.AdminMerchantIpWhitelistDTOs.MerchantIpWhitelistUpdateRequest;
import com.scott.payment.admin.service.AdminMerchantIpWhitelistService;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.core.util.net.IpAddressNormalizer;
import com.scott.payment.component.core.util.net.IpAddressNormalizer.NormalizedIp;
import com.scott.payment.component.db.auth.entity.BaseMerchantInfoDO;
import com.scott.payment.component.db.auth.entity.MerchantIpWhitelistDO;
import com.scott.payment.component.db.auth.entity.MerchantOpenApiAccessConfigDO;
import com.scott.payment.component.db.auth.mapper.BaseMerchantInfoMapper;
import com.scott.payment.component.db.auth.mapper.MerchantIpWhitelistMapper;
import com.scott.payment.component.db.auth.mapper.MerchantOpenApiAccessConfigMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantIpWhitelistServiceImpl
 * @date : 2026-07-18 00:00
 * @email : scott_x@163.com
 * @description : 商户 OpenAPI IP 白名单领域服务实现，位于 service-admin 服务实现层，仅维护精确 IP 和商户维度校验开关。
 * @status : create
 */
@Service
public class AdminMerchantIpWhitelistServiceImpl implements AdminMerchantIpWhitelistService {

    /**
     * NOT DELETED，用于保存 Admin Merchant IP Whitelist Service Impl 中与 notdeleted 相关的业务属性。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final long NOT_DELETED = 0L;
    /**
     * ENABLED，表示当前配置项或业务能力的启停开关。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final int ENABLED = 1;
    /**
     * DISABLED，表示当前配置项或业务能力的启停开关。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final int DISABLED = 0;

    /**
     * whitelist Mapper 依赖，用于 Admin Merchant IP Whitelist Service Impl 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final MerchantIpWhitelistMapper whitelistMapper;
    /**
     * access Config Mapper 依赖，用于 Admin Merchant IP Whitelist Service Impl 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final MerchantOpenApiAccessConfigMapper accessConfigMapper;
    /**
     * merchant Info Mapper 依赖，用于 Admin Merchant IP Whitelist Service Impl 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final BaseMerchantInfoMapper merchantInfoMapper;

    /**
     * 创建商户 IP 白名单服务实现。
     *
     * @param whitelistMapper    白名单 Mapper
     * @param accessConfigMapper OpenAPI 访问配置 Mapper
     * @param merchantInfoMapper 商户基础资料 Mapper
     */
    public AdminMerchantIpWhitelistServiceImpl(MerchantIpWhitelistMapper whitelistMapper,
                                               MerchantOpenApiAccessConfigMapper accessConfigMapper,
                                               BaseMerchantInfoMapper merchantInfoMapper) {
        this.whitelistMapper = whitelistMapper;
        this.accessConfigMapper = accessConfigMapper;
        this.merchantInfoMapper = merchantInfoMapper;
    }

    /**
     * 分页查询商户 IP 白名单记录。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @Override
    public PageResult<MerchantIpWhitelistResponse> pageWhitelists(MerchantIpWhitelistQuery query) {
        MerchantIpWhitelistQuery condition = query == null ? new MerchantIpWhitelistQuery() : query;
        List<MerchantIpWhitelistDO> matchedRecords = listMatchedWhitelists(condition);
        if (matchedRecords.isEmpty()) {
            return PageResult.of(0, condition.safePageNo(), condition.safePageSize(), List.of());
        }
        Map<String, List<MerchantIpWhitelistDO>> grouped = groupByMerchant(matchedRecords);
        List<String> pageMerchantIds = paginateMerchantIds(new ArrayList<>(grouped.keySet()), condition);
        if (pageMerchantIds.isEmpty()) {
            return PageResult.of(grouped.size(), condition.safePageNo(), condition.safePageSize(), List.of());
        }
        Map<String, BaseMerchantInfoDO> merchantMap = loadMerchants(pageMerchantIds);
        Map<String, MerchantOpenApiAccessConfigDO> configMap = loadConfigs(pageMerchantIds);
        List<MerchantIpWhitelistResponse> responses = pageMerchantIds.stream()
                .map(merchantId -> toAggregatedResponse(grouped.get(merchantId), merchantMap.get(merchantId), configMap.get(merchantId)))
                .toList();
        return PageResult.of(grouped.size(), condition.safePageNo(), condition.safePageSize(), responses);
    }

    /**
     * 按查询条件查询商户维度聚合后的 IP 白名单记录，用于导出。
     *
     * @param query 查询条件
     * @return 商户维度白名单列表
     */
    @Override
    public List<MerchantIpWhitelistResponse> listWhitelists(MerchantIpWhitelistQuery query) {
        MerchantIpWhitelistQuery condition = query == null ? new MerchantIpWhitelistQuery() : query;
        Map<String, List<MerchantIpWhitelistDO>> grouped = groupByMerchant(listMatchedWhitelists(condition));
        if (grouped.isEmpty()) {
            return List.of();
        }
        List<String> merchantIds = new ArrayList<>(grouped.keySet());
        Map<String, BaseMerchantInfoDO> merchantMap = loadMerchants(merchantIds);
        Map<String, MerchantOpenApiAccessConfigDO> configMap = loadConfigs(merchantIds);
        return merchantIds.stream()
                .map(merchantId -> toAggregatedResponse(grouped.get(merchantId), merchantMap.get(merchantId), configMap.get(merchantId)))
                .toList();
    }

    private List<MerchantIpWhitelistDO> listMatchedWhitelists(MerchantIpWhitelistQuery condition) {
        List<String> merchantIds = findMerchantIds(condition);
        List<String> configMerchantIds = findConfigMerchantIds(condition);
        merchantIds = mergeMerchantIds(merchantIds, configMerchantIds);
        if (merchantIds != null && merchantIds.isEmpty()) {
            return List.of();
        }
        String ipType = trimToNull(condition.getIpType());
        String ipValue = trimToNull(condition.getIpValue());
        LambdaQueryWrapper<MerchantIpWhitelistDO> wrapper = Wrappers.<MerchantIpWhitelistDO>lambdaQuery()
                .eq(MerchantIpWhitelistDO::getDeleted, NOT_DELETED)
                .eq(StringUtils.hasText(condition.getMerchantId()), MerchantIpWhitelistDO::getMerchantId, normalizeMerchantId(condition.getMerchantId()))
                .in(merchantIds != null && !merchantIds.isEmpty(), MerchantIpWhitelistDO::getMerchantId, merchantIds)
                .eq(StringUtils.hasText(ipType), MerchantIpWhitelistDO::getIpType, ipType)
                .eq(condition.getStatus() != null, MerchantIpWhitelistDO::getStatus, normalizeStatus(condition.getStatus()))
                .like(StringUtils.hasText(ipValue), MerchantIpWhitelistDO::getIpValue, ipValue)
                .orderByDesc(MerchantIpWhitelistDO::getGmtModified)
                .orderByDesc(MerchantIpWhitelistDO::getId);
        return whitelistMapper.selectList(wrapper);
    }

    /**
     * 查询单条 IP 白名单详情。
     *
     * @param id 白名单记录 ID
     * @return 白名单详情
     */
    @Override
    public MerchantIpWhitelistResponse getWhitelist(Long id) {
        MerchantIpWhitelistDO row = requireWhitelist(id);
        BaseMerchantInfoDO merchant = findMerchant(row.getMerchantId());
        MerchantOpenApiAccessConfigDO config = findConfig(row.getMerchantId());
        return toResponse(row, merchant, config);
    }

    /**
     * 批量新增同一商户的精确 IP 白名单记录。
     *
     * @param request 新增请求
     * @return 新增后的记录集合
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<MerchantIpWhitelistResponse> createWhitelists(MerchantIpWhitelistCreateRequest request) {
        if (request == null) {
            throw badRequest("白名单请求不能为空");
        }
        BaseMerchantInfoDO merchant = requireMerchant(request.getMerchantId());
        List<NormalizedIp> normalizedIps = normalizeIpList(request.getIpValues());
        String operator = currentOperatorName();
        int status = request.getStatus() == null ? ENABLED : normalizeStatus(request.getStatus());
        LocalDateTime now = LocalDateTime.now();
        List<MerchantIpWhitelistDO> created = normalizedIps.stream()
                .map(ip -> buildWhitelist(merchant.getMerchantId(), ip, status, request.getRemark(), operator, now))
                .toList();
        try {
            for (MerchantIpWhitelistDO row : created) {
                whitelistMapper.insert(row);
            }
        } catch (DuplicateKeyException ex) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "同一商户下 IP 白名单不能重复");
        }
        MerchantOpenApiAccessConfigDO config = findConfig(merchant.getMerchantId());
        return created.stream().map(row -> toResponse(row, merchant, config)).toList();
    }

    /**
     * 更新单条精确 IP 白名单记录。
     *
     * @param id      白名单记录 ID
     * @param request 更新请求
     * @return 更新后的记录
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MerchantIpWhitelistResponse updateWhitelist(Long id, MerchantIpWhitelistUpdateRequest request) {
        if (request == null) {
            throw badRequest("白名单请求不能为空");
        }
        MerchantIpWhitelistDO existing = requireWhitelist(id);
        NormalizedIp ip = normalizeIp(request.getIpValue());
        existing.setIpType(ip.ipType());
        existing.setIpValue(ip.ipValue());
        existing.setStatus(normalizeStatus(request.getStatus()));
        existing.setRemark(trimToNull(request.getRemark()));
        existing.setUpdateBy(currentOperatorName());
        existing.setGmtModified(LocalDateTime.now());
        try {
            whitelistMapper.updateById(existing);
        } catch (DuplicateKeyException ex) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "同一商户下 IP 白名单不能重复");
        }
        return getWhitelist(id);
    }

    /**
     * 更新单条 IP 白名单记录状态。
     *
     * @param id     白名单记录 ID
     * @param status 状态，1 启用，0 停用
     * @return 更新后的记录
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MerchantIpWhitelistResponse updateWhitelistStatus(Long id, Integer status) {
        MerchantIpWhitelistDO row = requireWhitelist(id);
        row.setStatus(normalizeStatus(status));
        row.setUpdateBy(currentOperatorName());
        row.setGmtModified(LocalDateTime.now());
        whitelistMapper.updateById(row);
        return getWhitelist(id);
    }

    /**
     * 软删除单条 IP 白名单记录。
     *
     * @param id 白名单记录 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteWhitelist(Long id) {
        MerchantIpWhitelistDO row = requireWhitelist(id);
        row.setDeleted(row.getId());
        row.setUpdateBy(currentOperatorName());
        row.setGmtModified(LocalDateTime.now());
        whitelistMapper.updateById(row);
    }

    /**
     * 更新商户维度 IP 白名单校验开关。
     *
     * @param request 开关请求
     * @return 当前配置视图
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MerchantIpWhitelistResponse updateConfig(MerchantIpWhitelistConfigRequest request) {
        if (request == null) {
            throw badRequest("白名单配置请求不能为空");
        }
        BaseMerchantInfoDO merchant = requireMerchant(request.getMerchantId());
        MerchantOpenApiAccessConfigDO config = findConfig(merchant.getMerchantId());
        String operator = currentOperatorName();
        LocalDateTime now = LocalDateTime.now();
        if (config == null) {
            config = new MerchantOpenApiAccessConfigDO();
            config.setMerchantId(merchant.getMerchantId());
            config.setIpWhitelistEnabled(normalizeStatus(request.getIpWhitelistEnabled()));
            config.setRemark(trimToNull(request.getRemark()));
            config.setCreateBy(operator);
            config.setUpdateBy(operator);
            config.setGmtCreate(now);
            config.setGmtModified(now);
            config.setDeleted(NOT_DELETED);
            accessConfigMapper.insert(config);
        } else {
            config.setIpWhitelistEnabled(normalizeStatus(request.getIpWhitelistEnabled()));
            config.setRemark(trimToNull(request.getRemark()));
            config.setUpdateBy(operator);
            config.setGmtModified(now);
            accessConfigMapper.updateById(config);
        }
        MerchantIpWhitelistDO first = whitelistMapper.selectOne(Wrappers.<MerchantIpWhitelistDO>lambdaQuery()
                .eq(MerchantIpWhitelistDO::getDeleted, NOT_DELETED)
                .eq(MerchantIpWhitelistDO::getMerchantId, merchant.getMerchantId())
                .orderByDesc(MerchantIpWhitelistDO::getGmtModified)
                .last("LIMIT 1"));
        return toResponse(first, merchant, config);
    }

    private List<String> findMerchantIds(MerchantIpWhitelistQuery query) {
        if (!StringUtils.hasText(query.getMerchantKeyword())) {
            return null;
        }
        String keyword = query.getMerchantKeyword().trim();
        return merchantInfoMapper.selectList(Wrappers.<BaseMerchantInfoDO>lambdaQuery()
                        .eq(BaseMerchantInfoDO::getDeleted, 0)
                        .and(wrapper -> wrapper.like(BaseMerchantInfoDO::getMerchantId, keyword)
                                .or().like(BaseMerchantInfoDO::getMerchantName, keyword)
                                .or().like(BaseMerchantInfoDO::getMerchantShortName, keyword)))
                .stream()
                .map(BaseMerchantInfoDO::getMerchantId)
                .toList();
    }

    private List<String> findConfigMerchantIds(MerchantIpWhitelistQuery query) {
        if (query.getIpWhitelistEnabled() == null) {
            return null;
        }
        int expected = normalizeStatus(query.getIpWhitelistEnabled());
        if (expected == ENABLED) {
            return accessConfigMapper.selectList(Wrappers.<MerchantOpenApiAccessConfigDO>lambdaQuery()
                            .eq(MerchantOpenApiAccessConfigDO::getDeleted, NOT_DELETED)
                            .eq(MerchantOpenApiAccessConfigDO::getIpWhitelistEnabled, ENABLED))
                    .stream()
                    .map(MerchantOpenApiAccessConfigDO::getMerchantId)
                    .toList();
        }
        List<String> enabledMerchantIds = accessConfigMapper.selectList(Wrappers.<MerchantOpenApiAccessConfigDO>lambdaQuery()
                        .eq(MerchantOpenApiAccessConfigDO::getDeleted, NOT_DELETED)
                        .eq(MerchantOpenApiAccessConfigDO::getIpWhitelistEnabled, ENABLED))
                .stream()
                .map(MerchantOpenApiAccessConfigDO::getMerchantId)
                .toList();
        LambdaQueryWrapper<MerchantIpWhitelistDO> wrapper = Wrappers.<MerchantIpWhitelistDO>lambdaQuery()
                .eq(MerchantIpWhitelistDO::getDeleted, NOT_DELETED)
                .select(MerchantIpWhitelistDO::getMerchantId);
        if (!enabledMerchantIds.isEmpty()) {
            wrapper.notIn(MerchantIpWhitelistDO::getMerchantId, enabledMerchantIds);
        }
        return whitelistMapper.selectList(wrapper).stream()
                .map(MerchantIpWhitelistDO::getMerchantId)
                .distinct()
                .toList();
    }

    /**
     * 构造商户ID对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法依据当前领域对象和方法语义完成参数校验、格式转换、查询读取、状态写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param left left 输入值，参与 left 的查询、校验、转换、写入或日志摘要
     * @param right right 输入值，参与 right 的查询、校验、转换、写入或日志摘要
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private List<String> mergeMerchantIds(List<String> left, List<String> right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        if (left.isEmpty() || right.isEmpty()) {
            return List.of();
        }
        return left.stream().filter(right::contains).toList();
    }

    /**
     * 整理group按商户，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param rows 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private Map<String, List<MerchantIpWhitelistDO>> groupByMerchant(List<MerchantIpWhitelistDO> rows) {
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        Map<String, List<MerchantIpWhitelistDO>> grouped = new LinkedHashMap<>();
        for (MerchantIpWhitelistDO row : rows) {
            if (row == null || !StringUtils.hasText(row.getMerchantId())) {
                continue;
            }
            grouped.computeIfAbsent(row.getMerchantId(), key -> new ArrayList<>()).add(row);
        }
        return grouped;
    }

    /**
     * 整理paginate商户ID，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param merchantIds 商户号，用于限定数据归属、权限范围和配置读取范围
     * @param condition 查询条件对象，包含筛选字段、时间范围、分页参数和数据范围
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private List<String> paginateMerchantIds(List<String> merchantIds, MerchantIpWhitelistQuery condition) {
        if (merchantIds == null || merchantIds.isEmpty()) {
            return List.of();
        }
        int fromIndex = (int) Math.min((condition.safePageNo() - 1) * condition.safePageSize(), merchantIds.size());
        int toIndex = (int) Math.min(fromIndex + condition.safePageSize(), merchantIds.size());
        return merchantIds.subList(fromIndex, toIndex);
    }

/**
 * 构造whitelist对象，完成字段复制、格式标准化和敏感数据处理。
 * <p>
 * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
 * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
 * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
 * </p>
 * @param merchantId 商户号，用于限定数据归属、权限范围和配置读取范围
 * @param ip IP 输入值，参与 ip 的查询、校验、转换、写入或日志摘要
 * @param status 状态编码，取值必须来自对应枚举、字典或渠道协议
 * @param remark remark 输入值，参与 remark 的查询、校验、转换、写入或日志摘要
 * @param operator operator 输入值，参与 operator 的查询、校验、转换、写入或日志摘要
 * @param now now 输入值，参与 now 的查询、校验、转换、写入或日志摘要
 * @return 构造、转换或解析后的业务值
 */
    private MerchantIpWhitelistDO buildWhitelist(String merchantId,
                                                 NormalizedIp ip,
                                                 int status,
                                                 String remark,
                                                 String operator,
                                                 LocalDateTime now) {
        MerchantIpWhitelistDO row = new MerchantIpWhitelistDO();
        row.setMerchantId(merchantId);
        row.setIpType(ip.ipType());
        row.setIpValue(ip.ipValue());
        row.setStatus(status);
        row.setRemark(trimToNull(remark));
        row.setCreateBy(operator);
        row.setUpdateBy(operator);
        row.setGmtCreate(now);
        row.setGmtModified(now);
        row.setDeleted(NOT_DELETED);
        return row;
    }

    /**
     * 校验whitelist输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 运营后台服务 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param id 业务记录主键或主键集合，用于定位本次操作的目标记录
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private MerchantIpWhitelistDO requireWhitelist(Long id) {
        if (id == null) {
            throw badRequest("白名单记录不存在");
        }
        MerchantIpWhitelistDO row = whitelistMapper.selectOne(Wrappers.<MerchantIpWhitelistDO>lambdaQuery()
                .eq(MerchantIpWhitelistDO::getDeleted, NOT_DELETED)
                .eq(MerchantIpWhitelistDO::getId, id));
        if (row == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "白名单记录不存在");
        }
        return row;
    }

    /**
     * 校验商户输入，发现缺失、越权或格式错误时中断当前流程。
     * <p>
     * 前置条件：调用方传入需要在 运营后台服务 内校验的参数、状态或安全材料。
     * 该方法只执行校验和规则判断，不主动写入业务状态；校验通过后由后续步骤继续处理。
     * 异常边界：缺失、越权、重复、防重放失败或格式错误时抛出当前模块约定异常。
     * </p>
     * @param merchantId 商户号，用于限定数据归属、权限范围和配置读取范围
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private BaseMerchantInfoDO requireMerchant(String merchantId) {
        BaseMerchantInfoDO merchant = findMerchant(merchantId);
        if (merchant == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "商户不存在");
        }
        return merchant;
    }

    /**
     * 查询商户，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param merchantId 商户号，用于限定数据归属、权限范围和配置读取范围
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private BaseMerchantInfoDO findMerchant(String merchantId) {
        if (!StringUtils.hasText(merchantId)) {
            return null;
        }
        return merchantInfoMapper.selectOne(Wrappers.<BaseMerchantInfoDO>lambdaQuery()
                .eq(BaseMerchantInfoDO::getDeleted, 0)
                .eq(BaseMerchantInfoDO::getMerchantId, normalizeMerchantId(merchantId))
                .last("LIMIT 1"));
    }

    /**
     * 查询配置，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param merchantId 商户号，用于限定数据归属、权限范围和配置读取范围
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private MerchantOpenApiAccessConfigDO findConfig(String merchantId) {
        if (!StringUtils.hasText(merchantId)) {
            return null;
        }
        return accessConfigMapper.selectOne(Wrappers.<MerchantOpenApiAccessConfigDO>lambdaQuery()
                .eq(MerchantOpenApiAccessConfigDO::getDeleted, NOT_DELETED)
                .eq(MerchantOpenApiAccessConfigDO::getMerchantId, normalizeMerchantId(merchantId))
                .last("LIMIT 1"));
    }

    /**
     * 查询merchants，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param merchantIds 商户号，用于限定数据归属、权限范围和配置读取范围
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private Map<String, BaseMerchantInfoDO> loadMerchants(List<String> merchantIds) {
        List<String> ids = distinctMerchantIds(merchantIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        return merchantInfoMapper.selectList(Wrappers.<BaseMerchantInfoDO>lambdaQuery()
                        .eq(BaseMerchantInfoDO::getDeleted, 0)
                        .in(BaseMerchantInfoDO::getMerchantId, ids))
                .stream()
                .collect(Collectors.toMap(BaseMerchantInfoDO::getMerchantId, Function.identity(), (left, right) -> left));
    }

    /**
     * 查询configs，按调用方提供的过滤条件返回对应业务视图。
     * <p>
     * 前置条件：调用方已按 运营后台服务 的权限和数据范围传入查询条件。
     * 该方法通常不修改数据库状态；分页、时间范围和空结果处理由入参和返回类型共同表达。
     * 异常边界：底层查询或远程读取失败时按当前模块统一异常规则向上抛出或降级为空结果。
     * </p>
     * @param merchantIds 商户号，用于限定数据归属、权限范围和配置读取范围
     * @return 查询得到的业务对象、分页结果或空结果
     */
    private Map<String, MerchantOpenApiAccessConfigDO> loadConfigs(List<String> merchantIds) {
        List<String> ids = distinctMerchantIds(merchantIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        return accessConfigMapper.selectList(Wrappers.<MerchantOpenApiAccessConfigDO>lambdaQuery()
                        .eq(MerchantOpenApiAccessConfigDO::getDeleted, NOT_DELETED)
                        .in(MerchantOpenApiAccessConfigDO::getMerchantId, ids))
                .stream()
                .collect(Collectors.toMap(MerchantOpenApiAccessConfigDO::getMerchantId, Function.identity(), (left, right) -> left));
    }

    /**
     * 整理distinct商户ID，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param merchantIds 商户号，用于限定数据归属、权限范围和配置读取范围
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private List<String> distinctMerchantIds(List<String> merchantIds) {
        if (merchantIds == null || merchantIds.isEmpty()) {
            return List.of();
        }
        return merchantIds.stream()
                .filter(StringUtils::hasText)
                .map(this::normalizeMerchantId)
                .distinct()
                .toList();
    }

    /**
     * 解析normalizeiplist，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 运营后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param ipValues IP Values 输入值，参与 ip值 的查询、校验、转换、写入或日志摘要
     * @return 构造、转换或解析后的业务值
     */
    private List<NormalizedIp> normalizeIpList(List<String> ipValues) {
        if (ipValues == null || ipValues.isEmpty()) {
            throw badRequest("至少录入一个精确 IP");
        }
        Map<String, NormalizedIp> normalized = new LinkedHashMap<>();
        for (String value : ipValues) {
            if (!StringUtils.hasText(value)) {
                continue;
            }
            NormalizedIp ip = normalizeIp(value);
            normalized.put(ip.ipValue(), ip);
        }
        if (normalized.isEmpty()) {
            throw badRequest("至少录入一个精确 IP");
        }
        return List.copyOf(normalized.values());
    }

    /**
     * 解析normalizeip，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 运营后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 构造、转换或解析后的业务值
     */
    private NormalizedIp normalizeIp(String value) {
        try {
            return IpAddressNormalizer.normalizeExact(value);
        } catch (IllegalArgumentException ex) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), ex.getMessage());
        }
    }

/**
 * 构造响应对象，完成字段复制、格式标准化和敏感数据处理。
 * <p>
 * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
 * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
 * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
 * </p>
 * @param row 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
 * @param merchant merchant 输入值，参与 商户 的查询、校验、转换、写入或日志摘要
 * @param config config 输入值，参与 配置 的查询、校验、转换、写入或日志摘要
 * @return 构造、转换或解析后的业务值
 */
    private MerchantIpWhitelistResponse toResponse(MerchantIpWhitelistDO row,
                                                   BaseMerchantInfoDO merchant,
                                                   MerchantOpenApiAccessConfigDO config) {
        MerchantIpWhitelistResponse response = new MerchantIpWhitelistResponse();
        if (row != null) {
            response.setId(row.getId());
            response.setMerchantId(row.getMerchantId());
            response.setIpType(row.getIpType());
            response.setIpValue(row.getIpValue());
            response.setStatus(row.getStatus());
            response.setRemark(row.getRemark());
            response.setCreateBy(row.getCreateBy());
            response.setUpdateBy(row.getUpdateBy());
            response.setGmtCreate(row.getGmtCreate());
            response.setGmtModified(row.getGmtModified());
        }
        if (merchant != null) {
            response.setMerchantId(merchant.getMerchantId());
            response.setMerchantName(merchant.getMerchantName());
            response.setMerchantShortName(merchant.getMerchantShortName());
        }
        response.setIpWhitelistEnabled(config == null || config.getIpWhitelistEnabled() == null ? DISABLED : config.getIpWhitelistEnabled());
        response.setConfigRemark(config == null ? null : config.getRemark());
        return response;
    }

/**
 * 构造aggregated响应对象，完成字段复制、格式标准化和敏感数据处理。
 * <p>
 * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
 * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
 * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
 * </p>
 * @param rows 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
 * @param merchant merchant 输入值，参与 商户 的查询、校验、转换、写入或日志摘要
 * @param config config 输入值，参与 配置 的查询、校验、转换、写入或日志摘要
 * @return 构造、转换或解析后的业务值
 */
    private MerchantIpWhitelistResponse toAggregatedResponse(List<MerchantIpWhitelistDO> rows,
                                                             BaseMerchantInfoDO merchant,
                                                             MerchantOpenApiAccessConfigDO config) {
        MerchantIpWhitelistDO latest = rows == null || rows.isEmpty() ? null : rows.get(0);
        MerchantIpWhitelistResponse response = toResponse(latest, merchant, config);
        if (latest != null && response.getMerchantId() == null) {
            response.setMerchantId(latest.getMerchantId());
        }
        response.setIpWhitelists(rows == null ? List.of() : rows.stream().map(this::toItem).toList());
        return response;
    }

    /**
     * 构造item对象，完成字段复制、格式标准化和敏感数据处理。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 所需的源对象、配置或协议字段。
     * 该方法主要完成字段映射、格式标准化、金额币种整理或响应组装，不承担远程调用职责。
     * 异常边界：必要字段缺失或格式非法时抛出当前模块约定异常；敏感字段只保留脱敏、摘要或最小必要值。
     * </p>
     * @param row 源对象、目标对象或查询结果行，用于字段映射、补充展示信息或汇总统计
     * @return 构造、转换或解析后的业务值
     */
    private MerchantIpWhitelistItem toItem(MerchantIpWhitelistDO row) {
        MerchantIpWhitelistItem item = new MerchantIpWhitelistItem();
        item.setId(row.getId());
        item.setIpType(row.getIpType());
        item.setIpValue(row.getIpValue());
        item.setStatus(row.getStatus());
        item.setRemark(row.getRemark());
        item.setUpdateBy(row.getUpdateBy());
        item.setGmtModified(row.getGmtModified());
        return item;
    }

    /**
     * 解析normalize状态，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 运营后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param status 状态编码，取值必须来自对应枚举、字典或渠道协议
     * @return 构造、转换或解析后的业务值
     */
    private int normalizeStatus(Integer status) {
        return status != null && status == ENABLED ? ENABLED : DISABLED;
    }

    /**
     * 解析normalize商户ID，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 前置条件：调用方已传入 运营后台服务 中需要标准化的原始值。
     * 该方法完成金额、币种、时间、状态、路径或协议字段的规范化，不直接提交交易状态。
     * 异常边界：格式非法、精度不满足或枚举不支持时抛出当前模块约定异常。
     * </p>
     * @param merchantId 商户号，用于限定数据归属、权限范围和配置读取范围
     * @return 构造、转换或解析后的业务值
     */
    private String normalizeMerchantId(String merchantId) {
        return merchantId == null ? "" : merchantId.trim();
    }

    /**
     * 规范化trimtonull，返回调用链后续步骤可直接使用的业务值。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param value 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * 整理当前操作人名称，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private String currentOperatorName() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null) {
            return "system";
        }
        if (StringUtils.hasText(account.getRealName())) {
            return account.getRealName();
        }
        if (StringUtils.hasText(account.getLoginAccount())) {
            return account.getLoginAccount();
        }
        return "system";
    }

    /**
     * 整理bad请求，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 运营后台服务 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param message 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @return 方法执行后的业务结果、更新行数、转换对象或空结果
     */
    private ServiceException badRequest(String message) {
        return new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), message);
    }
}
