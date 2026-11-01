package com.scott.payment.payment.service.impl;

import com.scott.payment.component.core.openapi.result.OpenApiMessageAudience;
import com.scott.payment.component.core.openapi.result.OpenApiMessageLocale;
import com.scott.payment.component.core.openapi.result.OpenApiTransactionProjection;
import com.scott.payment.component.core.openapi.result.OpenApiTransactionResultProjector;
import com.scott.payment.payment.api.internal.dto.PaymentCreateResultDTO;
import com.scott.payment.payment.entity.TransactionOperationDO;

/**
 * 支付核心到 OpenAPI V1.3 的纯结果投影适配器。
 *
 * <p>同步响应、幂等回放和通知快照必须复用同一套公开码与商户安全文案。该类不修改交易事实，
 * 也不允许渠道原始码或原始文案绕过受众规则进入商户响应。</p>
 */
final class PaymentOpenApiResultSupport {

    /**
     * 阻止仅提供静态能力的类型被实例化。
     */
    private PaymentOpenApiResultSupport() {
    }

    /**
     * 使用支付核心已有事实覆盖 DTO 的商户公开码和文案，不修改交易状态或失败证据。
     *
     * @param resultDTO 支付核心内部响应
     */
    static void applyMerchantProjection(PaymentCreateResultDTO resultDTO) {
        if (resultDTO == null) {
            return;
        }
        // 公开结果仅由平台状态、处理阶段和受控原因码决定，不能从动态 message 反推。
        OpenApiTransactionProjection projection = project(resultDTO);
        resultDTO.setMerchantResponseCode(projection.resultCode().getCode());
        resultDTO.setMerchantResponseMessage(projection.message(
                OpenApiMessageAudience.MERCHANT, OpenApiMessageLocale.EN_US));
        // 只有平台确认存在失败原因时才覆盖失败描述，避免处理中响应携带历史失败文案。
        if (projection.failureReasonCode() != null) {
            resultDTO.setFailReasonMessage(projection.message(
                    OpenApiMessageAudience.MERCHANT, OpenApiMessageLocale.EN_US));
        }
    }

    /**
     * 按内部响应中的状态、阶段和受控原因生成 V1.3 交易结果。
     */
    static OpenApiTransactionProjection project(PaymentCreateResultDTO resultDTO) {
        if (resultDTO == null) {
            return OpenApiTransactionResultProjector.project(null, null, null, null, null);
        }
        return OpenApiTransactionResultProjector.project(
                resultDTO.getStatus(), resultDTO.getProcessStage(), resultDTO.getFailReasonCode(),
                resultDTO.getPendingReasonCode(), resultDTO.getMerchantResponseCode());
    }

    /**
     * 按持久化动作事实生成 V1.3 交易结果，旧商户码不参与渠道归责。
     */
    static OpenApiTransactionProjection project(TransactionOperationDO operationDO) {
        if (operationDO == null) {
            return OpenApiTransactionResultProjector.project(null, null, null, null, null);
        }
        return OpenApiTransactionResultProjector.project(
                operationDO.getTransactionStatus(), operationDO.getProcessStage(), operationDO.getFailReasonCode(),
                operationDO.getPendingReasonCode(), null);
    }
}
