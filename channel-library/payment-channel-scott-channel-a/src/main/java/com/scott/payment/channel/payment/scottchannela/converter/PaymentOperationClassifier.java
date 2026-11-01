package com.scott.payment.channel.payment.scottchannela.converter;

import com.scott.payment.channel.payment.dto.request.ChannelPaymentRequest;
import com.scott.payment.channel.payment.enums.ChannelCapability;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 集中识别 ScottChannelA 交易类型，保证请求构造和响应解释使用相同分类规则。
 **/
final class PaymentOperationClassifier {

    /**
     * 禁止实例化无状态交易类型分类工具。
     **/
    private PaymentOperationClassifier() {
    }

    /**
     * 判断查询目标是否为退款动作。
     **/
    static boolean isRefundQuery(ChannelPaymentRequest request) {
        return request.getExtension().containsKey("refundId")
                || request.getExtension().containsKey("merchantRefundNo");
    }

    /**
     * 判断当前请求或查询目标是否属于卡授权生命周期。
     **/
    static boolean isCardAuthorizationRequest(ChannelPaymentRequest request) {
        String requestOperation = normalize(request.getTransactionType());
        String operation = effectiveOperation(request);
        return isCardAuthorizationOperation(operation)
                || (ChannelCapability.QUERY.getCode().equals(requestOperation) && isCardAuthorizationQuery(request));
    }

    /**
     * 解析当前写操作或查询所代表的原始交易类型。
     *
     * @param request 平台统一渠道请求
     * @return 规范化后的交易类型
     **/
    static String effectiveOperation(ChannelPaymentRequest request) {
        if (request == null) {
            return "";
        }
        String operation = normalize(request.getTransactionType());
        if (ChannelCapability.QUERY.getCode().equals(operation)) {
            return normalize(request.getExtension().get("originalTransactionType"));
        }
        return operation;
    }

    /**
     * 使用原交易类型或显式授权号识别卡授权查询。
     **/
    static boolean isCardAuthorizationQuery(ChannelPaymentRequest request) {
        String originalTransactionType = normalize(request.getExtension().get("originalTransactionType"));
        return isCardAuthorizationOperation(originalTransactionType)
                || StringUtils.hasText(request.getExtension().get("authorizationId"));
    }

    /**
     * 判断交易类型是否为渠道已经发布的卡授权写操作。
     **/
    static boolean isCardAuthorizationOperation(String operation) {
        return ChannelCapability.AUTHORIZATION.getCode().equals(operation)
                || ChannelCapability.PRE_AUTHORIZATION.getCode().equals(operation)
                || ChannelCapability.INCREMENTAL_AUTHORIZATION.getCode().equals(operation)
                || ChannelCapability.CAPTURE.getCode().equals(operation)
                || ChannelCapability.PRE_AUTH_COMPLETION.getCode().equals(operation)
                || ChannelCapability.VOID.getCode().equals(operation);
    }

    /**
     * 判断交易是否创建新的普通授权或预授权。
     **/
    static boolean isInitialAuthorization(String operation) {
        return ChannelCapability.AUTHORIZATION.getCode().equals(operation)
                || ChannelCapability.PRE_AUTHORIZATION.getCode().equals(operation);
    }

    /**
     * 将平台或渠道协议值统一为可比较的英文大写形式。
     **/
    static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
