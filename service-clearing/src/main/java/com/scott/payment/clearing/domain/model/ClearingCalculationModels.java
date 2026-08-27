package com.scott.payment.clearing.domain.model;

import com.scott.payment.finance.fee.model.FeeCalculationModels.EntryDirection;
import com.scott.payment.finance.fee.model.FeeCalculationModels.FeeCalculationResult;
import com.scott.payment.finance.fee.model.FeeCalculationModels.TierContext;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.FeeRuleConfigurationSnapshot;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.FeeVersionSnapshot;
import com.scott.payment.finance.fee.model.FeeRefundCalculationModels.FeeRefundCommand;
import com.scott.payment.finance.fee.model.FeeRefundCalculationModels.FeeRefundResult;
import com.scott.payment.finance.money.model.Money;
import com.scott.payment.finance.reserve.model.ReserveCalculationModels.ReserveCalculationResult;
import com.scott.payment.finance.reserve.model.ReserveCalculationModels.ReserveReturnCommand;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingCalculationModels
 * @date : 2026-08-26 09:32
 * @email : scott_x@163.com
 * @description : 定义清分服务内部纯计算编排契约，保持本金、费用组件和保证金事实分离且不包含结算汇率。
 * @status : create
 */
public final class ClearingCalculationModels {

    private ClearingCalculationModels() {
    }

    /**
     * 单次动作清分计算命令。
     *
     * @param operation 数据库权威动作事实
     * @param feeSnapshot 动作受理时冻结并校验通过的费用版本
     * @param paymentType 支付类型，例如 BANK_CARD
     * @param paymentMethod 支付方式或品牌，例如 VISA；允许 ALL 规则匹配
     * @param occurredRiskServices 已实际调用的风险服务类型，不包含调用结果正文
     * @param tierContexts 按费用规则 ID 提供的数据库月累计事实
     * @param feeRefundCommand 原交易费用返还上下文；不返费时为空
     * @param reserveReturnCommand 退款保证金返还上下文；非退款动作为空
     */
    public record ClearingCalculationCommand(ClearingOperationFacts operation,
                                             FeeVersionSnapshot feeSnapshot,
                                             String paymentType,
                                             String paymentMethod,
                                             Set<String> occurredRiskServices,
                                             Map<Long, TierContext> tierContexts,
                                             FeeRefundCommand feeRefundCommand,
                                             ReserveReturnCommand reserveReturnCommand) {

        public ClearingCalculationCommand {
            Objects.requireNonNull(operation, "clearing operation facts are required");
            Objects.requireNonNull(feeSnapshot, "fee snapshot is required");
            occurredRiskServices = occurredRiskServices == null ? Set.of() : Set.copyOf(occurredRiskServices);
            tierContexts = tierContexts == null ? Map.of() : Map.copyOf(tierContexts);
        }
    }

    /**
     * 单条命中费用规则及其原币种组件结果。
     *
     * @param rule 规则匹配维度和不可变规则快照
     * @param tierContext 本动作使用的月累计 before/delta 事实
     * @param result 百分比标签币种、固定费 USD 和限额求值状态
     */
    public record CalculatedFee(FeeRuleConfigurationSnapshot rule,
                                TierContext tierContext,
                                FeeCalculationResult result) {
    }

    /**
     * 当前动作清分计算结果。
     *
     * @param principal 本金原币种金额；无本金动作为空
     * @param principalDirection 商户视角本金方向；无本金动作为空
     * @param fees 命中的交易费用组，保证金禁止混入
     * @param feeRefund 原交易实际收费返还结果；不返费时为空
     * @param reserve 单条保证金 HOLD 或 RETURN 结果；不计提时为空
     */
    public record ClearingCalculationResult(Money principal,
                                            EntryDirection principalDirection,
                                            List<CalculatedFee> fees,
                                            FeeRefundResult feeRefund,
                                            ReserveCalculationResult reserve) {

        public ClearingCalculationResult {
            fees = fees == null ? List.of() : List.copyOf(fees);
            if ((principal == null) != (principalDirection == null)) {
                throw new IllegalArgumentException("principal amount and direction must be present together");
            }
        }

        /** @return 是否至少产生一条本金、非零费用组件、返费或保证金清分事实 */
        public boolean required() {
            return principal != null || fees.stream().anyMatch(fee -> !fee.result().components().isEmpty())
                    || feeRefund != null && !feeRefund.components().isEmpty() || reserve != null;
        }
    }
}
