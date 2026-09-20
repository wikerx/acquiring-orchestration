package com.scott.payment.payment.service.impl;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.payment.entity.TransactionOperationDO;
import com.scott.payment.payment.entity.TransactionOrderDO;
import com.scott.payment.payment.service.PaymentChannelRouteService;
import com.scott.payment.payment.service.dto.PaymentRouteResultDTO;
import org.springframework.util.StringUtils;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OriginalTransactionRouteResolver
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : Restores the immutable channel identity selected by the original transaction.
 * @status : create
 */
final class OriginalTransactionRouteResolver {

    private OriginalTransactionRouteResolver() {
    }

    static PaymentRouteResultDTO restore(PaymentChannelRouteService routeService,
                                         TransactionOrderDO sourceOrder,
                                         TransactionOperationDO sourceOperation) {
        PaymentRouteResultDTO snapshot = snapshot(sourceOrder, sourceOperation);
        if (snapshot == null || !StringUtils.hasText(snapshot.getChannelCode()) || snapshot.getMidConfigId() == null) {
            throw new ServiceException(ApiResultEnum.ORIGINAL_TRANSACTION_REJECTED);
        }
        if (hasFrozenRoute(sourceOperation)) {
            return snapshot;
        }
        return routeService.restore(
                snapshot.getChannelCode(),
                snapshot.getChannelId(),
                snapshot.getMidConfigId(),
                snapshot.getMidNo());
    }

    static PaymentRouteResultDTO snapshot(TransactionOrderDO sourceOrder,
                                          TransactionOperationDO sourceOperation) {
        PaymentRouteResultDTO frozenSnapshot = parseFrozenRoute(sourceOperation);
        if (frozenSnapshot != null) {
            return frozenSnapshot;
        }
        String channelCode = firstText(
                sourceOperation == null ? null : sourceOperation.getChannelCode(),
                sourceOrder == null ? null : sourceOrder.getChannelCode());
        Long channelId = sourceOperation != null && sourceOperation.getChannelId() != null
                ? sourceOperation.getChannelId()
                : sourceOrder == null ? null : sourceOrder.getChannelId();
        Long midConfigId = sourceOperation != null && sourceOperation.getChannelMidConfigId() != null
                ? sourceOperation.getChannelMidConfigId()
                : sourceOrder == null ? null : sourceOrder.getChannelMidConfigId();
        String fallbackMidNo = firstText(
                sourceOrder == null ? null : sourceOrder.getChannelMerchantId(),
                sourceOperation == null ? null : sourceOperation.getChannelTerminalId());
        if (!StringUtils.hasText(channelCode)) {
            return null;
        }
        PaymentRouteResultDTO snapshot = PaymentRouteResultDTO.routed(channelCode);
        snapshot.setChannelId(channelId);
        snapshot.setMidConfigId(midConfigId);
        snapshot.setMidNo(fallbackMidNo);
        snapshot.setRouteReason("RESTORED_FROM_ORIGINAL_TRANSACTION");
        return snapshot;
    }

    /**
     * 解析交易动作创建时冻结的原始渠道路由；快照损坏时拒绝回退到当前路由，避免误用新 MID。
     */
    private static PaymentRouteResultDTO parseFrozenRoute(TransactionOperationDO sourceOperation) {
        if (sourceOperation == null || !StringUtils.hasText(sourceOperation.getChannelRouteSnapshotJson())) {
            return null;
        }
        PaymentRouteResultDTO snapshot;
        try {
            snapshot = JsonUtils.parseObject(
                    sourceOperation.getChannelRouteSnapshotJson(), PaymentRouteResultDTO.class);
        } catch (RuntimeException exception) {
            throw new ServiceException(ApiResultEnum.ORIGINAL_TRANSACTION_REJECTED);
        }
        if (snapshot == null
                || !StringUtils.hasText(snapshot.getChannelCode())
                || snapshot.getChannelId() == null
                || snapshot.getMidConfigId() == null) {
            throw new ServiceException(ApiResultEnum.ORIGINAL_TRANSACTION_REJECTED);
        }
        if (StringUtils.hasText(sourceOperation.getChannelCode())
                && !sourceOperation.getChannelCode().equalsIgnoreCase(snapshot.getChannelCode())) {
            throw new ServiceException(ApiResultEnum.ORIGINAL_TRANSACTION_REJECTED);
        }
        if (sourceOperation.getChannelMidConfigId() != null
                && !sourceOperation.getChannelMidConfigId().equals(snapshot.getMidConfigId())) {
            throw new ServiceException(ApiResultEnum.ORIGINAL_TRANSACTION_REJECTED);
        }
        if (sourceOperation.getChannelId() != null
                && !sourceOperation.getChannelId().equals(snapshot.getChannelId())) {
            throw new ServiceException(ApiResultEnum.ORIGINAL_TRANSACTION_REJECTED);
        }
        return snapshot;
    }

    private static boolean hasFrozenRoute(TransactionOperationDO sourceOperation) {
        return sourceOperation != null && StringUtils.hasText(sourceOperation.getChannelRouteSnapshotJson());
    }

    private static String firstText(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }
}
