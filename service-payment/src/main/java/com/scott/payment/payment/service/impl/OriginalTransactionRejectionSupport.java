package com.scott.payment.payment.service.impl;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.payment.api.internal.dto.PaymentCreateResultDTO;
import com.scott.payment.payment.domain.state.PaymentProcessStageEnum;
import com.scott.payment.payment.domain.state.PaymentTransactionStatusEnum;

/**
 * 原交易不可用于后续动作时的稳定结果映射。
 *
 * <p>内部拒绝原因统一投影为商户受众可见结果，不泄露原交易所属渠道、MID 或路由快照校验细节。</p>
 */
final class OriginalTransactionRejectionSupport {

    private OriginalTransactionRejectionSupport() {
    }

    static boolean isOriginalTransactionRejected(ServiceException exception) {
        return exception != null
                && ApiResultEnum.ORIGINAL_TRANSACTION_REJECTED.getCode().equals(exception.getCode());
    }

    static void apply(PaymentCreateResultDTO result) {
        // 后续动作未调用渠道，使用平台拒绝码结束，不能伪造 PSP 拒绝响应。
        result.setStatus(PaymentTransactionStatusEnum.FAILED.getCode());
        result.setProcessStage(PaymentProcessStageEnum.FINISHED.getCode());
        result.setFailReasonCode(ApiResultEnum.ORIGINAL_TRANSACTION_REJECTED.getCode());
        result.setFailReasonMessage(ApiResultEnum.ORIGINAL_TRANSACTION_REJECTED.getMessage());
        PaymentOpenApiResultSupport.applyMerchantProjection(result);
    }
}
