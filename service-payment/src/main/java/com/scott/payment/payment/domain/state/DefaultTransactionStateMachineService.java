package com.scott.payment.payment.domain.state;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.payment.entity.TransactionOrderDO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultTransactionStateMachineService
 * @date : 2026-07-14 19:35
 * @email : scott_x@163.com
 * @description : 收单交易状态机默认实现，位于 service-payment 领域状态层，集中保护授权、请款、退款和撤销等资金动作的状态与金额边界。
 * @status : create
 */
@Service
public class DefaultTransactionStateMachineService implements TransactionStateMachineService {

    /**
     * 允许发起增量授权的原始交易类型。
     */
    private static final Set<String> INCREMENTAL_AUTHORIZATION_SOURCE_TYPES = Set.of(
            PaymentTransactionTypeEnum.AUTHORIZATION.getCode(),
            PaymentTransactionTypeEnum.PRE_AUTHORIZATION.getCode()
    );

    /**
     * 允许发起请款的原始交易类型。
     */
    private static final Set<String> CAPTURE_SOURCE_TYPES = Set.of(
            PaymentTransactionTypeEnum.AUTHORIZATION.getCode(),
            PaymentTransactionTypeEnum.PRE_AUTHORIZATION.getCode(),
            PaymentTransactionTypeEnum.PRE_AUTH_COMPLETION.getCode()
    );

    /**
     * 允许发起退款的原始交易类型。
     */
    private static final Set<String> REFUND_SOURCE_TYPES = Set.of(
            PaymentTransactionTypeEnum.PAYMENT.getCode(),
            PaymentTransactionTypeEnum.AUTHORIZATION.getCode(),
            PaymentTransactionTypeEnum.PRE_AUTHORIZATION.getCode(),
            PaymentTransactionTypeEnum.PRE_AUTH_COMPLETION.getCode()
    );

    /**
     * 允许发起撤销的原始交易类型。
     */
    private static final Set<String> VOID_SOURCE_TYPES = Set.of(
            PaymentTransactionTypeEnum.AUTHORIZATION.getCode(),
            PaymentTransactionTypeEnum.PRE_AUTHORIZATION.getCode(),
            PaymentTransactionTypeEnum.PAYMENT.getCode()
    );

    /**
     * 校验后续交易动作是否允许发起。
     *
     * @param sourceOrderDO 原交易生命周期主单
     * @param nextTransactionType 后续交易类型
     * @param requestAmount 本次请求金额
     */
    @Override
    public void validateFollowUpAction(TransactionOrderDO sourceOrderDO,
                                       PaymentTransactionTypeEnum nextTransactionType,
                                       BigDecimal requestAmount,
                                       String requestCurrency) {
        validateSourceOrder(sourceOrderDO);
        if (PaymentTransactionTypeEnum.INCREMENTAL_AUTHORIZATION == nextTransactionType) {
            validateType(sourceOrderDO, INCREMENTAL_AUTHORIZATION_SOURCE_TYPES, nextTransactionType);
            validateCurrency(sourceOrderDO, requestCurrency, nextTransactionType);
            validatePositiveAmount(requestAmount, nextTransactionType);
            return;
        }
        if (PaymentTransactionTypeEnum.CAPTURE == nextTransactionType) {
            validateType(sourceOrderDO, CAPTURE_SOURCE_TYPES, nextTransactionType);
            validateCurrency(sourceOrderDO, requestCurrency, nextTransactionType);
            validateAvailableAmount(requestAmount, sourceOrderDO.getAvailableCaptureAmount(), nextTransactionType);
            return;
        }
        if (PaymentTransactionTypeEnum.REFUND == nextTransactionType) {
            validateType(sourceOrderDO, REFUND_SOURCE_TYPES, nextTransactionType);
            validateOptionalCurrency(sourceOrderDO, requestCurrency, nextTransactionType);
            validateAvailableAmount(requestAmount, sourceOrderDO.getAvailableRefundAmount(), nextTransactionType);
            return;
        }
        if (PaymentTransactionTypeEnum.VOID == nextTransactionType) {
            validateType(sourceOrderDO, VOID_SOURCE_TYPES, nextTransactionType);
            validateVoidAmount(sourceOrderDO);
            return;
        }
        throw new ServiceException(ApiResultEnum.TRANSACTION_TYPE_NOT_SUPPORTED);
    }

    private void validateSourceOrder(TransactionOrderDO sourceOrderDO) {
        if (sourceOrderDO == null) {
            throw new ServiceException(ApiResultEnum.ORDER_NOT_FOUND);
        }
        if (!PaymentTransactionStatusEnum.SUCCESS.getCode().equals(sourceOrderDO.getTransactionStatus())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "source transaction status does not allow follow-up action");
        }
        if (!StringUtils.hasText(sourceOrderDO.getTransactionCurrency())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "source transaction currency is empty");
        }
    }

    private void validateType(TransactionOrderDO sourceOrderDO,
                              Set<String> allowedSourceTypes,
                              PaymentTransactionTypeEnum nextTransactionType) {
        if (!allowedSourceTypes.contains(sourceOrderDO.getTransactionType())) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(),
                    nextTransactionType.getCode() + " is not allowed for source transaction type");
        }
    }

    private void validatePositiveAmount(BigDecimal requestAmount, PaymentTransactionTypeEnum nextTransactionType) {
        if (requestAmount == null || requestAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), nextTransactionType.getCode() + " amount must be greater than zero");
        }
    }

    private void validateCurrency(TransactionOrderDO sourceOrderDO,
                                  String requestCurrency,
                                  PaymentTransactionTypeEnum nextTransactionType) {
        if (!StringUtils.hasText(requestCurrency)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), nextTransactionType.getCode() + " currency is required");
        }
        if (!sourceOrderDO.getTransactionCurrency().equalsIgnoreCase(requestCurrency)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), nextTransactionType.getCode() + " currency must match source transaction currency");
        }
    }

    private void validateOptionalCurrency(TransactionOrderDO sourceOrderDO,
                                          String requestCurrency,
                                          PaymentTransactionTypeEnum nextTransactionType) {
        if (!StringUtils.hasText(requestCurrency)) {
            return;
        }
        if (!sourceOrderDO.getTransactionCurrency().equalsIgnoreCase(requestCurrency)) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), nextTransactionType.getCode() + " currency must match source transaction currency");
        }
    }

    private void validateAvailableAmount(BigDecimal requestAmount,
                                         BigDecimal availableAmount,
                                         PaymentTransactionTypeEnum nextTransactionType) {
        validatePositiveAmount(requestAmount, nextTransactionType);
        BigDecimal safeAvailableAmount = availableAmount == null ? BigDecimal.ZERO : availableAmount;
        if (requestAmount.compareTo(safeAvailableAmount) > 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), nextTransactionType.getCode() + " amount exceeds available amount");
        }
    }

    private void validateVoidAmount(TransactionOrderDO sourceOrderDO) {
        BigDecimal capturedAmount = sourceOrderDO.getCapturedAmount() == null ? BigDecimal.ZERO : sourceOrderDO.getCapturedAmount();
        BigDecimal refundedAmount = sourceOrderDO.getRefundedAmount() == null ? BigDecimal.ZERO : sourceOrderDO.getRefundedAmount();
        if (capturedAmount.compareTo(BigDecimal.ZERO) > 0 || refundedAmount.compareTo(BigDecimal.ZERO) > 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "captured or refunded transaction can not be voided");
        }
    }
}
