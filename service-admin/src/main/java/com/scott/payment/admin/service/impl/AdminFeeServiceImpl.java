package com.scott.payment.admin.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeePlanDetailResponse;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeePlanQuery;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeePlanSummaryResponse;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeeReviewResponse;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeeRuleRequest;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeeRuleResponse;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeeRuleTierRequest;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeeRuleTierResponse;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeeSimulationRequest;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeeSimulationRecordQuery;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeeSimulationRecordResponse;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeeSimulationResponse;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeeTemplateCreateRequest;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeeVersionResponse;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeeVersionSaveRequest;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.MerchantFeeVersionSaveRequest;
import com.scott.payment.admin.entity.fee.FeeEntities.FeePlanDO;
import com.scott.payment.admin.entity.fee.FeeEntities.FeePlanVersionDO;
import com.scott.payment.admin.entity.fee.FeeEntities.FeeRuleDO;
import com.scott.payment.admin.entity.fee.FeeEntities.FeeRuleTierDO;
import com.scott.payment.admin.entity.fee.FeeEntities.FeeSimulationRecordDO;
import com.scott.payment.admin.mapper.FeePlanMapper;
import com.scott.payment.admin.mapper.FeePlanVersionMapper;
import com.scott.payment.admin.mapper.FeeRuleMapper;
import com.scott.payment.admin.mapper.FeeRuleTierMapper;
import com.scott.payment.admin.mapper.FeeSimulationRecordMapper;
import com.scott.payment.admin.service.impl.AdminSettlementRateResolver.ResolvedSettlementRate;
import com.scott.payment.admin.service.AdminFeeService;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.cache.PaymentCacheNames;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.db.auth.entity.BaseMerchantInfoDO;
import com.scott.payment.component.db.auth.mapper.BaseMerchantInfoMapper;
import com.scott.payment.component.db.cache.service.ManagedCacheInvalidationCoordinator;
import com.scott.payment.component.db.constant.DataSourceName;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminFeeServiceImpl
 * @date : 2026-08-18 00:00
 * @email : scott_x@163.com
 * @description : 费用配置核心服务，保证模板复制隔离、版本串行分配、双人复核和审核时即时生效。
 * @status : create
 */
@Service
public class AdminFeeServiceImpl implements AdminFeeService {

    private static final long NOT_DELETED = 0L;
    private static final String TEMPLATE = "TEMPLATE";
    private static final String MERCHANT = "MERCHANT";
    private static final String DRAFT = "DRAFT";
    private static final String PENDING_REVIEW = "PENDING_REVIEW";
    private static final String ACTIVE = "ACTIVE";
    private static final String REJECTED = "REJECTED";
    private static final String SUPERSEDED = "SUPERSEDED";
    private static final String ALL = "ALL";
    private static final String BANK_CARD = "BANK_CARD";
    private static final String RISK_FEE = "RISK_FEE";
    private static final String SETTLEMENT_FX_FEE = "SETTLEMENT_FX_FEE";
    private static final String SETTLEMENT_FX_FEE_NAME = "结算货币兑换费";
    private static final String NONE = "NONE";
    private static final String NOT_APPLICABLE = "NOT_APPLICABLE";
    private static final int MAX_EXPANDED_RULES = 200;

    private final FeePlanMapper planMapper;
    private final FeePlanVersionMapper versionMapper;
    private final FeeRuleMapper ruleMapper;
    private final FeeRuleTierMapper tierMapper;
    private final FeeSimulationRecordMapper simulationRecordMapper;
    private final BaseMerchantInfoMapper merchantInfoMapper;
    private final AdminFeeSimulationCalculator simulationCalculator;
    private final AdminSettlementRateResolver settlementRateResolver;
    private final AdminMerchantSettlementCurrencyService merchantSettlementCurrencyService;
    private final ManagedCacheInvalidationCoordinator cacheInvalidationCoordinator;

    /** 构造费用配置服务。 */
    public AdminFeeServiceImpl(FeePlanMapper planMapper,
                               FeePlanVersionMapper versionMapper,
                               FeeRuleMapper ruleMapper,
                               FeeRuleTierMapper tierMapper,
                               FeeSimulationRecordMapper simulationRecordMapper,
                               BaseMerchantInfoMapper merchantInfoMapper,
                               AdminFeeSimulationCalculator simulationCalculator,
                               AdminSettlementRateResolver settlementRateResolver,
                               AdminMerchantSettlementCurrencyService merchantSettlementCurrencyService,
                               ManagedCacheInvalidationCoordinator cacheInvalidationCoordinator) {
        this.planMapper = planMapper;
        this.versionMapper = versionMapper;
        this.ruleMapper = ruleMapper;
        this.tierMapper = tierMapper;
        this.simulationRecordMapper = simulationRecordMapper;
        this.merchantInfoMapper = merchantInfoMapper;
        this.simulationCalculator = simulationCalculator;
        this.settlementRateResolver = settlementRateResolver;
        this.merchantSettlementCurrencyService = merchantSettlementCurrencyService;
        this.cacheInvalidationCoordinator = cacheInvalidationCoordinator;
    }

    /** {@inheritDoc} */
    @Override
    @DS(DataSourceName.SLAVE)
    public PageResult<FeePlanSummaryResponse> pageTemplates(FeePlanQuery request) {
        FeePlanQuery query = request == null ? new FeePlanQuery() : request;
        LambdaQueryWrapper<FeePlanDO> wrapper = Wrappers.<FeePlanDO>lambdaQuery()
                .eq(FeePlanDO::getPlanType, TEMPLATE)
                .eq(FeePlanDO::getDeleted, NOT_DELETED)
                .eq(StringUtils.hasText(query.getStatus()), FeePlanDO::getStatus, upper(query.getStatus()))
                .orderByDesc(FeePlanDO::getUpdateTime);
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(value -> value.like(FeePlanDO::getPlanCode, keyword)
                    .or().like(FeePlanDO::getPlanName, keyword));
        }
        Page<FeePlanDO> page = planMapper.selectPage(new Page<>(query.safePageNo(), query.safePageSize()), wrapper);
        List<FeePlanSummaryResponse> records = page.getRecords().stream().map(this::toSummary).toList();
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), records);
    }

    /** {@inheritDoc} */
    @Override
    @DS(DataSourceName.SLAVE)
    public FeePlanDetailResponse getTemplate(Long id) {
        return getPlanDetail(requirePlan(id, TEMPLATE));
    }

    /** {@inheritDoc} */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public FeePlanDetailResponse createTemplate(FeeTemplateCreateRequest request,
                                                Long operatorId,
                                                String operatorName) {
        request.setSettlementCurrency(null);
        validateVersionRequest(request, false, "首次创建模板");
        LocalDateTime now = LocalDateTime.now();
        FeePlanDO plan = new FeePlanDO();
        plan.setPlanCode(generateCode("FT"));
        plan.setPlanName(request.getPlanName().trim());
        plan.setPlanType(TEMPLATE);
        plan.setOriginType("INDEPENDENT");
        plan.setStatus("DISABLED");
        plan.setRemark(trimToNull(request.getRemark()));
        plan.setCreateBy(operatorName);
        plan.setCreateTime(now);
        plan.setUpdateBy(operatorName);
        plan.setUpdateTime(now);
        plan.setDeleted(NOT_DELETED);
        planMapper.insert(plan);
        createRequestedVersion(plan, request, "CREATED", null, null,
                "INDEPENDENT", DRAFT, operatorId, operatorName);
        return getPlanDetail(plan);
    }

    /** {@inheritDoc} */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public FeePlanDetailResponse createTemplateVersion(Long id,
                                                       FeeVersionSaveRequest request,
                                                       Long operatorId,
                                                       String operatorName) {
        request.setSettlementCurrency(null);
        validateVersionRequest(request, true, null);
        FeePlanDO plan = requireLockedPlan(id, TEMPLATE);
        if ("ARCHIVED".equals(plan.getStatus())) {
            throw conflict("归档模板不能创建新版本");
        }
        requireNoWorkingVersion(plan.getId());
        createRequestedVersion(plan, request, "UPDATED", null, null,
                "INDEPENDENT", DRAFT, operatorId, operatorName);
        touchPlan(plan, operatorName);
        return getPlanDetail(plan);
    }

    /** {@inheritDoc} */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public FeePlanDetailResponse updateTemplateDraft(Long planId,
                                                     Long versionId,
                                                     FeeVersionSaveRequest request,
                                                     Long operatorId,
                                                     String operatorName) {
        FeePlanVersionDO draft = requireLockedVersion(versionId);
        requirePlanVersion(draft, planId);
        requireDraft(draft);
        FeePlanDO plan = requireLockedPlan(planId, TEMPLATE);
        if ("ARCHIVED".equals(plan.getStatus())) {
            throw conflict("归档模板不能编辑草稿");
        }
        request.setSettlementCurrency(null);
        validateVersionRequest(request, draft.getVersionNo() > 1, "首次创建模板");

        LocalDateTime now = LocalDateTime.now();
        applyVersionSettings(draft, request, operatorId, operatorName, now);
        deleteDraftRules(draft.getId());
        insertRequestedRules(draft.getId(), request.getRules(), now);
        versionMapper.updateById(draft);
        touchPlan(plan, operatorName);
        return getPlanDetail(plan);
    }

    /** {@inheritDoc} */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public FeePlanDetailResponse submitTemplateVersion(Long versionId,
                                                       Long operatorId,
                                                       String operatorName) {
        FeePlanVersionDO draft = requireLockedVersion(versionId);
        requireDraft(draft);
        FeePlanDO plan = requireLockedPlan(draft.getPlanId(), TEMPLATE);
        if ("ARCHIVED".equals(plan.getStatus())) {
            throw conflict("归档模板不能提交审核");
        }
        LocalDateTime now = LocalDateTime.now();
        draft.setVersionStatus(PENDING_REVIEW);
        draft.setSubmitById(operatorId);
        draft.setSubmitByName(operatorName);
        draft.setSubmitTime(now);
        draft.setUpdateTime(now);
        versionMapper.updateById(draft);
        touchPlan(plan, operatorName);
        return getPlanDetail(plan);
    }

    /** {@inheritDoc} */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public FeePlanDetailResponse withdrawTemplateVersion(Long versionId,
                                                         Long operatorId,
                                                         String operatorName) {
        FeePlanVersionDO version = requireLockedVersion(versionId);
        requirePendingReview(version);
        if (!Objects.equals(version.getSubmitById(), operatorId)) {
            throw new ServiceException(ApiResultEnum.FORBIDDEN.getCode(), "待审核模板只能由原提交人撤回");
        }
        FeePlanDO plan = requireLockedPlan(version.getPlanId(), TEMPLATE);
        version.setVersionStatus(DRAFT);
        version.setSubmitById(operatorId);
        version.setSubmitByName(operatorName);
        version.setSubmitTime(LocalDateTime.now());
        version.setUpdateTime(version.getSubmitTime());
        versionMapper.updateById(version);
        touchPlan(plan, operatorName);
        return getPlanDetail(plan);
    }

    /** {@inheritDoc} */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public void updateTemplateStatus(Long id, boolean enabled, String operatorName) {
        FeePlanDO plan = requireLockedPlan(id, TEMPLATE);
        if ("ARCHIVED".equals(plan.getStatus())) {
            throw conflict("归档模板不能变更启停状态");
        }
        requireNoWorkingVersion(plan.getId());
        if (enabled && plan.getCurrentVersionId() == null) {
            throw conflict("模板尚无生效版本，不能启用");
        }
        String targetStatus = enabled ? "ENABLED" : "DISABLED";
        if (!targetStatus.equals(plan.getStatus())) {
            plan.setStatus(targetStatus);
            touchPlan(plan, operatorName);
        }
    }

    /** {@inheritDoc} */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public void archiveTemplate(Long id, String operatorName) {
        FeePlanDO plan = requireLockedPlan(id, TEMPLATE);
        requireNoWorkingVersion(plan.getId());
        plan.setStatus("ARCHIVED");
        touchPlan(plan, operatorName);
    }

    /** {@inheritDoc} */
    @Override
    @DS(DataSourceName.SLAVE)
    public PageResult<FeePlanSummaryResponse> pageMerchantFees(FeePlanQuery request) {
        FeePlanQuery query = request == null ? new FeePlanQuery() : request;
        List<FeePlanDO> configuredPlans = planMapper.selectList(Wrappers.<FeePlanDO>lambdaQuery()
                .eq(FeePlanDO::getPlanType, MERCHANT)
                .eq(FeePlanDO::getDeleted, NOT_DELETED));
        Map<String, FeePlanDO> planByMerchant = configuredPlans.stream()
                .collect(Collectors.toMap(FeePlanDO::getMerchantId, Function.identity(), (left, right) -> left));

        LambdaQueryWrapper<BaseMerchantInfoDO> merchantWrapper = Wrappers.<BaseMerchantInfoDO>lambdaQuery()
                .eq(BaseMerchantInfoDO::getDeleted, 0)
                .orderByDesc(BaseMerchantInfoDO::getGmtCreate);
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            merchantWrapper.and(value -> value.like(BaseMerchantInfoDO::getMerchantId, keyword)
                    .or().like(BaseMerchantInfoDO::getMerchantName, keyword)
                    .or().like(BaseMerchantInfoDO::getMerchantShortName, keyword));
        }
        applyMerchantFeeStatusFilter(merchantWrapper, configuredPlans, query.getStatus());
        Page<BaseMerchantInfoDO> page = merchantInfoMapper.selectPage(
                new Page<>(query.safePageNo(), query.safePageSize()), merchantWrapper);
        List<FeePlanSummaryResponse> records = page.getRecords().stream().map(merchant -> {
            FeePlanDO plan = planByMerchant.get(merchant.getMerchantId());
            FeePlanSummaryResponse response = plan == null ? unconfiguredMerchant(merchant) : toSummary(plan);
            response.setMerchantId(merchant.getMerchantId());
            response.setMerchantName(merchant.getMerchantName());
            response.setSettlementCurrency(upper(merchant.getSettlementCurrency()));
            return response;
        }).toList();
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), records);
    }

    /** {@inheritDoc} */
    @Override
    @DS(DataSourceName.SLAVE)
    public FeePlanDetailResponse getMerchantFee(String merchantId) {
        BaseMerchantInfoDO merchant = requireMerchant(merchantId);
        FeePlanDO plan = planMapper.selectOne(Wrappers.<FeePlanDO>lambdaQuery()
                .eq(FeePlanDO::getPlanType, MERCHANT)
                .eq(FeePlanDO::getMerchantId, merchantId)
                .eq(FeePlanDO::getDeleted, NOT_DELETED)
                .last("LIMIT 1"));
        if (plan == null) {
            return null;
        }
        FeePlanDetailResponse response = getPlanDetail(plan);
        response.setMerchantName(merchant.getMerchantName());
        response.setSettlementCurrency(upper(merchant.getSettlementCurrency()));
        return response;
    }

    /** {@inheritDoc} */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public FeePlanDetailResponse createMerchantVersion(String merchantId,
                                                       MerchantFeeVersionSaveRequest request,
                                                       Long operatorId,
                                                       String operatorName) {
        BaseMerchantInfoDO merchant = requireMerchant(merchantId);
        String requestedCurrency = StringUtils.hasText(request.getSettlementCurrency())
                ? request.getSettlementCurrency() : merchant.getSettlementCurrency();
        request.setSettlementCurrency(
                merchantSettlementCurrencyService.validateConfiguredCurrency(requestedCurrency));
        FeePlanDO plan = planMapper.selectMerchantPlanForUpdate(merchantId);
        boolean firstConfiguration = plan == null;
        if (plan == null) {
            plan = createMerchantPlan(merchant, request, operatorName);
        } else {
            requireNoWorkingVersion(plan.getId());
        }

        FeePlanDO sourceTemplate = null;
        FeePlanVersionDO sourceVersion = null;
        if (request.getTemplateId() != null) {
            sourceTemplate = requirePlan(request.getTemplateId(), TEMPLATE);
            if (!"ENABLED".equals(sourceTemplate.getStatus()) || sourceTemplate.getCurrentVersionId() == null) {
                throw conflict("只能选择当前启用且已有生效版本的模板");
            }
            sourceVersion = requireVersion(sourceTemplate.getCurrentVersionId());
        }

        boolean copyTemplateExactly = sourceVersion != null && (request.getRules() == null || request.getRules().isEmpty());
        if (copyTemplateExactly) {
            String changeReason = normalizedChangeReason(
                    request.getChangeReason(), !firstConfiguration, "首次绑定模板");
            createCopiedVersion(plan, sourceTemplate, sourceVersion, "TEMPLATE_ASSIGNED",
                    "TEMPLATE", changeReason, request.getSettlementCurrency(), operatorId, operatorName);
        } else {
            validateVersionRequest(request, !firstConfiguration, "首次配置");
            Long sourceTemplateId = sourceTemplate == null ? plan.getSourceTemplateId() : sourceTemplate.getId();
            Integer sourceTemplateVersionNo = sourceVersion == null
                    ? plan.getSourceTemplateVersionNo() : sourceVersion.getVersionNo();
            String originType = sourceTemplateId == null ? "INDEPENDENT" : "TEMPLATE_CUSTOMIZED";
            String changeType = sourceTemplateId == null ? "UPDATED" : "CUSTOMIZED";
            createRequestedVersion(plan, request, changeType, sourceTemplateId,
                    sourceTemplateVersionNo, originType, PENDING_REVIEW, operatorId, operatorName);
        }
        touchPlan(plan, operatorName);
        FeePlanDetailResponse response = getPlanDetail(plan);
        response.setMerchantName(merchant.getMerchantName());
        response.setSettlementCurrency(upper(merchant.getSettlementCurrency()));
        return response;
    }

    /** {@inheritDoc} */
    @Override
    @DS(DataSourceName.SLAVE)
    public PageResult<FeeReviewResponse> pageReviews(FeePlanQuery request) {
        FeePlanQuery query = request == null ? new FeePlanQuery() : request;
        Page<FeePlanVersionDO> page = versionMapper.selectPage(new Page<>(query.safePageNo(), query.safePageSize()),
                Wrappers.<FeePlanVersionDO>lambdaQuery()
                        .eq(FeePlanVersionDO::getVersionStatus,
                                StringUtils.hasText(query.getVersionStatus()) ? upper(query.getVersionStatus()) : PENDING_REVIEW)
                        .eq(FeePlanVersionDO::getDeleted, NOT_DELETED)
                        .orderByAsc(FeePlanVersionDO::getSubmitTime));
        Set<Long> planIds = page.getRecords().stream().map(FeePlanVersionDO::getPlanId).collect(Collectors.toSet());
        Map<Long, FeePlanDO> plans = planIds.isEmpty() ? Map.of() : planMapper.selectBatchIds(planIds).stream()
                .collect(Collectors.toMap(FeePlanDO::getId, Function.identity()));
        Map<String, String> merchantNames = merchantNames(plans.values().stream()
                .map(FeePlanDO::getMerchantId).filter(Objects::nonNull).collect(Collectors.toSet()));
        List<FeeReviewResponse> records = page.getRecords().stream()
                .map(version -> toReview(version, plans.get(version.getPlanId()), merchantNames)).toList();
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), records);
    }

    /** {@inheritDoc} */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public FeePlanDetailResponse approveVersion(Long versionId,
                                                String comment,
                                                Long reviewerId,
                                                String reviewerName) {
        FeePlanVersionDO version = requireLockedVersion(versionId);
        requirePendingReview(version);
        requireDifferentReviewer(version, reviewerId, reviewerName);
        FeePlanDO plan = requireLockedPlan(version.getPlanId(), null);
        if (MERCHANT.equals(plan.getPlanType())) {
            cacheInvalidationCoordinator.prepare(
                    PaymentCacheNames.MERCHANT_ACTIVE_FEE,
                    plan.getMerchantId()
            );
        }
        if (MERCHANT.equals(plan.getPlanType()) && StringUtils.hasText(version.getSettlementCurrency())) {
            merchantSettlementCurrencyService.synchronizeApprovedCurrency(
                    plan.getMerchantId(), version.getSettlementCurrency(), reviewerName);
        }
        LocalDateTime now = LocalDateTime.now();
        boolean firstActivation = plan.getCurrentVersionId() == null;

        if (plan.getCurrentVersionId() != null) {
            FeePlanVersionDO current = requireVersion(plan.getCurrentVersionId());
            current.setVersionStatus(SUPERSEDED);
            current.setSupersededTime(now);
            current.setUpdateTime(now);
            versionMapper.updateById(current);
        }
        version.setVersionStatus(ACTIVE);
        version.setReviewById(reviewerId);
        version.setReviewByName(reviewerName);
        version.setReviewComment(trimToNull(comment));
        version.setReviewTime(now);
        version.setEffectiveTime(now);
        version.setUpdateTime(now);
        versionMapper.updateById(version);

        plan.setCurrentVersionId(version.getId());
        plan.setCurrentVersionNo(version.getVersionNo());
        plan.setSourceTemplateId(version.getSourceTemplateId());
        plan.setSourceTemplateVersionNo(version.getSourceTemplateVersionNo());
        plan.setOriginType(version.getOriginType());
        if (MERCHANT.equals(plan.getPlanType()) || firstActivation || !"DISABLED".equals(plan.getStatus())) {
            plan.setStatus("ENABLED");
        }
        touchPlan(plan, reviewerName);
        return getPlanDetail(plan);
    }

    /** {@inheritDoc} */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public FeePlanDetailResponse rejectVersion(Long versionId,
                                               String comment,
                                               Long reviewerId,
                                               String reviewerName) {
        if (!StringUtils.hasText(comment)) {
            throw invalid("审核拒绝必须填写原因");
        }
        FeePlanVersionDO version = requireLockedVersion(versionId);
        requirePendingReview(version);
        requireDifferentReviewer(version, reviewerId, reviewerName);
        LocalDateTime now = LocalDateTime.now();
        version.setVersionStatus(REJECTED);
        version.setReviewById(reviewerId);
        version.setReviewByName(reviewerName);
        version.setReviewComment(comment.trim());
        version.setReviewTime(now);
        version.setUpdateTime(now);
        versionMapper.updateById(version);
        return getPlanDetail(requirePlan(version.getPlanId(), null));
    }

    /** {@inheritDoc} */
    @Override
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public FeeSimulationResponse simulate(FeeSimulationRequest request,
                                          Long operatorId,
                                          String operatorName) {
        FeePlanDO plan = planMapper.selectOne(Wrappers.<FeePlanDO>lambdaQuery()
                .eq(FeePlanDO::getPlanType, MERCHANT)
                .eq(FeePlanDO::getMerchantId, request.getMerchantId().trim())
                .eq(FeePlanDO::getStatus, "ENABLED")
                .eq(FeePlanDO::getDeleted, NOT_DELETED)
                .last("LIMIT 1"));
        if (plan == null || plan.getCurrentVersionId() == null) {
            throw conflict("商户尚未配置生效费率");
        }
        FeePlanVersionDO version = requireVersion(plan.getCurrentVersionId());
        if (!ACTIVE.equals(version.getVersionStatus())) {
            throw conflict("商户当前费率版本未生效");
        }
        String feeCategory = upper(request.getFeeCategory());
        String riskServiceType = normalizeSimulationRiskServiceType(feeCategory, request.getRiskServiceType());
        List<FeeRuleDO> candidates = ruleMapper.selectList(Wrappers.<FeeRuleDO>lambdaQuery()
                .eq(FeeRuleDO::getPlanVersionId, version.getId())
                .eq(FeeRuleDO::getFeeCategory, feeCategory)
                .eq(FeeRuleDO::getRiskServiceType, riskServiceType)
                .eq(FeeRuleDO::getTransactionType, upper(request.getTransactionType()))
                .eq(FeeRuleDO::getPaymentType, upper(request.getPaymentType()))
                .eq(FeeRuleDO::getDeleted, NOT_DELETED)
                .orderByAsc(FeeRuleDO::getSortNo)
                .orderByAsc(FeeRuleDO::getId));
        String method = upper(request.getPaymentMethod());
        FeeRuleDO rule = candidates.stream()
                .filter(item -> method.equals(item.getPaymentMethod()))
                .findFirst()
                .or(() -> candidates.stream().filter(item -> "ALL".equals(item.getPaymentMethod())).findFirst())
                .orElseThrow(() -> conflict("当前版本未匹配到费用规则"));
        List<FeeRuleTierDO> tiers = tierMapper.selectList(Wrappers.<FeeRuleTierDO>lambdaQuery()
                .eq(FeeRuleTierDO::getFeeRuleId, rule.getId())
                .eq(FeeRuleTierDO::getDeleted, NOT_DELETED)
                .orderByAsc(FeeRuleTierDO::getLowerBound));
        ResolvedSettlementRate resolvedRate = settlementRateResolver.resolve(
                request.getLabelCurrency(), LocalDateTime.now());
        FeeSimulationResponse response;
        try {
            response = simulationCalculator.calculate(request, rule, tiers, resolvedRate.rate(),
                    version.getReserveRate());
        } catch (IllegalArgumentException exception) {
            throw invalid(exception.getMessage());
        }
        String simulationNo = generateCode("FS");
        response.setSimulationNo(simulationNo);
        response.setPlanVersionId(version.getId());
        response.setSettlementRateId(resolvedRate.businessRateId());
        response.setSettlementRateSource(resolvedRate.sourceCode());
        response.setRateEffectiveTime(resolvedRate.effectiveTime());
        response.setRateValuationTime(resolvedRate.valuationTime());
        simulationRecordMapper.insert(toSimulationRecord(
                simulationNo, version.getId(), request, response, resolvedRate, operatorId, operatorName));
        return response;
    }

    /** {@inheritDoc} */
    @Override
    @DS(DataSourceName.SLAVE)
    public PageResult<FeeSimulationRecordResponse> pageSimulationRecords(FeeSimulationRecordQuery request) {
        FeeSimulationRecordQuery query = request == null ? new FeeSimulationRecordQuery() : request;
        LambdaQueryWrapper<FeeSimulationRecordDO> wrapper = Wrappers.<FeeSimulationRecordDO>lambdaQuery()
                .eq(StringUtils.hasText(query.getMerchantId()), FeeSimulationRecordDO::getMerchantId,
                        query.getMerchantId() == null ? null : query.getMerchantId().trim())
                .eq(StringUtils.hasText(query.getTransactionType()), FeeSimulationRecordDO::getTransactionType,
                        upper(query.getTransactionType()))
                .orderByDesc(FeeSimulationRecordDO::getCreateTime)
                .orderByDesc(FeeSimulationRecordDO::getId);
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.like(FeeSimulationRecordDO::getSimulationNo, query.getKeyword().trim());
        }
        Page<FeeSimulationRecordDO> page = simulationRecordMapper.selectPage(
                new Page<>(query.safePageNo(), query.safePageSize()), wrapper);
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(),
                page.getRecords().stream().map(this::toSimulationRecordResponse).toList());
    }

    private FeePlanDO createMerchantPlan(BaseMerchantInfoDO merchant,
                                         MerchantFeeVersionSaveRequest request,
                                         String operatorName) {
        LocalDateTime now = LocalDateTime.now();
        FeePlanDO plan = new FeePlanDO();
        plan.setPlanCode(generateCode("FM"));
        plan.setPlanName(StringUtils.hasText(request.getPlanName())
                ? request.getPlanName().trim() : merchant.getMerchantName() + "费率");
        plan.setPlanType(MERCHANT);
        plan.setMerchantId(merchant.getMerchantId());
        plan.setOriginType("INDEPENDENT");
        plan.setStatus("DISABLED");
        plan.setRemark(trimToNull(request.getRemark()));
        plan.setCreateBy(operatorName);
        plan.setCreateTime(now);
        plan.setUpdateBy(operatorName);
        plan.setUpdateTime(now);
        plan.setDeleted(NOT_DELETED);
        planMapper.insert(plan);
        return plan;
    }

    private FeePlanVersionDO createRequestedVersion(FeePlanDO plan,
                                                    FeeVersionSaveRequest request,
                                                    String changeType,
                                                    Long sourceTemplateId,
                                                    Integer sourceTemplateVersionNo,
                                                    String originType,
                                                    String versionStatus,
                                                    Long operatorId,
                                                    String operatorName) {
        LocalDateTime now = LocalDateTime.now();
        FeePlanVersionDO version = baseVersion(plan, changeType, request.getChangeReason(),
                sourceTemplateId, sourceTemplateVersionNo, originType, versionStatus,
                operatorId, operatorName, now);
        applyVersionSettings(version, request, operatorId, operatorName, now);
        versionMapper.insert(version);
        insertRequestedRules(version.getId(), request.getRules(), now);
        return version;
    }

    /**
     * 将已校验的费率配置写入草稿或新版本，并记录当前保存人快照。
     *
     * @param version 目标版本
     * @param request 已完成业务校验的配置
     * @param operatorId 当前保存账号 ID
     * @param operatorName 当前保存人名称
     * @param now 本次保存系统时间
     */
    private void applyVersionSettings(FeePlanVersionDO version,
                                      FeeVersionSaveRequest request,
                                      Long operatorId,
                                      String operatorName,
                                      LocalDateTime now) {
        version.setReserveRate(request.getReserveRate());
        version.setReserveDelayUnit(upper(request.getReserveDelayUnit()));
        version.setReserveDelayDays(request.getReserveDelayDays());
        version.setSettlementCurrency(trimUpper(request.getSettlementCurrency()));
        version.setInitialDelayUnit(upper(request.getInitialDelayUnit()));
        version.setInitialDelayDays(request.getInitialDelayDays());
        version.setRegularDelayDays(request.getRegularDelayDays());
        version.setSettlementFrequency(upper(request.getSettlementFrequency()));
        version.setFrequencyDay(normalizedFrequencyDay(request.getSettlementFrequency(), request.getFrequencyDay()));
        version.setChangeReason(request.getChangeReason());
        version.setSubmitById(operatorId);
        version.setSubmitByName(operatorName);
        version.setSubmitTime(now);
        version.setUpdateTime(now);
    }

    private FeePlanVersionDO createCopiedVersion(FeePlanDO plan,
                                                 FeePlanDO template,
                                                 FeePlanVersionDO source,
                                                 String changeType,
                                                 String originType,
                                                 String changeReason,
                                                 String settlementCurrency,
                                                 Long operatorId,
                                                 String operatorName) {
        LocalDateTime now = LocalDateTime.now();
        FeePlanVersionDO version = baseVersion(plan, changeType, changeReason,
                template.getId(), source.getVersionNo(), originType, PENDING_REVIEW,
                operatorId, operatorName, now);
        version.setReserveRate(source.getReserveRate());
        version.setReserveDelayUnit(StringUtils.hasText(source.getReserveDelayUnit())
                ? source.getReserveDelayUnit() : "D");
        version.setReserveDelayDays(source.getReserveDelayDays());
        version.setSettlementCurrency(trimUpper(settlementCurrency));
        version.setInitialDelayUnit(source.getInitialDelayUnit());
        version.setInitialDelayDays(source.getInitialDelayDays());
        version.setRegularDelayDays(source.getRegularDelayDays());
        version.setSettlementFrequency(source.getSettlementFrequency());
        version.setFrequencyDay(source.getFrequencyDay());
        versionMapper.insert(version);
        copyRules(source.getId(), version.getId(), now);
        return version;
    }

    private FeePlanVersionDO baseVersion(FeePlanDO plan,
                                         String changeType,
                                         String changeReason,
                                         Long sourceTemplateId,
                                         Integer sourceTemplateVersionNo,
                                         String originType,
                                         String versionStatus,
                                         Long operatorId,
                                         String operatorName,
                                         LocalDateTime now) {
        FeePlanVersionDO version = new FeePlanVersionDO();
        version.setPlanId(plan.getId());
        version.setVersionNo(nextVersionNo(plan.getId()));
        version.setVersionStatus(versionStatus);
        version.setChangeType(changeType);
        version.setSourceTemplateId(sourceTemplateId);
        version.setSourceTemplateVersionNo(sourceTemplateVersionNo);
        version.setOriginType(originType);
        version.setChangeReason(changeReason.trim());
        version.setSubmitById(operatorId);
        version.setSubmitByName(operatorName);
        version.setSubmitTime(now);
        version.setCreateTime(now);
        version.setUpdateTime(now);
        version.setDeleted(NOT_DELETED);
        return version;
    }

    /**
     * 删除尚未提交草稿的旧规则和阶梯，随后由调用方在同一事务内写入完整新快照。
     * 审核中、已生效和历史版本不会进入该方法。
     *
     * @param versionId 草稿版本主键
     */
    private void deleteDraftRules(Long versionId) {
        List<FeeRuleDO> existingRules = ruleMapper.selectList(Wrappers.<FeeRuleDO>lambdaQuery()
                .eq(FeeRuleDO::getPlanVersionId, versionId)
                .eq(FeeRuleDO::getDeleted, NOT_DELETED));
        if (existingRules.isEmpty()) {
            return;
        }
        List<Long> ruleIds = existingRules.stream().map(FeeRuleDO::getId).toList();
        tierMapper.delete(Wrappers.<FeeRuleTierDO>lambdaQuery()
                .in(FeeRuleTierDO::getFeeRuleId, ruleIds));
        ruleMapper.delete(Wrappers.<FeeRuleDO>lambdaQuery()
                .eq(FeeRuleDO::getPlanVersionId, versionId));
    }

    private void insertRequestedRules(Long versionId, List<FeeRuleRequest> requests, LocalDateTime now) {
        for (FeeRuleRequest request : requests) {
            FeeRuleDO rule = new FeeRuleDO();
            rule.setPlanVersionId(versionId);
            rule.setRuleGroupCode(request instanceof ExpandedFeeRuleRequest expanded
                    ? expanded.ruleGroupCode() : generateCode("FRG"));
            rule.setFeeCategory(upper(request.getFeeCategory()));
            rule.setRuleName(request.getRuleName().trim());
            rule.setTransactionType(upper(request.getTransactionType()));
            rule.setPaymentType(upper(request.getPaymentType()));
            rule.setPaymentMethod(upper(request.getPaymentMethod()));
            rule.setRiskServiceType(upper(request.getRiskServiceType()));
            rule.setChargeTrigger(upper(request.getChargeTrigger()));
            rule.setFeeMode(upper(request.getFeeMode()));
            rule.setPercentageRate(request.getPercentageRate());
            rule.setFixedAmountUsd(request.getFixedAmountUsd());
            rule.setMinimumAmountUsd(request.getMinimumAmountUsd());
            rule.setMaximumAmountUsd(request.getMaximumAmountUsd());
            rule.setTierMetric(trimUpper(request.getTierMetric()));
            rule.setTierPeriod("TIER".equals(rule.getFeeMode()) ? "MONTH" : null);
            rule.setSortNo(request.getSortNo() == null ? 0 : request.getSortNo());
            rule.setRemark(trimToNull(request.getRemark()));
            rule.setCreateTime(now);
            rule.setDeleted(NOT_DELETED);
            ruleMapper.insert(rule);
            for (FeeRuleTierRequest tierRequest : request.getTiers()) {
                FeeRuleTierDO tier = new FeeRuleTierDO();
                tier.setFeeRuleId(rule.getId());
                tier.setLowerBound(tierRequest.getLowerBound());
                tier.setUpperBound(tierRequest.getUpperBound());
                tier.setPercentageRate(tierRequest.getPercentageRate());
                tier.setFixedAmountUsd(tierRequest.getFixedAmountUsd());
                tier.setMinimumAmountUsd(tierRequest.getMinimumAmountUsd());
                tier.setMaximumAmountUsd(tierRequest.getMaximumAmountUsd());
                tier.setSortNo(tierRequest.getSortNo() == null ? 0 : tierRequest.getSortNo());
                tier.setCreateTime(now);
                tier.setDeleted(NOT_DELETED);
                tierMapper.insert(tier);
            }
        }
    }

    private void copyRules(Long sourceVersionId, Long targetVersionId, LocalDateTime now) {
        List<FeeRuleDO> sourceRules = ruleMapper.selectList(Wrappers.<FeeRuleDO>lambdaQuery()
                .eq(FeeRuleDO::getPlanVersionId, sourceVersionId)
                .eq(FeeRuleDO::getDeleted, NOT_DELETED)
                .orderByAsc(FeeRuleDO::getSortNo));
        for (FeeRuleDO sourceRule : sourceRules) {
            FeeRuleDO target = new FeeRuleDO();
            copyRuleValues(sourceRule, target);
            target.setId(null);
            target.setPlanVersionId(targetVersionId);
            target.setCreateTime(now);
            target.setDeleted(NOT_DELETED);
            ruleMapper.insert(target);
            List<FeeRuleTierDO> sourceTiers = tierMapper.selectList(Wrappers.<FeeRuleTierDO>lambdaQuery()
                    .eq(FeeRuleTierDO::getFeeRuleId, sourceRule.getId())
                    .eq(FeeRuleTierDO::getDeleted, NOT_DELETED)
                    .orderByAsc(FeeRuleTierDO::getLowerBound));
            for (FeeRuleTierDO sourceTier : sourceTiers) {
                FeeRuleTierDO targetTier = new FeeRuleTierDO();
                copyTierValues(sourceTier, targetTier);
                targetTier.setId(null);
                targetTier.setFeeRuleId(target.getId());
                targetTier.setCreateTime(now);
                targetTier.setDeleted(NOT_DELETED);
                tierMapper.insert(targetTier);
            }
        }
    }

    private void validateVersionRequest(FeeVersionSaveRequest request,
                                        boolean changeReasonRequired,
                                        String defaultChangeReason) {
        if (request == null) {
            throw invalid("费用版本配置不能为空");
        }
        if (request.getReserveRate() == null || request.getReserveRate().signum() < 0
                || request.getReserveRate().compareTo(new BigDecimal("100")) > 0) {
            throw invalid("滚动保证金比例必须在 0 至 100 之间");
        }
        String reserveUnit = upper(request.getReserveDelayUnit());
        if (!Set.of("T", "D").contains(reserveUnit)) {
            throw invalid("保证金留存周期单位只允许 T 或 D");
        }
        request.setReserveDelayUnit(reserveUnit);
        if (request.getReserveDelayDays() == null || request.getReserveDelayDays() < 1) {
            throw invalid("保证金留存周期最低为 T/D+1");
        }
        String initialUnit = upper(request.getInitialDelayUnit());
        if (!Set.of("T", "D").contains(initialUnit)) {
            throw invalid("结算周期单位只允许 T 或 D");
        }
        request.setInitialDelayUnit(initialUnit);
        request.setRegularDelayUnit(initialUnit);
        if (request.getInitialDelayDays() == null || request.getInitialDelayDays() < 1
                || request.getRegularDelayDays() == null || request.getRegularDelayDays() < 1) {
            throw invalid("系统最低支持 T/D+1");
        }
        normalizedFrequencyDay(request.getSettlementFrequency(), request.getFrequencyDay());
        request.setChangeReason(normalizedChangeReason(
                request.getChangeReason(), changeReasonRequired, defaultChangeReason));
        if (request.getRules() == null || request.getRules().isEmpty()) {
            throw invalid("至少配置一条费用规则");
        }
        long settlementFxFeeCount = request.getRules().stream()
                .filter(Objects::nonNull)
                .filter(rule -> SETTLEMENT_FX_FEE.equals(upper(rule.getFeeCategory())))
                .count();
        if (settlementFxFeeCount > 1) {
            throw invalid("同一费用版本的结算货币兑换费只能配置一条");
        }
        validateCategoryFeeModes(request.getRules());
        request.setRules(expandRules(request.getRules()));
        Set<String> dimensions = new HashSet<>();
        request.getRules().forEach(rule -> validateRule(rule, dimensions));
    }

    /**
     * 同一费用分类统一使用一种计费模式，避免结算匹配时同时出现标准与阶梯口径。
     */
    private void validateCategoryFeeModes(List<FeeRuleRequest> rules) {
        Map<String, String> categoryModes = new HashMap<>();
        for (FeeRuleRequest rule : rules) {
            if (rule == null) {
                continue;
            }
            String category = upper(rule.getFeeCategory());
            if (RISK_FEE.equals(category) || SETTLEMENT_FX_FEE.equals(category)) {
                continue;
            }
            String mode = upper(rule.getFeeMode());
            if (!Set.of("STANDARD", "TIER").contains(mode)) {
                continue;
            }
            String configuredMode = categoryModes.putIfAbsent(category, mode);
            if (configuredMode != null && !configuredMode.equals(mode)) {
                throw invalid("同一费用分类只能选择一种计费模式");
            }
        }
    }

    private String normalizedChangeReason(String changeReason,
                                          boolean required,
                                          String defaultChangeReason) {
        if (StringUtils.hasText(changeReason)) {
            return changeReason.trim();
        }
        if (required) {
            throw invalid("变更原因不能为空");
        }
        return defaultChangeReason;
    }

    private void validateRule(FeeRuleRequest rule, Set<String> dimensions) {
        if (rule == null) {
            throw invalid("费用规则不能为空");
        }
        String category = upper(rule.getFeeCategory());
        if (!Set.of("TRANSACTION_FEE", "REFUND_FEE", RISK_FEE,
                "DISPUTE_FEE", SETTLEMENT_FX_FEE).contains(category)) {
            throw invalid("不支持的费用分类");
        }
        rule.setFeeCategory(category);
        if (SETTLEMENT_FX_FEE.equals(category)) {
            normalizeSettlementFxRule(rule);
        } else if (!StringUtils.hasText(rule.getTransactionType())
                || !StringUtils.hasText(rule.getPaymentType())) {
            throw invalid("费用规则匹配维度不能为空");
        }
        String paymentMethod = StringUtils.hasText(rule.getPaymentMethod()) ? upper(rule.getPaymentMethod()) : ALL;
        rule.setPaymentMethod(paymentMethod);
        String riskServiceType = normalizeRiskRule(rule, category);
        String dimension = category + "|" + riskServiceType + "|" + upper(rule.getTransactionType()) + "|"
                + upper(rule.getPaymentType()) + "|" + paymentMethod;
        if (!dimensions.add(dimension)) {
            throw invalid("同一版本内费用匹配维度不能重复: " + dimension);
        }
        validateAmounts(rule.getPercentageRate(), rule.getFixedAmountUsd(),
                rule.getMinimumAmountUsd(), rule.getMaximumAmountUsd());
        if (RISK_FEE.equals(category)) {
            rule.setFeeMode("STANDARD");
            rule.setTierMetric(null);
            rule.setTierPeriod(null);
            rule.setTiers(new ArrayList<>());
            return;
        }
        if (SETTLEMENT_FX_FEE.equals(category)) {
            return;
        }
        String mode = upper(rule.getFeeMode());
        if (!Set.of("STANDARD", "TIER").contains(mode)) {
            throw invalid("计费模式只允许 STANDARD 或 TIER");
        }
        if ("STANDARD".equals(mode)) {
            rule.setTiers(new ArrayList<>());
            return;
        }
        String metric = upper(rule.getTierMetric());
        if (!Set.of("COUNT", "AMOUNT").contains(metric)) {
            throw invalid("阶梯指标只允许 COUNT 或 AMOUNT");
        }
        rule.setTierMetric(metric);
        validateTiers(rule.getTiers(), metric);
    }

    /**
     * 将结算货币兑换费收敛为无业务匹配维度的标准单笔规则。
     * 数据库非空维度统一使用 ALL，仅作为内部占位，不参与结算匹配。
     *
     * @param rule 待保存的结算货币兑换费规则
     */
    private void normalizeSettlementFxRule(FeeRuleRequest rule) {
        if (!"STANDARD".equals(upper(rule.getFeeMode()))) {
            throw invalid("结算货币兑换费只支持标准单笔计费");
        }
        rule.setRuleName(SETTLEMENT_FX_FEE_NAME);
        rule.setTransactionType(ALL);
        rule.setPaymentType(ALL);
        rule.setPaymentMethod(ALL);
        rule.setFeeMode("STANDARD");
        rule.setTierMetric(null);
        rule.setTierPeriod(null);
        rule.setTiers(new ArrayList<>());
    }

    /**
     * 规范化风控服务类型和收费触发方式，并收敛为固定 USD 单笔费用。
     *
     * @param rule 待保存原子规则
     * @param category 已规范化费用分类
     * @return 用于唯一匹配维度的风控类型；非风控费用返回 NONE
     */
    private String normalizeRiskRule(FeeRuleRequest rule, String category) {
        if (!RISK_FEE.equals(category)) {
            rule.setRiskServiceType(NONE);
            rule.setChargeTrigger(NOT_APPLICABLE);
            return NONE;
        }
        String riskServiceType = upper(rule.getRiskServiceType());
        if (!Set.of("INTERNAL", "EXTERNAL", "THREE_DS").contains(riskServiceType)) {
            throw invalid("风控手续费必须选择内风控、外风控或 3DS");
        }
        String chargeTrigger = upper(rule.getChargeTrigger());
        Set<String> allowedTriggers = "INTERNAL".equals(riskServiceType)
                ? Set.of("NO_CHARGE", "SUCCESS", "SUCCESS_OR_FAILURE")
                : Set.of("NO_CHARGE", "ON_CALL");
        if (!allowedTriggers.contains(chargeTrigger)) {
            throw invalid("风控手续费触发方式与风控类型不匹配");
        }
        rule.setRiskServiceType(riskServiceType);
        rule.setChargeTrigger(chargeTrigger);
        rule.setPercentageRate(BigDecimal.ZERO);
        rule.setMinimumAmountUsd(null);
        rule.setMaximumAmountUsd(null);
        if ("NO_CHARGE".equals(chargeTrigger)) {
            rule.setFixedAmountUsd(BigDecimal.ZERO);
        } else if (rule.getFixedAmountUsd() == null || rule.getFixedAmountUsd().signum() <= 0) {
            throw invalid("风控手续费实际收费时固定 USD 金额必须大于 0");
        }
        return riskServiceType;
    }

    /**
     * 归一费用试算的风控服务维度，避免同一交易维度下随机命中内风控、外风控或 3DS 规则。
     *
     * @param feeCategory 费用分类
     * @param requestedRiskServiceType 页面选择的风控服务类型
     * @return 风控费用的明确服务类型；非风控费用固定返回 NONE
     */
    private String normalizeSimulationRiskServiceType(String feeCategory, String requestedRiskServiceType) {
        if (!"RISK_FEE".equals(feeCategory)) {
            return "NONE";
        }
        String riskServiceType = upper(requestedRiskServiceType);
        if (!Set.of("INTERNAL", "EXTERNAL", "THREE_DS").contains(riskServiceType)) {
            throw invalid("风控手续费试算必须选择内风控、外风控或3DS");
        }
        return riskServiceType;
    }

    /**
     * 将页面批量选择展开为数据库可唯一匹配的原子规则。
     * 非银行卡支付类型不使用卡品牌维度，统一落为 ALL。
     */
    private List<FeeRuleRequest> expandRules(List<FeeRuleRequest> requests) {
        List<FeeRuleRequest> expanded = new ArrayList<>();
        for (FeeRuleRequest request : requests) {
            if (request == null) {
                throw invalid("费用规则不能为空");
            }
            String ruleGroupCode = generateCode("FRG");
            if (SETTLEMENT_FX_FEE.equals(upper(request.getFeeCategory()))) {
                FeeRuleRequest settlementFxRule = copyAtomicRule(request, ALL, ALL, ALL, ruleGroupCode);
                settlementFxRule.setRuleName(SETTLEMENT_FX_FEE_NAME);
                expanded.add(settlementFxRule);
                continue;
            }
            List<String> transactionTypes = dimensionValues(
                    request.getTransactionTypes(), request.getTransactionType(), "交易类型");
            List<String> paymentTypes = dimensionValues(
                    request.getPaymentTypes(), request.getPaymentType(), "支付类型");
            List<String> paymentMethods = List.of(ALL);
            if (paymentTypes.contains(BANK_CARD)) {
                paymentMethods = dimensionValues(
                        request.getPaymentMethods(), request.getPaymentMethod(), null);
                if (paymentMethods.size() > 1 && paymentMethods.contains(ALL)) {
                    throw invalid("支付方式不能同时选择 ALL 和具体卡品牌");
                }
            }
            for (String transactionType : transactionTypes) {
                for (String paymentType : paymentTypes) {
                    List<String> atomicMethods = BANK_CARD.equals(paymentType) ? paymentMethods : List.of(ALL);
                    for (String paymentMethod : atomicMethods) {
                        FeeRuleRequest atomicRule = copyAtomicRule(
                                request, transactionType, paymentType, paymentMethod, ruleGroupCode);
                        expanded.add(atomicRule);
                        if (expanded.size() > MAX_EXPANDED_RULES) {
                            throw invalid("单次提交展开后的费用规则不能超过 " + MAX_EXPANDED_RULES + " 条");
                        }
                    }
                }
            }
        }
        return expanded;
    }

    /**
     * 合并批量字段和兼容单值字段，按录入顺序去重并统一为大写。
     *
     * @param batchValues 新版页面提交的多选值
     * @param compatibleValue 旧版接口提交的单值
     * @param dimensionName 非空时表示该维度禁止使用 ALL
     * @return 已去重的标准化维度值
     */
    private List<String> dimensionValues(List<String> batchValues,
                                         String compatibleValue,
                                         String dimensionName) {
        List<String> source = batchValues == null || batchValues.isEmpty()
                ? List.of(StringUtils.hasText(compatibleValue) ? compatibleValue : ALL)
                : batchValues;
        LinkedHashSet<String> normalized = source.stream()
                .filter(StringUtils::hasText)
                .map(this::upper)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (normalized.isEmpty() || dimensionName != null && normalized.contains(ALL)) {
            throw invalid((dimensionName == null ? "支付方式" : dimensionName) + "不能为空");
        }
        return new ArrayList<>(normalized);
    }

    /**
     * 将一条页面批量规则复制为单一交易类型、支付类型和支付方式的原子规则。
     *
     * @param source 页面批量规则
     * @param transactionType 单一交易类型
     * @param paymentType 单一支付类型
     * @param paymentMethod 单一支付方式
     * @return 可直接校验并持久化的原子规则
     */
    private FeeRuleRequest copyAtomicRule(FeeRuleRequest source,
                                          String transactionType,
                                          String paymentType,
                                          String paymentMethod,
                                          String ruleGroupCode) {
        FeeRuleRequest target = new ExpandedFeeRuleRequest(ruleGroupCode);
        target.setFeeCategory(source.getFeeCategory());
        target.setRuleName(StringUtils.hasText(source.getRuleName())
                ? source.getRuleName().trim()
                : generatedRuleName(source.getFeeCategory(), transactionType, paymentType, paymentMethod));
        target.setTransactionType(transactionType);
        target.setPaymentType(paymentType);
        target.setPaymentMethod(paymentMethod);
        target.setRiskServiceType(source.getRiskServiceType());
        target.setChargeTrigger(source.getChargeTrigger());
        target.setFeeMode(source.getFeeMode());
        target.setPercentageRate(source.getPercentageRate());
        target.setFixedAmountUsd(source.getFixedAmountUsd());
        target.setMinimumAmountUsd(source.getMinimumAmountUsd());
        target.setMaximumAmountUsd(source.getMaximumAmountUsd());
        target.setTierMetric(source.getTierMetric());
        target.setTierPeriod(source.getTierPeriod());
        target.setSortNo(source.getSortNo());
        target.setRemark(source.getRemark());
        target.setTiers(source.getTiers() == null ? new ArrayList<>() : new ArrayList<>(source.getTiers()));
        return target;
    }

    /**
     * 为未填写配置名称的规则生成稳定、可识别的原子规则名称。
     *
     * @param feeCategory 费用分类
     * @param transactionType 交易类型
     * @param paymentType 支付类型
     * @param paymentMethod 支付方式
     * @return 最长 128 个字符的配置名称
     */
    private String generatedRuleName(String feeCategory,
                                     String transactionType,
                                     String paymentType,
                                     String paymentMethod) {
        String name = String.join("-", upper(feeCategory), transactionType, paymentType, paymentMethod);
        return name.length() <= 128 ? name : name.substring(0, 128);
    }

    private void validateTiers(List<FeeRuleTierRequest> tiers, String metric) {
        if (tiers == null || tiers.isEmpty()) {
            throw invalid("阶梯费率至少需要一个档位");
        }
        if (tiers.stream().anyMatch(item -> item == null || item.getLowerBound() == null)) {
            throw invalid("阶梯下界不能为空");
        }
        tiers.sort(Comparator.comparing(FeeRuleTierRequest::getLowerBound));
        if (tiers.get(0).getLowerBound().compareTo(BigDecimal.ZERO) != 0) {
            throw invalid("阶梯首档必须从 0 开始");
        }
        for (int index = 0; index < tiers.size(); index++) {
            FeeRuleTierRequest tier = tiers.get(index);
            validateAmounts(tier.getPercentageRate(), tier.getFixedAmountUsd(),
                    tier.getMinimumAmountUsd(), tier.getMaximumAmountUsd());
            if ("COUNT".equals(metric) && (tier.getLowerBound().stripTrailingZeros().scale() > 0
                    || tier.getUpperBound() != null && tier.getUpperBound().stripTrailingZeros().scale() > 0)) {
                throw invalid("笔数阶梯边界必须为整数");
            }
            boolean last = index == tiers.size() - 1;
            if (last && tier.getUpperBound() != null) {
                throw invalid("阶梯最后一档上界必须为空");
            }
            if (!last) {
                if (tier.getUpperBound() == null || tier.getUpperBound().compareTo(tier.getLowerBound()) <= 0) {
                    throw invalid("阶梯上界必须大于下界");
                }
                if (tier.getUpperBound().compareTo(tiers.get(index + 1).getLowerBound()) != 0) {
                    throw invalid("阶梯区间必须连续且不能重叠");
                }
            }
        }
    }

    private void validateAmounts(BigDecimal percentage,
                                 BigDecimal fixed,
                                 BigDecimal minimum,
                                 BigDecimal maximum) {
        if (percentage == null || percentage.signum() < 0 || fixed == null || fixed.signum() < 0) {
            throw invalid("百分比费率和 USD 固定费用不能为负数");
        }
        if ((minimum != null && minimum.signum() < 0) || (maximum != null && maximum.signum() < 0)) {
            throw invalid("USD 最低和最高费用不能为负数");
        }
        if (minimum != null && maximum != null && minimum.compareTo(maximum) > 0) {
            throw invalid("USD 最低费用不能大于最高费用");
        }
    }

    private Integer normalizedFrequencyDay(String frequencyValue, Integer day) {
        String frequency = upper(frequencyValue);
        if (!Set.of("DAILY", "WEEKLY", "BIWEEKLY", "MONTHLY").contains(frequency)) {
            throw invalid("不支持的结算频率");
        }
        if ("DAILY".equals(frequency)) {
            return null;
        }
        int maximum = "MONTHLY".equals(frequency) ? 28 : 7;
        if (day == null || day < 1 || day > maximum) {
            throw invalid("结算执行日超出允许范围");
        }
        return day;
    }

    private void applyMerchantFeeStatusFilter(LambdaQueryWrapper<BaseMerchantInfoDO> wrapper,
                                              List<FeePlanDO> plans,
                                              String statusValue) {
        if (!StringUtils.hasText(statusValue)) {
            return;
        }
        String status = upper(statusValue);
        Set<String> configuredIds = plans.stream().map(FeePlanDO::getMerchantId).collect(Collectors.toSet());
        if ("UNCONFIGURED".equals(status)) {
            if (!configuredIds.isEmpty()) {
                wrapper.notIn(BaseMerchantInfoDO::getMerchantId, configuredIds);
            }
            return;
        }
        Set<String> matchedIds = plans.stream().filter(item -> status.equals(item.getStatus()))
                .map(FeePlanDO::getMerchantId).collect(Collectors.toSet());
        if (matchedIds.isEmpty()) {
            wrapper.eq(BaseMerchantInfoDO::getMerchantId, "__NO_MATCH__");
        } else {
            wrapper.in(BaseMerchantInfoDO::getMerchantId, matchedIds);
        }
    }

    private FeePlanDetailResponse getPlanDetail(FeePlanDO plan) {
        FeePlanDetailResponse response = new FeePlanDetailResponse();
        copySummary(toSummary(plan), response);
        List<FeePlanVersionDO> versions = versionMapper.selectList(Wrappers.<FeePlanVersionDO>lambdaQuery()
                .eq(FeePlanVersionDO::getPlanId, plan.getId())
                .eq(FeePlanVersionDO::getDeleted, NOT_DELETED)
                .orderByDesc(FeePlanVersionDO::getVersionNo));
        Map<Long, List<FeeRuleResponse>> rulesByVersion = loadRulesByVersion(
                versions.stream().map(FeePlanVersionDO::getId).toList());
        response.setVersions(versions.stream()
                .map(version -> toVersion(version, rulesByVersion.getOrDefault(version.getId(), List.of())))
                .toList());
        response.setCurrentVersion(response.getVersions().stream()
                .filter(item -> Objects.equals(item.getId(), plan.getCurrentVersionId())).findFirst().orElse(null));
        return response;
    }

    private FeeVersionResponse toVersion(FeePlanVersionDO version, List<FeeRuleResponse> rules) {
        FeeVersionResponse response = new FeeVersionResponse();
        response.setId(version.getId());
        response.setPlanId(version.getPlanId());
        response.setVersionNo(version.getVersionNo());
        response.setVersionStatus(version.getVersionStatus());
        response.setChangeType(version.getChangeType());
        response.setSourceTemplateId(version.getSourceTemplateId());
        response.setSourceTemplateVersionNo(version.getSourceTemplateVersionNo());
        response.setOriginType(version.getOriginType());
        response.setReserveRate(version.getReserveRate());
        response.setReserveDelayUnit(StringUtils.hasText(version.getReserveDelayUnit())
                ? version.getReserveDelayUnit() : "D");
        response.setReserveDelayDays(version.getReserveDelayDays());
        response.setSettlementCurrency(version.getSettlementCurrency());
        response.setInitialDelayUnit(version.getInitialDelayUnit());
        response.setInitialDelayDays(version.getInitialDelayDays());
        response.setRegularDelayUnit(version.getInitialDelayUnit());
        response.setRegularDelayDays(version.getRegularDelayDays());
        response.setSettlementFrequency(version.getSettlementFrequency());
        response.setFrequencyDay(version.getFrequencyDay());
        response.setChangeReason(version.getChangeReason());
        response.setSubmitById(version.getSubmitById());
        response.setSubmitByName(version.getSubmitByName());
        response.setSubmitTime(version.getSubmitTime());
        response.setReviewById(version.getReviewById());
        response.setReviewByName(version.getReviewByName());
        response.setReviewComment(version.getReviewComment());
        response.setReviewTime(version.getReviewTime());
        response.setEffectiveTime(version.getEffectiveTime());
        response.setSupersededTime(version.getSupersededTime());
        response.setRules(rules);
        return response;
    }

    /**
     * 批量加载方案全部版本的规则和阶梯，查询次数不随版本数量增长。
     *
     * @param versionIds 方案内版本主键
     * @return 版本主键到逻辑规则列表的映射
     */
    private Map<Long, List<FeeRuleResponse>> loadRulesByVersion(List<Long> versionIds) {
        if (versionIds.isEmpty()) {
            return Map.of();
        }
        List<FeeRuleDO> rules = ruleMapper.selectList(Wrappers.<FeeRuleDO>lambdaQuery()
                .in(FeeRuleDO::getPlanVersionId, versionIds)
                .eq(FeeRuleDO::getDeleted, NOT_DELETED)
                .orderByAsc(FeeRuleDO::getPlanVersionId)
                .orderByAsc(FeeRuleDO::getSortNo)
                .orderByAsc(FeeRuleDO::getId));
        if (rules.isEmpty()) {
            return Map.of();
        }
        List<Long> ruleIds = rules.stream().map(FeeRuleDO::getId).toList();
        Map<Long, List<FeeRuleTierDO>> tiers = tierMapper.selectList(Wrappers.<FeeRuleTierDO>lambdaQuery()
                        .in(FeeRuleTierDO::getFeeRuleId, ruleIds)
                        .eq(FeeRuleTierDO::getDeleted, NOT_DELETED)
                        .orderByAsc(FeeRuleTierDO::getFeeRuleId)
                        .orderByAsc(FeeRuleTierDO::getSortNo)
                        .orderByAsc(FeeRuleTierDO::getLowerBound)).stream()
                .collect(Collectors.groupingBy(FeeRuleTierDO::getFeeRuleId,
                        LinkedHashMap::new, Collectors.toList()));
        Map<Long, List<FeeRuleDO>> atomicRulesByVersion = rules.stream()
                .collect(Collectors.groupingBy(FeeRuleDO::getPlanVersionId,
                        LinkedHashMap::new, Collectors.toList()));
        Map<Long, List<FeeRuleResponse>> result = new HashMap<>();
        atomicRulesByVersion.forEach((versionId, versionRules) ->
                result.put(versionId, toLogicalRules(versionRules, tiers)));
        return result;
    }

    /**
     * 将持久化原子规则还原为编辑页逻辑规则。新数据按服务端分组编码聚合；历史数据只有形成完整
     * 维度组合且全部计费属性一致时才聚合，防止保存时补出原本不存在的匹配组合。
     */
    private List<FeeRuleResponse> toLogicalRules(List<FeeRuleDO> rules,
                                                 Map<Long, List<FeeRuleTierDO>> tiersByRule) {
        Map<RuleGroupingKey, List<FeeRuleDO>> groups = new LinkedHashMap<>();
        for (FeeRuleDO rule : rules) {
            List<FeeRuleTierDO> tiers = tiersByRule.getOrDefault(rule.getId(), List.of());
            RuleGroupingKey key = StringUtils.hasText(rule.getRuleGroupCode())
                    ? new RuleGroupingKey(rule.getRuleGroupCode(), null)
                    : new RuleGroupingKey(null, legacyRuleKey(rule, tiers));
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(rule);
        }
        List<FeeRuleResponse> result = new ArrayList<>();
        for (Map.Entry<RuleGroupingKey, List<FeeRuleDO>> entry : groups.entrySet()) {
            List<FeeRuleDO> groupedRules = entry.getValue();
            if (entry.getKey().ruleGroupCode() != null || isCompleteDimensionProduct(groupedRules)) {
                result.add(toLogicalRule(groupedRules, tiersByRule));
            } else {
                groupedRules.forEach(rule -> result.add(toLogicalRule(List.of(rule), tiersByRule)));
            }
        }
        return result;
    }

    private LegacyRuleKey legacyRuleKey(FeeRuleDO rule, List<FeeRuleTierDO> tiers) {
        List<FeeTierSignature> tierSignatures = tiers.stream().map(tier -> new FeeTierSignature(
                canonicalDecimal(tier.getLowerBound()), canonicalDecimal(tier.getUpperBound()),
                canonicalDecimal(tier.getPercentageRate()), canonicalDecimal(tier.getFixedAmountUsd()),
                canonicalDecimal(tier.getMinimumAmountUsd()), canonicalDecimal(tier.getMaximumAmountUsd()),
                tier.getSortNo())).toList();
        return new LegacyRuleKey(rule.getFeeCategory(), rule.getRuleName(), rule.getRiskServiceType(),
                rule.getChargeTrigger(), rule.getFeeMode(), canonicalDecimal(rule.getPercentageRate()),
                canonicalDecimal(rule.getFixedAmountUsd()), canonicalDecimal(rule.getMinimumAmountUsd()),
                canonicalDecimal(rule.getMaximumAmountUsd()), rule.getTierMetric(), rule.getTierPeriod(),
                rule.getSortNo(), rule.getRemark(), tierSignatures);
    }

    private boolean isCompleteDimensionProduct(List<FeeRuleDO> rules) {
        LinkedHashSet<String> transactionTypes = new LinkedHashSet<>();
        LinkedHashSet<String> paymentTypes = new LinkedHashSet<>();
        LinkedHashSet<String> bankCardMethods = new LinkedHashSet<>();
        Set<AtomicRuleDimension> actual = new HashSet<>();
        for (FeeRuleDO rule : rules) {
            if (!StringUtils.hasText(rule.getTransactionType()) || !StringUtils.hasText(rule.getPaymentType())
                    || !StringUtils.hasText(rule.getPaymentMethod())) {
                return false;
            }
            transactionTypes.add(rule.getTransactionType());
            paymentTypes.add(rule.getPaymentType());
            if (BANK_CARD.equals(rule.getPaymentType())) {
                bankCardMethods.add(rule.getPaymentMethod());
            } else if (!ALL.equals(rule.getPaymentMethod())) {
                return false;
            }
            if (!actual.add(new AtomicRuleDimension(rule.getTransactionType(), rule.getPaymentType(),
                    rule.getPaymentMethod()))) {
                return false;
            }
        }
        if (paymentTypes.contains(BANK_CARD) && bankCardMethods.isEmpty()) {
            return false;
        }
        Set<AtomicRuleDimension> expected = new HashSet<>();
        for (String transactionType : transactionTypes) {
            for (String paymentType : paymentTypes) {
                List<String> paymentMethods = BANK_CARD.equals(paymentType)
                        ? new ArrayList<>(bankCardMethods) : List.of(ALL);
                for (String paymentMethod : paymentMethods) {
                    expected.add(new AtomicRuleDimension(transactionType, paymentType, paymentMethod));
                }
            }
        }
        return actual.equals(expected);
    }

    private FeeRuleResponse toLogicalRule(List<FeeRuleDO> rules,
                                          Map<Long, List<FeeRuleTierDO>> tiersByRule) {
        FeeRuleDO first = rules.get(0);
        FeeRuleResponse response = toRule(first, tiersByRule.getOrDefault(first.getId(), List.of()));
        List<String> paymentTypes = distinctRuleValues(rules, FeeRuleDO::getPaymentType);
        response.setTransactionTypes(distinctRuleValues(rules, FeeRuleDO::getTransactionType));
        response.setPaymentTypes(paymentTypes);
        response.setPaymentMethods(paymentTypes.contains(BANK_CARD)
                ? distinctRuleValues(rules.stream()
                        .filter(rule -> BANK_CARD.equals(rule.getPaymentType())).toList(), FeeRuleDO::getPaymentMethod)
                : List.of(ALL));
        return response;
    }

    private List<String> distinctRuleValues(List<FeeRuleDO> rules, Function<FeeRuleDO, String> extractor) {
        return rules.stream().map(extractor).filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new)).stream().toList();
    }

    private String canonicalDecimal(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private FeeRuleResponse toRule(FeeRuleDO rule, List<FeeRuleTierDO> tiers) {
        FeeRuleResponse response = new FeeRuleResponse();
        response.setId(rule.getId());
        response.setFeeCategory(rule.getFeeCategory());
        response.setRuleName(rule.getRuleName());
        response.setTransactionType(rule.getTransactionType());
        response.setPaymentType(rule.getPaymentType());
        response.setPaymentMethod(rule.getPaymentMethod());
        response.setRiskServiceType(rule.getRiskServiceType());
        response.setChargeTrigger(rule.getChargeTrigger());
        response.setFeeMode(rule.getFeeMode());
        response.setPercentageRate(rule.getPercentageRate());
        response.setFixedAmountUsd(rule.getFixedAmountUsd());
        response.setMinimumAmountUsd(rule.getMinimumAmountUsd());
        response.setMaximumAmountUsd(rule.getMaximumAmountUsd());
        response.setTierMetric(rule.getTierMetric());
        response.setTierPeriod(rule.getTierPeriod());
        response.setSortNo(rule.getSortNo());
        response.setRemark(rule.getRemark());
        response.setTiers(tiers.stream().map(this::toTier).toList());
        return response;
    }

    private FeeRuleTierResponse toTier(FeeRuleTierDO tier) {
        FeeRuleTierResponse response = new FeeRuleTierResponse();
        response.setId(tier.getId());
        response.setLowerBound(tier.getLowerBound());
        response.setUpperBound(tier.getUpperBound());
        response.setPercentageRate(tier.getPercentageRate());
        response.setFixedAmountUsd(tier.getFixedAmountUsd());
        response.setMinimumAmountUsd(tier.getMinimumAmountUsd());
        response.setMaximumAmountUsd(tier.getMaximumAmountUsd());
        response.setSortNo(tier.getSortNo());
        return response;
    }

    private FeePlanSummaryResponse toSummary(FeePlanDO plan) {
        FeePlanSummaryResponse response = new FeePlanSummaryResponse();
        response.setId(plan.getId());
        response.setPlanCode(plan.getPlanCode());
        response.setPlanName(plan.getPlanName());
        response.setPlanType(plan.getPlanType());
        response.setMerchantId(plan.getMerchantId());
        response.setSourceTemplateId(plan.getSourceTemplateId());
        response.setSourceTemplateVersionNo(plan.getSourceTemplateVersionNo());
        response.setOriginType(plan.getOriginType());
        response.setCurrentVersionId(plan.getCurrentVersionId());
        response.setCurrentVersionNo(plan.getCurrentVersionNo());
        response.setStatus(plan.getStatus());
        response.setRemark(plan.getRemark());
        FeePlanVersionDO workingVersion = workingVersion(plan.getId());
        if (workingVersion != null) {
            response.setPendingVersionId(workingVersion.getId());
            response.setPendingVersionNo(workingVersion.getVersionNo());
            response.setPendingVersionStatus(workingVersion.getVersionStatus());
            response.setPendingSubmitById(workingVersion.getSubmitById());
            if (plan.getCurrentVersionId() == null) {
                response.setSourceTemplateId(workingVersion.getSourceTemplateId());
                response.setSourceTemplateVersionNo(workingVersion.getSourceTemplateVersionNo());
                response.setOriginType(workingVersion.getOriginType());
            }
        }
        response.setCreateTime(plan.getCreateTime());
        response.setUpdateTime(plan.getUpdateTime());
        return response;
    }

    private void copySummary(FeePlanSummaryResponse source, FeePlanSummaryResponse target) {
        target.setId(source.getId());
        target.setPlanCode(source.getPlanCode());
        target.setPlanName(source.getPlanName());
        target.setPlanType(source.getPlanType());
        target.setMerchantId(source.getMerchantId());
        target.setMerchantName(source.getMerchantName());
        target.setSettlementCurrency(source.getSettlementCurrency());
        target.setSourceTemplateId(source.getSourceTemplateId());
        target.setSourceTemplateVersionNo(source.getSourceTemplateVersionNo());
        target.setOriginType(source.getOriginType());
        target.setCurrentVersionId(source.getCurrentVersionId());
        target.setCurrentVersionNo(source.getCurrentVersionNo());
        target.setStatus(source.getStatus());
        target.setRemark(source.getRemark());
        target.setPendingVersionId(source.getPendingVersionId());
        target.setPendingVersionNo(source.getPendingVersionNo());
        target.setPendingVersionStatus(source.getPendingVersionStatus());
        target.setPendingSubmitById(source.getPendingSubmitById());
        target.setCreateTime(source.getCreateTime());
        target.setUpdateTime(source.getUpdateTime());
    }

    private FeeReviewResponse toReview(FeePlanVersionDO version,
                                       FeePlanDO plan,
                                       Map<String, String> merchantNames) {
        FeeReviewResponse response = new FeeReviewResponse();
        response.setVersionId(version.getId());
        response.setPlanId(version.getPlanId());
        if (plan != null) {
            response.setPlanCode(plan.getPlanCode());
            response.setPlanName(plan.getPlanName());
            response.setPlanType(plan.getPlanType());
            response.setMerchantId(plan.getMerchantId());
            response.setMerchantName(merchantNames.get(plan.getMerchantId()));
        }
        response.setVersionNo(version.getVersionNo());
        response.setChangeType(version.getChangeType());
        response.setChangeReason(version.getChangeReason());
        response.setSubmitByName(version.getSubmitByName());
        response.setSubmitTime(version.getSubmitTime());
        return response;
    }

    private FeeSimulationRecordDO toSimulationRecord(String simulationNo,
                                                     Long planVersionId,
                                                     FeeSimulationRequest request,
                                                     FeeSimulationResponse response,
                                                     ResolvedSettlementRate resolvedRate,
                                                     Long operatorId,
                                                     String operatorName) {
        FeeSimulationRecordDO record = new FeeSimulationRecordDO();
        record.setSimulationNo(simulationNo);
        record.setPlanVersionId(planVersionId);
        record.setMerchantId(trimToNull(request.getMerchantId()));
        record.setFeeCategory(upper(request.getFeeCategory()));
        record.setTransactionType(upper(request.getTransactionType()));
        record.setPaymentType(upper(request.getPaymentType()));
        record.setPaymentMethod(upper(request.getPaymentMethod()));
        record.setRiskServiceType(normalizeSimulationRiskServiceType(
                record.getFeeCategory(), request.getRiskServiceType()));
        record.setLabelAmount(request.getLabelAmount());
        record.setLabelCurrency(upper(request.getLabelCurrency()));
        record.setLabelToUsdRate(resolvedRate.rate());
        record.setSettlementRateId(resolvedRate.businessRateId());
        record.setSettlementRateSource(resolvedRate.sourceCode());
        record.setRateEffectiveTime(resolvedRate.effectiveTime());
        record.setRateValuationTime(resolvedRate.valuationTime());
        record.setMonthlyCountBefore(request.getMonthlyCountBefore());
        record.setMonthlyAmountUsdBefore(request.getMonthlyAmountUsdBefore());
        record.setMatchedRuleId(response.getMatchedRuleId());
        record.setMatchedTierId(response.getMatchedTierId());
        record.setPercentageFeeLabel(response.getPercentageFeeLabel());
        record.setRawFeeUsd(response.getRawFeeUsd());
        record.setFinalFeeUsd(response.getFinalFeeUsd());
        record.setReserveAmountUsd(response.getReserveAmountUsd());
        record.setEstimatedNetSettlementUsd(response.getEstimatedNetSettlementUsd());
        record.setFormulaSnapshot(response.getFormulaSnapshot());
        record.setOperatorId(operatorId);
        record.setOperatorName(operatorName);
        record.setCreateTime(LocalDateTime.now());
        return record;
    }

    private FeeSimulationRecordResponse toSimulationRecordResponse(FeeSimulationRecordDO row) {
        FeeSimulationRecordResponse response = new FeeSimulationRecordResponse();
        response.setId(row.getId());
        response.setSimulationNo(row.getSimulationNo());
        response.setPlanVersionId(row.getPlanVersionId());
        response.setMerchantId(row.getMerchantId());
        response.setFeeCategory(row.getFeeCategory());
        response.setTransactionType(row.getTransactionType());
        response.setPaymentType(row.getPaymentType());
        response.setPaymentMethod(row.getPaymentMethod());
        response.setRiskServiceType(row.getRiskServiceType());
        response.setLabelAmount(row.getLabelAmount());
        response.setLabelCurrency(row.getLabelCurrency());
        response.setLabelToUsdRate(row.getLabelToUsdRate());
        response.setSettlementRateId(row.getSettlementRateId());
        response.setSettlementRateSource(row.getSettlementRateSource());
        response.setRateEffectiveTime(row.getRateEffectiveTime());
        response.setRateValuationTime(row.getRateValuationTime());
        response.setMatchedRuleId(row.getMatchedRuleId());
        response.setMatchedTierId(row.getMatchedTierId());
        response.setFinalFeeUsd(row.getFinalFeeUsd());
        response.setReserveAmountUsd(row.getReserveAmountUsd());
        response.setEstimatedNetSettlementUsd(row.getEstimatedNetSettlementUsd());
        response.setOperatorName(row.getOperatorName());
        response.setCreateTime(row.getCreateTime());
        return response;
    }

    private FeePlanSummaryResponse unconfiguredMerchant(BaseMerchantInfoDO merchant) {
        FeePlanSummaryResponse response = new FeePlanSummaryResponse();
        response.setMerchantId(merchant.getMerchantId());
        response.setMerchantName(merchant.getMerchantName());
        response.setSettlementCurrency(upper(merchant.getSettlementCurrency()));
        response.setPlanType(MERCHANT);
        response.setStatus("UNCONFIGURED");
        return response;
    }

    private FeePlanVersionDO workingVersion(Long planId) {
        return versionMapper.selectOne(Wrappers.<FeePlanVersionDO>lambdaQuery()
                .select(FeePlanVersionDO::getId, FeePlanVersionDO::getVersionNo,
                        FeePlanVersionDO::getVersionStatus, FeePlanVersionDO::getSubmitById,
                        FeePlanVersionDO::getSourceTemplateId,
                        FeePlanVersionDO::getSourceTemplateVersionNo,
                        FeePlanVersionDO::getOriginType)
                .eq(FeePlanVersionDO::getPlanId, planId)
                .in(FeePlanVersionDO::getVersionStatus, DRAFT, PENDING_REVIEW)
                .eq(FeePlanVersionDO::getDeleted, NOT_DELETED)
                .orderByDesc(FeePlanVersionDO::getVersionNo)
                .last("LIMIT 1"));
    }

    private int nextVersionNo(Long planId) {
        FeePlanVersionDO latest = versionMapper.selectOne(Wrappers.<FeePlanVersionDO>lambdaQuery()
                .select(FeePlanVersionDO::getVersionNo)
                .eq(FeePlanVersionDO::getPlanId, planId)
                .eq(FeePlanVersionDO::getDeleted, NOT_DELETED)
                .orderByDesc(FeePlanVersionDO::getVersionNo)
                .last("LIMIT 1"));
        return latest == null ? 1 : latest.getVersionNo() + 1;
    }

    private void requireNoWorkingVersion(Long planId) {
        Long count = versionMapper.selectCount(Wrappers.<FeePlanVersionDO>lambdaQuery()
                .eq(FeePlanVersionDO::getPlanId, planId)
                .in(FeePlanVersionDO::getVersionStatus, DRAFT, PENDING_REVIEW)
                .eq(FeePlanVersionDO::getDeleted, NOT_DELETED));
        if (count != null && count > 0) {
            throw conflict("当前方案已有草稿或待审核版本，请先处理当前版本");
        }
    }

    private FeePlanDO requirePlan(Long id, String expectedType) {
        FeePlanDO plan = planMapper.selectOne(Wrappers.<FeePlanDO>lambdaQuery()
                .eq(FeePlanDO::getId, id)
                .eq(expectedType != null, FeePlanDO::getPlanType, expectedType)
                .eq(FeePlanDO::getDeleted, NOT_DELETED)
                .last("LIMIT 1"));
        if (plan == null) {
            throw notFound("费用方案不存在");
        }
        return plan;
    }

    private FeePlanDO requireLockedPlan(Long id, String expectedType) {
        FeePlanDO plan = planMapper.selectByIdForUpdate(id);
        if (plan == null || expectedType != null && !expectedType.equals(plan.getPlanType())) {
            throw notFound("费用方案不存在");
        }
        return plan;
    }

    private FeePlanVersionDO requireVersion(Long id) {
        FeePlanVersionDO version = versionMapper.selectOne(Wrappers.<FeePlanVersionDO>lambdaQuery()
                .eq(FeePlanVersionDO::getId, id)
                .eq(FeePlanVersionDO::getDeleted, NOT_DELETED)
                .last("LIMIT 1"));
        if (version == null) {
            throw notFound("费用版本不存在");
        }
        return version;
    }

    private FeePlanVersionDO requireLockedVersion(Long id) {
        FeePlanVersionDO version = versionMapper.selectByIdForUpdate(id);
        if (version == null) {
            throw notFound("费用版本不存在");
        }
        return version;
    }

    private BaseMerchantInfoDO requireMerchant(String merchantId) {
        BaseMerchantInfoDO merchant = merchantInfoMapper.selectOne(Wrappers.<BaseMerchantInfoDO>lambdaQuery()
                .eq(BaseMerchantInfoDO::getMerchantId, merchantId)
                .eq(BaseMerchantInfoDO::getDeleted, 0)
                .last("LIMIT 1"));
        if (merchant == null) {
            throw notFound("商户不存在");
        }
        return merchant;
    }

    private void requirePendingReview(FeePlanVersionDO version) {
        if (!PENDING_REVIEW.equals(version.getVersionStatus())) {
            throw conflict("只有待审核版本可以执行该操作");
        }
    }

    private void requireDraft(FeePlanVersionDO version) {
        if (!DRAFT.equals(version.getVersionStatus())) {
            throw conflict("只有草稿版本可以编辑或提交审核");
        }
    }

    private void requirePlanVersion(FeePlanVersionDO version, Long planId) {
        if (!Objects.equals(version.getPlanId(), planId)) {
            throw notFound("费用模板草稿不存在");
        }
    }

    private void requireDifferentReviewer(FeePlanVersionDO version, Long reviewerId, String reviewerName) {
        boolean sameId = reviewerId != null && Objects.equals(version.getSubmitById(), reviewerId);
        boolean sameNameWithoutId = reviewerId == null && Objects.equals(version.getSubmitByName(), reviewerName);
        if (sameId || sameNameWithoutId) {
            throw new ServiceException(ApiResultEnum.FORBIDDEN.getCode(), "提交人与审核人不能是同一人");
        }
    }

    private void touchPlan(FeePlanDO plan, String operatorName) {
        plan.setUpdateBy(operatorName);
        plan.setUpdateTime(LocalDateTime.now());
        planMapper.updateById(plan);
    }

    private String merchantName(String merchantId) {
        return requireMerchant(merchantId).getMerchantName();
    }

    private Map<String, String> merchantNames(Set<String> merchantIds) {
        if (merchantIds.isEmpty()) {
            return Map.of();
        }
        return merchantInfoMapper.selectList(Wrappers.<BaseMerchantInfoDO>lambdaQuery()
                        .in(BaseMerchantInfoDO::getMerchantId, merchantIds)
                        .eq(BaseMerchantInfoDO::getDeleted, 0)).stream()
                .collect(Collectors.toMap(BaseMerchantInfoDO::getMerchantId,
                        BaseMerchantInfoDO::getMerchantName, (left, right) -> left));
    }

    private void copyRuleValues(FeeRuleDO source, FeeRuleDO target) {
        target.setRuleGroupCode(source.getRuleGroupCode());
        target.setFeeCategory(source.getFeeCategory());
        target.setRuleName(source.getRuleName());
        target.setTransactionType(source.getTransactionType());
        target.setPaymentType(source.getPaymentType());
        target.setPaymentMethod(source.getPaymentMethod());
        target.setRiskServiceType(source.getRiskServiceType());
        target.setChargeTrigger(source.getChargeTrigger());
        target.setFeeMode(source.getFeeMode());
        target.setPercentageRate(source.getPercentageRate());
        target.setFixedAmountUsd(source.getFixedAmountUsd());
        target.setMinimumAmountUsd(source.getMinimumAmountUsd());
        target.setMaximumAmountUsd(source.getMaximumAmountUsd());
        target.setTierMetric(source.getTierMetric());
        target.setTierPeriod(source.getTierPeriod());
        target.setSortNo(source.getSortNo());
        target.setRemark(source.getRemark());
    }

    private void copyTierValues(FeeRuleTierDO source, FeeRuleTierDO target) {
        target.setLowerBound(source.getLowerBound());
        target.setUpperBound(source.getUpperBound());
        target.setPercentageRate(source.getPercentageRate());
        target.setFixedAmountUsd(source.getFixedAmountUsd());
        target.setMinimumAmountUsd(source.getMinimumAmountUsd());
        target.setMaximumAmountUsd(source.getMaximumAmountUsd());
        target.setSortNo(source.getSortNo());
    }

    private void copySummary(FeePlanSummaryResponse source, FeePlanDetailResponse target) {
        copySummary(source, (FeePlanSummaryResponse) target);
    }

    private record RuleGroupingKey(String ruleGroupCode, LegacyRuleKey legacyRuleKey) {
    }

    private record LegacyRuleKey(String feeCategory,
                                 String ruleName,
                                 String riskServiceType,
                                 String chargeTrigger,
                                 String feeMode,
                                 String percentageRate,
                                 String fixedAmountUsd,
                                 String minimumAmountUsd,
                                 String maximumAmountUsd,
                                 String tierMetric,
                                 String tierPeriod,
                                 Integer sortNo,
                                 String remark,
                                 List<FeeTierSignature> tiers) {
    }

    private record FeeTierSignature(String lowerBound,
                                    String upperBound,
                                    String percentageRate,
                                    String fixedAmountUsd,
                                    String minimumAmountUsd,
                                    String maximumAmountUsd,
                                    Integer sortNo) {
    }

    private record AtomicRuleDimension(String transactionType, String paymentType, String paymentMethod) {
    }

    /** 服务端内部原子规则，分组编码不接受外部请求赋值。 */
    private static final class ExpandedFeeRuleRequest extends FeeRuleRequest {
        private final String ruleGroupCode;

        private ExpandedFeeRuleRequest(String ruleGroupCode) {
            this.ruleGroupCode = ruleGroupCode;
        }

        private String ruleGroupCode() {
            return ruleGroupCode;
        }
    }

    private String generateCode(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
    }

    private String upper(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String trimUpper(String value) {
        return StringUtils.hasText(value) ? upper(value) : null;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private ServiceException invalid(String message) {
        return new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), message);
    }

    private ServiceException conflict(String message) {
        return new ServiceException(ApiResultEnum.MERCHANT_CONFIG_NOT_FOUND.getCode(), message);
    }

    private ServiceException notFound(String message) {
        return new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), message);
    }
}
