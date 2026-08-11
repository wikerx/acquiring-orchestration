package com.scott.payment.payment.service.impl;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.payment.api.internal.dto.PaymentCreateResultDTO;
import com.scott.payment.payment.domain.state.PaymentProcessStageEnum;
import com.scott.payment.payment.domain.state.PaymentTransactionStatusEnum;

/** Applies the stable merchant response for original-transaction route rejection. */
final class OriginalTransactionRejectionSupport {

    private OriginalTransactionRejectionSupport() {
    }

    static boolean isOriginalTransactionRejected(ServiceException exception) {
        return exception != null
                && ApiResultEnum.ORIGINAL_TRANSACTION_REJECTED.getCode().equals(exception.getCode());
    }

    static void apply(PaymentCreateResultDTO result) {
        result.setStatus(PaymentTransactionStatusEnum.FAILED.getCode());
        result.setProcessStage(PaymentProcessStageEnum.FINISHED.getCode());
        result.setFailReasonCode(ApiResultEnum.ORIGINAL_TRANSACTION_REJECTED.getCode());
        result.setFailReasonMessage(ApiResultEnum.ORIGINAL_TRANSACTION_REJECTED.getMessage());
        result.setMerchantResponseCode(ApiResultEnum.ORIGINAL_TRANSACTION_REJECTED.getCode());
        result.setMerchantResponseMessage(ApiResultEnum.ORIGINAL_TRANSACTION_REJECTED.getMessage());
    }
}
