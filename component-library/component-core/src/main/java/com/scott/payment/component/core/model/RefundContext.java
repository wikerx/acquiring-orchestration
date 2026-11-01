package com.scott.payment.component.core.model;

import java.math.BigDecimal;

/**
 * 控制台退款弹窗的只读额度快照，所有金额均为标签币种；实际提交仍由支付核心锁定并校验额度。
 * @param currency 退款输入使用的标签币种
 * @param currencyExponent 标签币种的辅币精度
 * @param refundedAmount 已成功退款金额
 * @param pendingRefundAmount 处理中退款占用金额
 * @param availableRefundAmount 扣除处理中退款后的剩余可退金额
 */
public record RefundContext(String currency, int currencyExponent, BigDecimal refundedAmount,
                            BigDecimal pendingRefundAmount, BigDecimal availableRefundAmount) {
}
