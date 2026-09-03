package com.scott.payment.admin.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scott.payment.admin.application.risk.cache.RiskRuleCacheInvalidationCoordinator;
import com.scott.payment.admin.dto.merchant.AdminMerchantIpWhitelistDTOs.MerchantIpWhitelistConfigRequest;
import com.scott.payment.admin.dto.merchant.AdminMerchantIpWhitelistDTOs.MerchantIpWhitelistApprovalRequest;
import com.scott.payment.admin.dto.merchant.AdminMerchantIpWhitelistDTOs.MerchantIpWhitelistCreateRequest;
import com.scott.payment.admin.dto.merchant.AdminMerchantIpWhitelistDTOs.MerchantIpWhitelistItem;
import com.scott.payment.admin.dto.merchant.AdminMerchantIpWhitelistDTOs.MerchantIpWhitelistQuery;
import com.scott.payment.admin.dto.merchant.AdminMerchantIpWhitelistDTOs.MerchantIpWhitelistResponse;
import com.scott.payment.admin.dto.merchant.AdminMerchantIpWhitelistDTOs.MerchantIpWhitelistUpdateRequest;
import com.scott.payment.admin.dto.merchant.AdminMerchantIpWhitelistDTOs.MerchantIpWhitelistSubmissionRequest;
import com.scott.payment.admin.service.AdminMerchantIpWhitelistService;
import com.scott.payment.admin.service.MerchantAccessApprovalNotificationService;
import com.scott.payment.admin.support.approval.MerchantAccessApprovalStatus;
import com.scott.payment.admin.support.approval.MerchantAccessSubmitSource;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.cache.PaymentCacheNames;
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
import com.scott.payment.component.db.cache.service.ManagedCacheInvalidationCoordinator;
import com.scott.payment.component.db.constant.DataSourceName;
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
 * @description : admin商户ipwhitelist服务实现，位于 运营后台服务，执行该业务的规则校验和数据读写，并保持现有事务与异常边界。
 * @status : create
 */
@Service
public class AdminMerchantIpWhitelistServiceImpl implements AdminMerchantIpWhitelistService {

    /**
     * {@code NOT_DELETED}常量，统一 {@code AdminMerchantIpWhitelistServiceImpl} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final long NOT_DELETED = 0L;
    /**
     * 启用标识，表示当前配置项或业务能力的启停开关。
     * <p>
     * 单位：无；格式：布尔值或 0/1 标识；不允许为空；非敏感字段。
     * 取值范围：仅允许平台约定的真假取值；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final int ENABLED = 1;
    /**
     * {@code DISABLED}，表示当前配置项或业务能力的启停开关。
     * <p>
     * 单位：无；格式：布尔值或 0/1 标识；不允许为空；非敏感字段。
     * 取值范围：仅允许平台约定的真假取值；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final int DISABLED = 0;

    private final MerchantIpWhitelistMapper whitelistMapper;
    private final MerchantOpenApiAccessConfigMapper accessConfigMapper;
    private final BaseMerchantInfoMapper merchantInfoMapper;

    /**
     * 风控运行时规则缓存可靠失效协调器。
     */
    private final RiskRuleCacheInvalidationCoordinator cacheInvalidationCoordinator;

    /**
     * 商户 OpenAPI 访问策略缓存可靠失效协调器。
     */
    private final ManagedCacheInvalidationCoordinator securityCacheInvalidationCoordinator;

    /** 审批结果邮件通知服务。 */
    private final MerchantAccessApprovalNotificationService approvalNotificationService;

    /**
     * 创建商户 IP 白名单服务实现。
     *
     * @param whitelistMapper    白名单 Mapper
     * @param accessConfigMapper OpenAPI 访问配置 Mapper
     * @param merchantInfoMapper 商户基础资料 Mapper
     * @param cacheInvalidationCoordinator 风控规则缓存失效协调器
     * @param securityCacheInvalidationCoordinator 商户安全缓存可靠失效协调器
     */
    public AdminMerchantIpWhitelistServiceImpl(MerchantIpWhitelistMapper whitelistMapper,
                                               MerchantOpenApiAccessConfigMapper accessConfigMapper,
                                               BaseMerchantInfoMapper merchantInfoMapper,
                                               RiskRuleCacheInvalidationCoordinator cacheInvalidationCoordinator,
                                               ManagedCacheInvalidationCoordinator
                                                       securityCacheInvalidationCoordinator,
                                               MerchantAccessApprovalNotificationService approvalNotificationService) {
        this.whitelistMapper = whitelistMapper;
        this.accessConfigMapper = accessConfigMapper;
        this.merchantInfoMapper = merchantInfoMapper;
        this.cacheInvalidationCoordinator = cacheInvalidationCoordinator;
        this.securityCacheInvalidationCoordinator = securityCacheInvalidationCoordinator;
        this.approvalNotificationService = approvalNotificationService;
    }

    /**
     * 分页查询商户 IP 白名单记录。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @Override
    @DS(DataSourceName.SLAVE)
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
    @DS(DataSourceName.SLAVE)
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
     * 查询满足商户、开关、IP 类型、状态和 IP 内容条件的原始白名单记录。
     *
     * <p>商户关键词或开关条件明确筛选为空时返回空集合；未提供对应筛选条件时
     * 使用 {@code null} 表示不限制商户范围。</p>
     *
     * @param condition 已补齐默认值的白名单查询
     * @return 按更新时间和主键倒序的原始白名单记录
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
                .eq(condition.getApprovalStatus() != null, MerchantIpWhitelistDO::getApprovalStatus, condition.getApprovalStatus())
                .eq(StringUtils.hasText(condition.getSubmitSource()), MerchantIpWhitelistDO::getSubmitSource,
                        condition.getSubmitSource() == null ? null : condition.getSubmitSource().trim().toUpperCase())
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
    @DS(DataSourceName.SLAVE)
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
        cacheInvalidationCoordinator.prepare();
        securityCacheInvalidationCoordinator.prepare(
                PaymentCacheNames.MERCHANT_OPENAPI_ACCESS,
                merchant.getMerchantId()
        );
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
        cacheInvalidationCoordinator.prepare();
        securityCacheInvalidationCoordinator.prepare(
                PaymentCacheNames.MERCHANT_OPENAPI_ACCESS,
                existing.getMerchantId()
        );
        existing.setIpType(ip.ipType());
        existing.setIpValue(ip.ipValue());
        existing.setStatus(isApproved(existing) ? normalizeStatus(request.getStatus()) : DISABLED);
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
        requireApproved(row);
        cacheInvalidationCoordinator.prepare();
        securityCacheInvalidationCoordinator.prepare(
                PaymentCacheNames.MERCHANT_OPENAPI_ACCESS,
                row.getMerchantId()
        );
        row.setStatus(normalizeStatus(status));
        row.setUpdateBy(currentOperatorName());
        row.setGmtModified(LocalDateTime.now());
        whitelistMapper.updateById(row);
        return getWhitelist(id);
    }

    /**
     * 审批商户提交的 IP 白名单记录，使用待审核状态作为 CAS 条件防止重复审批。
     *
     * @param id      白名单记录 ID
     * @param request 审批请求
     * @return 审批后的记录
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MerchantIpWhitelistResponse approveWhitelist(Long id, MerchantIpWhitelistApprovalRequest request) {
        if (request == null) {
            throw badRequest("审批请求不能为空");
        }
        MerchantAccessApprovalStatus approvalStatus;
        try {
            approvalStatus = MerchantAccessApprovalStatus.fromCode(request.getApprovalStatus());
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception.getMessage());
        }
        if (approvalStatus == MerchantAccessApprovalStatus.PENDING) {
            throw badRequest("审批结果只能是审核通过或审核拒绝");
        }
        String approvalRemark = trimToNull(request.getApprovalRemark());
        if (approvalStatus == MerchantAccessApprovalStatus.REJECTED && !StringUtils.hasText(approvalRemark)) {
            throw badRequest("审核拒绝时必须填写拒绝原因");
        }
        MerchantIpWhitelistDO row = requireWhitelist(id);
        if (row.getApprovalStatus() == null
                || row.getApprovalStatus() != MerchantAccessApprovalStatus.PENDING.code()) {
            throw badRequest("仅待审核记录允许审批");
        }
        int transactionStatus;
        try {
            transactionStatus = approvalStatus.transactionStatus(request.getStatus());
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception.getMessage());
        }
        String operator = currentOperatorName();
        LocalDateTime reviewTime = LocalDateTime.now();
        cacheInvalidationCoordinator.prepare();
        securityCacheInvalidationCoordinator.prepare(PaymentCacheNames.MERCHANT_OPENAPI_ACCESS, row.getMerchantId());
        int updated = whitelistMapper.update(null, Wrappers.<MerchantIpWhitelistDO>lambdaUpdate()
                .set(MerchantIpWhitelistDO::getApprovalStatus, approvalStatus.code())
                .set(MerchantIpWhitelistDO::getApprovalRemark, approvalRemark)
                .set(MerchantIpWhitelistDO::getStatus, transactionStatus)
                .set(MerchantIpWhitelistDO::getReviewBy, operator)
                .set(MerchantIpWhitelistDO::getReviewTime, reviewTime)
                .set(MerchantIpWhitelistDO::getUpdateBy, operator)
                .set(MerchantIpWhitelistDO::getGmtModified, reviewTime)
                .eq(MerchantIpWhitelistDO::getId, id)
                .eq(MerchantIpWhitelistDO::getDeleted, NOT_DELETED)
                .eq(MerchantIpWhitelistDO::getApprovalStatus, MerchantAccessApprovalStatus.PENDING.code()));
        if (updated != 1) {
            throw badRequest("记录已被其他操作员审批，请刷新后重试");
        }
        BaseMerchantInfoDO merchant = requireMerchant(row.getMerchantId());
        approvalNotificationService.sendAfterCommit(
                merchant,
                MerchantAccessApprovalNotificationService.TYPE_IP_WHITELIST,
                row.getIpValue(),
                approvalStatus,
                transactionStatus,
                approvalRemark,
                reviewTime
        );
        return getWhitelist(id);
    }

    /**
     * 查询指定商户自己的全部 IP 白名单记录。
     *
     * @param merchantId 已认证商户号
     * @return 未删除记录列表
     */
    @Override
    @DS(DataSourceName.MASTER)
    public List<MerchantIpWhitelistResponse> listMerchantWhitelists(String merchantId) {
        BaseMerchantInfoDO merchant = requireMerchant(merchantId);
        MerchantOpenApiAccessConfigDO config = findConfig(merchant.getMerchantId());
        return whitelistMapper.selectList(Wrappers.<MerchantIpWhitelistDO>lambdaQuery()
                        .eq(MerchantIpWhitelistDO::getMerchantId, merchant.getMerchantId())
                        .eq(MerchantIpWhitelistDO::getDeleted, NOT_DELETED)
                        .orderByDesc(MerchantIpWhitelistDO::getGmtModified)
                        .orderByDesc(MerchantIpWhitelistDO::getId))
                .stream()
                .map(row -> toResponse(row, merchant, config))
                .toList();
    }

    /**
     * 新增商户提交的待审核 IP 白名单，交易状态固定为禁止。
     *
     * @param merchantId 已认证商户号
     * @param request    IP 列表和提交说明
     * @return 新增待审核记录
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<MerchantIpWhitelistResponse> submitMerchantWhitelists(
            String merchantId, MerchantIpWhitelistSubmissionRequest request) {
        if (request == null) {
            throw badRequest("白名单请求不能为空");
        }
        BaseMerchantInfoDO merchant = requireMerchant(merchantId);
        List<NormalizedIp> normalizedIps = normalizeIpList(request.getIpValues());
        String operator = MerchantAccessSubmitSource.MERCHANT.name() + ":" + merchant.getMerchantId();
        LocalDateTime now = LocalDateTime.now();
        List<MerchantIpWhitelistDO> created = normalizedIps.stream().map(ip -> {
            MerchantIpWhitelistDO row = buildWhitelist(
                    merchant.getMerchantId(), ip, DISABLED, request.getRemark(), operator, now);
            row.setApprovalStatus(MerchantAccessApprovalStatus.PENDING.code());
            row.setSubmitSource(MerchantAccessSubmitSource.MERCHANT.name());
            row.setReviewBy(null);
            row.setReviewTime(null);
            return row;
        }).toList();
        try {
            created.forEach(whitelistMapper::insert);
        } catch (DuplicateKeyException exception) {
            throw badRequest("同一商户下 IP 白名单不能重复");
        }
        MerchantOpenApiAccessConfigDO config = findConfig(merchant.getMerchantId());
        return created.stream().map(row -> toResponse(row, merchant, config)).toList();
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
        cacheInvalidationCoordinator.prepare();
        securityCacheInvalidationCoordinator.prepare(
                PaymentCacheNames.MERCHANT_OPENAPI_ACCESS,
                row.getMerchantId()
        );
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
        cacheInvalidationCoordinator.prepare();
        securityCacheInvalidationCoordinator.prepare(
                PaymentCacheNames.MERCHANT_OPENAPI_ACCESS,
                merchant.getMerchantId()
        );
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
     * 按商户号、名称或简称关键词查询未删除商户号。
     *
     * @param query 白名单查询条件
     * @return 匹配的商户号；未提供关键词时返回 {@code null} 表示不限制
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
     * 按商户 IP 白名单总开关筛选商户范围。
     *
     * <p>启用条件只返回显式开启的商户；停用条件同时包含显式关闭和未配置的商户，
     * 并结合已有白名单记录形成可查询范围。</p>
     *
     * @param query 白名单查询条件
     * @return 开关条件对应的商户号；未指定开关时返回 {@code null}
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

    private List<String> paginateMerchantIds(List<String> merchantIds, MerchantIpWhitelistQuery condition) {
        if (merchantIds == null || merchantIds.isEmpty()) {
            return List.of();
        }
        int fromIndex = (int) Math.min((condition.safePageNo() - 1) * condition.safePageSize(), merchantIds.size());
        int toIndex = (int) Math.min(fromIndex + condition.safePageSize(), merchantIds.size());
        return merchantIds.subList(fromIndex, toIndex);
    }

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
        row.setApprovalStatus(MerchantAccessApprovalStatus.APPROVED.code());
        row.setApprovalRemark(null);
        row.setSubmitSource(MerchantAccessSubmitSource.ADMIN.name());
        row.setReviewBy(operator);
        row.setReviewTime(now);
        row.setRemark(trimToNull(remark));
        row.setCreateBy(operator);
        row.setUpdateBy(operator);
        row.setGmtCreate(now);
        row.setGmtModified(now);
        row.setDeleted(NOT_DELETED);
        return row;
    }

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

    private BaseMerchantInfoDO requireMerchant(String merchantId) {
        BaseMerchantInfoDO merchant = findMerchant(merchantId);
        if (merchant == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "商户不存在");
        }
        return merchant;
    }

    private BaseMerchantInfoDO findMerchant(String merchantId) {
        if (!StringUtils.hasText(merchantId)) {
            return null;
        }
        return merchantInfoMapper.selectOne(Wrappers.<BaseMerchantInfoDO>lambdaQuery()
                .eq(BaseMerchantInfoDO::getDeleted, 0)
                .eq(BaseMerchantInfoDO::getMerchantId, normalizeMerchantId(merchantId))
                .last("LIMIT 1"));
    }

    private MerchantOpenApiAccessConfigDO findConfig(String merchantId) {
        if (!StringUtils.hasText(merchantId)) {
            return null;
        }
        return accessConfigMapper.selectOne(Wrappers.<MerchantOpenApiAccessConfigDO>lambdaQuery()
                .eq(MerchantOpenApiAccessConfigDO::getDeleted, NOT_DELETED)
                .eq(MerchantOpenApiAccessConfigDO::getMerchantId, normalizeMerchantId(merchantId))
                .last("LIMIT 1"));
    }

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

    private NormalizedIp normalizeIp(String value) {
        try {
            return IpAddressNormalizer.normalizeExact(value);
        } catch (IllegalArgumentException ex) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), ex.getMessage());
        }
    }

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
            response.setApprovalStatus(row.getApprovalStatus());
            response.setApprovalRemark(row.getApprovalRemark());
            response.setSubmitSource(row.getSubmitSource());
            response.setReviewBy(row.getReviewBy());
            response.setReviewTime(row.getReviewTime());
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

    private MerchantIpWhitelistItem toItem(MerchantIpWhitelistDO row) {
        MerchantIpWhitelistItem item = new MerchantIpWhitelistItem();
        item.setId(row.getId());
        item.setIpType(row.getIpType());
        item.setIpValue(row.getIpValue());
        item.setStatus(row.getStatus());
        item.setApprovalStatus(row.getApprovalStatus());
        item.setApprovalRemark(row.getApprovalRemark());
        item.setSubmitSource(row.getSubmitSource());
        item.setReviewBy(row.getReviewBy());
        item.setReviewTime(row.getReviewTime());
        item.setRemark(row.getRemark());
        item.setUpdateBy(row.getUpdateBy());
        item.setGmtModified(row.getGmtModified());
        return item;
    }

    private int normalizeStatus(Integer status) {
        return status != null && status == ENABLED ? ENABLED : DISABLED;
    }

    private boolean isApproved(MerchantIpWhitelistDO row) {
        return row != null && row.getApprovalStatus() != null
                && row.getApprovalStatus() == MerchantAccessApprovalStatus.APPROVED.code();
    }

    private void requireApproved(MerchantIpWhitelistDO row) {
        if (!isApproved(row)) {
            throw badRequest("仅审核通过的记录允许修改交易状态");
        }
    }

    private String normalizeMerchantId(String merchantId) {
        return merchantId == null ? "" : merchantId.trim();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

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

    private ServiceException badRequest(String message) {
        return new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), message);
    }
}
