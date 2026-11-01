package com.scott.payment.payment.service.impl;

import com.scott.payment.component.core.openapi.result.OpenApiResultCode;
import com.scott.payment.payment.api.internal.dto.PaymentCreateResultDTO;
import com.scott.payment.payment.domain.state.PaymentProcessStageEnum;
import com.scott.payment.payment.domain.state.PaymentTransactionStatusEnum;
import com.scott.payment.payment.entity.TransactionOperationDO;
import com.scott.payment.payment.entity.TransactionOrderDO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证同步结果 CAS 冲突后，对外结果以数据库终态及受控商户文案为准。
 */
class PaymentPersistedResultSupportTests {

    @Test
    void shouldReplaceStaleFailureWithPersistedSuccess() {
        PaymentCreateResultDTO result = new PaymentCreateResultDTO();
        result.setStatus(PaymentTransactionStatusEnum.FAILED.getCode());
        result.setFailReasonCode("CHANNEL_REQUEST_FAILED");
        TransactionOperationDO operation = new TransactionOperationDO();
        operation.setTransactionStatus(PaymentTransactionStatusEnum.SUCCESS.getCode());
        operation.setProcessStage(PaymentProcessStageEnum.FINISHED.getCode());
        operation.setAuthCode("AUTH-1");
        operation.setAcquirerReferenceNo("ARN-1");
        TransactionOrderDO order = successfulPaymentOrder();

        PaymentPersistedResultSupport.overwriteAuthoritativeFields(result, operation, order);

        assertThat(result.getStatus()).isEqualTo(PaymentTransactionStatusEnum.SUCCESS.getCode());
        assertThat(result.getFailReasonCode()).isNull();
        assertThat(result.getMerchantResponseCode()).isEqualTo(OpenApiResultCode.TRANSACTION_SUCCESS.getCode());
        assertThat(result.getAuthCode()).isEqualTo("AUTH-1");
        assertThat(result.getAcquirerReferenceNo()).isEqualTo("ARN-1");
        assertThat(result.getTotalAuthorizedAmount()).isEqualByComparingTo("12.34");
        assertThat(result.getTotalCapturedAmount()).isEqualByComparingTo("12.34");
    }

    @Test
    void shouldNotExposeInternalFailureMessage() {
        PaymentCreateResultDTO result = new PaymentCreateResultDTO();
        TransactionOperationDO operation = new TransactionOperationDO();
        operation.setTransactionStatus(PaymentTransactionStatusEnum.FAILED.getCode());
        operation.setProcessStage(PaymentProcessStageEnum.FINISHED.getCode());
        operation.setFailReasonCode("INTERNAL_DATABASE_ERROR");
        operation.setFailReasonMessage("jdbc://secret-host/private-db");
        operation.setChannelStatus("ERROR");

        PaymentPersistedResultSupport.overwriteAuthoritativeFields(result, operation, failedPaymentOrder());

        assertThat(result.getMerchantResponseCode()).isEqualTo(OpenApiResultCode.TRANSACTION_FAILED.getCode());
        assertThat(result.getFailReasonMessage()).doesNotContain("secret-host");
        assertThat(result.getMerchantResponseMessage()).isEqualTo(OpenApiResultCode.TRANSACTION_FAILED.getMessage());
    }

    @Test
    void shouldNotExposeRawChannelFailureDetails() {
        PaymentCreateResultDTO result = new PaymentCreateResultDTO();
        TransactionOperationDO operation = new TransactionOperationDO();
        operation.setTransactionStatus(PaymentTransactionStatusEnum.FAILED.getCode());
        operation.setProcessStage(PaymentProcessStageEnum.FINISHED.getCode());
        operation.setFailReasonCode("CHANNEL_DECLINED");
        operation.setFailReasonMessage("internal mapped failure");
        operation.setChannelStatus("DECLINED");
        operation.setChannelResponseCode("DO_NOT_HONOR_05");
        operation.setChannelResponseMessage("issuer raw decline detail");

        PaymentPersistedResultSupport.overwriteAuthoritativeFields(result, operation, failedPaymentOrder());

        assertThat(result.getFailReasonMessage())
                .isEqualTo("The transaction was declined by the issuer bank. "
                        + "Check the original order, then ask the payer to contact their bank "
                        + "or use another payment method when applicable.");
        assertThat(result.getMerchantResponseCode()).isEqualTo(OpenApiResultCode.CHANNEL_RETURNED_ERROR.getCode());
        assertThat(result.getMerchantResponseMessage())
                .isEqualTo("The transaction was declined by the issuer bank. "
                        + "Check the original order, then ask the payer to contact their bank "
                        + "or use another payment method when applicable.");
        assertThat(result.getMerchantResponseMessage())
                .doesNotContain("DO_NOT_HONOR_05", "issuer raw decline detail");
    }

    @Test
    void shouldOverwriteProcessingSnapshotWithLatestAsynchronousSuccess() {
        PaymentCreateResultDTO result = new PaymentCreateResultDTO();
        result.setStatus(PaymentTransactionStatusEnum.PROCESSING.getCode());
        result.setProcessStage(PaymentProcessStageEnum.WAITING_CALLBACK.getCode());
        result.setPendingReasonCode("CHANNEL_RESULT_UNKNOWN");
        result.setFailReasonCode("STALE_FAILURE");
        result.setFailReasonMessage("stale failure detail");

        PaymentPersistedResultSupport.overwriteLatestIdempotencyStatus(
                result, PaymentTransactionStatusEnum.SUCCESS.getCode());

        assertThat(result.getStatus()).isEqualTo(PaymentTransactionStatusEnum.SUCCESS.getCode());
        assertThat(result.getProcessStage()).isEqualTo(PaymentProcessStageEnum.FINISHED.getCode());
        assertThat(result.getPendingReasonCode()).isNull();
        assertThat(result.getFailReasonCode()).isNull();
        assertThat(result.getFailReasonMessage()).isNull();
        assertThat(result.getMerchantResponseCode()).isEqualTo(OpenApiResultCode.TRANSACTION_SUCCESS.getCode());
        assertThat(result.getMerchantResponseMessage()).isEqualTo(OpenApiResultCode.TRANSACTION_SUCCESS.getMessage());
    }

    @Test
    void shouldOverwriteProcessingSnapshotWithLatestAsynchronousFailure() {
        PaymentCreateResultDTO result = new PaymentCreateResultDTO();
        result.setStatus(PaymentTransactionStatusEnum.PROCESSING.getCode());
        result.setProcessStage(PaymentProcessStageEnum.WAITING_CALLBACK.getCode());
        result.setPendingReasonCode("CHANNEL_RESULT_UNKNOWN");
        result.setFailReasonCode("STALE_FAILURE");
        result.setFailReasonMessage("stale internal failure detail");

        PaymentPersistedResultSupport.overwriteLatestIdempotencyStatus(
                result, PaymentTransactionStatusEnum.FAILED.getCode());

        assertThat(result.getStatus()).isEqualTo(PaymentTransactionStatusEnum.FAILED.getCode());
        assertThat(result.getProcessStage()).isEqualTo(PaymentProcessStageEnum.FINISHED.getCode());
        assertThat(result.getPendingReasonCode()).isNull();
        assertThat(result.getFailReasonCode()).isNull();
        assertThat(result.getFailReasonMessage())
                .isEqualTo(OpenApiResultCode.TRANSACTION_FAILED.getMessage());
        assertThat(result.getMerchantResponseCode()).isEqualTo(OpenApiResultCode.TRANSACTION_FAILED.getCode());
        assertThat(result.getMerchantResponseMessage()).isEqualTo(OpenApiResultCode.TRANSACTION_FAILED.getMessage());
        assertThat(result.getFailReasonMessage()).doesNotContain("internal");
    }

    private TransactionOrderDO successfulPaymentOrder() {
        TransactionOrderDO order = new TransactionOrderDO();
        order.setTransactionAmount(new java.math.BigDecimal("12.34"));
        order.setCapturedAmount(new java.math.BigDecimal("12.34"));
        order.setAuthorizedAmount(new java.math.BigDecimal("12.34"));
        order.setAuthorizedCancelAmount(java.math.BigDecimal.ZERO);
        order.setRefundedAmount(java.math.BigDecimal.ZERO);
        order.setChargebackAmount(java.math.BigDecimal.ZERO);
        return order;
    }

    private TransactionOrderDO failedPaymentOrder() {
        TransactionOrderDO order = successfulPaymentOrder();
        order.setCapturedAmount(java.math.BigDecimal.ZERO);
        order.setAuthorizedAmount(java.math.BigDecimal.ZERO);
        return order;
    }
}
