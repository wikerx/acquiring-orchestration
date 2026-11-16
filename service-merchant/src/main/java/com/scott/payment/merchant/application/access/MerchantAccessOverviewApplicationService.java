package com.scott.payment.merchant.application.access;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.security.openapi.OpenApiMerchantKeyMaterialService;
import com.scott.payment.component.security.openapi.OpenApiMerchantKeyMaterialVO;
import com.scott.payment.merchant.dto.access.MerchantAccessOverviewDTOs.AccessCheckStatus;
import com.scott.payment.merchant.dto.access.MerchantAccessOverviewDTOs.AccessOverview;
import com.scott.payment.merchant.dto.access.MerchantAccessOverviewDTOs.AccessRuleCheck;
import com.scott.payment.merchant.dto.access.MerchantAccessOverviewDTOs.CredentialCheck;
import com.scott.payment.merchant.dto.access.MerchantAccessOverviewDTOs.MerchantProfileCheck;
import com.scott.payment.merchant.dto.access.MerchantAccessOverviewDTOs.PaymentCapability;
import com.scott.payment.merchant.dto.access.MerchantAccessOverviewDTOs.PaymentCapabilityCheck;
import com.scott.payment.merchant.dto.access.MerchantAccessOverviewDTOs.PlatformConfigurationCheck;
import com.scott.payment.merchant.dto.access.MerchantAccessOverviewDTOs.SettlementCheck;
import com.scott.payment.merchant.service.MerchantAccessOverviewQueryService;
import com.scott.payment.merchant.service.MerchantAccessOverviewQueryService.AccessRuleFacts;
import com.scott.payment.merchant.service.MerchantAccessOverviewQueryService.FeeFacts;
import com.scott.payment.merchant.service.MerchantAccessOverviewQueryService.MerchantProfileFacts;
import com.scott.payment.merchant.service.MerchantAccessOverviewQueryService.PaymentCapabilityFacts;
import com.scott.payment.merchant.service.MerchantAccessOverviewQueryService.SettlementFacts;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * 商户支付接入概览应用服务，基于真实密钥和访问规则计算当前接入可用状态。
 */
@Service
public class MerchantAccessOverviewApplicationService {

    private static final String ENABLED = "ENABLED";
    private static final String ACTIVE = "ACTIVE";
    private static final String PASSED = "PASSED";
    private static final String PENDING = "PENDING";
    private static final String PENDING_REVIEW = "PENDING_REVIEW";
    private static final int REQUIRED_STAGE_COUNT = 6;

    private final OpenApiMerchantKeyMaterialService keyMaterialService;
    private final MerchantAccessOverviewQueryService queryService;

    /**
     * 创建商户接入概览应用服务。
     *
     * @param keyMaterialService OpenAPI 密钥概要服务
     * @param queryService       商户访问规则统计服务
     */
    public MerchantAccessOverviewApplicationService(OpenApiMerchantKeyMaterialService keyMaterialService,
                                                    MerchantAccessOverviewQueryService queryService) {
        this.keyMaterialService = keyMaterialService;
        this.queryService = queryService;
    }

    /**
     * 查询当前认证商户的接入概览。
     *
     * <p>商户资料和技术接入缺口需要商户处理；结算、支付能力、费率和风控属于平台配置。
     * IP 白名单和店铺网址都是开放真实交易前的技术必检项；店铺网址是否必需不再依赖
     * 可选风控功能开关，避免商户资料不完整时误报接入可用。</p>
     *
     * @param merchantId 认证上下文中的商户号
     * @return 当前六阶段接入状态和非敏感配置摘要
     */
    public AccessOverview queryOverview(String merchantId) {
        String normalizedMerchantId = requireMerchantId(merchantId);
        LocalDateTime evaluationTime = LocalDateTime.now();
        MerchantProfileFacts profileFacts = queryService.findMerchantProfile(normalizedMerchantId);
        if (profileFacts == null) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "merchant not found");
        }

        MerchantProfileCheck merchantProfile = merchantProfileCheck(profileFacts);
        SettlementCheck settlement = settlementCheck(queryService.findSettlement(normalizedMerchantId));
        PaymentCapabilityCheck paymentCapability = paymentCapabilityCheck(
                queryService.countActivePaymentBindings(normalizedMerchantId, evaluationTime),
                queryService.listPaymentCapabilities(normalizedMerchantId, evaluationTime));
        PlatformConfigurationCheck fee = feeCheck(queryService.findFee(normalizedMerchantId));
        PlatformConfigurationCheck risk = riskCheck(profileFacts);

        OpenApiMerchantKeyMaterialVO material = keyMaterialService.queryMaterial(normalizedMerchantId);
        CredentialCheck credentials = credentialCheck(material);
        AccessRuleCheck ipWhitelist = accessRuleCheck(
                queryService.summarizeIpWhitelists(normalizedMerchantId), true, true);
        AccessRuleCheck sourceUrl = accessRuleCheck(
                queryService.summarizeSourceUrls(normalizedMerchantId), true, true);
        AccessCheckStatus technicalStatus = technicalStatus(credentials, ipWhitelist, sourceUrl);

        AccessOverview overview = new AccessOverview();
        overview.setMerchantProfile(merchantProfile);
        overview.setSettlement(settlement);
        overview.setPaymentCapability(paymentCapability);
        overview.setFee(fee);
        overview.setRisk(risk);
        overview.setTechnicalStatus(technicalStatus);
        overview.setCredentials(credentials);
        overview.setIpWhitelist(ipWhitelist);
        overview.setSourceUrl(sourceUrl);
        overview.setOpenApiBaseUrl(material == null ? null : material.getOpenApiBaseUrl());
        overview.setSdkVersion(material == null ? null : material.getSdkVersion());
        overview.setCryptoMode(material == null ? null : material.getCryptoMode());

        List<AccessCheckStatus> stageStatuses = List.of(
                merchantProfile.getStatus(),
                settlement.getStatus(),
                paymentCapability.getStatus(),
                fee.getStatus(),
                risk.getStatus(),
                technicalStatus);
        overview.setRequiredCheckCount(REQUIRED_STAGE_COUNT);
        overview.setCompletedRequiredChecks((int) stageStatuses.stream().filter(this::isReady).count());
        overview.setOverallStatus(overallStatus(merchantProfile.getStatus(), technicalStatus, stageStatuses));
        return overview;
    }

    /**
     * 商户资料阶段只有人工审核通过后才完成；待审核由平台处理，其余状态需要商户补充资料。
     */
    private MerchantProfileCheck merchantProfileCheck(MerchantProfileFacts facts) {
        MerchantProfileCheck check = new MerchantProfileCheck();
        check.setOnboardingStatus(facts.onboardingStatus());
        check.setReviewStatus(facts.reviewStatus());
        check.setActivationStatus(facts.activationStatus());
        check.setLastUpdatedTime(facts.lastUpdatedTime());
        if (PASSED.equalsIgnoreCase(facts.reviewStatus())) {
            check.setStatus(AccessCheckStatus.READY);
        } else if (PENDING.equalsIgnoreCase(facts.reviewStatus())
                || PENDING_REVIEW.equalsIgnoreCase(facts.onboardingStatus())) {
            check.setStatus(AccessCheckStatus.PENDING_REVIEW);
        } else {
            check.setStatus(AccessCheckStatus.ACTION_REQUIRED);
        }
        return check;
    }

    /**
     * 结算阶段只返回币种、周期和日切等非敏感摘要；缺失配置由平台继续处理。
     */
    private SettlementCheck settlementCheck(SettlementFacts facts) {
        SettlementCheck check = new SettlementCheck();
        if (facts == null) {
            check.setStatus(AccessCheckStatus.PENDING_REVIEW);
            return check;
        }
        check.setSettlementCurrency(facts.targetCurrency());
        check.setBusinessTimeZone(facts.businessTimeZone());
        check.setDailyCutoffTime(facts.dailyCutoffTime());
        check.setProcessingMode(facts.processingMode());
        check.setInitialDelayUnit(facts.initialDelayUnit());
        check.setInitialDelayDays(facts.initialDelayDays());
        check.setRegularDelayDays(facts.regularDelayDays());
        check.setSettlementFrequency(facts.settlementFrequency());
        check.setFrequencyDay(facts.frequencyDay());
        check.setLastUpdatedTime(facts.lastUpdatedTime());
        check.setStatus(settlementReady(facts)
                ? AccessCheckStatus.READY : AccessCheckStatus.PENDING_REVIEW);
        return check;
    }

    /**
     * 支付能力必须同时存在有效商户绑定和绑定交集，避免仅有失效 MID 时误报已开通。
     */
    private PaymentCapabilityCheck paymentCapabilityCheck(
            long activeBindingCount,
            List<PaymentCapabilityFacts> capabilityFacts) {
        List<PaymentCapabilityFacts> safeFacts = capabilityFacts == null ? List.of() : capabilityFacts;
        PaymentCapabilityCheck check = new PaymentCapabilityCheck();
        check.setActiveBindingCount(activeBindingCount);
        check.setCapabilities(safeFacts.stream().map(facts -> {
            PaymentCapability capability = new PaymentCapability();
            capability.setPaymentType(facts.paymentType());
            capability.setPaymentMethod(facts.paymentMethod());
            capability.setCurrency(facts.currency());
            return capability;
        }).toList());
        check.setLastUpdatedTime(safeFacts.stream()
                .map(PaymentCapabilityFacts::lastUpdatedTime)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null));
        check.setStatus(activeBindingCount > 0 && !safeFacts.isEmpty()
                ? AccessCheckStatus.READY : AccessCheckStatus.PENDING_REVIEW);
        return check;
    }

    /** 当前生效费率版本存在时，商户费率阶段才算完成。 */
    private PlatformConfigurationCheck feeCheck(FeeFacts facts) {
        PlatformConfigurationCheck check = new PlatformConfigurationCheck();
        if (facts != null) {
            check.setActiveVersionNo(facts.versionNo());
            check.setLastUpdatedTime(facts.lastUpdatedTime());
        }
        boolean ready = facts != null
                && facts.versionNo() != null
                && ACTIVE.equalsIgnoreCase(facts.versionStatus());
        check.setStatus(ready ? AccessCheckStatus.READY : AccessCheckStatus.PENDING_REVIEW);
        return check;
    }

    /**
     * 风控内部规则不向商户暴露。真实交易已激活可证明管理端门禁已通过，否则显示平台处理中。
     */
    private PlatformConfigurationCheck riskCheck(MerchantProfileFacts facts) {
        PlatformConfigurationCheck check = new PlatformConfigurationCheck();
        check.setRiskLevel(facts.riskLevel());
        check.setLastUpdatedTime(facts.lastUpdatedTime());
        check.setStatus(ACTIVE.equalsIgnoreCase(facts.activationStatus())
                ? AccessCheckStatus.READY : AccessCheckStatus.PENDING_REVIEW);
        return check;
    }

    /**
     * 计算 API 凭证、请求加密和响应解密材料是否全部可用。
     */
    private CredentialCheck credentialCheck(OpenApiMerchantKeyMaterialVO material) {
        CredentialCheck check = new CredentialCheck();
        if (material == null) {
            check.setStatus(AccessCheckStatus.ACTION_REQUIRED);
            check.setMerchantResponsePrivateKeyAvailable(false);
            return check;
        }
        check.setJwtKeyStatus(material.getJwtKeyStatus());
        check.setPlatformPayloadKeyStatus(material.getPlatformPayloadKeyStatus());
        check.setMerchantResponseKeyStatus(material.getMerchantResponseKeyStatus());
        check.setMerchantResponsePrivateKeyAvailable(material.isMerchantResponsePrivateKeyAvailable());
        check.setLastUpdatedTime(latest(
                material.getJwtUpdatedTime(),
                material.getPlatformPayloadUpdatedTime(),
                material.getMerchantResponseUpdatedTime()));
        boolean ready = isEnabled(material.getJwtKeyStatus())
                && isEnabled(material.getPlatformPayloadKeyStatus())
                && isEnabled(material.getMerchantResponseKeyStatus())
                && material.isMerchantResponsePrivateKeyAvailable();
        check.setStatus(ready ? AccessCheckStatus.READY : AccessCheckStatus.ACTION_REQUIRED);
        return check;
    }

    /**
     * 将一类访问规则统计转换为页面检查结果。
     */
    private AccessRuleCheck accessRuleCheck(AccessRuleFacts facts, boolean required, boolean enabled) {
        AccessRuleFacts safeFacts = facts == null
                ? new AccessRuleFacts(0, 0, 0, 0, 0, null)
                : facts;
        AccessRuleCheck check = new AccessRuleCheck();
        check.setRequired(required);
        check.setEnabled(enabled);
        check.setTotalCount(safeFacts.totalCount());
        check.setActiveCount(safeFacts.activeCount());
        check.setPendingCount(safeFacts.pendingCount());
        check.setRejectedCount(safeFacts.rejectedCount());
        check.setDisabledCount(safeFacts.disabledCount());
        check.setLastUpdatedTime(safeFacts.lastUpdatedTime());
        if (!required) {
            check.setStatus(AccessCheckStatus.NOT_REQUIRED);
        } else if (safeFacts.activeCount() > 0) {
            check.setStatus(AccessCheckStatus.READY);
        } else if (safeFacts.pendingCount() > 0) {
            check.setStatus(AccessCheckStatus.PENDING_REVIEW);
        } else {
            check.setStatus(AccessCheckStatus.ACTION_REQUIRED);
        }
        return check;
    }

    /**
     * 按阻断、待审核、可用的顺序汇总全部必检项。
     */
    private AccessCheckStatus technicalStatus(CredentialCheck credentials,
                                              AccessRuleCheck ipWhitelist,
                                              AccessRuleCheck sourceUrl) {
        Stream<AccessCheckStatus> requiredStatuses = Stream.of(
                credentials.getStatus(),
                ipWhitelist.getStatus(),
                Boolean.TRUE.equals(sourceUrl.getRequired()) ? sourceUrl.getStatus() : null)
                .filter(Objects::nonNull);
        var statuses = requiredStatuses.toList();
        if (statuses.contains(AccessCheckStatus.ACTION_REQUIRED)) {
            return AccessCheckStatus.ACTION_REQUIRED;
        }
        if (statuses.contains(AccessCheckStatus.PENDING_REVIEW)) {
            return AccessCheckStatus.PENDING_REVIEW;
        }
        return AccessCheckStatus.READY;
    }

    /**
     * 商户资料和技术阶段的缺口需要商户处理；其他未完成阶段由平台继续配置。
     */
    private AccessCheckStatus overallStatus(AccessCheckStatus merchantProfileStatus,
                                            AccessCheckStatus technicalStatus,
                                            List<AccessCheckStatus> stageStatuses) {
        if (merchantProfileStatus == AccessCheckStatus.ACTION_REQUIRED
                || technicalStatus == AccessCheckStatus.ACTION_REQUIRED) {
            return AccessCheckStatus.ACTION_REQUIRED;
        }
        if (stageStatuses.stream().anyMatch(status -> status == AccessCheckStatus.PENDING_REVIEW)) {
            return AccessCheckStatus.PENDING_REVIEW;
        }
        return AccessCheckStatus.READY;
    }

    /** 判断结算档案和结算周期字段是否形成可执行配置。 */
    private boolean settlementReady(SettlementFacts facts) {
        if (!StringUtils.hasText(facts.targetCurrency())
                || !StringUtils.hasText(facts.businessTimeZone())
                || !StringUtils.hasText(facts.dailyCutoffTime())
                || !StringUtils.hasText(facts.processingMode())
                || !StringUtils.hasText(facts.initialDelayUnit())
                || facts.initialDelayDays() == null
                || facts.regularDelayDays() == null
                || !StringUtils.hasText(facts.settlementFrequency())) {
            return false;
        }
        String frequency = facts.settlementFrequency().trim().toUpperCase(Locale.ROOT);
        return switch (frequency) {
            case "DAILY" -> facts.frequencyDay() == null;
            case "WEEKLY", "BIWEEKLY" -> between(facts.frequencyDay(), 1, 7);
            case "MONTHLY" -> between(facts.frequencyDay(), 1, 28);
            default -> false;
        };
    }

    /** 判断可空整数是否落在闭区间内。 */
    private boolean between(Integer value, int minimum, int maximum) {
        return value != null && value >= minimum && value <= maximum;
    }

    /**
     * 判断密钥状态是否为可用状态。
     */
    private boolean isEnabled(String status) {
        return ENABLED.equalsIgnoreCase(status);
    }

    /**
     * 判断检查项是否已满足。
     */
    private boolean isReady(AccessCheckStatus status) {
        return status == AccessCheckStatus.READY;
    }

    /**
     * 选择多个可空时间中的最近值。
     */
    private LocalDateTime latest(LocalDateTime... values) {
        return Stream.of(values)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
    }

    /**
     * 规范化认证上下文中的商户号，缺失时拒绝查询。
     */
    private String requireMerchantId(String merchantId) {
        if (!StringUtils.hasText(merchantId)) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED.getCode(), "merchant context missing");
        }
        return merchantId.trim();
    }
}
