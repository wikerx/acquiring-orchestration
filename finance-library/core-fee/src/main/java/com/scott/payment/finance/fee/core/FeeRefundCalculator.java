package com.scott.payment.finance.fee.core;

import com.scott.payment.finance.money.model.Money;
import com.scott.payment.finance.fee.model.FeeRefundCalculationModels.FeeRefundCommand;
import com.scott.payment.finance.fee.model.FeeRefundCalculationModels.FeeRefundComponent;
import com.scott.payment.finance.fee.model.FeeRefundCalculationModels.FeeRefundResult;
import com.scott.payment.finance.fee.model.FeeRefundCalculationModels.RefundableFeeComponent;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;

import static com.scott.payment.finance.fee.model.FeeCalculationModels.EntryDirection.CREDIT;
import static com.scott.payment.finance.fee.model.FeeRefundCalculationModels.FeeRefundPolicy.FULL;
import static com.scott.payment.finance.fee.model.FeeRefundCalculationModels.FeeRefundPolicy.NONE;
import static com.scott.payment.finance.fee.model.FeeRefundCalculationModels.FeeRefundPolicy.PROPORTIONAL;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : FeeRefundCalculator
 * @date : 2026-08-25 19:35
 * @email : scott_x@163.com
 * @description : 按原支付固化策略返还原实际收费组件，逐组件控制币种精度和累计返还上限。
 * @status : create
 */
public class FeeRefundCalculator {

    /**
     * 财务计算统一 MathContext，约束中间计算精度并避免过早舍入。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final MathContext CALCULATION_CONTEXT = MathContext.DECIMAL128;

    /**
     * 计算本次退款需要追加的费用返还组件。
     *
     * @param command 原策略、退款进度及可返原收费组件
     * @return 逐原组件的费用返还事实
     */
    public FeeRefundResult calculate(FeeRefundCommand command) {
        if (command.policy() == NONE) {
            return new FeeRefundResult(NONE, BigDecimal.ZERO, List.of());
        }
        if (command.policy() == FULL) {
            List<FeeRefundComponent> components = new ArrayList<>();
            for (RefundableFeeComponent source : command.sourceComponents()) {
                Money original = source.originalChargedAmount();
                BigDecimal remainingValue = original.amount().subtract(source.refundedAmountBefore().amount(),
                        CALCULATION_CONTEXT);
                Money remaining = new Money(remainingValue, original.currency(),
                        original.exponent()).rounded(command.roundingMode());
                if (remaining.amount().signum() > 0) {
                    Money zero = new Money(BigDecimal.ZERO, original.currency(),
                            original.exponent()).rounded(command.roundingMode());
                    components.add(new FeeRefundComponent(source.sourceComponentNo(), CREDIT, remaining, zero));
                }
            }
            return new FeeRefundResult(FULL, BigDecimal.ONE, components);
        }
        if (command.policy() == PROPORTIONAL) {
            BigDecimal ratio = command.refundLabelAmount().amount()
                    .divide(command.originalLabelAmount().amount(), CALCULATION_CONTEXT);
            boolean finalRefund = command.refundedLabelAmountBefore().amount()
                    .add(command.refundLabelAmount().amount(), CALCULATION_CONTEXT)
                    .compareTo(command.originalLabelAmount().amount()) == 0;
            List<FeeRefundComponent> components = new ArrayList<>();
            for (RefundableFeeComponent source : command.sourceComponents()) {
                Money original = source.originalChargedAmount();
                BigDecimal remainingValue = original.amount().subtract(source.refundedAmountBefore().amount(),
                        CALCULATION_CONTEXT);
                BigDecimal proportionalValue = original.amount().multiply(ratio, CALCULATION_CONTEXT);
                Money proportional = new Money(proportionalValue, original.currency(),
                        original.exponent()).rounded(command.roundingMode());
                BigDecimal returnedValue = finalRefund ? remainingValue : proportional.amount().min(remainingValue);
                Money returned = new Money(returnedValue, original.currency(),
                        original.exponent()).rounded(command.roundingMode());
                if (returned.amount().signum() > 0) {
                    Money remaining = new Money(remainingValue.subtract(returned.amount(),
                            CALCULATION_CONTEXT), original.currency(), original.exponent())
                            .rounded(command.roundingMode());
                    components.add(new FeeRefundComponent(source.sourceComponentNo(), CREDIT, returned, remaining));
                }
            }
            return new FeeRefundResult(PROPORTIONAL, ratio, components);
        }
        throw new IllegalArgumentException("unsupported fee refund policy");
    }
}
