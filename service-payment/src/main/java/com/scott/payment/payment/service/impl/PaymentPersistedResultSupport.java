package com.scott.payment.payment.service.impl;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.payment.api.internal.dto.PaymentCreateResultDTO;
import com.scott.payment.payment.domain.state.PaymentProcessStageEnum;
import com.scott.payment.payment.domain.state.PaymentTransactionStatusEnum;
import com.scott.payment.payment.entity.TransactionOperationDO;
import com.scott.payment.payment.entity.TransactionOrderDO;
import org.springframework.util.StringUtils;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentPersistedResultSupport
 * @date : 2026-10-25 00:00
 * @email : scott_x@163.com
 * @description : 支付同步响应的数据库事实覆盖器；仅在渠道结果 CAS 失败时使用，避免迟到同步响应覆盖并发回调终态。
 * @status : create
 */
final class PaymentPersistedResultSupport {

    private PaymentPersistedResultSupport() {
    }

    /**
     * 用最新动作单和生命周期主单覆盖对外响应中的权威状态及累计资金字段。
     *
     * @param result 当前同步渠道结果
     * @param operation 最新数据库动作事实
     * @param order 最新数据库生命周期事实
     * @return 已按数据库事实覆盖的原结果对象
     */
    static PaymentCreateResultDTO overwriteAuthoritativeFields(PaymentCreateResultDTO result,
                                                               TransactionOperationDO operation,
                                                               TransactionOrderDO order) {
        if (result == null || operation == null || order == null) {
            throw new IllegalStateException("persisted payment operation and order are required after CAS conflict");
        }
        result.setStatus(operation.getTransactionStatus());
        result.setProcessStage(operation.getProcessStage());
        result.setPendingReasonCode(operation.getPendingReasonCode());
        result.setFailReasonCode(operation.getFailReasonCode());
        result.setFailReasonMessage(merchantVisibleFailureMessage(operation));
        result.setAuthCode(operation.getAuthCode());
        result.setAcquirerReferenceNo(operation.getAcquirerReferenceNo());
        result.setMerchantResponseCode(merchantResponseCode(operation.getTransactionStatus()));
        result.setMerchantResponseMessage(merchantResponseMessage(operation));
        result.setTotalAuthorizedAmount(order.getAuthorizedAmount());
        result.setTotalAuthorizedCancelAmount(order.getAuthorizedCancelAmount());
        result.setTotalCapturedAmount(order.getCapturedAmount());
        result.setTotalRefundAmount(order.getRefundedAmount());
        result.setTotalRefuseAmount(order.getChargebackAmount());
        return result;
    }

    /**
     * 以幂等记录的最新状态覆盖异步确认前保存的旧响应快照。
     *
     * <p>幂等表只持有稳定状态而不持有完整渠道结果，因此这里只修正状态、阶段和统一商户响应；
     * 金额及渠道审计字段仍以原快照或交易查询接口为准。</p>
     *
     * @param result 幂等结果快照
     * @param transactionStatus 幂等记录中的最新交易状态
     * @return 已覆盖最新状态的结果
     */
    static PaymentCreateResultDTO overwriteLatestIdempotencyStatus(PaymentCreateResultDTO result,
                                                                    String transactionStatus) {
        if (result == null || !StringUtils.hasText(transactionStatus)) {
            return result;
        }
        result.setStatus(transactionStatus);
        result.setMerchantResponseCode(merchantResponseCode(transactionStatus));
        if (PaymentTransactionStatusEnum.SUCCESS.getCode().equals(transactionStatus)) {
            result.setProcessStage(PaymentProcessStageEnum.FINISHED.getCode());
            result.setPendingReasonCode(null);
            result.setFailReasonCode(null);
            result.setFailReasonMessage(null);
            result.setMerchantResponseMessage(ApiResultEnum.PAYMENT_SUCCESS.getMessage());
        } else if (PaymentTransactionStatusEnum.FAILED.getCode().equals(transactionStatus)) {
            result.setProcessStage(PaymentProcessStageEnum.FINISHED.getCode());
            result.setPendingReasonCode(null);
            result.setFailReasonCode(null);
            result.setFailReasonMessage(
                    "Payment failed. Please use the transaction ID to query details or contact support.");
            result.setMerchantResponseMessage(ApiResultEnum.PAYMENT_REJECTED.getMessage());
        } else if (PaymentTransactionStatusEnum.PENDING.getCode().equals(transactionStatus)) {
            result.setMerchantResponseMessage(ApiResultEnum.PENDING.getMessage());
        } else {
            result.setMerchantResponseMessage(ApiResultEnum.PROCESSING.getMessage());
        }
        return result;
    }

    private static String merchantResponseCode(String status) {
        if (PaymentTransactionStatusEnum.SUCCESS.getCode().equals(status)) {
            return ApiResultEnum.PAYMENT_SUCCESS.getCode();
        }
        if (PaymentTransactionStatusEnum.FAILED.getCode().equals(status)) {
            return ApiResultEnum.PAYMENT_REJECTED.getCode();
        }
        if (PaymentTransactionStatusEnum.PENDING.getCode().equals(status)) {
            return ApiResultEnum.PENDING.getCode();
        }
        return ApiResultEnum.PROCESSING.getCode();
    }

    private static String merchantResponseMessage(TransactionOperationDO operation) {
        String status = operation.getTransactionStatus();
        if (PaymentTransactionStatusEnum.SUCCESS.getCode().equals(status)) {
            return ApiResultEnum.PAYMENT_SUCCESS.getMessage();
        }
        if (PaymentTransactionStatusEnum.PENDING.getCode().equals(status)) {
            return ApiResultEnum.PENDING.getMessage();
        }
        if (!PaymentTransactionStatusEnum.FAILED.getCode().equals(status)) {
            return ApiResultEnum.PROCESSING.getMessage();
        }
        if (PaymentRiskDecisionSupport.isRiskRejected(operation.getFailReasonCode())) {
            return PaymentRiskDecisionSupport.MERCHANT_RISK_BLOCKED_MESSAGE;
        }
        return ApiResultEnum.PAYMENT_REJECTED.getMessage();
    }

    private static String merchantVisibleFailureMessage(TransactionOperationDO operation) {
        if (!PaymentTransactionStatusEnum.FAILED.getCode().equals(operation.getTransactionStatus())
                || !StringUtils.hasText(operation.getFailReasonCode())) {
            return null;
        }
        if (PaymentRiskDecisionSupport.isRiskRejected(operation.getFailReasonCode())) {
            return PaymentRiskDecisionSupport.MERCHANT_RISK_BLOCKED_MESSAGE;
        }
        return "Payment failed. Please use the transaction ID to query details or contact support.";
    }

}
