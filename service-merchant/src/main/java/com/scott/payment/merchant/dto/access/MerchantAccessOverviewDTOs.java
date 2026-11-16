package com.scott.payment.merchant.dto.access;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 商户支付接入概览响应模型，只暴露配置状态和统计事实，不包含密钥、指纹或规则明细。
 */
public final class MerchantAccessOverviewDTOs {

    /**
     * 禁止实例化接入概览 DTO 聚合类。
     */
    private MerchantAccessOverviewDTOs() {
    }

    /**
     * 接入检查结果。
     */
    public enum AccessCheckStatus {
        /** 当前必备配置完整且可用。 */
        READY,
        /** 当前没有可用配置，但已有配置等待平台审核。 */
        PENDING_REVIEW,
        /** 缺少必备配置、配置已停用或配置已被拒绝。 */
        ACTION_REQUIRED,
        /** 对应能力未启用，不影响当前 OpenAPI 接入。 */
        NOT_REQUIRED
    }

    /**
     * 当前认证商户的支付接入概览。
     */
    @Data
    public static class AccessOverview {
        /** 当前全部必检项的综合状态。 */
        private AccessCheckStatus overallStatus;
        /** 已满足的必检项数量。 */
        private Integer completedRequiredChecks;
        /** 当前需要满足的必检项总数。 */
        private Integer requiredCheckCount;
        /** 当前环境 OpenAPI 基础地址。 */
        private String openApiBaseUrl;
        /** 推荐使用的商户 SDK 版本。 */
        private String sdkVersion;
        /** 当前请求和响应加解密模式。 */
        private String cryptoMode;
        /** 商户资料与人工审核阶段。 */
        private MerchantProfileCheck merchantProfile;
        /** 结算账户、币种、周期、频率和日切配置阶段。 */
        private SettlementCheck settlement;
        /** 平台实际为商户开通的支付方式和币种。 */
        private PaymentCapabilityCheck paymentCapability;
        /** 商户费率配置阶段，不暴露内部费率规则。 */
        private PlatformConfigurationCheck fee;
        /** 平台风控、限额和 3DS 配置阶段。 */
        private PlatformConfigurationCheck risk;
        /** 凭证和入口访问控制的综合检查结果。 */
        private AccessCheckStatus technicalStatus;
        /** API 凭证、请求加密和响应解密检查结果。 */
        private CredentialCheck credentials;
        /** OpenAPI 请求 IP 白名单检查结果。 */
        private AccessRuleCheck ipWhitelist;
        /** 商户来源网址限制检查结果。 */
        private AccessRuleCheck sourceUrl;
    }

    /**
     * 商户资料和人工审核阶段状态。
     */
    @Data
    public static class MerchantProfileCheck {
        /** 当前阶段状态。 */
        private AccessCheckStatus status;
        /** 商户进件流程状态。 */
        private String onboardingStatus;
        /** 平台人工审核状态。 */
        private String reviewStatus;
        /** 真实交易激活状态。 */
        private String activationStatus;
        /** 商户主档最近更新时间。 */
        private LocalDateTime lastUpdatedTime;
    }

    /**
     * 商户可见的结算配置摘要，不包含银行账号或其他敏感收款信息。
     */
    @Data
    public static class SettlementCheck {
        /** 当前阶段状态。 */
        private AccessCheckStatus status;
        /** 商户结算币种。 */
        private String settlementCurrency;
        /** 结算业务时区。 */
        private String businessTimeZone;
        /** 每日结算日切时间，格式 HH:mm:ss。 */
        private String dailyCutoffTime;
        /** 结算处理模式。 */
        private String processingMode;
        /** 首次结算延迟单位。 */
        private String initialDelayUnit;
        /** 首次结算延迟天数。 */
        private Integer initialDelayDays;
        /** 后续结算延迟天数。 */
        private Integer regularDelayDays;
        /** 结算频率。 */
        private String settlementFrequency;
        /** 周结或月结的执行日。 */
        private Integer frequencyDay;
        /** 结算档案或周期配置最近更新时间。 */
        private LocalDateTime lastUpdatedTime;
    }

    /**
     * 商户实际支付能力阶段摘要。
     */
    @Data
    public static class PaymentCapabilityCheck {
        /** 当前阶段状态。 */
        private AccessCheckStatus status;
        /** 当前有效的 MID 绑定数量，仅用于说明开通进度。 */
        private Long activeBindingCount;
        /** 去除渠道和 MID 信息后的支付能力。 */
        private List<PaymentCapability> capabilities = new ArrayList<>();
        /** 支付能力最近更新时间。 */
        private LocalDateTime lastUpdatedTime;
    }

    /**
     * 商户可见的支付方式、卡品牌和币种组合。
     */
    @Data
    public static class PaymentCapability {
        /** 支付类型，例如 BANK_CARD。 */
        private String paymentType;
        /** 卡品牌或非银行卡支付方式。 */
        private String paymentMethod;
        /** ISO 4217 Alpha-3 交易币种。 */
        private String currency;
    }

    /**
     * 平台负责配置的阶段状态；不暴露内部规则或配置标识。
     */
    @Data
    public static class PlatformConfigurationCheck {
        /** 当前阶段状态。 */
        private AccessCheckStatus status;
        /** 当前生效版本号；仅费率阶段可能返回。 */
        private Integer activeVersionNo;
        /** 商户风险等级；仅风控阶段可能返回。 */
        private Integer riskLevel;
        /** 当前配置最近更新时间。 */
        private LocalDateTime lastUpdatedTime;
    }

    /**
     * API 凭证和加解密材料检查结果。
     */
    @Data
    public static class CredentialCheck {
        /** 凭证检查综合状态。 */
        private AccessCheckStatus status;
        /** JWT 签名密钥状态。 */
        private String jwtKeyStatus;
        /** 平台请求载荷加密公钥状态。 */
        private String platformPayloadKeyStatus;
        /** 商户响应加密密钥状态。 */
        private String merchantResponseKeyStatus;
        /** 商户响应私钥是否已经生成并可用于 SDK 解密。 */
        private Boolean merchantResponsePrivateKeyAvailable;
        /** 三类密钥材料中的最近更新时间。 */
        private LocalDateTime lastUpdatedTime;
    }

    /**
     * IP 白名单或来源网址限制的配置统计和检查结果。
     */
    @Data
    public static class AccessRuleCheck {
        /** 当前检查结果。 */
        private AccessCheckStatus status;
        /** 当前能力是否属于 OpenAPI 可用性的必检项。 */
        private Boolean required;
        /** 对应风控功能是否已经启用；IP 白名单固定为启用。 */
        private Boolean enabled;
        /** 当前商户未删除配置总数。 */
        private Long totalCount;
        /** 审核通过且已启用的有效配置数量。 */
        private Long activeCount;
        /** 等待平台审核的配置数量。 */
        private Long pendingCount;
        /** 审核拒绝的配置数量。 */
        private Long rejectedCount;
        /** 审核通过但已停用的配置数量。 */
        private Long disabledCount;
        /** 对应配置的最近更新时间。 */
        private LocalDateTime lastUpdatedTime;
    }
}
