package com.scott.payment.admin.dto.merchant;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 管理端商户开通工作台响应模型，只暴露非敏感支付能力维度和配置缺口。
 */
public final class AdminMerchantActivationReadinessDTOs {

    private AdminMerchantActivationReadinessDTOs() {
    }

    /**
     * 商户支付能力、路由唯一性和费率覆盖检查摘要。
     */
    @Data
    public static class PaymentCapabilityReadiness {
        /** 当前时点有效的商户 MID 绑定数量。 */
        private Integer activeBindingCount;
        /** 引用停用 MID、停用渠道或空能力配置的绑定数量。 */
        private Integer unavailableBindingCount;
        /** 当前可用于交易路由的原子支付能力。 */
        private List<PaymentCapability> capabilities = new ArrayList<>();
        /** 同一能力存在最高优先级冲突的维度。 */
        private List<PaymentCapability> routeConflicts = new ArrayList<>();
        /** 当前生效费率未覆盖的能力维度。 */
        private List<PaymentCapability> feeGaps = new ArrayList<>();
        /** 当前生效费率同时命中多条规则的能力维度。 */
        private List<PaymentCapability> feeAmbiguities = new ArrayList<>();
    }

    /**
     * 不包含渠道、MID 或密钥信息的原子支付能力。
     */
    @Data
    public static class PaymentCapability {
        /** 平台交易类型，例如 PAYMENT、AUTHORIZATION、REFUND。 */
        private String transactionType;
        /** 支付类型，例如 BANK_CARD、PAYPAL。 */
        private String paymentType;
        /** 银行卡品牌或非银行卡能力的 ALL。 */
        private String paymentMethod;
        /** ISO 4217 Alpha-3 交易币种。 */
        private String currency;
    }
}
