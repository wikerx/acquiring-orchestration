package com.scott.payment.finance.fee.model;

import com.scott.payment.finance.fee.model.FeeCalculationModels.FeeRuleSnapshot;
import com.scott.payment.finance.fee.model.FeeCalculationModels.FeeTierSnapshot;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : FeeConfigurationSnapshotModels
 * @date : 2026-08-25 23:45
 * @email : scott_x@163.com
 * @description : 定义交易动作受理时冻结的不可变费用版本契约，固定百分比标签币种与 USD 固定费/上下限口径，不包含汇率、数据库或缓存访问。
 * @status : create
 */
public final class FeeConfigurationSnapshotModels {

    /** 当前可生产清分的费用快照结构版本。 */
    public static final int CURRENT_SCHEMA_VERSION = 3;

    /**
     * ISO币种，表示金额字段使用的币种。
     * <p>
     * 单位：无；格式：ISO 4217 三位大写币种代码；不允许为空；非敏感字段。
     * 取值范围：取值必须来自平台支持币种；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：决定 amount、fee、settlementAmount 等金额字段的小数位和币种语义。
     * </p>
     */
    private static final Pattern ISO_CURRENCY = Pattern.compile("[A-Z]{3}");
    /**
     * {@code SHA256}常量，统一 {@code FeeConfigurationSnapshotModels} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Set<RoundingMode> SUPPORTED_ROUNDING_MODES =
            Set.of(RoundingMode.HALF_UP, RoundingMode.HALF_EVEN, RoundingMode.DOWN);

    private FeeConfigurationSnapshotModels() {
    }

    /** 百分比费用计算基数；一期只允许动作标签金额。 */
    public enum PercentageBasis {
        LABEL_AMOUNT
    }

    /** 商户费用配置的固定币种口径，不是可由 Admin 修改的配置项。 */
    public enum FeeCurrencyPolicy {
        LABEL_PERCENTAGE_USD_FIXED_LIMITS
    }

    /** 退款时原交易手续费返还策略。 */
    public enum RefundFeeReturnPolicy {
        /**
         * NONE 枚举值，表示当前枚举定义中的一个受控业务取值。
         * <p>
         * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
         * </p>
         */
        NONE,
        /**
         * FULL 枚举值，表示当前枚举定义中的一个受控业务取值。
         * <p>
         * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
         * </p>
         */
        FULL,
        PROPORTIONAL
    }

    /** 保证金计算基数；一期只允许原支付标签金额。 */
    public enum ReserveBasis {
        LABEL_AMOUNT
    }

    /** 退款保证金处理策略；一期按原支付比例返还。 */
    public enum ReserveRefundPolicy {
        PROPORTIONAL_RETURN
    }

    /**
     * 费用版本冻结的保证金策略。
     *
     * @param reserveRate 保证金百分比，10 表示 10%
     * @param reserveBasis 保证金计算基数
     * @param delayUnit D 表示自然日，T 表示工作日
     * @param delayDays 保证金留存天数
     * @param refundPolicy 退款保证金返还策略
     */
    public record ReservePolicySnapshot(BigDecimal reserveRate,
                                        ReserveBasis reserveBasis,
                                        String delayUnit,
                                        int delayDays,
                                        ReserveRefundPolicy refundPolicy) {

        public ReservePolicySnapshot {
            Objects.requireNonNull(reserveRate, "reserve rate is required");
            Objects.requireNonNull(reserveBasis, "reserve basis is required");
            Objects.requireNonNull(refundPolicy, "reserve refund policy is required");
            if (reserveRate.signum() < 0 || reserveRate.compareTo(new BigDecimal("100")) > 0) {
                throw new IllegalArgumentException("reserve rate must be between 0 and 100");
            }
            if (!Set.of("D", "T").contains(delayUnit)) {
                throw new IllegalArgumentException("reserve delay unit must be D or T");
            }
            if (delayDays < 1) {
                throw new IllegalArgumentException("reserve delay days must be positive");
            }
        }
    }

    /**
     * 带业务匹配维度的单条费用规则快照。
     *
     * @param ruleId 费用规则数据库主键
     * @param feeCategory 费用分类
     * @param transactionType 交易动作类型
     * @param paymentType 支付类型
     * @param paymentMethod 支付方式或卡品牌
     * @param riskServiceType 风控服务类型；非风控费用使用 NONE
     * @param chargeTrigger 收费触发方式；非风控费用沿用现有 NOT_APPLICABLE 值并按成功动作收费
     * @param calculationRule 百分比标签币种、固定费和上下限 USD 的费用计算规则
     * @param tiers 阶梯规则；标准费率为空列表
     */
    public record FeeRuleConfigurationSnapshot(Long ruleId,
                                               String feeCategory,
                                               String transactionType,
                                               String paymentType,
                                               String paymentMethod,
                                               String riskServiceType,
                                               String chargeTrigger,
                                               FeeRuleSnapshot calculationRule,
                                               List<FeeTierSnapshot> tiers) {

        public FeeRuleConfigurationSnapshot {
            requirePositive(ruleId, "rule id");
            requireText(feeCategory, "fee category");
            requireText(transactionType, "transaction type");
            requireText(paymentType, "payment type");
            requireText(paymentMethod, "payment method");
            requireText(riskServiceType, "risk service type");
            requireText(chargeTrigger, "charge trigger");
            Objects.requireNonNull(calculationRule, "calculation rule is required");
            if (!ruleId.equals(calculationRule.ruleId())) {
                throw new IllegalArgumentException("rule identity must match calculation rule");
            }
            tiers = tiers == null ? List.of() : List.copyOf(tiers);
        }
    }

    /**
     * 单个交易动作冻结的完整费用版本。
     *
     * @param schemaVersion 快照 JSON 结构版本，当前必须为 3
     * @param merchantId 费用版本所属平台商户号，用于防止跨商户错配
     * @param feePlanId 费用方案主键
     * @param feePlanVersionId 不可变费用版本主键
     * @param feePlanVersionNo 方案内版本号
     * @param pricingLockTime 动作受理时费用锁定时间
     * @param settlementCurrency 商户目标结算币种；清分阶段不据此换汇
     * @param percentageBasis 百分比费用基数
     * @param feeCurrencyPolicy 固定费用币种口径
     * @param roundingMode 组件金额舍入规则
     * @param reserve 保证金策略
     * @param refundFeeReturnPolicy 原交易手续费返还策略
     * @param rules 当前动作冻结的候选费用规则
     * @param snapshotHash 排除本字段后规范化 JSON 的 SHA-256 小写十六进制摘要
     */
    public record FeeVersionSnapshot(int schemaVersion,
                                     String merchantId,
                                     Long feePlanId,
                                     Long feePlanVersionId,
                                     int feePlanVersionNo,
                                     LocalDateTime pricingLockTime,
                                     String settlementCurrency,
                                     PercentageBasis percentageBasis,
                                     FeeCurrencyPolicy feeCurrencyPolicy,
                                     RoundingMode roundingMode,
                                     ReservePolicySnapshot reserve,
                                     RefundFeeReturnPolicy refundFeeReturnPolicy,
                                     List<FeeRuleConfigurationSnapshot> rules,
                                     String snapshotHash) {

        public FeeVersionSnapshot {
            if (schemaVersion != CURRENT_SCHEMA_VERSION) {
                throw new IllegalArgumentException("unsupported fee snapshot schema version");
            }
            requireText(merchantId, "merchant id");
            requirePositive(feePlanId, "fee plan id");
            requirePositive(feePlanVersionId, "fee plan version id");
            if (feePlanVersionNo < 1) {
                throw new IllegalArgumentException("fee plan version number must be positive");
            }
            Objects.requireNonNull(pricingLockTime, "pricing lock time is required");
            if (settlementCurrency == null || !ISO_CURRENCY.matcher(settlementCurrency).matches()) {
                throw new IllegalArgumentException("settlement currency must be an uppercase ISO code");
            }
            Objects.requireNonNull(percentageBasis, "percentage basis is required");
            Objects.requireNonNull(feeCurrencyPolicy, "fee currency policy is required");
            if (!SUPPORTED_ROUNDING_MODES.contains(roundingMode)) {
                throw new IllegalArgumentException("unsupported fee rounding mode");
            }
            Objects.requireNonNull(reserve, "reserve policy is required");
            Objects.requireNonNull(refundFeeReturnPolicy, "refund fee return policy is required");
            rules = rules == null ? List.of() : List.copyOf(rules);
            if (rules.isEmpty()) {
                throw new IllegalArgumentException("fee snapshot rules must not be empty");
            }
            if (snapshotHash == null || !SHA256.matcher(snapshotHash).matches()) {
                throw new IllegalArgumentException("fee snapshot hash must be a lowercase SHA-256 value");
            }
        }
    }

    private static void requirePositive(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
    }
}
