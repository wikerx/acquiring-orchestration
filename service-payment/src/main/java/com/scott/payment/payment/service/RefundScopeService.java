package com.scott.payment.payment.service;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.payment.entity.TransactionOrderDO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RefundScopeService
 * @date : 2026-08-06 00:00
 * @description : 退款范围金额规则服务，以原始交易本金和历史退款事实判定 FULL/PARTIAL，不按当前剩余额度误判全额退款。
 * @status : create
 */
@Service
public class RefundScopeService {

    /**
     * 判断退款申请范围。
     *
     * @param sourceOrderDO 原生命周期主单
     * @param requestAmount 本次退款交易币种金额
     * @param pendingRefundAmount 受理前其他非终态退款金额
     * @return FULL 或 PARTIAL
     */
    public String resolve(TransactionOrderDO sourceOrderDO,
                          BigDecimal requestAmount,
                          BigDecimal pendingRefundAmount) {
        if (sourceOrderDO == null || requestAmount == null) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        BigDecimal originalPrincipal = zeroIfNull(sourceOrderDO.getTransactionAmount());
        BigDecimal refundedAmount = zeroIfNull(sourceOrderDO.getRefundedAmount());
        BigDecimal pendingAmount = zeroIfNull(pendingRefundAmount);
        boolean firstAndCompleteRefund = requestAmount.compareTo(originalPrincipal) == 0
                && refundedAmount.compareTo(BigDecimal.ZERO) == 0
                && pendingAmount.compareTo(BigDecimal.ZERO) == 0;
        return firstAndCompleteRefund ? "FULL" : "PARTIAL";
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
