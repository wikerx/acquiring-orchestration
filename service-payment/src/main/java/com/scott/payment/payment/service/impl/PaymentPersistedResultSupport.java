package com.scott.payment.payment.service.impl;

import com.scott.payment.component.core.openapi.result.OpenApiMessageAudience;
import com.scott.payment.component.core.openapi.result.OpenApiMessageLocale;
import com.scott.payment.component.core.openapi.result.OpenApiTransactionProjection;
import com.scott.payment.component.core.openapi.result.OpenApiTransactionResultProjector;
import com.scott.payment.payment.api.internal.dto.PaymentCreateResultDTO;
import com.scott.payment.payment.domain.state.PaymentProcessStageEnum;
import com.scott.payment.payment.domain.state.PaymentTransactionStatusEnum;
import com.scott.payment.payment.entity.TransactionOperationDO;
import com.scott.payment.payment.entity.TransactionOrderDO;
import org.springframework.util.StringUtils;

/**
 * 使用最新数据库事实修正支付同步响应。
 *
 * <p>该支持类只用于同步结果 CAS 失败或幂等快照滞后的场景。并发回调、主动查询或恢复任务一旦先提交，
 * 当前线程内的渠道响应就不再具有权威性，必须以主库动作单和生命周期主单为准。</p>
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
        // 动作单拥有当前动作状态、阶段和失败证据；禁止继续使用 CAS 失败线程中的旧渠道判断。
        result.setStatus(operation.getTransactionStatus());
        result.setProcessStage(operation.getProcessStage());
        result.setPendingReasonCode(operation.getPendingReasonCode());
        result.setFailReasonCode(operation.getFailReasonCode());
        // 对外码和文案必须由受控事实重新投影，渠道原始 message 只能留在内部审计字段。
        OpenApiTransactionProjection projection = project(operation);
        result.setFailReasonMessage(projection.failureReasonCode() == null ? null
                : projection.message(OpenApiMessageAudience.MERCHANT, OpenApiMessageLocale.EN_US));
        result.setAuthCode(operation.getAuthCode());
        result.setAcquirerReferenceNo(operation.getAcquirerReferenceNo());
        result.setMerchantResponseCode(projection.resultCode().getCode());
        result.setMerchantResponseMessage(projection.message(
                OpenApiMessageAudience.MERCHANT, OpenApiMessageLocale.EN_US));
        // 生命周期累计金额属于主单事实，和动作单状态一起覆盖才能返回一致的闭环快照。
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
        // 幂等表只在已确认终态时清理历史 pending/failure 原因，处理中快照仍保留原阶段证据。
        if (PaymentTransactionStatusEnum.SUCCESS.getCode().equals(transactionStatus)) {
            result.setProcessStage(PaymentProcessStageEnum.FINISHED.getCode());
            result.setPendingReasonCode(null);
            result.setFailReasonCode(null);
            result.setFailReasonMessage(null);
        } else if (PaymentTransactionStatusEnum.FAILED.getCode().equals(transactionStatus)) {
            result.setProcessStage(PaymentProcessStageEnum.FINISHED.getCode());
            result.setPendingReasonCode(null);
            result.setFailReasonCode(null);
            result.setFailReasonMessage(null);
        }
        // 状态变化后重新生成商户投影，避免重放旧响应时出现状态与公开码不一致。
        OpenApiTransactionProjection projection = project(result);
        result.setMerchantResponseCode(projection.resultCode().getCode());
        result.setMerchantResponseMessage(projection.message(
                OpenApiMessageAudience.MERCHANT, OpenApiMessageLocale.EN_US));
        if (projection.failureReasonCode() != null) {
            result.setFailReasonMessage(projection.message(
                    OpenApiMessageAudience.MERCHANT, OpenApiMessageLocale.EN_US));
        }
        return result;
    }

    /** 按权威动作事实生成商户安全投影，不使用渠道原始文案。 */
    private static OpenApiTransactionProjection project(TransactionOperationDO operation) {
        return OpenApiTransactionResultProjector.project(
                operation.getTransactionStatus(), operation.getProcessStage(), operation.getFailReasonCode(),
                operation.getPendingReasonCode(), null);
    }

    /** 按幂等响应快照生成商户安全投影。 */
    private static OpenApiTransactionProjection project(PaymentCreateResultDTO result) {
        return OpenApiTransactionResultProjector.project(
                result.getStatus(), result.getProcessStage(), result.getFailReasonCode(),
                result.getPendingReasonCode(), result.getMerchantResponseCode());
    }

}
