package com.scott.payment.payment.service.impl;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;

/** 暂无权威结果时保持原准备处理中，等待同键恢复和后台核验。 */
final class PreparationOutcomeUnknownException extends ServiceException {

    PreparationOutcomeUnknownException(Throwable cause) {
        super(ApiResultEnum.TRANSACTION_ACTION_IN_PROGRESS.getCode(), "Payment preparation outcome is not confirmed", cause);
    }
}
