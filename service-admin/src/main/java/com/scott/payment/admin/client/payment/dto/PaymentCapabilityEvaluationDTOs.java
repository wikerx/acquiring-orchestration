package com.scott.payment.admin.client.payment.dto;

import java.util.List;

/** 管理端所需的支付核心能力评估。 */
public final class PaymentCapabilityEvaluationDTOs {

    private PaymentCapabilityEvaluationDTOs() {
    }

    public record Request(String merchantId, List<String> itemKeys, String currency, String transactionType) {
    }

    public record Response(String assessmentStatus, List<Method> methods) {
    }

    public record Method(String itemKey, String authorizationStatus, String businessReadiness,
                         boolean missingFee, boolean missingMid, List<String> reasonCodes) {
    }
}
