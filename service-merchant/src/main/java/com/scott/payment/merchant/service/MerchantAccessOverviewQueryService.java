package com.scott.payment.merchant.service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 查询商户接入配置的聚合事实，供接入概览应用服务计算可用状态。
 */
public interface MerchantAccessOverviewQueryService {

    /** 查询当前商户的进件、审核和激活状态。 */
    MerchantProfileFacts findMerchantProfile(String merchantId);

    /** 查询当前商户非敏感结算配置。 */
    SettlementFacts findSettlement(String merchantId);

    /** 查询当前生效商户费率版本摘要。 */
    FeeFacts findFee(String merchantId);

    /** 查询当前有效 MID 绑定数量。 */
    long countActivePaymentBindings(String merchantId, LocalDateTime now);

    /** 查询当前有效且去敏后的支付能力。 */
    List<PaymentCapabilityFacts> listPaymentCapabilities(String merchantId, LocalDateTime now);

    /**
     * 统计当前商户的 OpenAPI IP 白名单配置。
     *
     * @param merchantId 认证上下文中的商户号
     * @return IP 白名单统计事实
     */
    AccessRuleFacts summarizeIpWhitelists(String merchantId);

    /**
     * 统计当前商户的必需店铺网址配置。
     *
     * @param merchantId 认证上下文中的商户号
     * @return 来源网址统计事实
     */
    AccessRuleFacts summarizeSourceUrls(String merchantId);

    /**
     * 一类接入规则的只读统计事实。
     *
     * @param totalCount       未删除配置总数
     * @param activeCount      审核通过且已启用的配置数量
     * @param pendingCount     待审核配置数量
     * @param rejectedCount    审核拒绝配置数量
     * @param disabledCount    审核通过但已停用的配置数量
     * @param lastUpdatedTime  最近更新时间
     */
    record AccessRuleFacts(long totalCount,
                           long activeCount,
                           long pendingCount,
                           long rejectedCount,
                           long disabledCount,
                           LocalDateTime lastUpdatedTime) {
    }

    /** 商户主档开通状态事实。 */
    record MerchantProfileFacts(String onboardingStatus,
                                String reviewStatus,
                                String activationStatus,
                                String settlementCurrency,
                                Integer riskLevel,
                                LocalDateTime lastUpdatedTime) {
    }

    /** 商户结算档案和结算周期事实。 */
    record SettlementFacts(String targetCurrency,
                           String businessTimeZone,
                           String dailyCutoffTime,
                           String processingMode,
                           String initialDelayUnit,
                           Integer initialDelayDays,
                           Integer regularDelayDays,
                           String settlementFrequency,
                           Integer frequencyDay,
                           LocalDateTime lastUpdatedTime) {
    }

    /** 当前商户费率版本事实。 */
    record FeeFacts(Integer versionNo,
                    String versionStatus,
                    LocalDateTime lastUpdatedTime) {
    }

    /** 去除渠道和 MID 信息后的实际支付能力事实。 */
    record PaymentCapabilityFacts(String paymentType,
                                  String paymentMethod,
                                  String currency,
                                  LocalDateTime lastUpdatedTime) {
    }
}
