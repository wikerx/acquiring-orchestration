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
     * NOT DELETED 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final long NOT_DELETED = 0L;
    /**
     * ENABLED 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final int ENABLED = 1;
    /**
     * DISABLED 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final int DISABLED = 0;

    /**
     * whitelist Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final MerchantIpWhitelistMapper whitelistMapper;
    /**
     * access Config Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final MerchantOpenApiAccessConfigMapper accessConfigMapper;
    /**
     * merchant Info Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
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

    /**
     * 完成 list Matched Whitelists 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param condition condition 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
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

    /**
     * 查询 find Merchant Ids 所需数据，未命中时按调用场景返回空值或抛出异常。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param query query 输入值，含义由调用方法名称和所属业务对象限定
     * @return 解析或查询得到的业务值
     */
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

    /**
     * 查询 find Config Merchant Ids 所需数据，未命中时按调用场景返回空值或抛出异常。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param query query 输入值，含义由调用方法名称和所属业务对象限定
     * @return 解析或查询得到的业务值
     */
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
     * 完成 merge Merchant Ids 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param left left 输入值，含义由调用方法名称和所属业务对象限定
     * @param right right 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
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
     * 完成 group By Merchant 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param rows rows 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
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
     * 完成 paginate Merchant Ids 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param merchantIds 商户号，用于限定数据归属、幂等范围和权限边界
     * @param condition condition 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
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
 * 构建 build Whitelist 对应的领域对象、请求对象或日志对象。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param merchantId 商户号，用于限定数据归属、幂等范围和权限边界
 * @param ip ip 输入值，含义由调用方法名称和所属业务对象限定
 * @param status 状态编码，取值必须来自对应枚举或数据库受控字典
 * @param remark remark 输入值，含义由调用方法名称和所属业务对象限定
 * @param operator operator 输入值，含义由调用方法名称和所属业务对象限定
 * @param now now 输入值，含义由调用方法名称和所属业务对象限定
 * @return 转换或构建后的目标对象
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
     * 强制校验 require Whitelist 必填值，缺失时中断当前业务流程。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
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
     * 强制校验 require Merchant 必填值，缺失时中断当前业务流程。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param merchantId 商户号，用于限定数据归属、幂等范围和权限边界
     * @return 当前方法计算或转换后的业务结果
     */
    private BaseMerchantInfoDO requireMerchant(String merchantId) {
        BaseMerchantInfoDO merchant = findMerchant(merchantId);
        if (merchant == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "商户不存在");
        }
        return merchant;
    }

    /**
     * 查询 find Merchant 所需数据，未命中时按调用场景返回空值或抛出异常。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param merchantId 商户号，用于限定数据归属、幂等范围和权限边界
     * @return 解析或查询得到的业务值
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
     * 查询 find Config 所需数据，未命中时按调用场景返回空值或抛出异常。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param merchantId 商户号，用于限定数据归属、幂等范围和权限边界
     * @return 解析或查询得到的业务值
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
     * 查询 load Merchants 所需数据，未命中时按调用场景返回空值或抛出异常。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param merchantIds 商户号，用于限定数据归属、幂等范围和权限边界
     * @return 解析或查询得到的业务值
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
     * 查询 load Configs 所需数据，未命中时按调用场景返回空值或抛出异常。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param merchantIds 商户号，用于限定数据归属、幂等范围和权限边界
     * @return 解析或查询得到的业务值
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
     * 完成 distinct Merchant Ids 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param merchantIds 商户号，用于限定数据归属、幂等范围和权限边界
     * @return 当前方法计算或转换后的业务结果
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
     * 标准化 normalize Ip List 输入值，统一大小写、空白字符或协议格式。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param ipValues ip Values 输入值，含义由调用方法名称和所属业务对象限定
     * @return 标准化后的业务字段值
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
     * 标准化 normalize Ip 输入值，统一大小写、空白字符或协议格式。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param value 待校验或转换的原始值
     * @return 标准化后的业务字段值
     */
    private NormalizedIp normalizeIp(String value) {
        try {
            return IpAddressNormalizer.normalizeExact(value);
        } catch (IllegalArgumentException ex) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), ex.getMessage());
        }
    }

/**
 * 转换生成 to Response 对应的传输对象、导出行或协议字段。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param row row 输入值，含义由调用方法名称和所属业务对象限定
 * @param merchant merchant 输入值，含义由调用方法名称和所属业务对象限定
 * @param config config 输入值，含义由调用方法名称和所属业务对象限定
 * @return 转换或构建后的目标对象
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
 * 转换生成 to Aggregated Response 对应的传输对象、导出行或协议字段。
 * <p>
 * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
 * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
 * </p>
 * @param rows rows 输入值，含义由调用方法名称和所属业务对象限定
 * @param merchant merchant 输入值，含义由调用方法名称和所属业务对象限定
 * @param config config 输入值，含义由调用方法名称和所属业务对象限定
 * @return 转换或构建后的目标对象
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
     * 转换生成 to Item 对应的传输对象、导出行或协议字段。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param row row 输入值，含义由调用方法名称和所属业务对象限定
     * @return 转换或构建后的目标对象
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
     * 标准化 normalize Status 输入值，统一大小写、空白字符或协议格式。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param status 状态编码，取值必须来自对应枚举或数据库受控字典
     * @return 标准化后的业务字段值
     */
    private int normalizeStatus(Integer status) {
        return status != null && status == ENABLED ? ENABLED : DISABLED;
    }

    /**
     * 标准化 normalize Merchant Id 输入值，统一大小写、空白字符或协议格式。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param merchantId 商户号，用于限定数据归属、幂等范围和权限边界
     * @return 标准化后的业务字段值
     */
    private String normalizeMerchantId(String merchantId) {
        return merchantId == null ? "" : merchantId.trim();
    }

    /**
     * 完成 trim To Null 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param value 待校验或转换的原始值
     * @return 当前方法计算或转换后的业务结果
     */
    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * 完成 current Operator Name 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @return 当前方法计算或转换后的业务结果
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
     * 完成 bad Request 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param message 错误提示或消息内容，供异常转换、日志摘要或返回结果使用
     * @return 当前方法计算或转换后的业务结果
     */
    private ServiceException badRequest(String message) {
        return new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), message);
    }
}
