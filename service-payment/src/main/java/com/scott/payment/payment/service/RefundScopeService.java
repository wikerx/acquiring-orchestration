package com.scott.payment.payment.service;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.payment.entity.TransactionOrderDO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 退款范围金额规则服务，以商户标签本金和历史标签退款事实判定 FULL/PARTIAL，不按渠道金额或当前剩余额度误判全额退款。
 */
@Service
public class RefundScopeService {

    /**
     * 判断退款申请范围。
     *
     * @param sourceOrderDO 原生命周期主单
     * @param requestAmount 本次退款标签金额
     * @param pendingRefundAmount 受理前其他非终态退款标签金额
     * @return FULL 或 PARTIAL
     */
    public String resolve(TransactionOrderDO sourceOrderDO,
                          BigDecimal requestAmount,
                          BigDecimal pendingRefundAmount) {
        if (sourceOrderDO == null || requestAmount == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        BigDecimal originalPrincipal = zeroIfNull(sourceOrderDO.getLabelAmount());
        BigDecimal refundedAmount = zeroIfNull(sourceOrderDO.getLabelRefundedAmount());
        BigDecimal pendingAmount = zeroIfNull(pendingRefundAmount);
        boolean firstAndCompleteRefund = requestAmount.compareTo(originalPrincipal) == 0
                && refundedAmount.compareTo(BigDecimal.ZERO) == 0
                && pendingAmount.compareTo(BigDecimal.ZERO) == 0;
        return firstAndCompleteRefund ? "FULL" : "PARTIAL";
    }

    /**
     * 将空金额转换为零值。
     */
    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
